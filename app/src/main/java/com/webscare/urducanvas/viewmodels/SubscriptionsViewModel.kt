package com.webscare.urducanvas.viewmodels

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.android.billingclient.api.ProductDetails
import com.webscare.urducanvas.data.model.SubscriptionPlan
import com.webscare.urducanvas.di.BillingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val billingManager: BillingManager
) : ViewModel() {

    val billingState: StateFlow<BillingManager.BillingState> = billingManager.billingState
    val isSubscribed: StateFlow<Boolean> = billingManager.isSubscribed
    val activePlan: StateFlow<String?> = billingManager.activePlan

    private var _isRestoring = false

    private val _plans = MutableStateFlow<List<SubscriptionPlan>>(emptyList())
    val plans: StateFlow<List<SubscriptionPlan>> = _plans

    fun loadProducts() = billingManager.queryProducts()

    fun buildPlans(products: List<ProductDetails>) {
        val activePlanProductId = billingManager.activePlan.value

        val allPlans = products.mapNotNull { product ->
            val offerDetails = product.subscriptionOfferDetails?.firstOrNull() ?: return@mapNotNull null
            val price = offerDetails.pricingPhases.pricingPhaseList.firstOrNull()?.formattedPrice ?: return@mapNotNull null

            when (product.productId) {
                "urducanvas_monthly" -> SubscriptionPlan(
                    id = 1, productId = product.productId,
                    title = "Monthly", price = price,
                    duration = "/Month", badge = null
                )
                "urducanvas_6months" -> SubscriptionPlan(
                    id = 2, productId = product.productId,
                    title = "6 Months", price = price,
                    duration = "/6 Months", badge = "Save 25%"
                )
                "urducanvas_yearly" -> SubscriptionPlan(
                    id = 3, productId = product.productId,
                    title = "1 Year", price = price,
                    duration = "/ Year", badge = "Save 35%"
                )
                else -> null
            }
        }.sortedBy { it.id }

        // Subscribed hai toh active plan filter out karo
        val filteredPlans = if (activePlanProductId != null) {
            allPlans.filter { it.productId != activePlanProductId }
        } else {
            allPlans
        }

        filteredPlans.forEachIndexed { i, plan -> plan.isSelected = i == 0 }
        _plans.value = filteredPlans
    }

    fun subscribe(activity: Activity, planId: Int) =
        if (billingManager.isSubscribed.value) {
            billingManager.launchPlanChange(activity, planId)
        } else {
            billingManager.launchPurchase(activity, planId)
        }

    fun resetState() = billingManager.resetState()

    fun restore() {
        _isRestoring = true
        billingManager.restorePurchases()
    }

    fun isRestoring(): Boolean {
        val v = _isRestoring
        _isRestoring = false
        return v
    }
}