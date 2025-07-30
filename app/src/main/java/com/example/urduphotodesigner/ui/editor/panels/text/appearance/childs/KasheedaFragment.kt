package com.example.urduphotodesigner.ui.editor.panels.text.appearance.childs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.KashidaSize
import com.example.urduphotodesigner.common.canvas.enums.LetterCasing
import com.example.urduphotodesigner.databinding.FragmentKasheedaBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class KasheedaFragment : Fragment() {
    private var _binding: FragmentKasheedaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKasheedaBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        initObserver()
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
                card.strokeWidth = if (mapKasheedaSizeToFrequency(kasheedaSize) == mapProgressToFrequency(kasheeda)) 4 else 0
            }

            // Update SeekBar to match frequency range
            binding.kashidaSeekBar.progress = kasheeda
            binding.kashidaValue.text = "$kasheeda"
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
            card.setOnClickListener {
                val currentFrequency = viewModel.kasheeda.value ?: 0
                val newFrequency = mapKasheedaSizeToFrequency(kasheedaType)

                // Set the Kashida size based on selected card
                if (currentFrequency != newFrequency) {
                    viewModel.setKasheeda(newFrequency)
                }
            }
        }

        // SeekBar to adjust custom Kashida frequency
        binding.kashidaSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
               if (fromUser){
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