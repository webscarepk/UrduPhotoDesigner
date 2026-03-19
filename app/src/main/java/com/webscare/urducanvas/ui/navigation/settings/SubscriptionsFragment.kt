package com.webscare.urducanvas.ui.navigation.settings

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentSubscriptionsBinding
import com.webscare.urducanvas.di.BillingManager
import com.webscare.urducanvas.viewmodels.SubscriptionsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.webscare.urducanvas.common.utils.SubscriptionDialogHelper
import javax.inject.Inject

@AndroidEntryPoint
class SubscriptionsFragment : androidx.fragment.app.Fragment() {

    private var _binding: FragmentSubscriptionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SubscriptionsAdapter
    private val viewModel: SubscriptionsViewModel by viewModels()

    @Inject
    lateinit var billingManager: BillingManager

    private var selectedPlanId: Int = 2

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubscriptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setEvents()
        observeBillingState()
        observeSubscriptionState()
        observePlans()
        viewModel.loadProducts()
        binding.root.post { startEntranceAnimation() }
    }

    // ─── Observe Plans ─────────────────────────────────────────────────────────

    private fun observePlans() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.plans.collect { plans ->
                if (plans.isEmpty()) return@collect
                adapter.submitList(plans)
                selectedPlanId = plans.firstOrNull { it.isSelected }?.id ?: plans.first().id
                // 2 plans hain toh span 2, 3 hain toh span 3
                (binding.subscriptionsRV.layoutManager as GridLayoutManager).spanCount = plans.size
            }
        }
    }

    // ─── Observe Subscription State ───────────────────────────────────────────

    private fun observeSubscriptionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isSubscribed.collect { subscribed ->
                if (subscribed) {
                    binding.continueBtn.text = getString(R.string.upgrade_now)
                    binding.activePlanCard.visibility = View.VISIBLE
                    binding.activePlanCard.alpha = 0f
                } else {
                    binding.continueBtn.text = getString(R.string.continue_)
                    binding.activePlanCard.visibility = View.GONE
                    binding.activePlanCard.alpha = 0f
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.activePlan.collect { productId ->
                binding.subscriptionCardTitle.text = when (productId) {
                    "urducanvas_monthly" -> "Monthly"
                    "urducanvas_6months" -> "6 Months"
                    "urducanvas_yearly" -> "Yearly"
                    else -> "Pro"
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.expiryDate.collect { expiryMillis ->
                if (expiryMillis != null) {
                    val formatted = java.text.SimpleDateFormat(
                        "MMM dd, yyyy", java.util.Locale.getDefault()
                    ).format(java.util.Date(expiryMillis))
                    binding.expiryText.text = "Active until\n$formatted"
                    binding.expiryText.isVisible = true
                } else {
                    binding.expiryText.isVisible = false
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isCancelled.collect { cancelled ->
                if (cancelled) {
                    binding.cancel.text = "Resubscribe"
                } else {
                    binding.cancel.text = "Cancel"
                }
            }
        }
    }

    // ─── Observe Billing State ─────────────────────────────────────────────────
    private fun observeBillingState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.billingState.collect { state ->
                    when (state) {
                        is BillingManager.BillingState.Idle -> {
                            binding.continueBtn.isEnabled = true
                        }
                        is BillingManager.BillingState.Loading -> {
                            binding.continueBtn.isEnabled = false
                        }
                        is BillingManager.BillingState.ProductsLoaded -> {
                            binding.continueBtn.isEnabled = true
                            viewModel.buildPlans(state.products)
                        }
                        is BillingManager.BillingState.PurchaseSuccess -> {
                            binding.continueBtn.isEnabled = true
                            if (viewModel.isRestoring()) {
                                showRestoreSuccessDialog()
                            } else {
                                showPurchaseSuccessDialog()
                            }
                            viewModel.resetState()
                        }
                        is BillingManager.BillingState.Error -> {
                            binding.continueBtn.isEnabled = true
                            if (viewModel.isRestoring()) {
                                showRestoreFailedDialog()
                            } else {
                                showErrorDialog(state.message)
                            }
                            viewModel.resetState()
                        }
                    }
                }
            }
        }
    }

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
                true,
                secondaryText = if (isUpgrade) "View Plan" else " ",
                cancelable = false,
                onPrimary = { findNavController().navigateUp() }
            )
        ).show(childFragmentManager, "purchase_success")
    }

    private fun showCancelConfirmDialog() {
        SubscriptionBottomSheet.newInstance(
            SubscriptionSheetConfig(
                iconRes = R.drawable.ic_cancelled_icon,   // your cancelled state icon
                title = "Subscription Cancelled",
                message = "Your access continues until billing period ends. Manage on Google Play.",
                primaryText = "Continue with free plan",
                secondaryText = "Buy Subscription",
                onPrimary = {
                    openUrl("https://play.google.com/store/account/subscriptions?package=${requireContext().packageName}")
                },
                onSecondary = { findNavController().navigateUp() }
            )
        ).show(childFragmentManager, "cancel_confirm")
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

    // ─── Setup ─────────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        binding.subscriptionsRV.layoutManager = GridLayoutManager(requireContext(), 3)
        adapter = SubscriptionsAdapter { selectedPlan ->
            selectedPlanId = selectedPlan.id
        }
        binding.subscriptionsRV.adapter = adapter
    }

    private fun setEvents() {
        binding.continueBtn.addPressEffect {
            viewModel.subscribe(requireActivity(), selectedPlanId)
        }

        binding.cancel.addPressEffect {
            if (viewModel.isCancelled.value) {
                openUrl("https://play.google.com/store/account/subscriptions?package=${requireContext().packageName}")
            } else {
                showCancelConfirmDialog()
            }
        }

        binding.restore.addPressEffect {
            viewModel.restore()
        }

        binding.termsOfUse.addPressEffect {
            openUrl("https://play.google.com/store/apps/details?id=${requireContext().packageName}")
        }

        binding.privacyPolicy.addPressEffect {
            openUrl("https://urducanvas.com/privacy-policy")
        }

        binding.back.addPressEffect { findNavController().navigateUp() }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: Exception) { }
    }

    // ─── Animations ────────────────────────────────────────────────────────────

    private fun View.slideUpSoft(delay: Long = 0) {
        translationY = height.toFloat(); alpha = 0f
        animate().translationY(0f).alpha(1f)
            .setStartDelay(delay).setDuration(700)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    private fun startEntranceAnimation() {
        binding.mainBgImage.alpha = 0f
        binding.mainBgImage.animate().alpha(1f).setDuration(500)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                binding.subscriptionsCard.slideUpSoft()

                // ✅ Animate activePlanCard right after subscriptionsCard
                if (binding.activePlanCard.visibility == View.VISIBLE) {
                    binding.activePlanCard.alpha = 0f
                    binding.activePlanCard.translationY = binding.activePlanCard.height.toFloat().coerceAtLeast(60f)
                    binding.activePlanCard.slideUpSoft(delay = 150)
                }

                binding.subscriptionsCard.postDelayed({
                    binding.subscriptionsRV.alpha = 1f
                }, 550)

                binding.subscriptionsCard.postDelayed({ showBottomSection() }, 700)
            }
    }

    private fun showBottomSection() {
        val views = mutableListOf(
            binding.continueBtn, binding.subTitle, binding.termsOfUse,
            binding.view1, binding.privacyPolicy, binding.view2, binding.restore
        )
        views.forEachIndexed { i, v -> v.slideUpSoft(delay = (i * 40).toLong()) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSubscriptionStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}