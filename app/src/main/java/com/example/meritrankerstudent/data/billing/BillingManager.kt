package com.example.meritrankerstudent.data.billing

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.android.billingclient.api.*
import com.example.meritrankerstudent.observability.AppObservability
import com.example.meritrankerstudent.observability.TelemetryEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single application-scoped BillingManager for Google Play Billing (PBL 9.1.0).
 * 
 * Strict Phase 1 Requirements:
 * - One-time credit pack purchases only (ProductType.INAPP)
 * - NO subscriptions
 * - NO recurring billing
 * - Authoritative Google-localized pricing from ProductDetails.oneTimePurchaseOfferDetails
 * - Zero calls to consumePurchase() (deferred until backend credit verification)
 * - Zero calls to acknowledgePurchase()
 * - Zero client-side credit granting (stops at PURCHASED_UNVERIFIED)
 * - Double-tap protection
 * - Safe token fingerprinting (SHA-256)
 * - Purchase recovery via queryPurchasesAsync on connect and app resume
 */
class BillingManager private constructor(
    private val context: Context,
    private val customPacks: List<CreditPackConfig> = BillingConstants.DEFAULT_CREDIT_PACKS
) : PurchasesUpdatedListener, BillingClientStateListener {

    private val tag = "MeritRankerBilling"
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    private val _billingState = MutableStateFlow<BillingState>(BillingState.Idle)
    val billingState: StateFlow<BillingState> = _billingState.asStateFlow()

    private val _packItemStates = MutableStateFlow<List<CreditPackItemState>>(emptyList())
    val packItemStates: StateFlow<List<CreditPackItemState>> = _packItemStates.asStateFlow()

    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    private var lastPurchaseLaunchTimestamp = 0L
    private val launchDebounceThresholdMs = 1200L

    private var pendingLaunchPlanId: String? = null

    // Future backend callback boundary
    var onPlayPurchaseDetectedListener: ((DetectedPurchasePayload) -> Unit)? = null

    init {
        initializePackStatePlaceholders()
        startBillingConnection()
    }

    private fun initializePackStatePlaceholders() {
        val initialItems = customPacks.map { config ->
            CreditPackItemState(
                config = config,
                googleProductDetails = null,
                isAvailable = false,
                isConfigured = config.isConfigured
            )
        }
        _packItemStates.value = initialItems
        _billingState.value = BillingState.Ready(packs = initialItems, isConnected = false)
    }

    fun startBillingConnection() {
        if (billingClient.isReady) {
            Log.d(tag, "BillingClient is already connected and ready.")
            queryProductCatalog()
            reconcilePurchases()
            return
        }

        _billingState.value = BillingState.Connecting
        Log.i(tag, "Connecting to Google Play Billing Service (PBL 9.1.0)...")
        billingClient.startConnection(this)
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage

        if (responseCode == BillingClient.BillingResponseCode.OK) {
            Log.i(tag, "Google Play BillingClient setup finished successfully.")
            queryProductCatalog()
            reconcilePurchases()
        } else {
            Log.e(tag, "Google Play BillingClient setup failed: code=$responseCode, message=$debugMessage")
            val userMsg = mapResponseCodeToUserMessage(responseCode, debugMessage)
            _billingState.value = BillingState.Error(userMessage = userMsg, responseCode = responseCode)
            _packItemStates.value = customPacks.map { config ->
                CreditPackItemState(
                    config = config,
                    googleProductDetails = null,
                    isAvailable = false,
                    isConfigured = config.isConfigured
                )
            }
        }
    }

    override fun onBillingServiceDisconnected() {
        Log.w(tag, "Google Play Billing service disconnected. Scheduling auto-reconnection...")
        _billingState.value = BillingState.Ready(packs = _packItemStates.value, isConnected = false)
        coroutineScope.launch {
            delay(3000)
            if (!billingClient.isReady) {
                Log.d(tag, "Retrying Google Play Billing connection...")
                startBillingConnection()
            }
        }
    }

    /**
     * Query Google Play Product Details for configured one-time INAPP products.
     * Uses PBL 9.1.0 QueryProductDetailsParams and handles unfetchedProductList diagnostics safely.
     */
    fun queryProductCatalog() {
        if (!billingClient.isReady) {
            Log.w(tag, "Cannot query product catalog: BillingClient is not ready.")
            return
        }

        val configuredProductIds = BillingConstants.getConfiguredProductIds(customPacks)
        if (configuredProductIds.isEmpty()) {
            Log.i(tag, "No Google Play product IDs configured yet. Displaying placeholder preview cards.")
            updatePackStatesWithDetails(emptyMap())
            _billingState.value = BillingState.Ready(packs = _packItemStates.value, isConnected = true)
            return
        }

        _billingState.value = BillingState.ProductLoading

        val productList = configuredProductIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        Log.d(tag, "Querying Google Play ProductDetails for INAPP products: $configuredProductIds")
        billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
            val responseCode = billingResult.responseCode
            if (responseCode == BillingClient.BillingResponseCode.OK) {
                val fetchedDetails = queryResult.productDetailsList
                val unfetched = queryResult.unfetchedProductList

                Log.i(tag, "Fetched ${fetchedDetails.size} Google Play products. Unfetched: ${unfetched.size}")

                for (item in unfetched) {
                    Log.w(tag, "Unfetched product: id=${item.productId}, statusCode=${item.statusCode}")
                }

                synchronized(productDetailsMap) {
                    productDetailsMap.clear()
                    for (pd in fetchedDetails) {
                        productDetailsMap[pd.productId] = pd
                        val oneTimeList = pd.oneTimePurchaseOfferDetailsList
                        if (oneTimeList != null && oneTimeList.isNotEmpty()) {
                            Log.i(tag, "Product ${pd.productId} has ${oneTimeList.size} eligible oneTimePurchaseOfferDetails:")
                            for (offer in oneTimeList) {
                                val optionId = offer.purchaseOptionId
                                val offerId = offer.offerId
                                val price = offer.formattedPrice
                                val token = offer.offerToken ?: ""
                                Log.i(tag, "  -> productId=${pd.productId}, purchaseOptionId=$optionId, offerId=$offerId, formattedPrice=$price, offerTokenPresent=${token.isNotBlank()}, offerTokenLength=${token.length}")
                            }
                        } else {
                            val singleOffer = pd.oneTimePurchaseOfferDetails
                            val singleToken = singleOffer?.offerToken ?: ""
                            Log.i(tag, "Product ${pd.productId} has single oneTimePurchaseOfferDetails: formattedPrice=${singleOffer?.formattedPrice}, offerTokenPresent=${singleToken.isNotBlank()}, offerTokenLength=${singleToken.length}")
                        }
                    }
                }

                updatePackStatesWithDetails(productDetailsMap)
                _billingState.value = BillingState.Ready(packs = _packItemStates.value, isConnected = true)
            } else {
                Log.e(tag, "Failed to query ProductDetails: code=$responseCode, message=${billingResult.debugMessage}")
                updatePackStatesWithDetails(emptyMap())
                _billingState.value = BillingState.Error(
                    userMessage = "Unable to load credit pack pricing from Google Play. Please try again.",
                    responseCode = responseCode
                )
            }
        }
    }

    private fun updatePackStatesWithDetails(detailsMap: Map<String, ProductDetails>) {
        val updatedItems = customPacks.map { config ->
            val pd = detailsMap[config.googlePlayProductId]
            val playProductDetails = pd?.let {
                val oneTimeList = it.oneTimePurchaseOfferDetailsList
                val selectedOffer = if (!config.purchaseOptionId.isNullOrBlank() && oneTimeList != null) {
                    oneTimeList.firstOrNull { offer -> offer.purchaseOptionId == config.purchaseOptionId }
                        ?: oneTimeList.firstOrNull()
                        ?: it.oneTimePurchaseOfferDetails
                } else {
                    oneTimeList?.firstOrNull() ?: it.oneTimePurchaseOfferDetails
                }

                PlayProductDetails(
                    productId = it.productId,
                    formattedPrice = selectedOffer?.formattedPrice ?: "Price Unavailable",
                    priceAmountMicros = selectedOffer?.priceAmountMicros ?: 0L,
                    priceCurrencyCode = selectedOffer?.priceCurrencyCode ?: "INR",
                    title = it.title,
                    description = it.description,
                    rawProductDetails = it
                )
            }

            CreditPackItemState(
                config = config,
                googleProductDetails = playProductDetails,
                isAvailable = playProductDetails != null,
                isConfigured = config.isConfigured
            )
        }

        _packItemStates.value = updatedItems
    }

    /**
     * Launches the real Google Play Billing Sheet for a chosen one-time credit pack.
     * Includes double-tap protection, connection verification, purchaseOption resolution, and offerToken selection.
     */
    fun launchPurchase(activity: Activity, packConfig: CreditPackConfig): Boolean {
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastPurchaseLaunchTimestamp < launchDebounceThresholdMs) {
            Log.w(tag, "Purchase launch ignored: Debounce threshold active (double-tap protection).")
            return false
        }
        lastPurchaseLaunchTimestamp = currentTime

        if (_billingState.value is BillingState.Launching) {
            Log.w(tag, "Purchase launch ignored: Another purchase flow is already launching.")
            return false
        }

        if (!packConfig.isConfigured) {
            Log.w(tag, "Cannot purchase pack ${packConfig.localPlanId}: Product ID is not yet configured in Play Console.")
            _billingState.value = BillingState.Error(
                userMessage = "This credit pack is coming soon and will be available shortly.",
                responseCode = BillingClient.BillingResponseCode.ITEM_UNAVAILABLE
            )
            return false
        }

        if (!billingClient.isReady) {
            Log.w(tag, "BillingClient not ready. Reconnecting before launch...")
            _billingState.value = BillingState.Connecting
            startBillingConnection()
            return false
        }

        val productDetails = synchronized(productDetailsMap) {
            productDetailsMap[packConfig.googlePlayProductId]
        }

        if (productDetails == null) {
            Log.e(tag, "ProductDetails not found for productId=${packConfig.googlePlayProductId}. Refreshing catalog...")
            queryProductCatalog()
            _billingState.value = BillingState.Error(
                userMessage = "Product details unavailable. Refreshing Google Play catalog...",
                responseCode = BillingClient.BillingResponseCode.ITEM_UNAVAILABLE
            )
            return false
        }

        // Select exact purchase option
        val oneTimeList = productDetails.oneTimePurchaseOfferDetailsList
        val selectedOffer = if (!packConfig.purchaseOptionId.isNullOrBlank() && oneTimeList != null) {
            oneTimeList.firstOrNull { it.purchaseOptionId == packConfig.purchaseOptionId }
                ?: oneTimeList.firstOrNull()
                ?: productDetails.oneTimePurchaseOfferDetails
        } else {
            oneTimeList?.firstOrNull() ?: productDetails.oneTimePurchaseOfferDetails
        }

        if (selectedOffer == null) {
            Log.e(tag, "No eligible one-time purchase offer found for productId=${packConfig.googlePlayProductId}")
            _billingState.value = BillingState.Error(
                userMessage = "No eligible purchase option available for this pack.",
                responseCode = BillingClient.BillingResponseCode.ITEM_UNAVAILABLE
            )
            return false
        }

        val offerToken = selectedOffer.offerToken ?: ""
        Log.i(
            tag,
            "Selected offer for launch: productId=${packConfig.googlePlayProductId}, purchaseOptionId=${selectedOffer.purchaseOptionId}, offerId=${selectedOffer.offerId}, formattedPrice=${selectedOffer.formattedPrice}, offerTokenPresent=${offerToken.isNotBlank()}, offerTokenLength=${offerToken.length}"
        )

        _billingState.value = BillingState.Launching(localPlanId = packConfig.localPlanId)
        pendingLaunchPlanId = packConfig.localPlanId

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        if (offerToken.isNotBlank()) {
            productDetailsParamsBuilder.setOfferToken(offerToken)
        }

        val productDetailsParamsList = listOf(productDetailsParamsBuilder.build())

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        AppObservability.analytics.logEvent(TelemetryEvent.PlayBillingSheetLaunched(packConfig.localPlanId))
        Log.i(tag, "Launching Google Play Billing Flow for productId=${packConfig.googlePlayProductId}")

        val billingResult = billingClient.launchBillingFlow(activity, flowParams)
        val responseCode = billingResult.responseCode
        Log.i(tag, "launchBillingFlow returned: code=$responseCode, message=${billingResult.debugMessage}")

        if (responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(tag, "launchBillingFlow returned error: code=$responseCode, message=${billingResult.debugMessage}")
            val userMsg = mapResponseCodeToUserMessage(responseCode, billingResult.debugMessage)
            _billingState.value = BillingState.Error(userMessage = userMsg, responseCode = responseCode)
            pendingLaunchPlanId = null
            return false
        }

        return true
    }

    /**
     * Google Play PurchasesUpdatedListener callback.
     * Evaluates purchase outcomes: OK, USER_CANCELED, ITEM_ALREADY_OWNED, and error states.
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage

        when (responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) {
                    Log.w(tag, "onPurchasesUpdated returned OK but purchase list was empty.")
                    _billingState.value = BillingState.Ready(packs = _packItemStates.value, isConnected = true)
                    return
                }

                for (purchase in purchases) {
                    processPurchase(purchase)
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i(tag, "User canceled Google Play purchase sheet. Returning to pack selection.")
                AppObservability.analytics.logEvent(TelemetryEvent.PlayBillingUserCanceled)
                pendingLaunchPlanId = null
                _billingState.value = BillingState.UserCanceled
                coroutineScope.launch {
                    delay(300)
                    _billingState.value = BillingState.Ready(packs = _packItemStates.value, isConnected = true)
                }
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.w(tag, "Item already owned. Reconciling existing purchases...")
                reconcilePurchases()
            }

            else -> {
                Log.e(tag, "onPurchasesUpdated error: code=$responseCode, message=$debugMessage")
                AppObservability.analytics.logEvent(TelemetryEvent.PlayBillingError(responseCode))
                val userMsg = mapResponseCodeToUserMessage(responseCode, debugMessage)
                _billingState.value = BillingState.Error(userMessage = userMsg, responseCode = responseCode)
                pendingLaunchPlanId = null
            }
        }
    }

    private fun processPurchase(purchase: Purchase) {
        val productId = purchase.products.firstOrNull() ?: "unknown"
        val matchedPlan = BillingConstants.findPackByProductId(productId, customPacks)
        val localPlanId = matchedPlan?.localPlanId ?: pendingLaunchPlanId

        val payload = DetectedPurchasePayload.fromGooglePurchase(purchase, localPlanId)
        Log.i(
            tag,
            "Detected Google Play purchase: productId=$productId, state=${purchase.purchaseState}, tokenFingerprint=${payload.tokenFingerprint}"
        )

        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                AppObservability.analytics.logEvent(TelemetryEvent.PlayBillingPurchaseDetected(productId))
                _billingState.value = BillingState.PurchasedUnverified(payload)
                onPlayPurchaseDetectedListener?.invoke(payload)
            }

            Purchase.PurchaseState.PENDING -> {
                Log.i(tag, "Purchase is in PENDING state (awaiting payment instrument clearance).")
                AppObservability.analytics.logEvent(TelemetryEvent.PlayBillingPending(productId))
                _billingState.value = BillingState.Pending(payload)
            }

            else -> {
                Log.w(tag, "Purchase in unspecified state: ${purchase.purchaseState}")
            }
        }

        pendingLaunchPlanId = null
    }

    /**
     * Purchase Recovery: Queries Google Play for active/unconsumed INAPP purchases.
     * Guarantees that purchases completed during process interruption or app backgrounding are detected.
     */
    fun reconcilePurchases() {
        if (!billingClient.isReady) {
            Log.d(tag, "Cannot reconcile purchases: BillingClient is not ready.")
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        Log.d(tag, "Reconciling one-time INAPP purchases with Google Play...")
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.i(tag, "Reconciliation returned ${purchases.size} INAPP purchases.")
                for (purchase in purchases) {
                    processPurchase(purchase)
                }
            } else {
                Log.w(tag, "queryPurchasesAsync failed: code=${billingResult.responseCode}, message=${billingResult.debugMessage}")
            }
        }
    }

    /**
     * Called on App Lifecycle Resume to ensure connection and recover any completed transactions.
     */
    fun onAppResume() {
        if (billingClient.isReady) {
            reconcilePurchases()
        } else {
            startBillingConnection()
        }
    }

    fun dismissState() {
        _billingState.value = BillingState.Ready(packs = _packItemStates.value, isConnected = billingClient.isReady)
    }

    private fun mapResponseCodeToUserMessage(code: Int, debugMessage: String): String {
        return when (code) {
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ->
                "Google Play billing service is temporarily unavailable. Please check your connection and try again."
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                "Google Play billing is not supported on this device or account."
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
                "This credit pack is currently unavailable. Please check back shortly."
            BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
                "Configuration error with Google Play. Please try again later."
            BillingClient.BillingResponseCode.ERROR ->
                "An unexpected payment error occurred. Please check your network and try again."
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                "You already have an active purchase pending processing. Verifying..."
            BillingClient.BillingResponseCode.NETWORK_ERROR ->
                "Network error connecting to Google Play. Please check your internet connection."
            else ->
                if (debugMessage.isNotBlank()) "Google Play Error: $debugMessage" else "Payment could not be completed (Code: $code)."
        }
    }

    companion object {
        @Volatile
        private var instance: BillingManager? = null

        fun getInstance(
            context: Context,
            customPacks: List<CreditPackConfig> = BillingConstants.DEFAULT_CREDIT_PACKS
        ): BillingManager {
            return instance ?: synchronized(this) {
                instance ?: BillingManager(context.applicationContext, customPacks).also { instance = it }
            }
        }
    }
}
