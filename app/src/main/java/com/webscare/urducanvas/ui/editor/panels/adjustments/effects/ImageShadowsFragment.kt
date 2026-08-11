package com.webscare.urducanvas.ui.editor.panels.adjustments.effects

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
import com.webscare.urducanvas.databinding.FragmentImagesShadowBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.ColorPickerFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt

@AndroidEntryPoint
class ImageShadowsFragment : Fragment() {
    private var _binding: FragmentImagesShadowBinding? = null
    private val binding get() = _binding!!

    private lateinit var colorsAdapter: ColorsAdapter
    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImagesShadowBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initSeekBars()
        setupRecyclerView()
        initObservers()
    }

    private fun setupRecyclerView() {
        colorsAdapter = ColorsAdapter(
            Constants.shadowColorList,
            { color ->
                val dx = viewModel.shadowDx.value ?: 1f
                val dy = viewModel.shadowDy.value ?: 1f
                val radius = viewModel.shadowRadius.value ?: 8f
                val opacity = viewModel.shadowOpacity.value ?: 64
                viewModel.setImageShadow(
                    true, color.colorCode.toColorInt(),
                    dx, dy, radius, opacity,
                    pushToUndo = true
                )
            },
            {
                val shadowColor = viewModel.shadowColor.value ?: Color.GRAY
                val dx = viewModel.shadowDx.value ?: 1f
                val dy = viewModel.shadowDy.value ?: 1f
                val radius = viewModel.shadowRadius.value ?: 8f
                val opacity = viewModel.shadowOpacity.value ?: 64
                viewModel.setImageShadow(
                    false, shadowColor,
                    dx, dy, radius, opacity,
                    pushToUndo = true
                )
            },
            {
                viewModel.startPicking(PickerTarget.COLOR_PICKER_SHADOW)
                childFragmentManager.beginTransaction()
                    .replace(R.id.shadowsFragment, ColorPickerFragment())
                    .addToBackStack(null)
                    .commit()
            },
            { viewModel.startPicking(PickerTarget.EYE_DROPPER_SHADOW) }
        )
        binding.colors.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2, androidx.recyclerview.widget.GridLayoutManager.HORIZONTAL, false)
        binding.colors.adapter = colorsAdapter
    }

    private var isSeekingAngle = false
    private var isSeekingDistance = false

    private fun initSeekBars() {

        // ── ANGLE (replaces Shadow X + Shadow Y) ─────────────────────────────
        // 0–360°. Converted to dx/dy inside ViewModel via setShadowAngle().
        binding.shadowX.apply {
            max = 360
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    binding.shadowXSize.text = "${progress}°"
                    viewModel.setShadowAngle(progress.toFloat())
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {
                    isSeekingAngle = true
                    viewModel.enableFeature("Shadow")
                }
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    isSeekingAngle = false
                    val color = viewModel.shadowColor.value ?: android.graphics.Color.GRAY
                    val dx = viewModel.shadowDx.value ?: 1f
                    val dy = viewModel.shadowDy.value ?: 1f
                    val radius = viewModel.shadowRadius.value ?: 8f
                    val opacity = viewModel.shadowOpacity.value ?: 64
                    viewModel.setImageShadow(
                        true, color, dx, dy, radius, opacity, pushToUndo = true
                    )
                }
            })
        }

        // ── DISTANCE (replaces Shadow Y) ─────────────────────────────────────
        // 0–100px. Converted to dx/dy inside ViewModel via setShadowDistance().
        binding.shadowY.apply {
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    binding.shadowYSize.text = "$progress"
                    viewModel.setShadowDistance(progress.toFloat())
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {
                    isSeekingDistance = true
                    viewModel.enableFeature("Shadow")
                }
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    isSeekingDistance = false
                    val color = viewModel.shadowColor.value ?: android.graphics.Color.GRAY
                    val dx = viewModel.shadowDx.value ?: 1f
                    val dy = viewModel.shadowDy.value ?: 1f
                    val radius = viewModel.shadowRadius.value ?: 8f
                    val opacity = viewModel.shadowOpacity.value ?: 64
                    viewModel.setImageShadow(
                        true, color, dx, dy, radius, opacity, pushToUndo = true
                    )
                }
            })
        }

        // ── OPACITY ───────────────────────────────────────────────────────────
        binding.opacity.setOnSeekBarChangeListener(createSeekListener { progress, push ->
            val color = viewModel.shadowColor.value ?: android.graphics.Color.GRAY
            val dx = viewModel.shadowDx.value ?: 1f
            val dy = viewModel.shadowDy.value ?: 1f
            val radius = viewModel.shadowRadius.value ?: 8f
            viewModel.setImageShadow(
                true, color, dx, dy, radius, progress, pushToUndo = push
            )
        })

        // ── RADIUS ────────────────────────────────────────────────────────────
        binding.radius.setOnSeekBarChangeListener(createSeekListener { progress, push ->
            val color = viewModel.shadowColor.value ?: android.graphics.Color.GRAY
            val dx = viewModel.shadowDx.value ?: 1f
            val dy = viewModel.shadowDy.value ?: 1f
            val opacity = viewModel.shadowOpacity.value ?: 64
            viewModel.setImageShadow(
                true, color, dx, dy, progress.toFloat(), opacity, pushToUndo = push
            )
        })
    }

    private fun createSeekListener(
        onChange: (progress: Int, pushToUndo: Boolean) -> Unit
    ): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                onChange(progress, false)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                onChange(seekBar.progress, true)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {
                viewModel.enableFeature("Shadow")
            }
        }
    }

    private fun initObservers() {
        viewModel.shadowColor.observe(viewLifecycleOwner) { color ->
            colorsAdapter.selectedColor = color ?: Color.BLACK
        }

        // Angle seekbar (was shadowX) — skip update while user is actively dragging
        viewModel.shadowAngle.observe(viewLifecycleOwner) { angle ->
            if (isSeekingAngle) return@observe
            val safeAngle = angle?.roundToInt() ?: 135
            binding.shadowXSize.text = "${safeAngle}°"
            if (binding.shadowX.progress != safeAngle) binding.shadowX.progress = safeAngle
        }

        // Distance seekbar (was shadowY) — skip update while user is actively dragging
        viewModel.shadowDistance.observe(viewLifecycleOwner) { dist ->
            if (isSeekingDistance) return@observe
            val safeDist = dist?.roundToInt() ?: 21
            binding.shadowYSize.text = "$safeDist"
            if (binding.shadowY.progress != safeDist) binding.shadowY.progress = safeDist
        }

        viewModel.shadowOpacity.observe(viewLifecycleOwner) { opacity ->
            binding.opacitySize.text = "${opacity?.toInt() ?: 0}"
            binding.opacity.progress = opacity?.toInt() ?: 0
        }

        viewModel.shadowRadius.observe(viewLifecycleOwner) { radius ->
            binding.radiusSize.text = "${radius?.toInt() ?: 0}"
            binding.radius.progress = radius?.toInt() ?: 0
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPicking()
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncShadowStateFromSelected()
    }

    companion object {
        fun newInstance() = ImageShadowsFragment()
    }
}