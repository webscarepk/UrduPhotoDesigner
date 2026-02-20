package com.webscare.urducanvas.ui.editor.panels.adjustments.custom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.databinding.FragmentAdvancedAdjustmentsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AdvancedAdjustmentsFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentAdvancedAdjustmentsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdvancedAdjustmentsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initSeekBars()
        initObservers()
    }

    // 🎛 Initialize SeekBars
    private fun initSeekBars() {

        // 🟡 Blur (0 → 25)
        binding.blur.apply {
            min = 0
            max = 25
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.blurSize.text = progress.toString()
                    if (fromUser) {
                        viewModel.setBlur(progress.toFloat())
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        // 🟢 Sharpness (0 → 5)
        binding.sharpness.apply {
            min = 0
            max = 500
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress / 100f // normalize 0–5
                    binding.sharpnessSize.text = String.format("%.2f", value)
                    if (fromUser) {
                        viewModel.setSharpness(value)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        // 🔵 Clarity (-100 → +100)
        binding.clarity.apply {
            min = -100
            max = 100
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.claritySize.text = progress.toString()
                    if (fromUser) {
                        viewModel.setClarity(progress.toFloat())
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        // 🟣 Fade / Vignette (0 → 100)
        binding.fade.apply {
            min = 0
            max = 100
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.fadeSize.text = progress.toString()
                    if (fromUser) {
                        viewModel.setFade(progress.toFloat())
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
    }

    // 🟢 Observe ViewModel LiveData to sync UI
    private fun initObservers() {

        viewModel.blur.observe(viewLifecycleOwner) { value ->
            val safeValue = value?.toInt() ?: 0
            if (binding.blur.progress != safeValue) {
                binding.blur.progress = safeValue
                binding.blurSize.text = "$safeValue"
            }
        }

        viewModel.sharpness.observe(viewLifecycleOwner) { value ->
            val scaled = ((value ?: 0f) * 100).toInt() // map 0–5 to 0–500
            if (binding.sharpness.progress != scaled) {
                binding.sharpness.progress = scaled
                binding.sharpnessSize.text = String.format("%.2f", value ?: 0f)
            }
        }

        viewModel.clarity.observe(viewLifecycleOwner) { value ->
            val safeValue = value?.toInt() ?: 0
            if (binding.clarity.progress != safeValue) {
                binding.clarity.progress = safeValue
                binding.claritySize.text = "$safeValue"
            }
        }

        viewModel.fade.observe(viewLifecycleOwner) { value ->
            val safeValue = value?.toInt() ?: 0
            if (binding.fade.progress != safeValue) {
                binding.fade.progress = safeValue
                binding.fadeSize.text = "$safeValue"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}