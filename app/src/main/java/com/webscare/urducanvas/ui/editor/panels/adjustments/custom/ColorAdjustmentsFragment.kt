package com.webscare.urducanvas.ui.editor.panels.adjustments.custom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.databinding.FragmentColorAdjustmentsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ColorAdjustmentsFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentColorAdjustmentsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
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
            min = 0
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

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        // 💧 Vibrance (0 → 2)
        binding.vibrance.apply {
            min = 0
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

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        // 🌡 Temperature (-100 → +100)
        binding.temperature.apply {
            min = -100
            max = 100
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.temperatureSize.text = progress.toString()
                    if (fromUser) {
                        viewModel.setTemperature(progress.toFloat())
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        // 🟣 Tint (-100 → +100)
        binding.tint.apply {
            min = -100
            max = 100
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.tintSize.text = progress.toString()
                    if (fromUser) {
                        viewModel.setTint(progress.toFloat())
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
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
            if (binding.temperature.progress != safeValue) {
                binding.temperature.progress = safeValue
                binding.temperatureSize.text = "$safeValue"
            }
        }

        viewModel.tint.observe(viewLifecycleOwner) { value ->
            val safeValue = value?.toInt() ?: 0
            if (binding.tint.progress != safeValue) {
                binding.tint.progress = safeValue
                binding.tintSize.text = "$safeValue"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
