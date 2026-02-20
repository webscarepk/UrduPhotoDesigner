package com.webscare.urducanvas.ui.navigation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.SubscriptionPlan
import com.webscare.urducanvas.databinding.FragmentSubscriptionsBinding
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SubscriptionsFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentSubscriptionsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SubscriptionsAdapter
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

        binding.root.post {
            startEntranceAnimation()
        }

    }

    private fun View.slideUpSoft(delay: Long = 0) {
        this.translationY = this.height.toFloat()
        this.alpha = 0f

        this.animate()
            .translationY(0f)
            .alpha(1f)
            .setStartDelay(delay)
            .setDuration(700)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    private fun startEntranceAnimation() {

        // Phase 1 — BG fade
        binding.mainBgImage.alpha = 0f
        binding.mainBgImage.animate()
            .alpha(1f)
            .setDuration(500)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {

                // Phase 2 — Card soft slide
                binding.subscriptionsCard.slideUpSoft()

                // Phase 3 — RV after card
                binding.subscriptionsCard.postDelayed({

                    binding.subscriptionsRV.alpha = 1f

                    val controller = AnimationUtils.loadLayoutAnimation(
                        requireContext(),
                        R.anim.layout_drop_controller
                    )
                    binding.subscriptionsRV.layoutAnimation = controller

                    // IMPORTANT — run after layout
                    binding.subscriptionsRV.post {
                        binding.subscriptionsRV.scheduleLayoutAnimation()
                    }
                    loadDummyPlans()
                }, 550)

                // Phase 4 — Bottom section together
                binding.subscriptionsCard.postDelayed({
                    showBottomSection()
                }, 700)
            }
    }

    private fun showBottomSection() {
        val views = listOf(
            binding.continueBtn,
            binding.subTitle,
            binding.termsOfUse,
            binding.view1,
            binding.privacyPolicy,
            binding.view2,
            binding.restore
        )

        views.forEachIndexed { index, view ->
            view.slideUpSoft(delay = (index * 40).toLong())
        }
    }

    private fun loadDummyPlans() {
        val plans = listOf(
            _root_ide_package_.com.webscare.urducanvas.data.model.SubscriptionPlan(
                id = 1,
                title = "Monthly",
                price = "Rs 399",
                duration = "Per Month",
                badge = "SAVE 25%"
            ),
            _root_ide_package_.com.webscare.urducanvas.data.model.SubscriptionPlan(
                id = 2,
                title = "Yearly",
                price = "Rs 2499",
                duration = "Per Year",
                badge = "BEST VALUE"
            ),
            _root_ide_package_.com.webscare.urducanvas.data.model.SubscriptionPlan(
                id = 3,
                title = "Lifetime",
                price = "Rs 4999",
                duration = "One Time",
                badge = null
            )
        )

        plans[1].isSelected = true
        adapter.submitList(plans)
    }

    private fun setEvents() {
        binding.subscriptionsRV.layoutManager = GridLayoutManager(requireContext(), 3)

        adapter = SubscriptionsAdapter { selectedPlan ->
            // handle selection
        }

        binding.subscriptionsRV.adapter = adapter
        binding.back.addPressEffect { findNavController().navigateUp() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}