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

        // ── Feather (radius — how far inward the fade extends) ───────────────
        binding.feather.apply {
            max = 100
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    binding.featherSize.text = progress.toString()
                    if (fromUser) {
                        viewModel.setFeather(progress.toFloat())
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {
                    viewModel.enableFeature("Feather")
                }

                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        // ── Softness (featherWidth — how gradual the fade transition is) ─────
        // 0 = linear ramp (hard edge visible), 100 = very smooth cubic ease-in
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

        // ── Feather radius ────────────────────────────────────────────────────
        viewModel.featherRadius.observe(viewLifecycleOwner) { value ->
            val safeValue = value?.toInt() ?: 0
            if (binding.feather.progress != safeValue) {
                binding.feather.progress = safeValue
            }
            binding.featherSize.text = safeValue.toString()
        }

        // ── Feather softness ─────────────────────────────────────────────────
        viewModel.featherWidth.observe(viewLifecycleOwner) { value ->
            val safeValue = value?.toInt() ?: 50
            if (binding.softness.progress != safeValue) {
                binding.softness.progress = safeValue
            }
            binding.softnessSize.text = safeValue.toString()
        }

        // ── Opacity ──────────────────────────────────────────────────────────
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