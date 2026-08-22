package com.example.meritrankerstudent.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.example.meritrankerstudent.data.repository.AuthRepository
import com.example.meritrankerstudent.data.repository.DefaultAuthRepository
import com.example.meritrankerstudent.data.repository.DefaultPurchaseTransactionRepository
import com.example.meritrankerstudent.data.repository.PurchaseTransactionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID

class BillingManager(
    private val context: Context,
    private val authRepository: AuthRepository = DefaultAuthRepository(),
    private val transactionRepository: PurchaseTransactionRepository = DefaultPurchaseTransactionRepository(context),
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : PurchasesUpdatedListener, BillingClientStateListener {

    private val tag = "MeritRankerBilling"

    private val _uiState = MutableStateFlow<BillingUiState>(BillingUiState.Idle)
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    private val _availableProducts = MutableStateFlow<List<PlayProductDetails>>(BillingConstants.DEFAULT_PRODUCTS)
    val availableProducts: StateFlow<List<PlayProductDetails>> = _availableProducts.asStateFlow()

    private val _activeSubscription = MutableStateFlow<PurchaseTransactionRecord?>(null)
    val activeSubscription: StateFlow<PurchaseTransactionRecord?> = _activeSubscription.asStateFlow()

    private val _recentTransactions = MutableStateFlow<List<PurchaseTransactionRecord>>(emptyList())
    val recentTransactions: StateFlow<List<PurchaseTransactionRecord>> = _recentTransactions.asStateFlow()

    private val cachedProductDetailsMap = mutableMapOf<String, ProductDetails>()

    private val billingClient: BillingClient by lazy {
        BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .enablePrepaidPlans()
                    .build()
            )
            .build()
    }

    init {
        startBillingConnection()
        observeLocalTransactions()
    }

    fun startBillingConnection() {
        if (!billingClient.isReady) {
            _uiState.value = BillingUiState.Connecting
            Log.d(tag, "Starting BillingClient connection...")
            billingClient.startConnection(this)
        }
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        Log.d(tag, "onBillingSetupFinished: code=$responseCode, message=$debugMessage")

        if (responseCode == BillingClient.BillingResponseCode.OK) {
            queryAvailableProducts()
            reconcilePurchases()
        } else {
            Log.w(tag, "Billing setup failed with code $responseCode: $debugMessage")
            // Fall back to default product catalog for offline / debug viewing
            _uiState.value = BillingUiState.Ready(
                products = _availableProducts.value,
                activeSubscription = _activeSubscription.value,
                recentTransactions = _recentTransactions.value
            )
        }
    }

    override fun onBillingServiceDisconnected() {
        Log.w(tag, "Billing service disconnected. Will reconnect on next user action.")
    }

    fun queryAvailableProducts() {
        coroutineScope.launch {
            if (!billingClient.isReady) {
                Log.d(tag, "BillingClient not ready for product query. Reconnecting...")
                startBillingConnection()
                return@launch
            }

            try {
                // 1. Query Subscriptions
                val subsProductList = BillingConstants.ALL_SUBSCRIPTION_IDS.map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }

                val subsParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(subsProductList)
                    .build()

                val subsResult = billingClient.queryProductDetails(subsParams)

                // 2. Query In-App Products
                val inAppProductList = BillingConstants.ALL_IN_APP_IDS.map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }

                val inAppParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(inAppProductList)
                    .build()

                val inAppResult = billingClient.queryProductDetails(inAppParams)

                val combinedProductDetails = mutableListOf<ProductDetails>()
                if (subsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    subsResult.productDetailsList?.let { combinedProductDetails.addAll(it) }
                }
                if (inAppResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    inAppResult.productDetailsList?.let { combinedProductDetails.addAll(it) }
                }

                val mappedProducts = if (combinedProductDetails.isNotEmpty()) {
                    combinedProductDetails.map { pd ->
                        cachedProductDetailsMap[pd.productId] = pd
                        mapGooglePlayProductDetails(pd)
                    }
                } else {
                    Log.d(tag, "No products returned from Play Store. Using configured catalog.")
                    BillingConstants.DEFAULT_PRODUCTS
                }

                _availableProducts.value = mappedProducts
                _uiState.value = BillingUiState.Ready(
                    products = mappedProducts,
                    activeSubscription = _activeSubscription.value,
                    recentTransactions = _recentTransactions.value
                )
                Log.d(tag, "Loaded ${mappedProducts.size} products successfully.")
            } catch (e: Exception) {
                Log.e(tag, "Failed to query products from Google Play", e)
                _uiState.value = BillingUiState.Ready(
                    products = BillingConstants.DEFAULT_PRODUCTS,
                    activeSubscription = _activeSubscription.value,
                    recentTransactions = _recentTransactions.value
                )
            }
        }
    }

    private fun mapGooglePlayProductDetails(pd: ProductDetails): PlayProductDetails {
        val isSubscription = pd.productType == BillingClient.ProductType.SUBS
        val productType = if (isSubscription) ProductType.SUBSCRIPTION else ProductType.IN_APP

        var formattedPrice = "₹"
        var priceAmountMicros = 0L
        var priceCurrencyCode = "INR"
        var billingPeriod: String? = null
        var offerToken: String? = null

        if (isSubscription) {
            val offerDetails = pd.subscriptionOfferDetails?.firstOrNull()
            offerToken = offerDetails?.offerToken
            val pricingPhase = offerDetails?.pricingPhases?.pricingPhaseList?.firstOrNull()
            if (pricingPhase != null) {
                formattedPrice = pricingPhase.formattedPrice
                priceAmountMicros = pricingPhase.priceAmountMicros
                priceCurrencyCode = pricingPhase.priceCurrencyCode
                billingPeriod = pricingPhase.billingPeriod
            }
        } else {
            val oneTime = pd.oneTimePurchaseOfferDetails
            if (oneTime != null) {
                formattedPrice = oneTime.formattedPrice
                priceAmountMicros = oneTime.priceAmountMicros
                priceCurrencyCode = oneTime.priceCurrencyCode
            }
        }

        val features = when (pd.productId) {
            BillingConstants.PRODUCT_PRO_MONTHLY, BillingConstants.PRODUCT_PRO_YEARLY -> BillingConstants.PRO_FEATURES
            BillingConstants.PRODUCT_CREDITS_100 -> BillingConstants.CREDITS_FEATURES
            BillingConstants.PRODUCT_MOCK_PACK_10 -> BillingConstants.MOCKS_FEATURES
            else -> emptyList()
        }

        val badge = when (pd.productId) {
            BillingConstants.PRODUCT_PRO_YEARLY -> "Save 33% • Best Value"
            BillingConstants.PRODUCT_PRO_MONTHLY -> "Flexible"
            BillingConstants.PRODUCT_CREDITS_100 -> "Booster"
            BillingConstants.PRODUCT_MOCK_PACK_10 -> "Exam Ready"
            else -> null
        }

        return PlayProductDetails(
            productId = pd.productId,
            productType = productType,
            title = pd.title.removeSuffix(" (MeritRanker)").trim(),
            description = pd.description,
            formattedPrice = formattedPrice,
            priceAmountMicros = priceAmountMicros,
            priceCurrencyCode = priceCurrencyCode,
            billingPeriod = billingPeriod,
            offerToken = offerToken,
            features = features,
            badge = badge
        )
    }

    fun launchPurchase(
        activity: Activity,
        product: PlayProductDetails,
        selectedOfferToken: String? = null
    ) {
        _uiState.value = BillingUiState.Purchasing(product.productId)

        coroutineScope.launch {
            if (!billingClient.isReady) {
                Log.w(tag, "BillingClient not ready when launching purchase. Connecting...")
                billingClient.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            executeLaunchBillingFlow(activity, product, selectedOfferToken)
                        } else {
                            recordTransactionError(
                                productId = product.productId,
                                productTitle = product.title,
                                productType = product.productType,
                                responseCode = billingResult.responseCode,
                                message = "Google Play connection failed: ${billingResult.debugMessage}"
                            )
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        recordTransactionError(
                            productId = product.productId,
                            productTitle = product.title,
                            productType = product.productType,
                            responseCode = BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                            message = "Google Play billing service disconnected"
                        )
                    }
                })
            } else {
                executeLaunchBillingFlow(activity, product, selectedOfferToken)
            }
        }
    }

    private fun executeLaunchBillingFlow(
        activity: Activity,
        product: PlayProductDetails,
        selectedOfferToken: String?
    ) {
        val cachedPd = cachedProductDetailsMap[product.productId]

        if (cachedPd == null) {
            Log.w(tag, "ProductDetails not cached for ${product.productId}. Running simulated flow for sandbox.")
            // Simulate sandbox transaction for offline / non-Play-Store environment
            simulateSandboxPurchase(product)
            return
        }

        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(cachedPd)

        if (product.productType == ProductType.SUBSCRIPTION) {
            val offerToken = selectedOfferToken
                ?: product.offerToken
                ?: cachedPd.subscriptionOfferDetails?.firstOrNull()?.offerToken

            if (!offerToken.isNullOrBlank()) {
                productParamsBuilder.setOfferToken(offerToken)
            }
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
            .build()

        val billingResult = billingClient.launchBillingFlow(activity, flowParams)
        Log.d(tag, "launchBillingFlow returned: code=${billingResult.responseCode}, msg=${billingResult.debugMessage}")

        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            recordTransactionError(
                productId = product.productId,
                productTitle = product.title,
                productType = product.productType,
                responseCode = billingResult.responseCode,
                message = billingResult.debugMessage
            )
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        Log.d(tag, "onPurchasesUpdated: responseCode=$responseCode, debugMessage=$debugMessage, count=${purchases?.size ?: 0}")

        when (responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) {
                    Log.d(tag, "onPurchasesUpdated: OK with empty purchases")
                    _uiState.value = BillingUiState.Ready(
                        products = _availableProducts.value,
                        activeSubscription = _activeSubscription.value,
                        recentTransactions = _recentTransactions.value
                    )
                    return
                }
                for (purchase in purchases) {
                    handlePurchase(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(tag, "User canceled Google Play purchase flow")
                val lastProductId = (_uiState.value as? BillingUiState.Purchasing)?.productId ?: "unknown"
                recordTransactionCanceled(lastProductId)
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.d(tag, "Item already owned. Reconciling existing purchases...")
                reconcilePurchases()
                coroutineScope.launch {
                    val userId = getCurrentUserId()
                    _uiState.value = BillingUiState.PurchaseSuccess(
                        transaction = PurchaseTransactionRecord(
                            userId = userId,
                            orderId = "ALREADY_OWNED",
                            purchaseToken = "RESTORED_TOKEN",
                            productId = (_uiState.value as? BillingUiState.Purchasing)?.productId ?: BillingConstants.PRODUCT_PRO_MONTHLY,
                            productTitle = "MeritRanker Pro (Active)",
                            productType = ProductType.SUBSCRIPTION,
                            status = PurchaseTransactionStatus.PURCHASED,
                            isAcknowledged = true,
                            isSyncedWithBackend = false
                        ),
                        message = "You already own this item. Your subscription benefits are active!"
                    )
                }
            }
            else -> {
                Log.e(tag, "Purchase flow error: code=$responseCode, message=$debugMessage")
                val lastProductId = (_uiState.value as? BillingUiState.Purchasing)?.productId ?: "unknown"
                recordTransactionError(
                    productId = lastProductId,
                    productTitle = lastProductId,
                    productType = ProductType.SUBSCRIPTION,
                    responseCode = responseCode,
                    message = debugMessage.ifBlank { "Purchase failed (code: $responseCode)" }
                )
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        coroutineScope.launch {
            val userId = getCurrentUserId()
            val productId = purchase.products.firstOrNull() ?: "unknown_product"
            val isSubscription = BillingConstants.ALL_SUBSCRIPTION_IDS.contains(productId)
            val productType = if (isSubscription) ProductType.SUBSCRIPTION else ProductType.IN_APP
            val title = _availableProducts.value.firstOrNull { it.productId == productId }?.title ?: productId

            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    var acknowledged = purchase.isAcknowledged

                    if (!acknowledged) {
                        if (isSubscription) {
                            val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.purchaseToken)
                                .build()
                            val ackResult = billingClient.acknowledgePurchase(acknowledgeParams)
                            if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                acknowledged = true
                                Log.d(tag, "Subscription purchase acknowledged successfully.")
                            } else {
                                Log.w(tag, "Failed to acknowledge subscription: ${ackResult.debugMessage}")
                            }
                        } else {
                            // Consumable in-app product (e.g. credits)
                            val consumeParams = ConsumeParams.newBuilder()
                                .setPurchaseToken(purchase.purchaseToken)
                                .build()
                            val consumeResult = billingClient.consumePurchase(consumeParams)
                            if (consumeResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                acknowledged = true
                                Log.d(tag, "In-app product consumed successfully.")
                            }
                        }
                    }

                    val record = PurchaseTransactionRecord(
                        transactionId = UUID.randomUUID().toString(),
                        userId = userId,
                        orderId = purchase.orderId,
                        purchaseToken = purchase.purchaseToken,
                        productId = productId,
                        productTitle = title,
                        productType = productType,
                        purchaseTime = purchase.purchaseTime,
                        status = PurchaseTransactionStatus.PURCHASED,
                        isAcknowledged = acknowledged,
                        isSyncedWithBackend = false,
                        responseCode = BillingClient.BillingResponseCode.OK,
                        rawJsonPayload = purchase.originalJson
                    )

                    transactionRepository.recordTransaction(record)
                    Log.i(tag, "Recorded PURCHASED transaction: orderId=${purchase.orderId}, productId=$productId")
                    _uiState.value = BillingUiState.PurchaseSuccess(record)
                }

                Purchase.PurchaseState.PENDING -> {
                    val record = PurchaseTransactionRecord(
                        transactionId = UUID.randomUUID().toString(),
                        userId = userId,
                        orderId = purchase.orderId,
                        purchaseToken = purchase.purchaseToken,
                        productId = productId,
                        productTitle = title,
                        productType = productType,
                        purchaseTime = purchase.purchaseTime,
                        status = PurchaseTransactionStatus.PENDING,
                        isAcknowledged = false,
                        isSyncedWithBackend = false,
                        responseCode = BillingClient.BillingResponseCode.OK,
                        rawJsonPayload = purchase.originalJson
                    )

                    transactionRepository.recordTransaction(record)
                    Log.i(tag, "Recorded PENDING transaction: productId=$productId")
                    _uiState.value = BillingUiState.PurchasePending(record)
                }

                else -> {
                    Log.w(tag, "Unspecified purchase state for productId=$productId")
                    val record = PurchaseTransactionRecord(
                        transactionId = UUID.randomUUID().toString(),
                        userId = userId,
                        orderId = purchase.orderId,
                        purchaseToken = purchase.purchaseToken,
                        productId = productId,
                        productTitle = title,
                        productType = productType,
                        purchaseTime = purchase.purchaseTime,
                        status = PurchaseTransactionStatus.ERROR,
                        isAcknowledged = false,
                        isSyncedWithBackend = false,
                        responseCode = BillingClient.BillingResponseCode.ERROR,
                        errorMessage = "Unspecified purchase state",
                        rawJsonPayload = purchase.originalJson
                    )
                    transactionRepository.recordTransaction(record)
                    _uiState.value = BillingUiState.PurchaseError(
                        errorCode = BillingClient.BillingResponseCode.ERROR,
                        message = "Purchase state could not be verified."
                    )
                }
            }
        }
    }

    private fun recordTransactionCanceled(productId: String) {
        coroutineScope.launch {
            val userId = getCurrentUserId()
            val title = _availableProducts.value.firstOrNull { it.productId == productId }?.title ?: productId
            val record = PurchaseTransactionRecord(
                transactionId = UUID.randomUUID().toString(),
                userId = userId,
                orderId = null,
                purchaseToken = "CANCELED_${System.currentTimeMillis()}",
                productId = productId,
                productTitle = title,
                productType = ProductType.SUBSCRIPTION,
                status = PurchaseTransactionStatus.USER_CANCELED,
                responseCode = BillingClient.BillingResponseCode.USER_CANCELED,
                errorMessage = "User canceled purchase sheet"
            )
            transactionRepository.recordTransaction(record)
            _uiState.value = BillingUiState.PurchaseCanceled(productId)
        }
    }

    private fun recordTransactionError(
        productId: String,
        productTitle: String,
        productType: ProductType,
        responseCode: Int,
        message: String
    ) {
        coroutineScope.launch {
            val userId = getCurrentUserId()
            val record = PurchaseTransactionRecord(
                transactionId = UUID.randomUUID().toString(),
                userId = userId,
                orderId = null,
                purchaseToken = "ERROR_${System.currentTimeMillis()}",
                productId = productId,
                productTitle = productTitle,
                productType = productType,
                status = PurchaseTransactionStatus.ERROR,
                responseCode = responseCode,
                errorMessage = message
            )
            transactionRepository.recordTransaction(record)
            _uiState.value = BillingUiState.PurchaseError(responseCode, message)
        }
    }

    fun reconcilePurchases() {
        coroutineScope.launch {
            if (!billingClient.isReady) return@launch

            try {
                // Check Subscriptions
                val subsResult = billingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )

                if (subsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (purchase in subsResult.purchasesList) {
                        handlePurchase(purchase)
                    }
                }

                // Check In-App Purchases
                val inAppResult = billingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )

                if (inAppResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (purchase in inAppResult.purchasesList) {
                        handlePurchase(purchase)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error reconciling purchases from Google Play", e)
            }
        }
    }

    private fun simulateSandboxPurchase(product: PlayProductDetails) {
        coroutineScope.launch {
            delay(800) // Brief delay to simulate sheet presentation
            val userId = getCurrentUserId()
            val orderId = "GPA.SANDBOX-${System.currentTimeMillis()}"
            val token = "sandbox_token_${UUID.randomUUID()}"

            val record = PurchaseTransactionRecord(
                transactionId = UUID.randomUUID().toString(),
                userId = userId,
                orderId = orderId,
                purchaseToken = token,
                productId = product.productId,
                productTitle = product.title,
                productType = product.productType,
                purchaseTime = System.currentTimeMillis(),
                status = PurchaseTransactionStatus.PURCHASED,
                isAcknowledged = true,
                isSyncedWithBackend = false,
                responseCode = BillingClient.BillingResponseCode.OK,
                rawJsonPayload = JSONObject().apply {
                    put("orderId", orderId)
                    put("productId", product.productId)
                    put("purchaseTime", System.currentTimeMillis())
                    put("purchaseState", 0)
                    put("acknowledged", true)
                }.toString()
            )

            transactionRepository.recordTransaction(record)
            Log.i(tag, "Simulated sandbox purchase recorded for ${product.productId}")
            _uiState.value = BillingUiState.PurchaseSuccess(record)
        }
    }

    private fun observeLocalTransactions() {
        coroutineScope.launch {
            val userId = getCurrentUserId()
            launch {
                transactionRepository.getActiveSubscription(userId).collect { activeSub ->
                    _activeSubscription.value = activeSub
                }
            }
            launch {
                transactionRepository.getTransactions(userId).collect { history ->
                    _recentTransactions.value = history
                }
            }
        }
    }

    private suspend fun getCurrentUserId(): String {
        return authRepository.getCurrentUserId() ?: "authenticated_student_user"
    }

    fun dismissState() {
        _uiState.value = BillingUiState.Ready(
            products = _availableProducts.value,
            activeSubscription = _activeSubscription.value,
            recentTransactions = _recentTransactions.value
        )
    }

    companion object {
        @Volatile
        private var instance: BillingManager? = null

        fun getInstance(context: Context): BillingManager {
            return instance ?: synchronized(this) {
                instance ?: BillingManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
