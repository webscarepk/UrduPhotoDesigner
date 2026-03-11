package com.webscare.urducanvas.ui.navigation.settings

import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.SubscriptionPlan
import com.webscare.urducanvas.databinding.FragmentSubscriptionsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import android.content.Intent
import com.webscare.urducanvas.di.BillingManager
import com.webscare.urducanvas.viewmodels.SubscriptionsViewModel

@AndroidEntryPoint
class SubscriptionsFragment : androidx.fragment.app.Fragment() {

    private var _binding: FragmentSubscriptionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SubscriptionsAdapter
    private val viewModel: SubscriptionsViewModel by viewModels()

    private var selectedPlanId: Int = 2  // Default: 6-month plan

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubscriptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setEvents()
        loadDummyPlans()
        observeBillingState()
        viewModel.loadProducts()          // Connect + query Play Console

        binding.root.post { startEntranceAnimation() }
    }

    // ─── Observe Billing ───────────────────────────────────────────────────────

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
                            // Optionally update prices from real Play Console data here
                        }
                        is BillingManager.BillingState.PurchaseSuccess -> {
                            showSuccessDialog()
                            viewModel.resetState()
                        }
                        is BillingManager.BillingState.Error -> {
                            binding.continueBtn.isEnabled = true
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            viewModel.resetState()
                        }
                    }
                }
            }
        }
    }

    // ─── Success Dialog ────────────────────────────────────────────────────────

    private fun showSuccessDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🎉 Subscription Activated!")
            .setMessage("You now have full access to Urdu Canvas Premium. Enjoy!")
            .setPositiveButton("Let's Go!") { dialog, _ ->
                dialog.dismiss()
                findNavController().navigateUp()
            }
            .setCancelable(false)
            .show()
    }

    // ─── Events ────────────────────────────────────────────────────────────────

    private fun setEvents() {
        binding.subscriptionsRV.layoutManager = GridLayoutManager(requireContext(), 3)

        adapter = SubscriptionsAdapter { selectedPlan ->
            selectedPlanId = selectedPlan.id
        }

        binding.subscriptionsRV.adapter = adapter

        // Subscribe button
        binding.continueBtn.addPressEffect {
            viewModel.subscribe(requireActivity(), selectedPlanId)
        }

        // Restore purchases
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
        } catch (e: Exception) { /* no browser */ }
    }

    // ─── Plans ─────────────────────────────────────────────────────────────────

    private fun loadDummyPlans() {
        val plans = listOf(
            SubscriptionPlan(id = 1, title = "Monthly",  price = "Rs 399",  duration = "/Month",    badge = null),
            SubscriptionPlan(id = 2, title = "6 Months", price = "Rs 899",  duration = "/6 Months", badge = "Save 25%"),
            SubscriptionPlan(id = 3, title = "1 Year",   price = "Rs 2999", duration = "/ Year",    badge = "Save 35%")
        )
        plans[1].isSelected = true
        adapter.submitList(plans)
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
                binding.subscriptionsCard.postDelayed({
                    binding.subscriptionsRV.alpha = 1f
                    val controller = AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_drop_controller)
                    binding.subscriptionsRV.layoutAnimation = controller
                    binding.subscriptionsRV.post { binding.subscriptionsRV.scheduleLayoutAnimation() }
                    loadDummyPlans()
                }, 550)
                binding.subscriptionsCard.postDelayed({ showBottomSection() }, 700)
            }
    }

    private fun showBottomSection() {
        listOf(binding.continueBtn, binding.subTitle, binding.termsOfUse,
            binding.view1, binding.privacyPolicy, binding.view2, binding.restore)
            .forEachIndexed { i, v -> v.slideUpSoft(delay = (i * 40).toLong()) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}