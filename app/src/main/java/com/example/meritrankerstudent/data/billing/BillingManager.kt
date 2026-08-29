package com.example.meritrankerstudent.data.billing

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.android.billingclient.api.*
import com.amplifyframework.core.Amplify
import com.example.meritrankerstudent.data.repository.AuthRepository
import com.example.meritrankerstudent.data.repository.DefaultAuthRepository
import com.example.meritrankerstudent.data.repository.DefaultGooglePlayBillingRepository
import com.example.meritrankerstudent.data.repository.GooglePlayBillingRepository
import com.example.meritrankerstudent.observability.AppObservability
import com.example.meritrankerstudent.observability.TelemetryEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * MeritRanker Application-Scoped BillingManager (PBL 9.1.0).
 * 
 * Production Lifecycle:
 * 1. Connects to Google Play Billing Service & loads real localized INAPP pricing.
 * 2. Launches Google Play purchase with deterministic SHA-256 user attribution (setObfuscatedAccountId).
 * 3. On PURCHASED: Transitions to Verifying state and calls MeritRanker backend.
 * 4. Idempotently grants credits in backend DynamoDB ledger.
 * 5. Consumes purchase via Google Play (consumeAsync) to satisfy acknowledgement & allow repeat purchases.
 * 6. Reconciles unconsumed purchases automatically on app resume / reconnection.
 */
