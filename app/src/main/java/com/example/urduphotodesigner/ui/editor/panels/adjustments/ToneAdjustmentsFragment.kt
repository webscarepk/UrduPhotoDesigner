package com.example.urduphotodesigner.ui.editor.panels.adjustments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.databinding.FragmentToneAdjustmentsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ToneAdjustmentsFragment : Fragment() {
    private var _binding: FragmentToneAdjustmentsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToneAdjustmentsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initSeekBars()
        initObservers()
    }

    private fun initObservers() {
        viewModel.brightness.observe(viewLifecycleOwner) { value ->
            val safeValue = value?.toInt() ?: 0
            if (binding.brightness.progress != safeValue) {
                binding.brightness.progress = safeValue
                binding.brightnessSize.text = "$safeValue"
            }
        }

        viewModel.contrast.observe(viewLifecycleOwner) { value ->
            val scaled = ((value ?: 1f) * 100).toInt() // convert 0–2 → 0–200
            if (binding.contrast.progress != scaled) {
                binding.contrast.progress = scaled
                binding.contrastSize.text = String.format("%.2f", value ?: 1f)
            }
        }

        viewModel.exposure.observe(viewLifecycleOwner) { value ->
            val scaled = ((value ?: 0f) * 10).toInt() // -2.0 → +2.0 mapped to -20 → 20
            if (binding.exposure.progress != scaled) {
                binding.exposure.progress = scaled
                binding.exposureSize.text = String.format("%.1f", value ?: 0f)
            }
        }

        viewModel.shadows.observe(viewLifecycleOwner) { value ->
            val safeValue = value?.toInt() ?: 0
            if (binding.shadows.progress != safeValue) {
                binding.shadows.progress = safeValue
                binding.shadowsSize.text = "$safeValue"
            }
        }
    }

    private fun initSeekBars() {

        // 🟡 Brightness
        binding.brightness.apply {
            min = -100
            max = 100
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        binding.brightnessSize.text = progress.toString()
                        viewModel.setBrightness(progress.toFloat())
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        // 🟢 Contrast
        binding.contrast.apply {
            min = 0
            max = 200
            progress = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val contrastValue = progress / 100f // normalize 0–2
                        binding.contrastSize.text = String.format("%.2f", contrastValue)
                        viewModel.setContrast(contrastValue)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        // 🔵 Exposure
        binding.exposure.apply {
            min = -20
            max = 20
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val exposureValue = progress / 10f // -2.0 → +2.0
                        binding.exposureSize.text = String.format("%.1f", exposureValue)
                        viewModel.setExposure(exposureValue)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        // 🟣 Shadows
        binding.shadows.apply {
            min = -100
            max = 100
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        binding.shadowsSize.text = progress.toString()
                        viewModel.setShadows(progress.toFloat())
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}