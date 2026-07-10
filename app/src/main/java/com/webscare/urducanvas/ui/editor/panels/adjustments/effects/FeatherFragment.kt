package com.webscare.urducanvas.ui.editor.panels.adjustments.effects

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
import com.webscare.urducanvas.common.canvas.enums.FeatherDirection
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentFeatherBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FeatherFragment : Fragment() {

    private var _binding: FragmentFeatherBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFeatherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initDirectionButtons()
        initSeekBars()
        initObservers()
    }

    // ── Direction buttons ─────────────────────────────────────────────────────

    private fun initDirectionButtons() {
        binding.all.addPressEffect { viewModel.setFeatherDirection(FeatherDirection.ALL) }
        binding.left.addPressEffect { viewModel.setFeatherDirection(FeatherDirection.LEFT) }
        binding.right.addPressEffect { viewModel.setFeatherDirection(FeatherDirection.RIGHT) }
        binding.top.addPressEffect { viewModel.setFeatherDirection(FeatherDirection.TOP) }
        binding.bottom.addPressEffect { viewModel.setFeatherDirection(FeatherDirection.BOTTOM) }
    }

    private fun applyDirectionSelection(selected: FeatherDirection) {
        val buttons = mapOf(
            FeatherDirection.ALL to binding.all,
            FeatherDirection.LEFT to binding.left,
            FeatherDirection.RIGHT to binding.right,
            FeatherDirection.TOP to binding.top,
            FeatherDirection.BOTTOM to binding.bottom,
        )
        buttons.forEach { (direction, button) ->
            val isSelected = direction == selected
            button.backgroundTintList = ContextCompat.getColorStateList(
                requireContext(),
                if (isSelected) R.color.appColor else R.color.contrast,
            )
            button.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isSelected) R.color.white else R.color.black,
                ),
            )
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
        // Direction — reflects selected element's current direction on element switch
        viewModel.featherDirection.observe(viewLifecycleOwner) { direction ->
            applyDirectionSelection(direction ?: FeatherDirection.ALL)
        }

        viewModel.featherRadius.observe(viewLifecycleOwner) { value ->
            val safeValue = value?.toInt() ?: 0
            if (binding.feather.progress != safeValue) binding.feather.progress = safeValue
            binding.featherSize.text = safeValue.toString()
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
