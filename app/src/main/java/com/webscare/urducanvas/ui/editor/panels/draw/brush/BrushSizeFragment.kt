package com.webscare.urducanvas.ui.editor.panels.draw.brush

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentBrushSizeBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BrushSizeFragment : Fragment() {

    private var _binding: FragmentBrushSizeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    private val presetSizes = listOf(2, 4, 8, 16, 32, 48, 64, 80)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrushSizeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSeekBar()
        setupPresets()
        observeViewModel()
    }

    private fun setupSeekBar() {
        binding.thicknessBar.apply {
            max = 99 // 0..99 corresponds to 1..100 px
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val thickness = (progress + 1).toFloat()
                    binding.sizeValue.text = "${progress + 1} px"
                    if (fromUser) {
                        viewModel.setBrushThickness(thickness)
                    }
                    updatePresetSelection(thickness.toInt())
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
    }

    private fun setupPresets() {
        val presetViews = listOf(
            binding.sizePreset2 to 2,
            binding.sizePreset4 to 4,
            binding.sizePreset8 to 8,
            binding.sizePreset16 to 16,
            binding.sizePreset32 to 32,
            binding.sizePreset48 to 48,
            binding.sizePreset64 to 64,
            binding.sizePreset80 to 80
        )

        presetViews.forEach { (view, size) ->
            view.addPressEffect {
                viewModel.setBrushThickness(size.toFloat())
            }
        }
    }

    private fun updatePresetSelection(currentSize: Int) {
        val appColor = ContextCompat.getColor(requireContext(), R.color.appColor)
        val contrastColor = ContextCompat.getColor(requireContext(), R.color.contrast)

        val rings = mapOf(
            2 to binding.sizeDotRing2,
            4 to binding.sizeDotRing4,
            8 to binding.sizeDotRing8,
            16 to binding.sizeDotRing16,
            32 to binding.sizeDotRing32,
            48 to binding.sizeDotRing48,
            64 to binding.sizeDotRing64,
            80 to binding.sizeDotRing80
        )

        rings.forEach { (size, ring) ->
            val isSelected = size == currentSize
            ring.backgroundTintList = ColorStateList.valueOf(
                if (isSelected) appColor else contrastColor
            )
        }
    }

    private fun observeViewModel() {
        viewModel.brushThickness.observe(viewLifecycleOwner) { thickness ->
            val sizeInt = thickness.toInt()
            val progress = (sizeInt - 1).coerceIn(0, 99)
            binding.thicknessBar.progress = progress
            binding.sizeValue.text = "$sizeInt px"
            updatePresetSelection(sizeInt)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): BrushSizeFragment = BrushSizeFragment()
    }
}
