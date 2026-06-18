package com.webscare.urducanvas.ui.navigation.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentSubscriptionsBinding
import com.webscare.urducanvas.di.BillingManager
import com.webscare.urducanvas.viewmodels.SubscriptionsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * UrduCanvas Pro paywall. Self-contained: keeps BillingManager + ViewModel as-is.
 *
 * State model (client-only — what Play Billing can actually tell us on device):
 *   NONE       not subscribed
 *   ACTIVE     subscribed, auto-renewing
 *   EXPIRING   subscribed, auto-renewing, but renewal date is within EXPIRING_WINDOW
 *   CANCELLED  subscribed, NOT auto-renewing (access until expiry)
 *
 * "Grace"/"OnHold"/"Deferred" are intentionally NOT modeled: the device cannot
 * detect them without a backend (Play Developer API / RTDN).
 */
@AndroidEntryPoint
class SubscriptionsFragment : androidx.fragment.app.Fragment() {

    private var _binding: FragmentSubscriptionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SubscriptionsViewModel by viewModels()

    @Inject
    lateinit var billingManager: BillingManager

    // ── Plan term identity. Ranks match BillingManager.PLAN_PRODUCT_IDS (1/2/3). ──
    private enum class Term(val rank: Int, val productId: String, val planId: Int) {
        MONTHLY(1, "urducanvas_monthly", 1),
        SIX(2, "urducanvas_6months", 2),
        YEARLY(3, "urducanvas_yearly", 3);

        companion object {
            fun fromProductId(id: String?): Term? = entries.firstOrNull { it.productId == id }
        }
    }

    private enum class SubState { NONE, ACTIVE, EXPIRING, CANCELLED }

    // Static plan presentation (matches the design's M table). Price comes live.
    private data class Meta(
        val name: String,
        val perMonth: String,
        val total: String,
        val billed: String,
        val offText: String?,     // null = no discount badge
        val save: String?         // null = no save banner
    )

    private val meta = mapOf(
        Term.MONTHLY to Meta("Monthly", "Rs. 400", "Rs. 400", "Every month", null, null),
        Term.SIX to Meta("6-Month", "Rs. 300", "Rs. 1,800", "Every 6 months", "25% OFF", "Save Rs. 600 / year"),
        Term.YEARLY to Meta("Yearly", "Rs. 260", "Rs. 3,120", "Annually", "35% OFF", "Save Rs. 1,680 / year")
    )

    private val benefits = listOf(
        "Premium Urdu Templates",
        "Custom Urdu Fonts (TTF/OTF)",
        "Premium Stickers & Library",
        "Request Custom Designs",
        "No Ads — Ever"
    )

    // Live price per product id, filled from ProductsLoaded.
    private val livePrice = mutableMapOf<String, String>()

