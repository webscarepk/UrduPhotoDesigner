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
import com.webscare.urducanvas.databinding.FragmentShadowsBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.ColorPickerFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt

@AndroidEntryPoint
class ShadowsFragment : Fragment() {
    private var _binding: FragmentShadowsBinding? = null
    private val binding get() = _binding!!

    private lateinit var colorsAdapter: ColorsAdapter
    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShadowsBinding.inflate(layoutInflater, container, false)
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
                val selectedColor = color.colorCode.toColorInt()
                val dx = viewModel.shadowDx.value ?: 0f
                val dy = viewModel.shadowDy.value ?: 0f
                viewModel.setTextShadow(true, selectedColor, dx, dy)
            },
            {
                val dx = viewModel.shadowDx.value ?: 0f
                val dy = viewModel.shadowDy.value ?: 0f
                viewModel.setTextShadow(false, android.R.color.transparent, dx, dy)
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
        binding.colors.adapter = colorsAdapter
    }

    private fun initSeekBars() {

        // ── ANGLE (replaces Shadow X) ─────────────────────────────────────────
        // 0–360°. ViewModel converts to dx/dy internally via setShadowAngle().
        // Canvas and serialization still use dx/dy — existing templates unaffected.
        binding.shadowX.apply {
            max = 360
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    binding.shadowXSize.text = "${progress}°"
                    viewModel.setShadowAngle(progress.toFloat())
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        // ── DISTANCE (replaces Shadow Y) ──────────────────────────────────────
        // 0–100px. ViewModel converts to dx/dy internally via setShadowDistance().
        binding.shadowY.apply {
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    binding.shadowYSize.text = "$progress"
                    viewModel.setShadowDistance(progress.toFloat())
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        // ── OPACITY ───────────────────────────────────────────────────────────
        binding.opacity.apply {
            min = 1
            max = 255
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    binding.opacitySize.text = "$progress"
                    val color = viewModel.shadowColor.value ?: Color.BLACK
                    val dx = viewModel.shadowDx.value ?: 0f
                    val dy = viewModel.shadowDy.value ?: 0f
                    viewModel.setShadowOpacity(progress)
                    viewModel.setTextShadow(true, color, dx, dy)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        // ── RADIUS ────────────────────────────────────────────────────────────
        binding.radius.apply {
            min = 1
            max = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    binding.radiusSize.text = "$progress"
                    val color = viewModel.shadowColor.value ?: Color.BLACK
                    val dx = viewModel.shadowDx.value ?: 0f
                    val dy = viewModel.shadowDy.value ?: 0f
                    viewModel.setShadowRadius(progress.toFloat())
                    viewModel.setTextShadow(true, color, dx, dy)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
    }

    private fun initObservers() {
        viewModel.shadowColor.observe(viewLifecycleOwner) { color ->
            colorsAdapter.selectedColor = color ?: Color.BLACK
        }

        // Angle seekbar (was shadowX)
        viewModel.shadowAngle.observe(viewLifecycleOwner) { angle ->
            val safeAngle = angle?.roundToInt() ?: 135
            binding.shadowXSize.text = "${safeAngle}°"
            if (binding.shadowX.progress != safeAngle) binding.shadowX.progress = safeAngle
        }

        // Distance seekbar (was shadowY)
        viewModel.shadowDistance.observe(viewLifecycleOwner) { dist ->
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

    companion object {
        fun newInstance() = ShadowsFragment()
    }
}