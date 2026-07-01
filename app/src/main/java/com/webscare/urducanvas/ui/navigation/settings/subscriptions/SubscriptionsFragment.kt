package com.webscare.urducanvas.ui.navigation.settings.subscriptions

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.SubscriptionPlan
import com.webscare.urducanvas.databinding.FragmentSubscriptionsBinding
import com.webscare.urducanvas.di.BillingManager
import com.webscare.urducanvas.viewmodels.MainViewModel
import com.webscare.urducanvas.viewmodels.SubscriptionsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SubscriptionsFragment : Fragment() {

    private var _binding: FragmentSubscriptionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SubscriptionsViewModel by viewModels()

    // Shared with the rest of the app — used only to read popular/premium
    // template thumbnails for the banner slideshow.
    private val mainViewModel: MainViewModel by activityViewModels()

    @Inject
    lateinit var billingManager: BillingManager

    private var plans: List<SubscriptionPlan> = emptyList()
    private var selectedIndex = 0
    private val selectedPlan: SubscriptionPlan? get() = plans.getOrNull(selectedIndex)

    private var purchasing = false

    private lateinit var planAdapter: SubscriptionsAdapter

    // ── Banner slideshow ─────────────────────────────────────────────────────
    private val sliderHandler = Handler(Looper.getMainLooper())
    private var sliderSlideCount = 0
    private val sliderTick = object : Runnable {
        override fun run() {
            val vp = _binding?.templateSlider ?: return
            if (sliderSlideCount <= 1) return
            val next = (vp.currentItem + 1) % sliderSlideCount
            vp.setCurrentItem(next, true)
            sliderHandler.postDelayed(this, 3200)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubscriptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPlanList()
        bindFeatureGrid()
        setEvents()
        observe()
        observeTemplates()
        viewModel.loadProducts()
        playHeaderEntrance()
    }

    // ── Plan cards (3-up row) ───────────────────────────────────────────────
    private fun setupPlanList() {
        planAdapter = SubscriptionsAdapter { plan ->
            val index = plans.indexOf(plan)
            if (index == -1 || index == selectedIndex) return@SubscriptionsAdapter
            selectedIndex = index
            updatePlanTexts(animate = true)
        }
        binding.planList.layoutManager =
            LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        binding.planList.adapter = planAdapter
        binding.planList.clipToPadding = false
    }

    /**
     * Keeps the plan cards centered instead of hugging the start edge when
     * there are fewer than a full row's worth — e.g. the plan the person is
     * already subscribed to gets filtered out of the list, leaving 1-2
     * cards that would otherwise sit flush left with dead space on the
     * right. Adds symmetric horizontal padding so the row visually centers;
     * no padding (flush edges) once there's enough content to fill the row.
     *
     * layout_subscriptions_item.xml's card is a fixed 108dp wide with 4dp
     * horizontal margin on each side (116dp total per card) — if that width
     * changes, update CARD_WIDTH_DP below to match.
     */
    private fun centerPlanListIfNeeded() {
        val rv = binding.planList
        rv.post {
            if (_binding == null) return@post
            val itemCount = planAdapter.itemCount
            if (itemCount == 0 || rv.width == 0) return@post

            val contentWidth = itemCount * dp(CARD_WIDTH_DP + CARD_MARGIN_DP * 2)
            val availableWidth = rv.width
            val sidePadding = ((availableWidth - contentWidth) / 2).coerceAtLeast(0)

            rv.setPadding(sidePadding, rv.paddingTop, sidePadding, rv.paddingBottom)
        }
    }

    // ── Static feature grid (2x2) ───────────────────────────────────────────
    private fun bindFeatureGrid() {

        with(binding.featureFonts) {
            featureIcon.setImageResource(R.drawable.ic_font_size)
            featureTitle.text = getString(R.string.sub_feature_fonts_title)
            featureSubtitle.text = getString(R.string.sub_feature_fonts_subtitle)
        }

        with(binding.featureTemplates) {
            featureIcon.setImageResource(R.drawable.ic_templates) // your template icon
            featureTitle.text = getString(R.string.sub_feature_templates_title)
            featureSubtitle.text = getString(R.string.sub_feature_templates_subtitle)
        }

        with(binding.featureExport) {
            featureIcon.setImageResource(R.drawable.ic_export) // your export icon
            featureTitle.text = getString(R.string.sub_feature_export_title)
            featureSubtitle.text = getString(R.string.sub_feature_export_subtitle)
        }

        with(binding.featureStickers) {
            featureIcon.setImageResource(R.drawable.ic_sticker)
            featureTitle.text = getString(R.string.sub_feature_stickers_title)
            featureSubtitle.text = getString(R.string.sub_feature_stickers_subtitle)
        }

        with(binding.featureCustom) {
            featureIcon.setImageResource(R.drawable.ic_pencil)
            featureTitle.text = getString(R.string.sub_feature_custom_title)
            featureSubtitle.text = getString(R.string.sub_feature_custom_subtitle)
        }

        with(binding.featureNoAds) {
            featureIcon.setImageResource(R.drawable.ic_close)
            featureTitle.text = getString(R.string.sub_feature_no_ads_title)
            featureSubtitle.text = getString(R.string.sub_feature_no_ads_subtitle)
        }

        binding.featureFonts.root.addPressEffect {  }
        binding.featureStickers.root.addPressEffect {  }
        binding.featureCustom.root.addPressEffect {  }
        binding.featureNoAds.root.addPressEffect {  }
        binding.featureTemplates.root.addPressEffect {  }
        binding.featureExport.root.addPressEffect {  }
    }

    // ── Entrance animations ─────────────────────────────────────────────────
    // Header (title/subtitle + footer) is visible immediately, so it plays
    // as soon as the view exists. The rest of the content is gated behind
    // the shimmer skeleton, so it plays once — the first time contentGroup
    // actually becomes visible — from playContentEntrance() below.
    private fun playHeaderEntrance() {
        val headerViews = listOf(binding.heroTitle, binding.heroSubtitle)
        headerViews.forEach {
            it.alpha = 0f
            it.translationY = dp(14).toFloat()
        }
        binding.footer.alpha = 0f
        binding.footer.translationY = dp(24).toFloat()

        binding.root.post {
            if (_binding == null) return@post
            headerViews.forEachIndexed { i, v ->
                v.animate()
                    .alpha(1f).translationY(0f)
                    .setStartDelay(i * 60L)
                    .setDuration(380)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            binding.footer.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(160)
                .setDuration(420)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private var contentEntrancePlayed = false
    private fun playContentEntrance() {
        if (contentEntrancePlayed) return
        contentEntrancePlayed = true

        val sections = listOf(
            binding.bannerCard, binding.planList, binding.ctaNote, binding.featureRow1, binding.featureRow2, binding.featureRow3
        )
        sections.forEach {
            it.alpha = 0f
            it.translationY = dp(18).toFloat()
        }
        sections.forEachIndexed { i, v ->
            v.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(i * 70L)
                .setDuration(420)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private var sliderCallbackRegistered = false
    private var templateSliderAdapter: TemplateSliderAdapter? = null

    // ── Banner slideshow of popular templates ───────────────────────────────
    private fun observeTemplates() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.localTemplates.collect { templates ->
                    val slides = templates
                        .filter { it.category?.trim().equals("Islamic", ignoreCase = true) }
                        .ifEmpty { templates }
                        .take(20)
                        .mapNotNull { t ->
                            t.thumbnail_url?.let { url -> TemplateSlide(url, t.template_name ?: "") }
                        }

                    if (slides.isEmpty()) return@collect

                    // Reuse the same adapter across emissions — localTemplates
                    // is a Room Flow that re-emits on every DB write while
                    // templates sync in the background. Recreating the
                    // adapter each time re-binds the visible page, resetting
                    // the shimmer and cancelling the in-flight Glide request.
                    val adapter = templateSliderAdapter ?: TemplateSliderAdapter().also {
                        templateSliderAdapter = it
                        binding.templateSlider.adapter = it
                    }
                    val sizeChanged = slides.size != sliderSlideCount
                    adapter.submitSlides(slides)

                    if (sizeChanged) {
                        buildSliderDots(slides.size)
                        sliderSlideCount = slides.size
                    }

                    if (!sliderCallbackRegistered) {
                        sliderCallbackRegistered = true
                        binding.templateSlider.registerOnPageChangeCallback(object :
                            ViewPager2.OnPageChangeCallback() {
                            override fun onPageSelected(position: Int) = updateSliderDots(position)
                        })
                    }
                    sliderHandler.removeCallbacks(sliderTick)
                    sliderHandler.postDelayed(sliderTick, 3200)
                }
            }
        }
    }

    private fun buildSliderDots(count: Int) {
        val row = binding.sliderDots
        row.removeAllViews()
        repeat(count) { i ->
            val dot = View(requireContext()).apply {
                layoutParams = ViewGroup.MarginLayoutParams(dp(5), dp(5)).apply {
                    marginStart = if (i == 0) 0 else dp(3)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i == 0) color(R.color.white) else 0x66FFFFFF.toInt())
                }
            }
            row.addView(dot)
        }
    }

    private fun updateSliderDots(activeIndex: Int) {
        val row = binding.sliderDots
        for (i in 0 until row.childCount) {
            val dot = row.getChildAt(i)
            (dot.background as? GradientDrawable)?.setColor(
                if (i == activeIndex) color(R.color.white) else 0x66FFFFFF.toInt()
            )
        }
    }

    private fun setEvents() {
        binding.back.addPressEffect { findNavController().navigateUp() }
        binding.continueBtn.addPressEffect { onCtaClicked() }
        binding.manageSubLink.addPressEffect {
            findNavController().navigate(R.id.manageSubscriptionFragment)
        }
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
                    playContentEntrance()

                    planAdapter.submitList(plans)
                    centerPlanListIfNeeded()
                    // First reveal — set instantly, nothing to count up from yet.
                    updatePlanTexts(animate = false)
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

    // ── Price texts, with a counter animation on plan change ───────────────
    // Both ctaNote ("Rs X billed every...") and btnIdleText ("Start Y ·
    // Rs Z/mo") are driven off the same selected plan, so one ValueAnimator
    // interpolates both underlying numbers and re-formats the text every
    // frame — a real odometer-style count, not just a text swap — plus a
    // small pop on the button text for tactile feedback.
    private var dispPerMonth = 0.0
    private var dispTotal = 0.0
    private var priceAnim: ValueAnimator? = null

    private fun applyPlanTexts(plan: SubscriptionPlan, perMonth: Double, total: Double) {
        binding.ctaNote.text = getString(
            R.string.sub_cta_note,
            money(plan.currencySymbol, total),
            plan.billed.lowercase(Locale.getDefault())
        )
        binding.btnIdleText.text = getString(
            R.string.sub_cta_start_plan,
            plan.title,
            money(plan.currencySymbol, perMonth)
        )
    }

    private fun updatePlanTexts(animate: Boolean) {
        val plan = selectedPlan ?: return
        priceAnim?.cancel()

        if (!animate) {
            dispPerMonth = plan.perMonth
            dispTotal = plan.total
            applyPlanTexts(plan, plan.perMonth, plan.total)
            return
        }

        val fromPerMonth = dispPerMonth
        val fromTotal = dispTotal
        priceAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedFraction.toDouble()
                val pm = fromPerMonth + (plan.perMonth - fromPerMonth) * f
                val tot = fromTotal + (plan.total - fromTotal) * f
                dispPerMonth = pm
                dispTotal = tot
                applyPlanTexts(plan, pm, tot)
            }
            start()
        }

        // Small tactile pop on the button text alongside the count-up.
        binding.btnIdleText.animate().cancel()
        binding.btnIdleText.scaleX = 0.94f
        binding.btnIdleText.scaleY = 0.94f
        binding.btnIdleText.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(260)
            .setInterpolator(OvershootInterpolator(2.2f))
            .start()
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

    /**
     * Formats a monetary Double value with the given currency symbol.
     *
     * - PKR (Rs): no decimals needed — "Rs 1,800"
     * - All other currencies (USD, AED, etc.): always 2 decimal places — "USD 4.99", "AED 5.49"
     */
    private fun money(symbol: String, value: Double): String {
        return if (symbol == "Rs") {
            "$symbol ${String.format(Locale.US, "%,.0f", value)}"
        } else {
            "$symbol ${String.format(Locale.US, "%,.2f", value)}"
        }
    }

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sliderHandler.removeCallbacks(sliderTick)
        sliderCallbackRegistered = false
        templateSliderAdapter = null
        contentEntrancePlayed = false
        priceAnim?.cancel()
        binding.btnIdleText.animate().cancel()
        _binding = null
    }

    companion object {
        // Must match layout_subscriptions_item.xml's card width/margins.
        private const val CARD_WIDTH_DP = 108
        private const val CARD_MARGIN_DP = 4
    }
}