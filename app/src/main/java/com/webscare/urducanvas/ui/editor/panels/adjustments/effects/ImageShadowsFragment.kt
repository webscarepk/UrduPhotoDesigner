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
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
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
                val element = viewModel.selectedElements.value?.firstOrNull() ?: return@ColorsAdapter
                viewModel.setImageShadow(
                    true,
                    color.colorCode.toColorInt(),
                    element.shadowDx,
                    element.shadowDy,
                    element.shadowRadius,
                    element.shadowOpacity,
                    pushToUndo = true,
                )
            },
            {
                val element = viewModel.selectedElements.value?.firstOrNull() ?: return@ColorsAdapter
                viewModel.setImageShadow(
                    false,
                    element.shadowColor,
                    element.shadowDx,
                    element.shadowDy,
                    element.shadowRadius,
                    element.shadowOpacity,
                    pushToUndo = true,
                )
            },
            {
                viewModel.startPicking(PickerTarget.COLOR_PICKER_SHADOW)
                childFragmentManager.beginTransaction()
                    .replace(R.id.shadowsFragment, ColorPickerFragment())
                    .addToBackStack(null)
                    .commit()
            },
            { viewModel.startPicking(PickerTarget.EYE_DROPPER_SHADOW) },
        )
        binding.colors.adapter = colorsAdapter
    }

    private fun initSeekBars() {
        // ── ANGLE (replaces Shadow X + Shadow Y) ─────────────────────────────
        // 0–360°. Converted to dx/dy inside ViewModel via setShadowAngle().
        binding.shadowX.apply {
            max = 360
            setOnSeekBarChangeListener(
                createSeekListener { progress, push ->
                    binding.shadowXSize.text = "$progress°"
                    viewModel.setShadowAngle(progress.toFloat())
                    if (push) {
                        val element = viewModel.selectedElements.value?.firstOrNull() ?: return@createSeekListener
                        viewModel.setImageShadow(
                            true,
                            element.shadowColor,
                            element.shadowDx,
                            element.shadowDy,
                            element.shadowRadius,
                            element.shadowOpacity,
                            pushToUndo = true,
                        )
                    }
                },
            )
        }

        // ── DISTANCE (replaces Shadow Y) ─────────────────────────────────────
        // 0–100px. Converted to dx/dy inside ViewModel via setShadowDistance().
        binding.shadowY.apply {
            max = 100
            setOnSeekBarChangeListener(
                createSeekListener { progress, push ->
                    binding.shadowYSize.text = "$progress"
                    viewModel.setShadowDistance(progress.toFloat())
                    if (push) {
                        val element = viewModel.selectedElements.value?.firstOrNull() ?: return@createSeekListener
                        viewModel.setImageShadow(
                            true,
                            element.shadowColor,
                            element.shadowDx,
                            element.shadowDy,
                            element.shadowRadius,
                            element.shadowOpacity,
                            pushToUndo = true,
                        )
                    }
                },
            )
        }

        // ── OPACITY ───────────────────────────────────────────────────────────
        binding.opacity.setOnSeekBarChangeListener(
            createSeekListener { progress, push ->
                val element = viewModel.selectedElements.value?.firstOrNull() ?: return@createSeekListener
                viewModel.setImageShadow(
                    true,
                    element.shadowColor,
                    element.shadowDx,
                    element.shadowDy,
                    element.shadowRadius,
                    progress,
                    pushToUndo = push,
                )
            },
        )

        // ── RADIUS ────────────────────────────────────────────────────────────
        binding.radius.setOnSeekBarChangeListener(
            createSeekListener { progress, push ->
                val element = viewModel.selectedElements.value?.firstOrNull() ?: return@createSeekListener
                viewModel.setImageShadow(
                    true,
                    element.shadowColor,
                    element.shadowDx,
                    element.shadowDy,
                    progress.toFloat(),
                    element.shadowOpacity,
                    pushToUndo = push,
                )
            },
        )
    }

    private fun createSeekListener(
        onChange: (progress: Int, pushToUndo: Boolean) -> Unit,
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

        // Angle seekbar (was shadowX)
        viewModel.shadowAngle.observe(viewLifecycleOwner) { angle ->
            val safeAngle = angle?.roundToInt() ?: 135
            binding.shadowXSize.text = "$safeAngle°"
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

    override fun onResume() {
        super.onResume()
        viewModel.syncShadowStateFromSelected()
    }

    companion object {
        fun newInstance() = ImageShadowsFragment()
    }
}
