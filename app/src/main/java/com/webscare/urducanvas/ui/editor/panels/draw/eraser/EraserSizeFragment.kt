package com.webscare.urducanvas.ui.editor.panels.draw.eraser

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
import com.webscare.urducanvas.databinding.FragmentEraserSizeBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EraserSizeFragment : Fragment() {

    private var _binding: FragmentEraserSizeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEraserSizeBinding.inflate(inflater, container, false)
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
            viewModel.setEraserSmoothingEnabled(isChecked)
            viewModel.enterDrawingMode(requireActivity())
        }
    }

    private fun setupSeekBars() {
        // Thickness / Size SeekBar (1..150)
        binding.thicknessBar.apply {
            max = 149 // 0..149 -> 1..150
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val thickness = (progress + 1).toFloat()
                    binding.sizeValue.text = "${progress + 1}"
                    if (fromUser) {
                        viewModel.setEraserThickness(thickness)
                        viewModel.updateSizePreview(thickness)
                        viewModel.enterDrawingMode(requireActivity())
                    }
                    updatePresetSelection(thickness.toInt())
                }

                override fun onStartTrackingTouch(sb: SeekBar) {
                    viewModel.showSizePreview((sb.progress + 1).toFloat(), isEraser = true)
                }

                override fun onStopTrackingTouch(sb: SeekBar) {
                    viewModel.hideSizePreview()
                }
            })
        }

        // Softness SeekBar (0..100%)
        binding.hardnessBar.apply {
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    binding.hardnessValue.text = "$progress%"
                    if (fromUser) {
                        // 0% softness = 1.0 hardness (crisp), 100% softness = 0.0 hardness (fully feathered)
                        val hardness = 1f - (progress / 100f)
                        viewModel.setEraserHardness(hardness)
                        viewModel.updateSizePreview(hardness = hardness, isEraser = true)
                        viewModel.enterDrawingMode(requireActivity())
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {
                    val hardness = 1f - (sb.progress / 100f)
                    viewModel.showSizePreview(hardness = hardness, isEraser = true)
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
                        viewModel.setEraserOpacity(opacity)
                        viewModel.updateSizePreview(opacity = opacity, isEraser = true)
                        viewModel.enterDrawingMode(requireActivity())
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {
                    val opacity = sb.progress / 100f
                    viewModel.showSizePreview(opacity = opacity, isEraser = true)
                }

                override fun onStopTrackingTouch(sb: SeekBar) {
                    viewModel.hideSizePreview()
                }
            })
        }
    }

    private fun setupPresets() {
        val presetViews = listOf(
            binding.sizePreset8 to 8,
            binding.sizePreset16 to 16,
            binding.sizePreset24 to 24,
            binding.sizePreset32 to 32,
            binding.sizePreset48 to 48,
            binding.sizePreset64 to 64,
            binding.sizePreset80 to 80,
            binding.sizePreset100 to 100
        )

        presetViews.forEach { (view, size) ->
            view.addPressEffect {
                viewModel.setEraserThickness(size.toFloat())
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
            8 to binding.sizeDotRing8,
            16 to binding.sizeDotRing16,
            24 to binding.sizeDotRing24,
            32 to binding.sizeDotRing32,
            48 to binding.sizeDotRing48,
            64 to binding.sizeDotRing64,
            80 to binding.sizeDotRing80,
            100 to binding.sizeDotRing100
        )

        rings.forEach { (size, ring) ->
            val isSelected = size == currentSize
            ring.strokeWidth = if (isSelected) strokePx else 0
            ring.strokeColor = strokeColor
            ring.setCardBackgroundColor(contrastColor)
        }
    }

    private fun observeViewModel() {
        viewModel.eraserThickness.observe(viewLifecycleOwner) { thickness ->
            val t = (thickness ?: 24f).toInt()
            val progress = (t - 1).coerceIn(0, 149)
            if (binding.thicknessBar.progress != progress) {
                binding.thicknessBar.progress = progress
            }
            binding.sizeValue.text = "$t"
            updatePresetSelection(t)
        }

        viewModel.eraserHardness.observe(viewLifecycleOwner) { hardness ->
            val h = hardness ?: 1f
            // 0% softness = 1.0 hardness, 100% softness = 0.0 hardness
            val softnessPercent = ((1f - h) * 100f).toInt().coerceIn(0, 100)
            if (binding.hardnessBar.progress != softnessPercent) {
                binding.hardnessBar.progress = softnessPercent
            }
            binding.hardnessValue.text = "$softnessPercent%"
        }

        viewModel.eraserOpacity.observe(viewLifecycleOwner) { opacity ->
            val o = opacity ?: 1f
            val opacityPercent = (o * 100f).toInt().coerceIn(0, 100)
            if (binding.opacityBar.progress != opacityPercent) {
                binding.opacityBar.progress = opacityPercent
            }
            binding.opacityValue.text = "$opacityPercent%"
        }

        viewModel.isEraserSmoothingEnabled.observe(viewLifecycleOwner) { isSmoothing ->
            binding.smoothEdgesSwitch.setChecked(isSmoothing == true, animate = false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
