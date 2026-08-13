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
        setupPad()
        initSeekBars()
        setupRecyclerView()
        initObservers()
    }

    private fun setupPad() {
        binding.shadowPad.mode = FeatherBiasPadView.Mode.OFFSET
        binding.shadowPad.maxDistance = 24f

        binding.shadowPad.onDragStateChanged = { dragging ->
            viewModel.setPagingLocked(dragging)
        }

        binding.shadowPad.onOffsetChanged = { angle, _ ->
            viewModel.setShadowAngle(angle)
            updateReadoutText(angle)
        }
    }

    private fun updateReadoutText(angle: Float) {
        binding.directionTitle.text = binding.shadowPad.getDirectionTitle()
        binding.readoutText.text = "${angle.roundToInt()}°"
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
        binding.colors.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2, androidx.recyclerview.widget.GridLayoutManager.HORIZONTAL, false)
        binding.colors.adapter = colorsAdapter
    }

    private fun initSeekBars() {
        // ── DISTANCE ──────────────────────────────────────────────────────────
        binding.distance.apply {
            min = 0
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    binding.distanceSize.text = "$progress"
                    if (!fromUser) return
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
                    binding.opacitySize.text = "$progress"
                    if (!fromUser) return
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
                    binding.radiusSize.text = "$progress"
                    if (!fromUser) return
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
            val safeColor = color ?: Color.BLACK
            colorsAdapter.selectedColor = safeColor
            binding.shadowPad.handleColor = safeColor
        }

        viewModel.shadowAngle.observe(viewLifecycleOwner) { angle ->
            val safeAngle = angle ?: 135f
            val safeDist = viewModel.shadowDistance.value ?: 21f
            binding.shadowPad.setOffset(safeAngle, safeDist)
            updateReadoutText(safeAngle)
        }

        viewModel.shadowDistance.observe(viewLifecycleOwner) { dist ->
            val safeDist = dist?.toInt() ?: 21
            if (binding.distance.progress != safeDist) binding.distance.progress = safeDist
            binding.distanceSize.text = "$safeDist"
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
        _binding?.colors?.adapter = null
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