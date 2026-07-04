package com.webscare.urducanvas.di

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.webscare.urducanvas.BuildConfig
import com.webscare.urducanvas.common.datastore.PreferenceDataStoreAPI
import com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.PREF_IS_SUBSCRIBED
import com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.PREF_ACTIVE_PLAN
import com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.isSubscribedValue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: PreferenceDataStoreAPI
) : PurchasesUpdatedListener {

    companion object {
        val PLAN_PRODUCT_IDS = mapOf(
            1 to "urducanvas_monthly",
            2 to "urducanvas_6months",
            3 to "urducanvas_yearly"
        )
    }

    sealed class BillingState {
        object Idle : BillingState()
        object Loading : BillingState()
        data class ProductsLoaded(val products: List<ProductDetails>) : BillingState()
        data class PurchaseSuccess(val purchase: Purchase) : BillingState()
        data class Error(val message: String) : BillingState()
    }

    enum class SubscriptionStatus { NOT_SUBSCRIBED, TRIAL, ACTIVE, CANCELED, PENDING }

    data class PlayBillingSnapshot(
        val status: SubscriptionStatus = SubscriptionStatus.NOT_SUBSCRIBED,
        val productId: String? = null,
        val purchaseState: Int? = null,
        val isAutoRenewing: Boolean? = null,
        val isAcknowledged: Boolean? = null,
        val orderId: String? = null,
        val isTrial: Boolean = false,
        // ── NEW: expiry millis derived from purchaseTime + billing period ──────
        val expiryTimeMillis: Long? = null
    )

    private val _snapshot = MutableStateFlow(PlayBillingSnapshot())
    val snapshot: StateFlow<PlayBillingSnapshot> = _snapshot

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed

    private val _activePlan = MutableStateFlow<String?>(null)
    val activePlan: StateFlow<String?> = _activePlan

    private val _expiryDate = MutableStateFlow<Long?>(null)
    val expiryDate: StateFlow<Long?> = _expiryDate

    private val _isCancelled = MutableStateFlow(false)
    val isCancelled: StateFlow<Boolean> = _isCancelled

    // ─── Plan change ───────────────────────────────────────────────────────────

    fun launchPlanChange(activity: Activity, newPlanId: Int) {
        startConnection {
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ) { _, purchases ->
                val currentPurchase = purchases.firstOrNull {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
                }

                val productId = PLAN_PRODUCT_IDS[newPlanId] ?: return@queryPurchasesAsync
                val productDetails = availableProducts.find { it.productId == productId }
                    ?: return@queryPurchasesAsync
                val offerToken = productDetails.subscriptionOfferDetails
                    ?.firstOrNull()?.offerToken ?: return@queryPurchasesAsync

                val currentProductId = currentPurchase?.products?.firstOrNull()
                val currentRank = PLAN_PRODUCT_IDS.entries
                    .firstOrNull { it.value == currentProductId }?.key ?: 0
                val isUpgrade = newPlanId > currentRank

                val replacementMode = if (isUpgrade)
                    BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_PRORATED_PRICE
                else
                    BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.DEFERRED

                val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build()

                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productDetailsParams))
                    .apply {
                        if (currentPurchase != null) {
                            setSubscriptionUpdateParams(
                                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                                    .setOldPurchaseToken(currentPurchase.purchaseToken)
                                    .setSubscriptionReplacementMode(replacementMode)
                                    .build()
                            )
                        }
                    }
                    .build()

                activity.runOnUiThread {
                    billingClient.launchBillingFlow(activity, flowParams)
                }
            }
        }
    }

    // ─── Check on launch ───────────────────────────────────────────────────────

    fun checkSubscriptionOnLaunch() {
        if (BuildConfig.DEBUG) return
        startConnection {
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    val activePurchase = purchases.firstOrNull {
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                    }
                    val productId = activePurchase?.products?.firstOrNull()
                    saveSubscriptionStatus(activePurchase != null, productId)
                    _isCancelled.value = activePurchase != null && !activePurchase.isAutoRenewing

                    if (activePurchase != null) fetchExpiryDate(activePurchase)
                    else _expiryDate.value = null

                    publishSnapshot(purchases)
                }
            }
        }
    }

    fun refreshSnapshot() {
        startConnection {
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    publishSnapshot(purchases)
                }
            }
        }
    }

    // ─── Snapshot publishing ───────────────────────────────────────────────────

    private fun publishSnapshot(purchases: List<Purchase>) {
        val purchased = purchases.firstOrNull {
            it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        val pending = purchases.firstOrNull {
            it.purchaseState == Purchase.PurchaseState.PENDING
        }

        when {
            pending != null && purchased == null -> {
                _snapshot.value = PlayBillingSnapshot(
                    status = SubscriptionStatus.PENDING,
                    productId = pending.products.firstOrNull(),
                    purchaseState = pending.purchaseState,
                    isAutoRenewing = null,
                    isAcknowledged = pending.isAcknowledged,
                    orderId = pending.orderId,
                    isTrial = false,
                    expiryTimeMillis = null
                )
            }

            purchased != null -> {
                val pid = purchased.products.firstOrNull()
                val trial = isTrialOffer(pid)
                val status = when {
                    !purchased.isAutoRenewing -> SubscriptionStatus.CANCELED
                    trial -> SubscriptionStatus.TRIAL
                    else -> SubscriptionStatus.ACTIVE
                }
                // Derive expiry from purchaseTime + billing period using cached product details.
                // This is instant (no extra network call) when products are already loaded.
                val expiry = resolveExpiry(pid, purchased.purchaseTime)

                _snapshot.value = PlayBillingSnapshot(
                    status = status,
                    productId = pid,
                    purchaseState = purchased.purchaseState,
                    isAutoRenewing = purchased.isAutoRenewing,
                    isAcknowledged = purchased.isAcknowledged,
                    orderId = purchased.orderId,
                    isTrial = trial,
                    expiryTimeMillis = expiry
                )

                // Also keep the legacy _expiryDate StateFlow in sync.
                _expiryDate.value = expiry
            }

            else -> {
                _snapshot.value = PlayBillingSnapshot(SubscriptionStatus.NOT_SUBSCRIBED)
                _expiryDate.value = null
            }
        }
    }

    private fun publishActiveSnapshot(purchase: Purchase) {
        val pid = purchase.products.firstOrNull()
        val trial = isTrialOffer(pid)
        val expiry = resolveExpiry(pid, purchase.purchaseTime)
        _snapshot.value = PlayBillingSnapshot(
            status = if (trial) SubscriptionStatus.TRIAL else SubscriptionStatus.ACTIVE,
            productId = pid,
            purchaseState = purchase.purchaseState,
            isAutoRenewing = purchase.isAutoRenewing,
            isAcknowledged = true,
            orderId = purchase.orderId,
            isTrial = trial,
            expiryTimeMillis = expiry
        )
        _expiryDate.value = expiry
    }

    // ─── Expiry helpers ────────────────────────────────────────────────────────

    /**
     * Resolves expiry millis using cached [availableProducts] (no network call).
     * Falls back to null if product details aren't loaded yet; [fetchExpiryDate]
     * will fill in _expiryDate via the async path when needed.
     */
    private fun resolveExpiry(productId: String?, purchaseTimeMillis: Long): Long? {
        if (productId == null) return null
        val billingPeriod = availableProducts
            .find { it.productId == productId }
            ?.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.lastOrNull()
            ?.billingPeriod
        return if (billingPeriod != null) calculateExpiry(purchaseTimeMillis, billingPeriod)
        else null
    }

    private fun fetchExpiryDate(purchase: Purchase) {
        val productId = purchase.products.firstOrNull() ?: return

        // Fast path: use already-loaded product details.
        val cached = resolveExpiry(productId, purchase.purchaseTime)
        if (cached != null) {
            _expiryDate.value = cached
            return
        }

        // Slow path: query product details then compute.
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            ).build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val billingPeriod = productDetailsList.productDetailsList
                    ?.firstOrNull()
                    ?.subscriptionOfferDetails
                    ?.firstOrNull()
                    ?.pricingPhases
                    ?.pricingPhaseList
                    ?.lastOrNull()
                    ?.billingPeriod
                _expiryDate.value = calculateExpiry(purchase.purchaseTime, billingPeriod)
            }
        }
    }

    private fun calculateExpiry(purchaseTimeMillis: Long, billingPeriod: String?): Long {
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = purchaseTimeMillis
        }
        when (billingPeriod) {
            "P1M" -> calendar.add(java.util.Calendar.MONTH, 1)
            "P3M" -> calendar.add(java.util.Calendar.MONTH, 3)
            "P6M" -> calendar.add(java.util.Calendar.MONTH, 6)
            "P1Y" -> calendar.add(java.util.Calendar.YEAR, 1)
            else  -> calendar.add(java.util.Calendar.MONTH, 1)
        }
        return calendar.timeInMillis
    }

    // ─── Trial detection ───────────────────────────────────────────────────────

    private fun isTrialOffer(productId: String?): Boolean {
        if (productId == null) return false
        val product = availableProducts.find { it.productId == productId } ?: return false
        return product.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.any { it.priceAmountMicros == 0L } == true
    }

    // ─── Billing client setup ──────────────────────────────────────────────────

    private val _billingState = MutableStateFlow<BillingState>(BillingState.Idle)
    val billingState: StateFlow<BillingState> = _billingState

    private var availableProducts: List<ProductDetails> = emptyList()

    private var retryDelayMs = 2000L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    // ─── Connection ────────────────────────────────────────────────────────────

    fun startConnection(onReady: () -> Unit = {}) {
        if (billingClient.isReady) {
            retryDelayMs = 2000L
            onReady()
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    retryDelayMs = 2000L
                    onReady()
                } else {
                    _billingState.value =
                        BillingState.Error("Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                handler.postDelayed({
                    startConnection(onReady)
                }, retryDelayMs)
                retryDelayMs = (retryDelayMs * 2).coerceAtMost(60000L)
            }
        })
    }

    // ─── Query Products ────────────────────────────────────────────────────────

    fun queryProducts() {
        _billingState.value = BillingState.Loading

        startConnection {
            val productList = PLAN_PRODUCT_IDS.values.map { productId ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            billingClient.queryProductDetailsAsync(params) { result, queryResult ->
                val products = queryResult.productDetailsList
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    availableProducts = products
                    _billingState.value = BillingState.ProductsLoaded(products)
                } else {
                    _billingState.value =
                        BillingState.Error("Failed to load products: ${result.debugMessage}")
                }
            }
        }
    }

    // ─── Launch Purchase ───────────────────────────────────────────────────────

    fun launchPurchase(activity: Activity, planId: Int) {
        val productId = PLAN_PRODUCT_IDS[planId] ?: run {
            _billingState.value = BillingState.Error("Unknown plan ID: $planId")
            return
        }

        val productDetails = availableProducts.find { it.productId == productId } ?: run {
            _billingState.value = BillingState.Error("Product not found. Try again.")
            return
        }

        val offerToken = productDetails.subscriptionOfferDetails
            ?.firstOrNull()?.offerToken ?: run {
            _billingState.value = BillingState.Error("No offer available for this plan.")
            return
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    // ─── Purchase Result ───────────────────────────────────────────────────────

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> acknowledgePurchase(purchase)
                        Purchase.PurchaseState.PENDING -> {
                            _snapshot.value = PlayBillingSnapshot(
                                status = SubscriptionStatus.PENDING,
                                productId = purchase.products.firstOrNull(),
                                purchaseState = purchase.purchaseState,
                                isAcknowledged = purchase.isAcknowledged,
                                orderId = purchase.orderId
                            )
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _billingState.value = BillingState.Idle
            }
            else -> {
                _billingState.value =
                    BillingState.Error("Purchase failed: ${result.debugMessage}")
            }
        }
    }

    // ─── Acknowledge ───────────────────────────────────────────────────────────

    private fun acknowledgePurchase(purchase: Purchase) {
        val productId = purchase.products.firstOrNull()
        _isCancelled.value = false

        if (purchase.isAcknowledged) {
            _billingState.value = BillingState.PurchaseSuccess(purchase)
            saveSubscriptionStatus(true, productId)
            publishActiveSnapshot(purchase)
            return
        }

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _billingState.value = BillingState.PurchaseSuccess(purchase)
                saveSubscriptionStatus(true, productId)
                publishActiveSnapshot(purchase)
            } else {
                _billingState.value =
                    BillingState.Error("Acknowledgement failed: ${result.debugMessage}")
            }
        }
    }

    // ─── Restore ───────────────────────────────────────────────────────────────

    fun restorePurchases() {
        startConnection {
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK
                    && purchases.isNotEmpty()
                ) {
                    val activePurchase = purchases.firstOrNull {
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                    }
                    if (activePurchase != null) {
                        val productId = activePurchase.products.firstOrNull()
                        _billingState.value = BillingState.PurchaseSuccess(activePurchase)
                        saveSubscriptionStatus(true, productId)
                    } else {
                        _billingState.value = BillingState.Error("No active subscription found.")
                    }
                    publishSnapshot(purchases)
                } else {
                    _billingState.value = BillingState.Error("Nothing to restore.")
                }
            }
        }
    }

    fun resetState() {
        _billingState.value = BillingState.Idle
    }

    // ─── Save (DataStore) ──────────────────────────────────────────────────────

    private fun saveSubscriptionStatus(value: Boolean, productId: String? = null) {
        isSubscribedValue = value
        _isSubscribed.value = value
        _activePlan.value = productId

        CoroutineScope(Dispatchers.IO).launch {
            dataStore.putPreference(PREF_IS_SUBSCRIBED, value)
            dataStore.putPreference(PREF_ACTIVE_PLAN, productId ?: "")
        }
    }

    fun debugSetSubscription(isSubscribed: Boolean, planId: Int? = null) {
        val productId = if (isSubscribed) {
            PLAN_PRODUCT_IDS[planId] ?: PLAN_PRODUCT_IDS[1]
        } else null
        saveSubscriptionStatus(isSubscribed, productId)
    }

    fun debugSetStatus(status: SubscriptionStatus, planId: Int = 2) {
        if (!BuildConfig.DEBUG) return
        val pid = PLAN_PRODUCT_IDS[planId]
        // Use a fake future expiry for debug previewing (30 days from now).
        val debugExpiry = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        _snapshot.value = when (status) {
            SubscriptionStatus.NOT_SUBSCRIBED -> PlayBillingSnapshot(status)
            SubscriptionStatus.TRIAL -> PlayBillingSnapshot(
                status, pid, 1, true, true, "GPA.DEBUG-TRIAL", true, debugExpiry)
            SubscriptionStatus.ACTIVE -> PlayBillingSnapshot(
                status, pid, 1, true, true, "GPA.3327…9041", false, debugExpiry)
            SubscriptionStatus.CANCELED -> PlayBillingSnapshot(
                status, pid, 1, false, true, "GPA.DEBUG-CANCEL", false, debugExpiry)
            SubscriptionStatus.PENDING -> PlayBillingSnapshot(
                status, pid, 2, null, false, null, false, null)
        }
        val subscribed = status == SubscriptionStatus.ACTIVE ||
                status == SubscriptionStatus.CANCELED || status == SubscriptionStatus.TRIAL
        saveSubscriptionStatus(subscribed, if (subscribed) pid else null)
        _isCancelled.value = status == SubscriptionStatus.CANCELED
    }

    // ─── Load from DataStore (offline fallback) ────────────────────────────────

    suspend fun loadSavedSubscriptionStatus() {
        val DEBUG_MODE = BuildConfig.DEBUG
        if (DEBUG_MODE) {
            val debugSubscribed = true
            val debugPlan = ""
            isSubscribedValue = debugSubscribed
            _isSubscribed.value = debugSubscribed
            _activePlan.value = debugPlan.ifEmpty { null }
            return
        }
        val savedStatus = dataStore.getFirstPreference(PREF_IS_SUBSCRIBED, false)
        val savedPlan = dataStore.getFirstPreference(PREF_ACTIVE_PLAN, "")

        isSubscribedValue = savedStatus
        _isSubscribed.value = savedStatus
        _activePlan.value = savedPlan.ifEmpty { null }
    }
}