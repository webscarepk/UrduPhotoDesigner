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

        // ── Feather ──────────────────────────────────────────────────────────
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

        // ── Opacity ──────────────────────────────────────────────────────────
        // Uses the same viewModel.setOpacity() / viewModel.opacity path as EditorFragment,
        // so opacity is shared state — moving this slider updates the element's paintAlpha
        // exactly the same way the top toolbar opacity control does.
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

        // ── Feather ──────────────────────────────────────────────────────────
        viewModel.featherRadius.observe(viewLifecycleOwner) { value ->
            val safeValue = value?.toInt() ?: 0
            if (binding.feather.progress != safeValue) {
                binding.feather.progress = safeValue
            }
            binding.featherSize.text = safeValue.toString()
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