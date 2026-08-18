package com.webscare.urducanvas.ui.editor.panels.text.appearance.childs

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.PickerTarget
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.views.FeatherBiasPadView
import com.webscare.urducanvas.databinding.FragmentGlowBinding
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.ColorPickerFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GlowFragment : Fragment() {
    private var _binding: FragmentGlowBinding? = null
    private val binding get() = _binding!!

    private lateinit var colorsAdapter: ColorsAdapter
    private val viewModel: CanvasViewModel by activityViewModels()

    private var isInnerGlowMode: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGlowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPad()
        initSeekBars()
        setupRecyclerView()
        setupModeToggle()
        initObservers()
    }

    private fun setupModeToggle() {
        binding.tabOuterGlow.addPressEffect {
            if (isInnerGlowMode) {
                isInnerGlowMode = false
                updateModeUI()
            }
        }
        binding.tabInnerGlow.addPressEffect {
            if (!isInnerGlowMode) {
                isInnerGlowMode = true
                updateModeUI()
            }
        }
        updateModeUI()
    }

    private fun updateModeUI() {
        if (isInnerGlowMode) {
            binding.tabInnerGlow.setBackgroundResource(R.drawable.rounded_toggle_selected)
            binding.tabInnerGlow.setTextColor(Color.WHITE)
            binding.tabOuterGlow.setBackgroundColor(Color.TRANSPARENT)
            binding.tabOuterGlow.setTextColor(Color.BLACK)

            val color = viewModel.innerGlowColor.value ?: Color.WHITE
            val radius = viewModel.innerGlowRadius.value ?: 6f
            val opacity = viewModel.innerGlowOpacity.value ?: 255

            if (::colorsAdapter.isInitialized) {
                colorsAdapter.selectedColor = color
            }
            binding.glowPad.handleColor = color
            binding.radius.progress = radius.toInt()
            binding.radiusSize.text = "${radius.toInt()}"
            binding.opacity.progress = opacity
            binding.opacitySize.text = "$opacity"
        } else {
            binding.tabOuterGlow.setBackgroundResource(R.drawable.rounded_toggle_selected)
            binding.tabOuterGlow.setTextColor(Color.WHITE)
            binding.tabInnerGlow.setBackgroundColor(Color.TRANSPARENT)
            binding.tabInnerGlow.setTextColor(Color.BLACK)

            val color = viewModel.outerGlowColor.value ?: "#00E5FF".toColorInt()
            val radius = viewModel.outerGlowRadius.value ?: 12f
            val opacity = viewModel.outerGlowOpacity.value ?: 255

            if (::colorsAdapter.isInitialized) {
                colorsAdapter.selectedColor = color
            }
            binding.glowPad.handleColor = color
            binding.radius.progress = radius.toInt()
            binding.radiusSize.text = "${radius.toInt()}"
            binding.opacity.progress = opacity
            binding.opacitySize.text = "$opacity"
        }
    }

    private fun setupPad() {
        binding.glowPad.mode = FeatherBiasPadView.Mode.OFFSET
        binding.glowPad.maxDistance = 24f

        binding.glowPad.onDragStateChanged = { dragging ->
            viewModel.setPagingLocked(dragging)
        }

        binding.glowPad.onOffsetChanged = { angle, distance ->
            // Update glow radius dynamically via pad drag
            val radius = (distance * 2.5f).coerceIn(1f, 60f)
            binding.radius.progress = radius.toInt()
            binding.radiusSize.text = "${radius.toInt()}"
            applyCurrentGlow(radius = radius)
        }
    }

    private fun setupRecyclerView() {
        colorsAdapter = ColorsAdapter(
            Constants.shadowColorList,
            { color ->
                val selectedColor = color.colorCode.toColorInt()
                applyCurrentGlow(enabled = true, color = selectedColor)
            },
            {
                applyCurrentGlow(enabled = false, color = Color.TRANSPARENT)
            },
            {
                viewModel.startPicking(PickerTarget.COLOR_PICKER_GLOW)
                childFragmentManager.beginTransaction()
                    .replace(R.id.glowFragment, ColorPickerFragment())
                    .addToBackStack(null)
                    .commit()
            },
            { viewModel.startPicking(PickerTarget.EYE_DROPPER_GLOW) }
        )
        binding.colors.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
        )
        binding.colors.adapter = colorsAdapter
    }

    private fun initSeekBars() {
        // ── OPACITY ───────────────────────────────────────────────────────────
        binding.opacity.apply {
            min = 1
            max = 255
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    binding.opacitySize.text = "$progress"
                    if (!fromUser) return
                    applyCurrentGlow(opacity = progress)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        // ── RADIUS ────────────────────────────────────────────────────────────
        binding.radius.apply {
            min = 1
            max = 60
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    binding.radiusSize.text = "$progress"
                    if (!fromUser) return
                    applyCurrentGlow(radius = progress.toFloat())
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
    }

    private fun applyCurrentGlow(
        enabled: Boolean? = null,
        color: Int? = null,
        radius: Float? = null,
        opacity: Int? = null
    ) {
        if (isInnerGlowMode) {
            val isEnabled = enabled ?: (viewModel.hasInnerGlow.value ?: true)
            val c = color ?: (viewModel.innerGlowColor.value ?: Color.WHITE)
            val r = radius ?: (viewModel.innerGlowRadius.value ?: binding.radius.progress.toFloat())
            val op = opacity ?: (viewModel.innerGlowOpacity.value ?: binding.opacity.progress)
            viewModel.setTextInnerGlow(isEnabled, c, r, op)
        } else {
            val isEnabled = enabled ?: (viewModel.hasOuterGlow.value ?: true)
            val c = color ?: (viewModel.outerGlowColor.value ?: Color.parseColor("#00E5FF"))
            val r = radius ?: (viewModel.outerGlowRadius.value ?: binding.radius.progress.toFloat())
            val op = opacity ?: (viewModel.outerGlowOpacity.value ?: binding.opacity.progress)
            viewModel.setTextOuterGlow(isEnabled, c, r, op)
        }
    }

    private fun initObservers() {
        viewModel.outerGlowColor.observe(viewLifecycleOwner) { color ->
            if (!isInnerGlowMode) {
                val safeColor = color ?: Color.parseColor("#00E5FF")
                if (::colorsAdapter.isInitialized) {
                    colorsAdapter.selectedColor = safeColor
                }
                binding.glowPad.handleColor = safeColor
            }
        }

        viewModel.innerGlowColor.observe(viewLifecycleOwner) { color ->
            if (isInnerGlowMode) {
                val safeColor = color ?: Color.WHITE
                if (::colorsAdapter.isInitialized) {
                    colorsAdapter.selectedColor = safeColor
                }
                binding.glowPad.handleColor = safeColor
            }
        }

        viewModel.outerGlowRadius.observe(viewLifecycleOwner) { radius ->
            if (!isInnerGlowMode) {
                binding.radiusSize.text = "${radius?.toInt() ?: 12}"
                binding.radius.progress = radius?.toInt() ?: 12
            }
        }

        viewModel.innerGlowRadius.observe(viewLifecycleOwner) { radius ->
            if (isInnerGlowMode) {
                binding.radiusSize.text = "${radius?.toInt() ?: 6}"
                binding.radius.progress = radius?.toInt() ?: 6
            }
        }

        viewModel.outerGlowOpacity.observe(viewLifecycleOwner) { opacity ->
            if (!isInnerGlowMode) {
                binding.opacitySize.text = "${opacity ?: 255}"
                binding.opacity.progress = opacity ?: 255
            }
        }

        viewModel.innerGlowOpacity.observe(viewLifecycleOwner) { opacity ->
            if (isInnerGlowMode) {
                binding.opacitySize.text = "${opacity ?: 255}"
                binding.opacity.progress = opacity ?: 255
            }
        }
    }

    override fun onDestroyView() {
        _binding?.colors?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPicking()
    }

    companion object {
        fun newInstance() = GlowFragment()
    }
}
