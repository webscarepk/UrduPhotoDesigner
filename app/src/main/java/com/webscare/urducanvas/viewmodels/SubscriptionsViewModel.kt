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
import kotlin.math.roundToInt

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val billingManager: BillingManager
) : ViewModel() {

    val billingState: StateFlow<BillingManager.BillingState> = billingManager.billingState
    val isSubscribed: StateFlow<Boolean> = billingManager.isSubscribed
    val activePlan: StateFlow<String?> = billingManager.activePlan
    val expiryDate: StateFlow<Long?> = billingManager.expiryDate
    val isCancelled: StateFlow<Boolean> = billingManager.isCancelled
    private var _isRestoring = false

    private val _plans = MutableStateFlow<List<SubscriptionPlan>>(emptyList())
    val plans: StateFlow<List<SubscriptionPlan>> = _plans

    fun loadProducts() = billingManager.queryProducts()

    fun buildPlans(products: List<ProductDetails>) {
        // 1) Pull the raw recurring price for every product up front so we can use
        //    the Monthly term as the strike-through / "save vs monthly" baseline.
        val priced = products.mapNotNull { product -> priceOf(product) }
        val monthlyPerMonth = priced
            .firstOrNull { it.productId == "urducanvas_monthly" }?.perMonth ?: 0

        val allPlans = priced.mapNotNull { p ->
            val (id, title, duration, badge) = when (p.productId) {
                "urducanvas_monthly" -> Quad(1, "Monthly", "/Month", null as String?)
                "urducanvas_6months" -> Quad(2, "6-Month", "/6 Months", "Save 25%")
                "urducanvas_yearly"  -> Quad(3, "Yearly", "/ Year", "Save 35%")
                else -> return@mapNotNull null
            }

            val discount =
                if (monthlyPerMonth > 0 && p.perMonth < monthlyPerMonth)
                    ((1f - p.perMonth.toFloat() / monthlyPerMonth) * 100f).roundToInt()
                else 0
            val save =
                if (monthlyPerMonth > 0) (monthlyPerMonth * p.months - p.total).coerceAtLeast(0)
                else 0

            SubscriptionPlan(
                id = id,
                productId = p.productId,
                title = title,
                price = p.formattedTotal,
                duration = duration,
                badge = badge,
                perMonth = p.perMonth,
                total = p.total,
                origPerMonth = monthlyPerMonth,
                discountPercent = discount,
                save = save,
                months = p.months,
                billed = billedLabel(p.months),
                currencySymbol = p.symbol
            )
        }.sortedBy { it.id }

        // Hide the currently-active plan when changing plans (same as before).
        val activePlanProductId = billingManager.activePlan.value
        val filtered = if (activePlanProductId != null)
            allPlans.filter { it.productId != activePlanProductId } else allPlans

        // Pre-select the 6-Month term when present, else the middle item.
        val preselect = filtered.indexOfFirst { it.productId == "urducanvas_6months" }
            .let { if (it >= 0) it else filtered.size / 2 }
        filtered.forEachIndexed { i, plan -> plan.isSelected = i == preselect }
        _plans.value = filtered
    }

    /** Parsed recurring price for one product (free-trial phases skipped). */
    private data class Priced(
        val productId: String,
        val perMonth: Int,
        val total: Int,
        val months: Int,
        val symbol: String,
        val formattedTotal: String
    )

    private fun priceOf(product: ProductDetails): Priced? {
        val offer = product.subscriptionOfferDetails?.firstOrNull() ?: return null
        // Last phase = the ongoing paid phase (a free trial would be an earlier 0-cost phase).
        val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return null
        val months = monthsOf(phase.billingPeriod)
        val total = (phase.priceAmountMicros / 1_000_000.0).roundToInt()
        val perMonth = (phase.priceAmountMicros / 1_000_000.0 / months).roundToInt()
        return Priced(
            productId = product.productId,
            perMonth = perMonth,
            total = total,
            months = months,
            symbol = symbolOf(phase.priceCurrencyCode),
            formattedTotal = phase.formattedPrice
        )
    }

    private fun monthsOf(period: String?): Int = when (period) {
        "P1M" -> 1; "P2M" -> 2; "P3M" -> 3; "P6M" -> 6; "P1Y" -> 12
        else -> 1
    }

    private fun billedLabel(months: Int): String = when (months) {
        1 -> "Every month"
        6 -> "Every 6 months"
        12 -> "Annually"
        else -> "Every $months months"
    }

    private fun symbolOf(currencyCode: String): String =
        if (currencyCode.equals("PKR", ignoreCase = true)) "Rs" else currencyCode

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

    fun refreshSubscriptionStatus() {
        billingManager.checkSubscriptionOnLaunch()
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
