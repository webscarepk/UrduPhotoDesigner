package com.webscare.urducanvas.ui.editor.panels.adjustments.custom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.databinding.FragmentColorAdjustmentsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ColorAdjustmentsFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentColorAdjustmentsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentColorAdjustmentsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initSeekBars()
        initObservers()
    }

    // 🟡 Initialize SeekBars
    private fun initSeekBars() {
        // 🎨 Saturation (0 → 2)
        binding.saturation.apply {
            max = 200
            progress = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress / 100f
                    binding.saturationSize.text = String.format("%.2f", value)
                    if (fromUser) {
                        viewModel.setSaturation(value)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {
                    viewModel.enableFeature("Color")
                }

                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        // 💧 Vibrance (0 → 2)
        binding.vibrance.apply {
            max = 200
            progress = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress / 100f
                    binding.vibranceSize.text = String.format("%.2f", value)
                    if (fromUser) {
                        viewModel.setVibrance(value)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {
                    viewModel.enableFeature("Color")
                }

                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        // Temperature (-100 to +100)
        // SeekBar.setMin() requires API 26; minSdk is 24, so we emulate negative
        // range with an offset: raw 0..200 maps to -100..+100 via (progress - 100).
        binding.temperature.apply {
            max = 200
            progress = 100 // represents 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress - 100
                    binding.temperatureSize.text = value.toString()
                    if (fromUser) {
                        viewModel.setTemperature(value.toFloat())
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {
                    viewModel.enableFeature("Color")
                }

                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        // Tint (-100 to +100) -- same offset trick as temperature
        binding.tint.apply {
            max = 200
            progress = 100 // represents 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress - 100
                    binding.tintSize.text = value.toString()
                    if (fromUser) {
                        viewModel.setTint(value.toFloat())
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {
                    viewModel.enableFeature("Color")
                }

                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
    }

    // 🟢 Observe ViewModel LiveData → Reflect UI
    private fun initObservers() {
        viewModel.saturation.observe(viewLifecycleOwner) { value ->
            val scaled = ((value ?: 1f) * 100).toInt()
            if (binding.saturation.progress != scaled) {
                binding.saturation.progress = scaled
                binding.saturationSize.text = String.format("%.2f", value ?: 1f)
            }
        }

        viewModel.vibrance.observe(viewLifecycleOwner) { value ->
            val scaled = ((value ?: 1f) * 100).toInt()
            if (binding.vibrance.progress != scaled) {
                binding.vibrance.progress = scaled
                binding.vibranceSize.text = String.format("%.2f", value ?: 1f)
            }
        }

        viewModel.temperature.observe(viewLifecycleOwner) { value ->
            val safeValue = value?.toInt() ?: 0
            val seekProgress = safeValue + 100 // offset back to 0..200
            if (binding.temperature.progress != seekProgress) {
                binding.temperature.progress = seekProgress
                binding.temperatureSize.text = "$safeValue"
            }
        }

        viewModel.tint.observe(viewLifecycleOwner) { value ->
            val safeValue = value?.toInt() ?: 0
            val seekProgress = safeValue + 100 // offset back to 0..200
            if (binding.tint.progress != seekProgress) {
                binding.tint.progress = seekProgress
                binding.tintSize.text = "$safeValue"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
