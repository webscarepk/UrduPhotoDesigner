package com.webscare.urducanvas.ui.editor.panels.draw.brush

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        setupSeekBars()
        setupPresets()
        setupSmoothingToggle()
        observeViewModel()
    }

    private fun setupSmoothingToggle() {
        binding.smoothEdgesSwitch.onCheckedChangeListener = { isChecked ->
            viewModel.setBrushSmoothingEnabled(isChecked)
            viewModel.enterDrawingMode(requireActivity())
        }
    }

    private fun setupSeekBars() {
        // Size / Thickness SeekBar (1..100)
        binding.thicknessBar.apply {
            max = 99 // 0..99 corresponds to 1..100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val thickness = (progress + 1).toFloat()
                    binding.sizeValue.text = "${progress + 1}"
                    if (fromUser) {
                        viewModel.setBrushThickness(thickness)
                        viewModel.updateSizePreview(thickness)
                        viewModel.enterDrawingMode(requireActivity())
                    }
                    updatePresetSelection(thickness.toInt())
                }

                override fun onStartTrackingTouch(sb: SeekBar) {
                    viewModel.showSizePreview((sb.progress + 1).toFloat(), isEraser = false)
                }

                override fun onStopTrackingTouch(sb: SeekBar) {
                    viewModel.hideSizePreview()
                }
            })
        }

        // Softness SeekBar (0..100% Softness)
        binding.hardnessBar.apply {
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    binding.hardnessValue.text = "$progress%"
                    if (fromUser) {
                        // 0% softness = 1.0 hardness (crisp), 100% softness = 0.0 hardness (fully soft/feathered/tapered)
                        val hardness = 1f - (progress / 100f)
                        viewModel.setBrushHardness(hardness)
                        viewModel.updateSizePreview(hardness = hardness, isEraser = false)
                        viewModel.enterDrawingMode(requireActivity())
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {
                    val hardness = 1f - (sb.progress / 100f)
                    viewModel.showSizePreview(hardness = hardness, isEraser = false)
                }

                override fun onStopTrackingTouch(sb: SeekBar) {
                    viewModel.hideSizePreview()
                }
            })
        }

        // Opacity SeekBar (0..100%)
        binding.opacityBar.apply {
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    binding.opacityValue.text = "$progress%"
                    if (fromUser) {
                        val opacity = progress / 100f
                        viewModel.setBrushOpacity(opacity)
                        viewModel.updateSizePreview(opacity = opacity, isEraser = false)
                        viewModel.enterDrawingMode(requireActivity())
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {
                    val opacity = sb.progress / 100f
                    viewModel.showSizePreview(opacity = opacity, isEraser = false)
                }

                override fun onStopTrackingTouch(sb: SeekBar) {
                    viewModel.hideSizePreview()
                }
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
                viewModel.enterDrawingMode(requireActivity())
            }
        }
    }

    private fun updatePresetSelection(currentSize: Int) {
        val density = resources.displayMetrics.density
        val strokePx = (1.5f * density + 0.5f).toInt()
        val strokeColor = ContextCompat.getColor(requireContext(), R.color.appColor)
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
            ring.strokeWidth = if (isSelected) strokePx else 0
            ring.strokeColor = strokeColor
            ring.setCardBackgroundColor(contrastColor)
        }
    }

    private fun observeViewModel() {
        viewModel.brushThickness.observe(viewLifecycleOwner) { thickness ->
            val sizeInt = thickness.toInt()
            val progress = (sizeInt - 1).coerceIn(0, 99)
            binding.thicknessBar.progress = progress
            binding.sizeValue.text = "$sizeInt"
            updatePresetSelection(sizeInt)
        }

        viewModel.brushHardness.observe(viewLifecycleOwner) { hardness ->
            // Convert hardness back to softness percentage (1f hardness = 0% soft, 0f hardness = 100% soft)
            val softness = (1f - hardness).coerceIn(0f, 1f)
            val progress = (softness * 100).toInt().coerceIn(0, 100)
            binding.hardnessBar.progress = progress
            binding.hardnessValue.text = "$progress%"
        }

        viewModel.brushOpacity.observe(viewLifecycleOwner) { opacity ->
            val progress = (opacity * 100).toInt().coerceIn(0, 100)
            binding.opacityBar.progress = progress
            binding.opacityValue.text = "$progress%"
        }

        viewModel.isBrushSmoothingEnabled.observe(viewLifecycleOwner) { isEnabled ->
            binding.smoothEdgesSwitch.setCheckedQuietly(isEnabled == true)
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
