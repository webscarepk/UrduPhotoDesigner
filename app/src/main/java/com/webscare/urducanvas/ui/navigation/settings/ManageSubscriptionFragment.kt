package com.webscare.urducanvas.ui.navigation.settings

import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentManageSubscriptionBinding
import com.webscare.urducanvas.di.BillingManager
import com.webscare.urducanvas.di.BillingManager.SubscriptionStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Manage subscription — reflects the real, app-only Play Billing state.
 *
 * The banner is driven entirely by [BillingManager.snapshot], which derives one
 * of five client-detectable states from `queryPurchasesAsync`
 * (Not subscribed / Free trial≈ / Active / Canceled / Pending). The monospace
 * readout shows the actual signals the library handed us — no invented renewal
 * or expiry dates, since those need the Play Developer API.
 */
@AndroidEntryPoint
class ManageSubscriptionFragment : androidx.fragment.app.Fragment() {

    private var _binding: FragmentManageSubscriptionBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var billingManager: BillingManager

    private var lastStatus: SubscriptionStatus? = null
    private var indetAnim: ValueAnimator? = null

    private data class Signal(val k: String, val v: String, val colorRes: Int)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageSubscriptionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setEvents()
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                billingManager.snapshot.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-query so the banner is always current when the screen reappears.
        billingManager.refreshSnapshot()
    }

    private fun setEvents() {
        binding.back.addPressEffect { findNavController().navigateUp() }
        binding.openInPlay.addPressEffect { openPlaySubscriptions() }
        binding.restore.addPressEffect {
            billingManager.restorePurchases()
            toast("Restoring purchases…")
        }
        binding.helpBilling.addPressEffect {
            openUrl("mailto:support@urducanvas.com?subject=Billing Support")
        }
    }

    // ── Render one state ───────────────────────────────────────────────────────
    private fun render(snap: BillingManager.PlayBillingSnapshot) {
        val status = snap.status
        val product = snap.productId ?: "pro_6month"
        val purchaseStateText = when (snap.purchaseState) {
            1 -> "PURCHASED"; 2 -> "PENDING"; else -> "—"
        }
        val autoRenewText = snap.isAutoRenewing?.toString() ?: "—"
        val ackText = snap.isAcknowledged?.toString() ?: "false"
        val orderId = snap.orderId ?: "—"

        // tone: accent (strong) + tint (light) per state
        val (accentRes, tintRes) = when (status) {
            SubscriptionStatus.NOT_SUBSCRIBED -> R.color.state_gray to R.color.state_gray_tint
            SubscriptionStatus.TRIAL -> R.color.state_teal to R.color.state_teal_tint
            SubscriptionStatus.ACTIVE -> R.color.state_green to R.color.state_green_tint
            SubscriptionStatus.CANCELED -> R.color.state_amber to R.color.state_amber_tint
            SubscriptionStatus.PENDING -> R.color.state_blue to R.color.state_blue_tint
        }
        val accent = color(accentRes)
        val tint = color(tintRes)

        // chip + icon box
        ViewCompat.setBackgroundTintList(binding.chip, ColorStateList.valueOf(tint))
        ViewCompat.setBackgroundTintList(binding.chipDot, ColorStateList.valueOf(accent))
        ViewCompat.setBackgroundTintList(binding.iconBox, ColorStateList.valueOf(tint))
        ImageViewCompat.setImageTintList(binding.stateIcon, ColorStateList.valueOf(accent))
        binding.chipText.setTextColor(accent)

        binding.chipText.text = getString(when (status) {
            SubscriptionStatus.NOT_SUBSCRIBED -> R.string.mng_chip_not_subscribed
            SubscriptionStatus.TRIAL -> R.string.mng_chip_trial
            SubscriptionStatus.ACTIVE -> R.string.mng_chip_active
            SubscriptionStatus.CANCELED -> R.string.mng_chip_canceled
            SubscriptionStatus.PENDING -> R.string.mng_chip_pending
        })
        binding.stateIcon.setImageResource(when (status) {
            SubscriptionStatus.NOT_SUBSCRIBED -> R.drawable.ic_sub_state_info
            SubscriptionStatus.TRIAL -> R.drawable.ic_sub_state_star
            SubscriptionStatus.ACTIVE -> R.drawable.ic_sub_state_refresh
            SubscriptionStatus.CANCELED -> R.drawable.ic_sub_state_pause
            SubscriptionStatus.PENDING -> R.drawable.ic_sub_state_clock
        })
        binding.bannerTitle.text = getString(when (status) {
            SubscriptionStatus.NOT_SUBSCRIBED -> R.string.mng_title_not_subscribed
            SubscriptionStatus.TRIAL -> R.string.mng_title_trial
            SubscriptionStatus.ACTIVE -> R.string.mng_title_active
            SubscriptionStatus.CANCELED -> R.string.mng_title_canceled
            SubscriptionStatus.PENDING -> R.string.mng_title_pending
        })
        binding.bannerDetail.text = getString(when (status) {
            SubscriptionStatus.NOT_SUBSCRIBED -> R.string.mng_detail_not_subscribed
            SubscriptionStatus.TRIAL -> R.string.mng_detail_trial
            SubscriptionStatus.ACTIVE -> R.string.mng_detail_active
            SubscriptionStatus.CANCELED -> R.string.mng_detail_canceled
            SubscriptionStatus.PENDING -> R.string.mng_detail_pending
        })

        // conditional blocks
        binding.monoBox.isVisible = status == SubscriptionStatus.NOT_SUBSCRIBED

        val signals: List<Signal> = when (status) {
            SubscriptionStatus.NOT_SUBSCRIBED -> emptyList()
            SubscriptionStatus.TRIAL -> listOf(
                Signal("purchaseState", purchaseStateText, R.color.sub_signal_good),
                Signal("isAutoRenewing", autoRenewText, R.color.sub_signal_good),
                Signal("free phase", "present", R.color.sub_signal_warn),
                Signal("acknowledged", ackText, R.color.sub_signal_good),
                Signal("product", product, R.color.sub_signal_mute),
            )
            SubscriptionStatus.ACTIVE -> listOf(
                Signal("purchaseState", purchaseStateText, R.color.sub_signal_good),
                Signal("isAutoRenewing", autoRenewText, R.color.sub_signal_good),
                Signal("acknowledged", ackText, R.color.sub_signal_good),
                Signal("product", product, R.color.sub_signal_mute),
                Signal("orderId", orderId, R.color.sub_signal_mute),
            )
            SubscriptionStatus.CANCELED -> listOf(
                Signal("purchaseState", purchaseStateText, R.color.sub_signal_good),
                Signal("isAutoRenewing", autoRenewText, R.color.sub_signal_warn),
                Signal("acknowledged", ackText, R.color.sub_signal_good),
                Signal("product", product, R.color.sub_signal_mute),
            )
            SubscriptionStatus.PENDING -> listOf(
                Signal("purchaseState", purchaseStateText, R.color.sub_signal_warn),
                Signal("isAutoRenewing", autoRenewText, R.color.sub_signal_mute),
                Signal("acknowledged", ackText, R.color.sub_signal_bad),
                Signal("product", product, R.color.sub_signal_mute),
            )
        }
        bindSignals(signals)

        // pending indeterminate bar
        val pending = status == SubscriptionStatus.PENDING
        binding.indeterminateTrack.isVisible = pending
        if (pending) {
            ViewCompat.setBackgroundTintList(binding.indeterminateBar, ColorStateList.valueOf(accent))
            startIndeterminate()
        } else stopIndeterminate()

        // ack rows
        binding.ackGoodRow.isVisible =
            status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIAL
        binding.ackWarnBox.isVisible = pending

        // CTAs
        binding.primaryBtn.text = getString(when (status) {
            SubscriptionStatus.NOT_SUBSCRIBED -> R.string.mng_cta_see_plans
            SubscriptionStatus.CANCELED -> R.string.mng_cta_resubscribe
            SubscriptionStatus.PENDING -> R.string.mng_cta_recheck
            else -> R.string.mng_cta_change_plan
        })
        binding.primaryBtn.addPressEffect {
            if (status == SubscriptionStatus.PENDING) {
                billingManager.refreshSnapshot()
                toast(getString(R.string.mng_toast_rechecked))
            } else {
                findNavController().navigate(R.id.subscriptionsFragment)
            }
        }

        val hasSecondary = status == SubscriptionStatus.TRIAL ||
                status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.CANCELED
        binding.secondaryBtn.isVisible = hasSecondary
        if (hasSecondary) {
            binding.secondaryBtn.text = getString(
                if (status == SubscriptionStatus.ACTIVE) R.string.mng_cta_cancel_play
                else R.string.mng_cta_manage_play
            )
            binding.secondaryBtn.addPressEffect { openPlaySubscriptions() }
        }

        // banner pop on state change (matches the design's componentDidUpdate)
        if (lastStatus != status) {
            lastStatus = status
            binding.bannerCard.alpha = 0f
            binding.bannerCard.translationY = dp(12).toFloat()
            binding.bannerCard.animate()
                .alpha(1f).translationY(0f)
                .setDuration(420)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    private fun bindSignals(signals: List<Signal>) {
        binding.signalsBox.isVisible = signals.isNotEmpty()
        val rows = binding.signalRows
        rows.removeAllViews()
        signals.forEachIndexed { i, s ->
            if (i > 0) {
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                    setBackgroundColor(color(R.color.sub_signal_row_divider))
                }
                rows.addView(divider)
            }
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val key = TextView(requireContext()).apply {
                text = s.k
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(color(R.color.sub_banner_detail))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val value = TextView(requireContext()).apply {
                text = s.v
                textSize = 12f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                setTextColor(color(s.colorRes))
            }
            row.addView(key); row.addView(value)
            rows.addView(row)
        }
    }

    // ── Indeterminate animation ────────────────────────────────────────────────
    private fun startIndeterminate() {
        binding.indeterminateTrack.post {
            val trackW = binding.indeterminateTrack.width
            val barW = binding.indeterminateBar.width.takeIf { it > 0 } ?: dp(80)
            indetAnim?.cancel()
            indetAnim = ValueAnimator.ofFloat(-barW.toFloat(), trackW.toFloat()).apply {
                duration = 1300
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { binding.indeterminateBar.translationX = it.animatedValue as Float }
                start()
            }
        }
    }

    private fun stopIndeterminate() {
        indetAnim?.cancel()
        indetAnim = null
    }

    // ── Actions ────────────────────────────────────────────────────────────────
    private fun openPlaySubscriptions() {
        val pid = billingManager.snapshot.value.productId
        val pkg = requireContext().packageName
        val url = if (pid != null)
            "https://play.google.com/store/account/subscriptions?sku=$pid&package=$pkg"
        else "https://play.google.com/store/account/subscriptions"
        toast(getString(R.string.mng_toast_opening_play))
        openUrl(url)
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        stopIndeterminate()
        _binding = null
    }
}
