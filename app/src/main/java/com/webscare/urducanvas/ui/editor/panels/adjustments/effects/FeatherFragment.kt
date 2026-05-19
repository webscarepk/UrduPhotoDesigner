package com.webscare.urducanvas.ui.editor.panels.adjustments.effects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.databinding.FragmentFeatherBinding
import dagger.hilt.android.AndroidEntryPoint

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
        initSeekBars()
        initObservers()
    }

    private fun initSeekBars() {

        // ── Feather radius ────────────────────────────────────────────────────
        // Seekbar 0–100 maps to featherRadius 0–100.
        // The sqrt curve inside drawFeatherMask ensures low values (1–10) are
        // immediately visible — no dead zone at the start of the range.
        binding.feather.apply {
            max = 100
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    binding.featherSize.text = progress.toString()
                    if (fromUser) {
                        // Enable hasFeather the first time the user moves the slider,
                        // not on touch-down, so we don't push a stale undo action.
                        if (progress > 0) viewModel.enableFeature("Feather")
                        viewModel.setFeather(progress.toFloat())
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        // ── Softness (featherWidth) ───────────────────────────────────────────
        // Seekbar 0–100 maps to an exponent of 1.0–8.0 inside drawFeatherMask.
        // 0  = hard linear ramp (clear transition band visible at edge).
        // 50 = gentle S-curve (natural photographic softness).
        // 100 = very gradual ease-in (barely perceptible edge, highly diffused).
        binding.softness.apply {
            max = 100
            progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    binding.softnessSize.text = progress.toString()
                    if (fromUser) {
                        viewModel.setFeatherWidth(progress.toFloat())
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        // ── Opacity ──────────────────────────────────────────────────────────
        binding.opacity.apply {
            max = 255
            progress = 255
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    binding.opacitySize.text = progress.toString()
                    if (fromUser) {
                        viewModel.setOpacity(progress)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
    }

    private fun initObservers() {

        viewModel.featherRadius.observe(viewLifecycleOwner) { value ->
            val safeValue = value?.toInt() ?: 0
            if (binding.feather.progress != safeValue) {
                binding.feather.progress = safeValue
            }
            binding.featherSize.text = safeValue.toString()
        }

        viewModel.featherWidth.observe(viewLifecycleOwner) { value ->
            val safeValue = value?.toInt() ?: 50
            if (binding.softness.progress != safeValue) {
                binding.softness.progress = safeValue
            }
            binding.softnessSize.text = safeValue.toString()
        }

        viewModel.opacity.observe(viewLifecycleOwner) { value ->
            val safeValue = value ?: 255
            if (binding.opacity.progress != safeValue) {
                binding.opacity.progress = safeValue
            }
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