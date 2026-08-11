package com.webscare.urducanvas.ui.editor.panels.adjustments.effects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.FeatherDirection
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentFeatherBinding
import dagger.hilt.android.AndroidEntryPoint

import com.webscare.urducanvas.common.views.Bias

@AndroidEntryPoint
class FeatherFragment : Fragment() {

    private var _binding: FragmentFeatherBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeatherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initBiasPad()
        initSeekBars()
        initObservers()
    }

    // ── Bias Pad ──────────────────────────────────────────────────────────────

    private fun initBiasPad() {
        binding.biasPad.onBiasChanged = { bias ->
            viewModel.setFeatherBias(bias)
        }
    }

    // ── SeekBars ──────────────────────────────────────────────────────────────

    private fun initSeekBars() {

        binding.feather.apply {
            max = 100
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    binding.featherSize.text = progress.toString()
                    if (fromUser) {
                        if (progress > 0) viewModel.enableFeature("Feather")
                        viewModel.setFeather(progress.toFloat())
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        binding.softness.apply {
            max = 100
            progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    binding.softnessSize.text = progress.toString()
                    if (fromUser) viewModel.setFeatherWidth(progress.toFloat())
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        binding.opacity.apply {
            max = 255
            progress = 255
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    binding.opacitySize.text = progress.toString()
                    if (fromUser) viewModel.setOpacity(progress)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun initObservers() {

        viewModel.featherBias.observe(viewLifecycleOwner) { bias ->
            binding.biasPad.bias = bias ?: Bias(0f, 0f)
        }

        viewModel.featherRadius.observe(viewLifecycleOwner) { value ->
            val safeValue = value?.toInt() ?: 0
            if (binding.feather.progress != safeValue) binding.feather.progress = safeValue
            binding.featherSize.text = safeValue.toString()

            val isFeatherEnabled = safeValue > 0
            binding.biasPad.isEnabled = isFeatherEnabled
            binding.softness.isEnabled = isFeatherEnabled
            binding.opacity.isEnabled = isFeatherEnabled
            binding.softness.alpha = if (isFeatherEnabled) 1.0f else 0.42f
            binding.opacity.alpha = if (isFeatherEnabled) 1.0f else 0.42f
        }

        viewModel.featherWidth.observe(viewLifecycleOwner) { value ->
            val safeValue = value?.toInt() ?: 50
            if (binding.softness.progress != safeValue) binding.softness.progress = safeValue
            binding.softnessSize.text = safeValue.toString()
        }

        viewModel.opacity.observe(viewLifecycleOwner) { value ->
            val safeValue = value ?: 255
            if (binding.opacity.progress != safeValue) binding.opacity.progress = safeValue
            binding.opacitySize.text = safeValue.toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): FeatherFragment = FeatherFragment()
    }
}