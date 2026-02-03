package com.example.urduphotodesigner.ui.navigation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.SubscriptionPlan
import com.example.urduphotodesigner.databinding.FragmentSubscriptionsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SubscriptionsFragment : Fragment() {
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
    }

    private fun loadDummyPlans() {
        val plans = listOf(
            SubscriptionPlan(
                id = 1,
                title = "Monthly",
                price = "Rs 399",
                duration = "Per Month",
                badge = "SAVE 25%"
            ),
            SubscriptionPlan(
                id = 2,
                title = "Yearly",
                price = "Rs 2499",
                duration = "Per Year",
                badge = "BEST VALUE"
            ),
            SubscriptionPlan(
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
        adapter = SubscriptionsAdapter { selectedPlan ->
            // handle selection
        }

        binding.subscriptionsRV.adapter = adapter

        loadDummyPlans()
        binding.back.addPressEffect { findNavController().navigateUp() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}