class BillingManager private constructor(
    private val context: Context,
    private val customPacks: List<CreditPackConfig> = BillingConstants.DEFAULT_CREDIT_PACKS,
    private val billingRepository: GooglePlayBillingRepository = DefaultGooglePlayBillingRepository(),
    private val authRepository: AuthRepository = DefaultAuthRepository()
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
    private val processingTokens = ConcurrentHashMap.newKeySet<String>()

    private var lastPurchaseLaunchTimestamp = 0L
    private val launchDebounceThresholdMs = 1200L

    private var pendingLaunchPlanId: String? = null
    private var cachedUserId: String? = null

    init {
        initializePackStatePlaceholders()
        refreshCachedUserId()
        startBillingConnection()
    }

    private fun refreshCachedUserId() {
        try {
            Amplify.Auth.getCurrentUser(
                { user -> cachedUserId = user.userId },
                { _ -> }
            )
        } catch (_: Exception) {
            // Ignore
        }
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
     */
    fun queryProductCatalog() {
        if (!billingClient.isReady) {
            Log.w(tag, "Cannot query product catalog: BillingClient is not ready.")
            return
        }

        val configuredProductIds = BillingConstants.getConfiguredProductIds(customPacks)
        if (configuredProductIds.isEmpty()) {
            Log.i(tag, "No Google Play product IDs configured yet.")
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

                synchronized(productDetailsMap) {
                    productDetailsMap.clear()
                    for (pd in fetchedDetails) {
                        productDetailsMap[pd.productId] = pd
                        val oneTimeList = pd.oneTimePurchaseOfferDetailsList
                        if (oneTimeList != null && oneTimeList.isNotEmpty()) {
                            for (offer in oneTimeList) {
                                val token = offer.offerToken ?: ""
                                Log.i(
                                    tag,
                                    "  -> productId=${pd.productId}, purchaseOptionId=${offer.purchaseOptionId}, formattedPrice=${offer.formattedPrice}, offerTokenLength=${token.length}"
                                )
                            }
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
     * Launches the real Google Play Billing Flow for a chosen one-time credit pack.
     * Incorporates deterministic SHA-256 user attribution (setObfuscatedAccountId).
     */
    fun launchPurchase(activity: Activity, packConfig: CreditPackConfig): Boolean {
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastPurchaseLaunchTimestamp < launchDebounceThresholdMs) {
            Log.w(tag, "Purchase launch ignored: Debounce active.")
            return false
        }
        lastPurchaseLaunchTimestamp = currentTime

        if (_billingState.value is BillingState.Launching || _billingState.value is BillingState.Verifying) {
            Log.w(tag, "Purchase launch ignored: Another transaction is in progress.")
            return false
        }

        if (!packConfig.isConfigured) {
            _billingState.value = BillingState.Error(
                userMessage = "This credit pack is coming soon and will be available shortly.",
                responseCode = BillingClient.BillingResponseCode.ITEM_UNAVAILABLE
            )
            return false
        }

        if (!billingClient.isReady) {
            _billingState.value = BillingState.Connecting
            startBillingConnection()
            return false
        }

        val productDetails = synchronized(productDetailsMap) {
            productDetailsMap[packConfig.googlePlayProductId]
        }

        if (productDetails == null) {
            queryProductCatalog()
            _billingState.value = BillingState.Error(
                userMessage = "Product details unavailable. Refreshing Google Play catalog...",
                responseCode = BillingClient.BillingResponseCode.ITEM_UNAVAILABLE
            )
            return false
        }

        val oneTimeList = productDetails.oneTimePurchaseOfferDetailsList
        val selectedOffer = if (!packConfig.purchaseOptionId.isNullOrBlank() && oneTimeList != null) {
            oneTimeList.firstOrNull { it.purchaseOptionId == packConfig.purchaseOptionId }
                ?: oneTimeList.firstOrNull()
                ?: productDetails.oneTimePurchaseOfferDetails
        } else {
            oneTimeList?.firstOrNull() ?: productDetails.oneTimePurchaseOfferDetails
        }

        if (selectedOffer == null) {
            _billingState.value = BillingState.Error(
                userMessage = "No eligible purchase option available for this pack.",
                responseCode = BillingClient.BillingResponseCode.ITEM_UNAVAILABLE
            )
            return false
        }

        val offerToken = selectedOffer.offerToken ?: ""
        _billingState.value = BillingState.Launching(localPlanId = packConfig.localPlanId)
        pendingLaunchPlanId = packConfig.localPlanId

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        if (offerToken.isNotBlank()) {
            productDetailsParamsBuilder.setOfferToken(offerToken)
        }

        val productDetailsParamsList = listOf(productDetailsParamsBuilder.build())
        val flowParamsBuilder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)

        // Phase 2: Set safe, deterministic, non-PII Obfuscated Account ID
        val userId = cachedUserId
        val obfuscatedId = BillingSecurityUtils.generateObfuscatedAccountId(userId)
        if (!obfuscatedId.isNullOrBlank()) {
            flowParamsBuilder.setObfuscatedAccountId(obfuscatedId)
            Log.d(tag, "Attaching obfuscatedAccountId to billing flow: length=${obfuscatedId.length}")
        }

        val flowParams = flowParamsBuilder.build()
        AppObservability.analytics.logEvent(TelemetryEvent.PlayBillingSheetLaunched(packConfig.localPlanId))
        Log.i(tag, "Launching Google Play Billing Flow for productId=${packConfig.googlePlayProductId}")

        val billingResult = billingClient.launchBillingFlow(activity, flowParams)
        val responseCode = billingResult.responseCode

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
     * PurchasesUpdatedListener callback.
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
                Log.i(tag, "PURCHASE_CANCELLED: User dismissed Google Play checkout sheet.")
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

    /**
     * Processes Google Play purchase through the authoritative backend verification & credit grant pipeline.
     */
    private fun processPurchase(purchase: Purchase) {
        val productId = purchase.products.firstOrNull() ?: "unknown"
        val matchedPlan = BillingConstants.findPackByProductId(productId, customPacks)
        val localPlanId = matchedPlan?.localPlanId ?: pendingLaunchPlanId

        val payload = DetectedPurchasePayload.fromGooglePurchase(purchase, localPlanId)
        val token = purchase.purchaseToken
        val fingerprint = payload.tokenFingerprint

        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                Log.i(tag, "BILLING_PURCHASED: productId=$productId, tokenFingerprint=$fingerprint")
                AppObservability.analytics.logEvent(TelemetryEvent.PlayBillingPurchaseDetected(productId))

                if (processingTokens.contains(token)) {
                    Log.d(tag, "Purchase with token $fingerprint is already currently being processed.")
                    return
                }
                processingTokens.add(token)

                // Set state to Verifying with backend
                _billingState.value = BillingState.Verifying(payload)

                coroutineScope.launch {
                    try {
                        val userId = authRepository.getCurrentUserId() ?: cachedUserId ?: ""

                        Log.i(tag, "BACKEND_VERIFY_STARTED: tokenFingerprint=$fingerprint, productId=$productId")
                        val result = billingRepository.verifyGooglePlayPurchase(
                            productId = productId,
                            purchaseToken = token,
                            orderId = purchase.orderId,
                            authenticatedUserId = userId
                        )

                        result.onSuccess { verifyResult ->
                            Log.i(
                                tag,
                                "BACKEND_VERIFY_SUCCESS: creditsGranted=${verifyResult.creditsGranted}, creditsBalance=${verifyResult.creditsBalance}, alreadyProcessed=${verifyResult.isAlreadyProcessed}"
                            )

                            // Consume purchase in Google Play post-credit grant to satisfy acknowledgement
                            consumePurchaseAsync(token, fingerprint)

                            _billingState.value = BillingState.Success(
                                creditsGranted = verifyResult.creditsGranted,
                                updatedBalance = verifyResult.creditsBalance,
                                payload = payload
                            )
                        }.onFailure { error ->
                            Log.e(tag, "BACKEND_VERIFY_FAILED: tokenFingerprint=$fingerprint, error=${error.message}")
                            _billingState.value = BillingState.Error(
                                userMessage = "Could not verify purchase with server. If you were charged, your credits will be added shortly on sync.",
                                responseCode = BillingClient.BillingResponseCode.ERROR
                            )
                        }
                    } finally {
                        processingTokens.remove(token)
                    }
                }
            }

            Purchase.PurchaseState.PENDING -> {
                Log.i(tag, "PURCHASE_PENDING: tokenFingerprint=$fingerprint")
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
     * Consumes Google Play one-time INAPP purchase so it can be repurchased and acknowledged.
     */
    private fun consumePurchaseAsync(purchaseToken: String, tokenFingerprint: String) {
        if (!billingClient.isReady) {
            Log.w(tag, "BillingClient not ready to consume purchase $tokenFingerprint. Deferred to reconciliation.")
            return
        }

        Log.i(tag, "CONSUME_STARTED: tokenFingerprint=$tokenFingerprint")
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        billingClient.consumeAsync(params) { billingResult, _ ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.i(tag, "CONSUME_SUCCESS: tokenFingerprint=$tokenFingerprint (Acknowledgement satisfied)")
            } else {
                Log.e(
                    tag,
                    "CONSUME_FAILED: tokenFingerprint=$tokenFingerprint, code=${billingResult.responseCode}, message=${billingResult.debugMessage}"
                )
            }
        }
    }

    /**
     * Purchase Recovery / Reconciliation: Queries Google Play for active/unconsumed INAPP purchases.
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
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        Log.i(tag, "PURCHASE_RECOVERED: tokenFingerprint=${BillingSecurityUtils.computeTokenFingerprint(purchase.purchaseToken)}")
                    }
                    processPurchase(purchase)
                }
            } else {
                Log.w(tag, "queryPurchasesAsync failed: code=${billingResult.responseCode}, message=${billingResult.debugMessage}")
            }
        }
    }

    /**
     * Called on App Lifecycle Resume.
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
                if (debugMessage.isNotBlank()) "Google Play: $debugMessage" else "Payment could not be completed (Code: $code)."
        }
    }

    companion object {
        @Volatile
        private var instance: BillingManager? = null

        fun getInstance(
            context: Context,
            customPacks: List<CreditPackConfig> = BillingConstants.DEFAULT_CREDIT_PACKS,
            billingRepository: GooglePlayBillingRepository = DefaultGooglePlayBillingRepository(),
            authRepository: AuthRepository = DefaultAuthRepository()
        ): BillingManager {
            return instance ?: synchronized(this) {
                instance ?: BillingManager(
                    context.applicationContext,
                    customPacks,
                    billingRepository,
                    authRepository
                ).also { instance = it }
            }
        }
    }
}