    // User's selected term in the toggle (defaults set once state known).
    private var selected: Term? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubscriptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setEvents()
        observe()
        viewModel.loadProducts()
        binding.mainBgImage.animate().alpha(1f).setDuration(450).start()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSubscriptionStatus()
    }

    // ── Events ────────────────────────────────────────────────────────────────
    private fun setEvents() {
        binding.back.addPressEffect { findNavController().navigateUp() }

        binding.segMonthly.addPressEffect { onPick(Term.MONTHLY) }
        binding.segSix.addPressEffect { onPick(Term.SIX) }
        binding.segYear.addPressEffect { onPick(Term.YEARLY) }

        binding.continueBtn.addPressEffect { onCtaClicked() }

        binding.manageLink.addPressEffect { openPlayManage() }

        binding.restore.addPressEffect { viewModel.restore() }
        binding.termsOfUse.addPressEffect {
            openUrl("https://play.google.com/store/apps/details?id=${requireContext().packageName}")
        }
        binding.privacyPolicy.addPressEffect { openUrl("https://urducanvas.com/privacy-policy") }
    }

    private fun onPick(term: Term) {
        selected = term
        renderAll()
    }

    // ── Observe billing flows ───────────────────────────────────────────────────
    private fun observe() {
        // Products → cache live prices, then re-render.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.billingState.collect { state ->
                    when (state) {
                        is BillingManager.BillingState.Loading ->
                            binding.continueBtn.isEnabled = false

                        is BillingManager.BillingState.ProductsLoaded -> {
                            binding.continueBtn.isEnabled = true
                            state.products.forEach { p ->
                                val price = p.subscriptionOfferDetails
                                    ?.firstOrNull()?.pricingPhases?.pricingPhaseList
                                    ?.firstOrNull()?.formattedPrice
                                if (price != null) livePrice[p.productId] = price
                            }
                            renderAll()
                        }

                        is BillingManager.BillingState.PurchaseSuccess -> {
                            binding.continueBtn.isEnabled = true
                            if (viewModel.isRestoring()) showRestoreSuccessDialog()
                            else showPurchaseSuccessDialog()
                            viewModel.resetState()
                        }

                        is BillingManager.BillingState.Error -> {
                            binding.continueBtn.isEnabled = true
                            if (viewModel.isRestoring()) showRestoreFailedDialog()
                            else showErrorDialog(state.message)
                            viewModel.resetState()
                        }

                        is BillingManager.BillingState.Idle ->
                            binding.continueBtn.isEnabled = true
                    }
                }
            }
        }

        // Any subscription-state flow changes → re-render the whole screen.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.isSubscribed,
                    viewModel.activePlan,
                    viewModel.expiryDate,
                    viewModel.isCancelled
                ) { _, _, _, _ -> Unit }.collect { renderAll() }
            }
        }
    }

    // ── State derivation (the design's sc()) ────────────────────────────────────
    private fun currentState(): SubState {
        val subscribed = viewModel.isSubscribed.value
        if (!subscribed) return SubState.NONE
        if (viewModel.isCancelled.value) return SubState.CANCELLED
        val expiry = viewModel.expiryDate.value
        if (expiry != null && expiry - System.currentTimeMillis() in 0..EXPIRING_WINDOW_MS) {
            return SubState.EXPIRING
        }
        return SubState.ACTIVE
    }

    private fun currentTerm(): Term? = Term.fromProductId(viewModel.activePlan.value)

    private fun selectedTerm(): Term {
        selected?.let { return it }
        // Default: subscribed → current plan; not subscribed → 6-Month.
        return currentTerm() ?: Term.SIX
    }

    private fun pricePerMonth(term: Term): String =
    // Live formatted price is the per-period charge; design shows per-month
        // marketing value, so prefer our static per-month label for the big number.
        meta.getValue(term).perMonth

    private fun totalPrice(term: Term): String =
        livePrice[term.productId] ?: meta.getValue(term).total

    private fun dateText(): String {
        val ms = viewModel.expiryDate.value ?: return "—"
        return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms))
    }

    // ── Render ──────────────────────────────────────────────────────────────────
    private fun renderAll() {
        if (_binding == null) return
        val state = currentState()
        renderHeader(state)
        renderSegments()
        renderHero()
        renderCta(state)
    }

    private fun renderHeader(state: SubState) {
        val subscribed = state != SubState.NONE
        binding.activePlanCard.isVisible = subscribed
        binding.freeBanner.isVisible = !subscribed
        if (!subscribed) return

        val cur = currentTerm() ?: Term.SIX
        val m = meta.getValue(cur)

        binding.subscriptionCardTitle.text = m.name
        binding.factPlan.text = m.name
        binding.factBilled.text = totalPrice(cur)

        // Status pill
        val (label, fg, bg) = when (state) {
            SubState.ACTIVE -> Triple("Active", 0xFF1D6B34.toInt(), 0xFFE3F1E8.toInt())
            SubState.EXPIRING -> Triple("Expiring soon", 0xFF9A6A00.toInt(), 0xFFFCEFCF.toInt())
            SubState.CANCELLED -> Triple("Canceled", 0xFFB23B3B.toInt(), 0xFFFBE3E3.toInt())
            SubState.NONE -> Triple("Free", 0xFF7C817C.toInt(), 0xFFEEF0EC.toInt())
        }
        binding.statusPill.text = label
        binding.statusPill.setTextColor(fg)
        binding.statusPill.background?.setTint(bg)

        // Renew label + bar + note + manage link, per state.
        val date = dateText()
        when (state) {
            SubState.EXPIRING -> {
                binding.factRenewLabel.text = "Expires on"
                binding.periodBar.progress = 90
                binding.periodBar.progressDrawable?.setTint(0xFFD8A200.toInt())
                binding.barNote.text = "Renew before $date to keep Pro."
                binding.manageLink.text = "Turn on renew"
            }
            SubState.CANCELLED -> {
                binding.factRenewLabel.text = "Access until"
                binding.periodBar.progress = 100
                binding.periodBar.progressDrawable?.setTint(0xFFC26A6A.toInt())
                binding.barNote.text = "Access ends $date."
                binding.manageLink.text = "Resume"
            }
            else -> {
                binding.factRenewLabel.text = "Renews on"
                binding.periodBar.progress = periodElapsedPercent(cur)
                binding.periodBar.progressDrawable?.setTint(0xFF1D6B34.toInt())
                binding.barNote.text = "Auto-renews on $date."
                binding.manageLink.text = "Manage"
            }
        }
        binding.factRenewDate.text = date
    }

    /** % of the current billing period already elapsed (time, not credits). */
    private fun periodElapsedPercent(term: Term): Int {
        val expiry = viewModel.expiryDate.value ?: return 55
        val cycleMs = when (term) {
            Term.MONTHLY -> 30L
            Term.SIX -> 182L
            Term.YEARLY -> 365L
        } * 24L * 60L * 60L * 1000L
        val start = expiry - cycleMs
        val now = System.currentTimeMillis()
        if (now <= start) return 0
        if (now >= expiry) return 100
        return (((now - start).toDouble() / cycleMs) * 100).toInt().coerceIn(0, 100)
    }

    private fun renderSegments() {
        val sel = selectedTerm()
        bindSegment(binding.segMonthly, sel == Term.MONTHLY)
        bindSegment(binding.segSix, sel == Term.SIX)
        bindSegment(binding.segYear, sel == Term.YEARLY)
    }

    private fun bindSegment(tv: android.widget.TextView, on: Boolean) {
        tv.setBackgroundResource(if (on) R.drawable.bg_segment_selected else android.R.color.transparent)
        tv.setTextColor(if (on) 0xFF1C1F1B.toInt() else 0xFF8A8F8A.toInt())
    }

    private fun renderHero() {
        val term = selectedTerm()
        val m = meta.getValue(term)

        binding.heroPlanName.text = "${m.name} plan"
        binding.heroPrice.text = pricePerMonth(term)

        val hasOffer = m.offText != null
        binding.heroOffBadge.isVisible = hasOffer
        binding.heroOffBadge.text = m.offText ?: ""
        binding.heroOrigPrice.isVisible = hasOffer
        binding.heroOrigPrice.text = meta.getValue(Term.MONTHLY).perMonth
        binding.heroOrigPrice.paintFlags =
            binding.heroOrigPrice.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG

        binding.heroBilledLine.text = "${totalPrice(term)} billed · ${m.billed}"

        binding.heroSaveBanner.isVisible = m.save != null
        binding.heroSaveBanner.text = m.save ?: ""

        // Benefits
        binding.benefitsContainer.removeAllViews()
        benefits.forEach { b ->
            val row = layoutInflater.inflate(
                R.layout.item_pro_benefit, binding.benefitsContainer, false
            )
            row.findViewById<android.widget.TextView>(R.id.benefitText).text = b
            binding.benefitsContainer.addView(row)
        }
    }

    // ── CTA logic (the design's cta()) ──────────────────────────────────────────
    private fun renderCta(state: SubState) {
        val sel = selectedTerm()
        val cur = currentTerm()
        val m = meta.getValue(sel)
        val date = dateText()

        val (label, variant, note) = when {
            state == SubState.NONE -> Triple(
                "Subscribe Now", Variant.PRIMARY,
                "You'll be charged ${totalPrice(sel)}, billed ${m.billed.lowercase()}."
            )
            sel == cur && state == SubState.ACTIVE -> Triple(
                "Your current plan", Variant.DISABLED,
                "Renews automatically on $date."
            )
            sel == cur && state == SubState.EXPIRING -> Triple(
                "Renew ${m.name}", Variant.PRIMARY,
                "Renew before $date — ${totalPrice(sel)}."
            )
            sel == cur && state == SubState.CANCELLED -> Triple(
                "Resubscribe", Variant.PRIMARY,
                "Reactivate ${m.name} — ${totalPrice(sel)} billed ${m.billed.lowercase()}."
            )
            cur != null && sel.rank > cur.rank -> Triple(
                "Upgrade to ${m.name}", Variant.PRIMARY,
                "Switch now — billed ${totalPrice(sel)} ${m.billed.lowercase()}."
            )
            else -> Triple(
                "Downgrade to ${m.name}", Variant.SECONDARY,
                "Takes effect at next renewal on $date."
            )
        }

        binding.continueBtn.text = label
        binding.ctaNote.text = note
        applyCtaVariant(variant)
    }

    private enum class Variant { PRIMARY, SECONDARY, DISABLED }

    private fun applyCtaVariant(v: Variant) {
        when (v) {
            Variant.PRIMARY -> {
                binding.continueBtn.setBackgroundResource(R.drawable.ic_button_gradient_wrap)
                binding.continueBtn.setTextColor(0xFFFFFFFF.toInt())
                binding.continueBtn.isEnabled = true
            }
            Variant.SECONDARY -> {
                binding.continueBtn.setBackgroundResource(R.drawable.bg_cta_secondary)
                binding.continueBtn.setTextColor(0xFF1D6B34.toInt())
                binding.continueBtn.isEnabled = true
            }
            Variant.DISABLED -> {
                binding.continueBtn.setBackgroundResource(R.drawable.bg_cta_disabled)
                binding.continueBtn.setTextColor(0xFF9AA09A.toInt())
                binding.continueBtn.isEnabled = false
            }
        }
    }

    private fun onCtaClicked() {
        val state = currentState()
        val sel = selectedTerm()
        val cur = currentTerm()

        when {
            // Disabled: current active plan — do nothing.
            sel == cur && state == SubState.ACTIVE -> Unit

            // Cancelled or expiring on the SAME plan → send to Play to resume/renew.
            sel == cur && (state == SubState.CANCELLED || state == SubState.EXPIRING) ->
                openPlayManage()

            // New subscription.
            state == SubState.NONE ->
                viewModel.subscribe(requireActivity(), sel.planId)

            // Upgrade / downgrade (ViewModel.subscribe routes to launchPlanChange
            // because isSubscribed == true).
            else ->
                viewModel.subscribe(requireActivity(), sel.planId)
        }
    }

    private fun openPlayManage() {
        openUrl("https://play.google.com/store/account/subscriptions?package=${requireContext().packageName}")
    }

    // ── Dialogs (reuse your existing SubscriptionBottomSheet) ───────────────────
    private fun showPurchaseSuccessDialog() {
        val isUpgrade = billingManager.isSubscribed.value
        SubscriptionBottomSheet.newInstance(
            SubscriptionSheetConfig(
                iconRes = R.drawable.ic_subscribed_icon,
                title = if (isUpgrade) "Plan Updated!" else "Subscription Activated",
                message = if (isUpgrade)
                    "Your plan has been updated successfully!\nNew plan is active now."
                else
                    "Your Creativity Just Got an Upgrade!\nEnjoy Full Access to UrduCanvas",
                primaryText = if (isUpgrade) "Continue" else "Start Creating",
                showBg = true,
                secondaryText = null,
                cancelable = false,
                onPrimary = { findNavController().navigateUp() }
            )
        ).show(childFragmentManager, "purchase_success")
    }

    private fun showErrorDialog(message: String) {
        if (message.contains("cancel", ignoreCase = true)) return
        SubscriptionBottomSheet.newInstance(
            SubscriptionSheetConfig(
                iconRes = R.drawable.ic_warning_icon,
                title = "Something Went Wrong",
                message = "Purchase could not be completed.\nPlease try again or contact support.",
                primaryText = "Try Again",
                secondaryText = "Contact Support",
                onPrimary = { },
                onSecondary = { openUrl("mailto:support@urducanvas.com?subject=Purchase Issue") }
            )
        ).show(childFragmentManager, "purchase_error")
    }

    private fun showRestoreSuccessDialog() {
        SubscriptionBottomSheet.newInstance(
            SubscriptionSheetConfig(
                iconRes = R.drawable.ic_restored_icon,
                title = "Subscription Restored!",
                message = "Your previous subscription has been restored successfully.",
                primaryText = "Continue",
                cancelable = false,
                onPrimary = { findNavController().navigateUp() }
            )
        ).show(childFragmentManager, "restore_success")
    }

    private fun showRestoreFailedDialog() {
        SubscriptionBottomSheet.newInstance(
            SubscriptionSheetConfig(
                iconRes = R.drawable.ic_nothing_found_icon,
                title = "Nothing Found",
                message = "No active subscription was found on this account.",
                primaryText = "Buy Subscription",
                onPrimary = { }
            )
        ).show(childFragmentManager, "restore_failed")
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        // "Expiring soon" window: renewal within 7 days. Tune as you like.
        private const val EXPIRING_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    }
}
