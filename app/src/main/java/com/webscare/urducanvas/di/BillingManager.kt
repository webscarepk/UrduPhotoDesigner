package com.webscare.urducanvas.di

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.webscare.urducanvas.common.datastore.PreferenceDataStoreAPI
import com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.PREF_IS_SUBSCRIBED
import com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.PREF_ACTIVE_PLAN  // ← add this key in your constants
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

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed

    private val _activePlan = MutableStateFlow<String?>(null)
    val activePlan: StateFlow<String?> = _activePlan

    // ─── Check on Launch ───────────────────────────────────────────────────────

    fun checkSubscriptionOnLaunch() {
//        startConnection {
//            billingClient.queryPurchasesAsync(
//                QueryPurchasesParams.newBuilder()
//                    .setProductType(BillingClient.ProductType.SUBS)
//                    .build()
//            ) { result, purchases ->
//                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
//                    val activePurchase = purchases.firstOrNull {
//                        it.purchaseState == Purchase.PurchaseState.PURCHASED
//                    }

//                    val productId = activePurchase?.products?.firstOrNull()
//                    saveSubscriptionStatus(activePurchase != null, productId)
//                }
//                // if result is not OK (e.g. no internet), DataStore fallback
//                // is already loaded via loadSavedSubscriptionStatus() on app start
//            }
//        }
    }

    private val _billingState = MutableStateFlow<BillingState>(BillingState.Idle)
    val billingState: StateFlow<BillingState> = _billingState

    private var availableProducts: List<ProductDetails> = emptyList()

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
        if (billingClient.isReady) { onReady(); return }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    onReady()
                } else {
                    _billingState.value =
                        BillingState.Error("Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                startConnection(onReady)
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
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        acknowledgePurchase(purchase)
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
        val productId = purchase.products.firstOrNull()  // ← grab plan from purchase

        if (purchase.isAcknowledged) {
            _billingState.value = BillingState.PurchaseSuccess(purchase)
            saveSubscriptionStatus(true, productId)
            return
        }

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _billingState.value = BillingState.PurchaseSuccess(purchase)
                saveSubscriptionStatus(true, productId)
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
                        val productId = activePurchase.products.firstOrNull()  // ← grab plan
                        _billingState.value = BillingState.PurchaseSuccess(activePurchase)
                        saveSubscriptionStatus(true, productId)
                    } else {
                        _billingState.value = BillingState.Error("No active subscription found.")
                    }
                } else {
                    _billingState.value = BillingState.Error("Nothing to restore.")
                }
            }
        }
    }

    fun resetState() {
        _billingState.value = BillingState.Idle
    }

    // ─── Save (Google Play + DataStore) ───────────────────────────────────────

    private fun saveSubscriptionStatus(value: Boolean, productId: String? = null) {
        isSubscribedValue = value
        _isSubscribed.value = value
        _activePlan.value = productId  // ← update in-memory plan

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

    // ─── Load from DataStore (offline fallback) ────────────────────────────────

    suspend fun loadSavedSubscriptionStatus() {
        val DEBUG_MODE = true
        if (DEBUG_MODE) {
            val debugSubscribed = false
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