package com.webscare.urducanvas.data.model

/**
 * A subscription plan/term.
 *
 * The first block (id, title, productId, price, duration, badge, isSelected) is
 * unchanged from the original model so existing call-sites and
 * [com.webscare.urducanvas.ui.navigation.settings.SubscriptionsAdapter] keep
 * compiling.
 *
 * The second block is added for the "Plan picker A" design (segmented term
 * toggle + detail card). Amounts are stored as Double to preserve decimals for
 * currencies like USD ($4.99) and AED (AED 5.49) — previously rounded to Int
 * which caused wrong amounts to display for non-PKR currencies:
 *
 *  - [perMonth]        effective price per month for this term
 *  - [total]           amount billed each period (e.g. 18.00 for the 6-month term)
 *  - [origPerMonth]    the monthly plan's per-month price — the strike-through baseline
 *  - [discountPercent] rounded saving vs [origPerMonth] (0 = no discount)
 *  - [save]            money saved over the period vs paying monthly
 *  - [months]          billing period length in months (1 / 6 / 12)
 *  - [billed]          cadence label, e.g. "Every month", "Every 6 months", "Annually"
 *  - [currencySymbol]  "Rs" for PKR, otherwise the ISO currency code
 */
data class SubscriptionPlan(
    val id: Int,
    val title: String,
    val productId: String,
    val price: String,
    val duration: String,
    val badge: String? = null,
    var isSelected: Boolean = false,

    // ── Plan picker A additions ────────────────────────────────────────────
    val perMonth: Double = 0.0,
    val total: Double = 0.0,
    val origPerMonth: Double = 0.0,
    val discountPercent: Int = 0,
    val save: Double = 0.0,
    val months: Int = 1,
    val billed: String = "",
    val currencySymbol: String = "Rs"
) {
    val hasDiscount: Boolean get() = discountPercent > 0
    val hasSave: Boolean get() = save > 0.0
    /** Show the strike-through original only when this term beats the monthly rate. */
    val showStrike: Boolean get() = origPerMonth > perMonth && origPerMonth > 0.0
}