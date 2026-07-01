package com.webscare.urducanvas.ui.navigation.settings.subscriptions

import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ManageSubscriptionFragment : Fragment() {

    private var _binding: FragmentManageSubscriptionBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var billingManager: BillingManager

    private var lastStatus: SubscriptionStatus? = null
    private var indetAnim: ValueAnimator? = null

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

    private fun render(snap: BillingManager.PlayBillingSnapshot) {
        val status = snap.status
        val planName = planFriendlyName(snap.productId)

        val (accentRes, tintRes) = when (status) {
            SubscriptionStatus.NOT_SUBSCRIBED -> R.color.state_gray to R.color.state_gray_tint
            SubscriptionStatus.TRIAL          -> R.color.state_teal to R.color.state_teal_tint
            SubscriptionStatus.ACTIVE         -> R.color.state_green to R.color.state_green_tint
            SubscriptionStatus.CANCELED       -> R.color.state_amber to R.color.state_amber_tint
            SubscriptionStatus.PENDING        -> R.color.state_blue to R.color.state_blue_tint
        }
        val accent = color(accentRes)
        val tint   = color(tintRes)

        ViewCompat.setBackgroundTintList(binding.chip, ColorStateList.valueOf(tint))
        ViewCompat.setBackgroundTintList(binding.chipDot, ColorStateList.valueOf(accent))
        binding.chipText.setTextColor(accent)

        binding.chipText.text = getString(when (status) {
            SubscriptionStatus.NOT_SUBSCRIBED -> R.string.mng_chip_not_subscribed
            SubscriptionStatus.TRIAL          -> R.string.mng_chip_trial
            SubscriptionStatus.ACTIVE         -> R.string.mng_chip_active
            SubscriptionStatus.CANCELED       -> R.string.mng_chip_canceled
            SubscriptionStatus.PENDING        -> R.string.mng_chip_pending
        })

        binding.bannerTitle.text = when (status) {
            SubscriptionStatus.NOT_SUBSCRIBED -> getString(R.string.mng_title_not_subscribed)
            SubscriptionStatus.TRIAL          -> "Free trial — $planName"
            SubscriptionStatus.ACTIVE         -> "$planName plan is active"
            SubscriptionStatus.CANCELED       -> "$planName — auto-renew is off"
            SubscriptionStatus.PENDING        -> getString(R.string.mng_title_pending)
        }

        // ── Detail text: user-friendly copy + date line when available ─────────
        binding.bannerDetail.text = buildDetailText(status, planName, snap.expiryTimeMillis)

        val pending = status == SubscriptionStatus.PENDING
        binding.indeterminateTrack.isVisible = pending
        if (pending) {
            ViewCompat.setBackgroundTintList(
                binding.indeterminateBar, ColorStateList.valueOf(accent))
            startIndeterminate()
        } else stopIndeterminate()

        binding.ackGoodRow.isVisible =
            status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIAL

        binding.primaryBtn.text = getString(when (status) {
            SubscriptionStatus.NOT_SUBSCRIBED -> R.string.mng_cta_see_plans
            SubscriptionStatus.CANCELED       -> R.string.mng_cta_resubscribe
            SubscriptionStatus.PENDING        -> R.string.mng_cta_recheck
            else                              -> R.string.mng_cta_change_plan
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

    /**
     * Builds the detail paragraph shown inside the banner card.
     *
     * Each state gets a plain user-facing sentence, followed by a date line
     * (e.g. "Renews on Jul 20, 2025") when [expiryMillis] is available.
     */
    private fun buildDetailText(
        status: SubscriptionStatus,
        planName: String,
        expiryMillis: Long?
    ): String {
        val base = when (status) {
            SubscriptionStatus.NOT_SUBSCRIBED ->
                "You don't have an active subscription. Choose a plan to unlock all Pro features."
            SubscriptionStatus.TRIAL ->
                "You're on a free trial. You won't be charged until your trial ends."
            SubscriptionStatus.ACTIVE ->
                "Your $planName plan is active and renews automatically."
            SubscriptionStatus.CANCELED ->
                "You've turned off auto-renew. You can still use Pro until your current period ends."
            SubscriptionStatus.PENDING ->
                "Your payment is being processed. Pro access will be enabled once it's confirmed."
        }

        val dateLine = formatDateLine(status, expiryMillis) ?: return base
        return "$base\n$dateLine"
    }

    /**
     * Returns a date line like "Renews on Jul 20, 2025" or "Access until Jul 20, 2025",
     * or null when the status doesn't warrant a date or millis aren't available yet.
     */
    private fun formatDateLine(status: SubscriptionStatus, expiryMillis: Long?): String? {
        if (expiryMillis == null || expiryMillis <= 0L) return null
        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val date = fmt.format(Date(expiryMillis))
        return when (status) {
            SubscriptionStatus.ACTIVE   -> "Renews on $date"
            SubscriptionStatus.CANCELED -> "Access until $date"
            SubscriptionStatus.TRIAL    -> "Trial ends $date"
            else                        -> null
        }
    }

    private fun planFriendlyName(productId: String?): String = when (productId) {
        "urducanvas_monthly" -> "Monthly"
        "urducanvas_6months" -> "6-Month"
        "urducanvas_yearly"  -> "Yearly"
        else                 -> "Pro"
    }

    private fun startIndeterminate() {
        binding.indeterminateTrack.post {
            val trackW = binding.indeterminateTrack.width
            val barW = binding.indeterminateBar.width.takeIf { it > 0 } ?: dp(80)
            indetAnim?.cancel()
            indetAnim = ValueAnimator.ofFloat(-barW.toFloat(), trackW.toFloat()).apply {
                duration = 1300
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    binding.indeterminateBar.translationX = it.animatedValue as Float
                }
                start()
            }
        }
    }

    private fun stopIndeterminate() {
        indetAnim?.cancel()
        indetAnim = null
    }

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
        } catch (_: Exception) { }
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