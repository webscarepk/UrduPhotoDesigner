package com.webscare.urducanvas.ui.navigation.settings

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.SubscriptionPlan
import com.webscare.urducanvas.databinding.FragmentSubscriptionsBinding
import com.webscare.urducanvas.di.BillingManager
import com.webscare.urducanvas.viewmodels.SubscriptionsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SubscriptionsFragment : androidx.fragment.app.Fragment() {

    private var _binding: FragmentSubscriptionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SubscriptionsViewModel by viewModels()

    @Inject
    lateinit var billingManager: BillingManager

    private var plans: List<SubscriptionPlan> = emptyList()
    private var selectedIndex = 0
    private val selectedPlan: SubscriptionPlan? get() = plans.getOrNull(selectedIndex)

    // Changed from Int to Double to correctly animate decimal prices (USD, AED, etc.)
    private var dispPerMonth = 0.0
    private var dispTotal = 0.0
    private var dispSave = 0.0

    private var thumbSpring: SpringAnimation? = null
    private var priceAnim: ValueAnimator? = null
    private var purchasing = false

    private val benefits = listOf(
        "Premium Urdu Templates",
        "Custom Urdu Fonts (TTF/OTF)",
        "Premium Stickers & Library",
        "Request Custom Designs",
        "No Ads — Ever"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubscriptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.priceStrike.paintFlags =
            binding.priceStrike.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        renderBenefits()
        setEvents()
        observe()
        viewModel.loadProducts()
    }

    private fun renderBenefits() {
        binding.featuresList.removeAllViews()
        benefits.forEach { b ->
            val row = layoutInflater.inflate(R.layout.item_pro_benefit, binding.featuresList, false)
            row.findViewById<TextView>(R.id.benefitText).text = b
            binding.featuresList.addView(row)
        }
    }

    private fun setEvents() {
        binding.back.addPressEffect { findNavController().navigateUp() }
        binding.continueBtn.addPressEffect { onCtaClicked() }
        binding.termsOfUse.addPressEffect {
            openUrl("https://play.google.com/store/apps/details?id=${requireContext().packageName}")
        }
        binding.privacyPolicy.addPressEffect { openUrl("https://urducanvas.com/privacy-policy") }
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.plans.collect { list ->
                    if (list.isEmpty()) return@collect
                    plans = list
                    selectedIndex = list.indexOfFirst { it.isSelected }.coerceAtLeast(0)

                    binding.skeleton.stopShimmer()
                    binding.skeleton.isVisible = false
                    binding.contentGroup.isVisible = true

                    buildSegments()
                    bindInfoCard()
                    selectedPlan?.let {
                        dispPerMonth = it.perMonth; dispTotal = it.total; dispSave = it.save
                    }
                    bindDetailCard(animate = false)
                    updateCtaText()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.billingState.collect { state ->
                    when (state) {
                        is BillingManager.BillingState.Loading ->
                            binding.continueBtn.isEnabled = false

                        is BillingManager.BillingState.ProductsLoaded -> {
                            binding.continueBtn.isEnabled = true
                            viewModel.buildPlans(state.products)
                        }

                        is BillingManager.BillingState.PurchaseSuccess -> {
                            setButtonState(ButtonState.DONE)
                            binding.continueBtn.postDelayed({ goToManage() }, 720)
                            viewModel.resetState()
                        }

                        is BillingManager.BillingState.Error -> {
                            purchasing = false
                            setButtonState(ButtonState.IDLE)
                            binding.continueBtn.isEnabled = true
                            showErrorDialog(state.message)
                            viewModel.resetState()
                        }

                        is BillingManager.BillingState.Idle -> {
                            if (purchasing) {
                                purchasing = false
                                setButtonState(ButtonState.IDLE)
                            }
                            binding.continueBtn.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    // ── Segmented toggle ───────────────────────────────────────────────────────
    private fun buildSegments() {
        val tabs = binding.segmentTabs
        tabs.removeAllViews()
        plans.forEachIndexed { i, plan ->
            val isActive = i == selectedIndex
            val tab = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                addPressEffect { selectIndex(i) }
            }

            val titleView = TextView(requireContext()).apply {
                text = plan.title
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setTextColor(color(if (isActive) R.color.sub_segment_text_active else R.color.sub_segment_text))
                typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.bold)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            tab.addView(titleView)

            if (plan.hasDiscount) {
                val saveView = TextView(requireContext()).apply {
                    text = "Save ${plan.discountPercent}%"
                    textSize = 10f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(color(if (isActive) R.color.sub_segment_text_active else R.color.appColor))
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                tab.addView(saveView)
            }

            tabs.addView(tab)
        }

        binding.segmentTrack.doOnLayout {
            val count = plans.size.coerceAtLeast(1)
            val inner = binding.segmentTrack.width -
                    binding.segmentTrack.paddingLeft - binding.segmentTrack.paddingRight
            val w = inner / count
            binding.segmentThumb.layoutParams = binding.segmentThumb.layoutParams.apply { width = w }
            binding.segmentThumb.requestLayout()
            binding.segmentThumb.translationX = (selectedIndex * w).toFloat()

            thumbSpring = SpringAnimation(binding.segmentThumb, DynamicAnimation.TRANSLATION_X).apply {
                spring = SpringForce().apply {
                    stiffness = 520f
                    dampingRatio = 0.62f
                }
            }
        }
    }

    private fun selectIndex(index: Int) {
        if (index == selectedIndex || index !in plans.indices) return
        selectedIndex = index

        val count = plans.size.coerceAtLeast(1)
        val inner = binding.segmentTrack.width -
                binding.segmentTrack.paddingLeft - binding.segmentTrack.paddingRight
        val target = (index * (inner / count)).toFloat()
        thumbSpring?.animateToFinalPosition(target)
            ?: run { binding.segmentThumb.translationX = target }

        binding.segmentTabs.children().forEachIndexed { i, v ->
            val tab = v as android.widget.LinearLayout
            (tab.getChildAt(0) as? TextView)?.setTextColor(
                color(if (i == index) R.color.sub_segment_text_active else R.color.sub_segment_text)
            )
            (tab.getChildAt(1) as? TextView)?.setTextColor(
                color(if (i == index) R.color.sub_segment_text_active else R.color.appColor)
            )
        }

        bindInfoCard()
        bindDetailCard(animate = true)
        updateCtaText()
    }

    // ── Info + detail card binding ─────────────────────────────────────────────
    private fun bindInfoCard() {
        val subscribed = viewModel.isSubscribed.value
        if (subscribed) {
            val name = planName(viewModel.activePlan.value)
            binding.infoTitle.text = "You're on the $name plan"
            binding.infoSubtitle.text = getString(R.string.sub_current_subtitle)
            binding.activeBadge.isVisible = true
        } else {
            binding.infoTitle.text = getString(R.string.sub_free_title)
            binding.infoSubtitle.text = getString(R.string.sub_free_subtitle)
            binding.activeBadge.isVisible = false
        }
    }

    private fun bindDetailCard(animate: Boolean) {
        val plan = selectedPlan ?: return

        binding.termLabel.text = getString(R.string.sub_plan_label, plan.title)

        binding.discountPill.isVisible = plan.hasDiscount
        if (plan.hasDiscount) binding.discountPill.text = getString(R.string.sub_percent_off, plan.discountPercent)

        binding.priceStrike.isVisible = plan.showStrike
        if (plan.showStrike) {
            binding.priceStrike.text = "${money(plan.currencySymbol, plan.origPerMonth)}/mo"
        }

        binding.saveShimmer.isVisible = plan.hasSave

        priceAnim?.cancel()
        if (!animate) {
            applyPriceFrame(plan, plan.perMonth, plan.total, plan.save)
            return
        }
        val fromPm = dispPerMonth; val fromTot = dispTotal; val fromSave = dispSave
        priceAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 480
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val f = a.animatedFraction.toDouble()
                val pm = fromPm + (plan.perMonth - fromPm) * f
                val tot = fromTot + (plan.total - fromTot) * f
                val sv = fromSave + (plan.save - fromSave) * f
                applyPriceFrame(plan, pm, tot, sv)
            }
            start()
        }
    }

    private fun applyPriceFrame(plan: SubscriptionPlan, perMonth: Double, total: Double, save: Double) {
        dispPerMonth = perMonth; dispTotal = total; dispSave = save
        binding.priceMain.text = money(plan.currencySymbol, perMonth)
        binding.priceSub.text = getString(
            R.string.sub_per_month_billed,
            money(plan.currencySymbol, total),
            plan.billed.lowercase(Locale.getDefault())
        )
        if (plan.hasSave) {
            val suffix = if (plan.months >= 12) getString(R.string.sub_save_suffix_year)
            else getString(R.string.sub_save_suffix_6month)
            binding.saveLine.text =
                getString(R.string.sub_save_line, money(plan.currencySymbol, save), suffix).trim()
        }
        binding.ctaNote.text = getString(
            R.string.sub_cta_note,
            money(plan.currencySymbol, total),
            plan.billed.lowercase(Locale.getDefault())
        )
    }

    private fun updateCtaText() {
        val plan = selectedPlan ?: return
        binding.btnIdleText.text = if (plan.hasDiscount) {
            "Start ${plan.title} — Save ${plan.discountPercent}%"
        } else {
            "Start ${plan.title}"
        }
    }

    // ── Subscribe button morph ─────────────────────────────────────────────────
    private enum class ButtonState { IDLE, BUSY, DONE }

    private fun setButtonState(state: ButtonState) {
        binding.btnIdleText.isVisible = state == ButtonState.IDLE
        binding.btnSpinner.isVisible = state == ButtonState.BUSY
        binding.btnDone.isVisible = state == ButtonState.DONE
        binding.continueBtn.isClickable = state == ButtonState.IDLE
    }

    private fun onCtaClicked() {
        if (purchasing) return
        val plan = selectedPlan ?: return
        purchasing = true
        setButtonState(ButtonState.BUSY)
        viewModel.subscribe(requireActivity(), plan.id)
    }

    private fun goToManage() {
        purchasing = false
        val navController = findNavController()
        navController.popBackStack(R.id.manageSubscriptionFragment, true)
        navController.navigate(R.id.manageSubscriptionFragment)
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
                onSecondary = { openUrl("mailto:support@urducanvas.com?subject=Purchase Issue") }))
            .show(childFragmentManager, "purchase_error")
    }

    private fun planName(productId: String?): String = when (productId) {
        "urducanvas_monthly" -> "Monthly"
        "urducanvas_6months" -> "6-Month"
        "urducanvas_yearly" -> "Yearly"
        else -> "Pro"
    }

    /**
     * Formats a monetary Double value with the given currency symbol.
     *
     * - PKR (Rs): no decimals needed — "Rs 1,800"
     * - All other currencies (USD, AED, etc.): always 2 decimal places — "USD 4.99", "AED 5.49"
     */
    private fun money(symbol: String, value: Double): String {
        return if (symbol == "Rs") {
            // PKR — whole numbers, comma-separated
            "$symbol ${String.format(Locale.US, "%,.0f", value)}"
        } else {
            // USD, AED, EUR, etc. — always show 2 decimal places
            "$symbol ${String.format(Locale.US, "%,.2f", value)}"
        }
    }

    private fun color(res: Int) = androidx.core.content.ContextCompat.getColor(requireContext(), res)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun ViewGroup.children() = (0 until childCount).map { getChildAt(it) }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        priceAnim?.cancel()
        thumbSpring?.cancel()
        _binding = null
    }
}