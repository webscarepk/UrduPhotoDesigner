package com.webscare.urducanvas.ui.editor.panels.text.appearance.childs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.webscare.urducanvas.common.canvas.enums.KashidaSize
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentKasheedaBinding
import com.webscare.urducanvas.viewmodels.SubscriptionsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlin.getValue

@AndroidEntryPoint
class KasheedaFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentKasheedaBinding? = null
    private val binding get() = _binding!!
    private val subscriptionViewModel: SubscriptionsViewModel by viewModels()
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKasheedaBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        initObserver()
    }

    private fun updateCardStroke(
        card: com.google.android.material.card.MaterialCardView,
        isSelected: Boolean
    ) {
        val context = card.context
        card.strokeColor = androidx.core.content.ContextCompat.getColor(context, com.webscare.urducanvas.R.color.appColor)
        card.strokeWidth = if (isSelected) 4 else 0
        card.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(context, com.webscare.urducanvas.R.color.contrast))
    }

    private fun initObserver() {
        viewModel.kasheeda.observe(viewLifecycleOwner) { kasheeda ->
            val kasheedaCards = listOf(
                binding.defaultKasheeda to KashidaSize.NONE,
                binding.small to KashidaSize.SMALL,
                binding.medium to KashidaSize.MEDIUM,
                binding.large to KashidaSize.LARGE
            )

            kasheedaCards.forEach { (card, kasheedaSize) ->
                val isSelected = mapKasheedaSizeToFrequency(kasheedaSize) == mapProgressToFrequency(kasheeda)
                updateCardStroke(card, isSelected)
            }

            // Update SeekBar to match frequency range
            binding.kashidaSeekBar.progress = kasheeda
            binding.kashidaValue.text = "$kasheeda"

            val isPremiumKasheeda = kasheeda > 1
            val isSubscribed = subscriptionViewModel.isSubscribed.value  // inject BillingManager, or read via ViewModel
            binding.isPremiumKasheeda.isVisible = isPremiumKasheeda && !isSubscribed
        }
    }

    private fun setEvents() {
        val kasheedaCards = listOf(
            binding.defaultKasheeda to KashidaSize.NONE,
            binding.small to KashidaSize.SMALL,
            binding.medium to KashidaSize.MEDIUM,
            binding.large to KashidaSize.LARGE
        )

        // Handle clicks on Kashida size cards (predefined sizes)
        kasheedaCards.forEach { (card, kasheedaType) ->
            card.addPressEffect {
                val currentFrequency = viewModel.kasheeda.value ?: 0
                val newFrequency = mapKasheedaSizeToFrequency(kasheedaType)

                // Set the Kashida size based on selected card
                if (currentFrequency != newFrequency) {
                    viewModel.setKasheeda(newFrequency)
                }
                kasheedaCards.forEach { (otherCard, otherKasheedaType) ->
                    updateCardStroke(otherCard, otherKasheedaType == kasheedaType)
                }
            }
        }

        // SeekBar to adjust custom Kashida frequency
        binding.kashidaSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.setKasheeda(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // Map SeekBar progress to Kashida frequency (scale from 0 to 10)
    private fun mapProgressToFrequency(progress: Int): Int {
        return when (progress) {
            in 1..3 -> 1  // SMALL range
            in 4..6 -> 4  // MEDIUM range
            in 7..10 -> 7 // LARGE range
            else -> 0     // NONE
        }
    }

    // Map KashidaSize to integer frequency (predefined Kashida sizes mapped to frequency values)
    private fun mapKasheedaSizeToFrequency(kasheedaSize: KashidaSize): Int {
        return when (kasheedaSize) {
            KashidaSize.SMALL -> 1
            KashidaSize.MEDIUM -> 4
            KashidaSize.LARGE -> 7
            KashidaSize.NONE -> 0
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {

        fun newInstance(): KasheedaFragment {
            val fragment = KasheedaFragment()
            return fragment
        }
    }
}