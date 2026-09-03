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
import com.webscare.urducanvas.common.views.FeatherBiasPadView
import com.webscare.urducanvas.databinding.FragmentImagesShadowBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.ColorPickerFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ImageShadowsFragment : Fragment() {
    private var _binding: FragmentImagesShadowBinding? = null
    private val binding get() = _binding!!

    private lateinit var colorsAdapter: ColorsAdapter
    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImagesShadowBinding.inflate(layoutInflater, container, false)
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
            viewModel.enableFeature("Shadow")
            viewModel.setShadowAngle(angle)
        }
    }

    private fun setupRecyclerView() {
        colorsAdapter = ColorsAdapter(Constants.shadowColorList, { color ->
            val dx = viewModel.shadowDx.value ?: 1f
            val dy = viewModel.shadowDy.value ?: 1f
            val radius = viewModel.shadowRadius.value ?: 8f
            val opacity = viewModel.shadowOpacity.value ?: 64
            viewModel.setImageShadow(
                true, color.colorCode.toColorInt(), dx, dy, radius, opacity, pushToUndo = true
            )
        }, {
            val shadowColor = viewModel.shadowColor.value ?: Color.GRAY
            val dx = viewModel.shadowDx.value ?: 1f
            val dy = viewModel.shadowDy.value ?: 1f
            val radius = viewModel.shadowRadius.value ?: 8f
            val opacity = viewModel.shadowOpacity.value ?: 64
            viewModel.setImageShadow(
                false, shadowColor, dx, dy, radius, opacity, pushToUndo = true
            )
        }, {
            viewModel.startPicking(PickerTarget.COLOR_PICKER_SHADOW)
            childFragmentManager.beginTransaction()
                .replace(R.id.imagesShadowsFragment, ColorPickerFragment()).addToBackStack(null)
                .commit()
        }, { viewModel.startPicking(PickerTarget.EYE_DROPPER_SHADOW) })
        binding.colors.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
        )
        binding.colors.adapter = colorsAdapter
    }

    private fun initSeekBars() {
        // ── DISTANCE ──────────────────────────────────────────────────────────
        binding.distance.setOnSeekBarChangeListener(createSeekListener { progress, push ->
            viewModel.setShadowDistance(progress.toFloat())
            val color = viewModel.shadowColor.value ?: Color.GRAY
            val dx = viewModel.shadowDx.value ?: 1f
            val dy = viewModel.shadowDy.value ?: 1f
            val radius = viewModel.shadowRadius.value ?: 8f
            val opacity = viewModel.shadowOpacity.value ?: 64
            viewModel.setImageShadow(
                true, color, dx, dy, radius, opacity, pushToUndo = push
            )
        })

        // ── SHADOW SCALE ───────────────────────────────────────────────────────
        binding.shadowScale.apply {
            min = 10
            max = 200
            setOnSeekBarChangeListener(createSeekListener { progress, push ->
                binding.scaleSize.text = "$progress%"
                viewModel.setShadowScale(progress.toFloat())
            })
        }

        // ── OPACITY ───────────────────────────────────────────────────────────
        binding.opacity.setOnSeekBarChangeListener(createSeekListener { progress, push ->
            val color = viewModel.shadowColor.value ?: Color.GRAY
            val dx = viewModel.shadowDx.value ?: 1f
            val dy = viewModel.shadowDy.value ?: 1f
            val radius = viewModel.shadowRadius.value ?: 8f
            viewModel.setImageShadow(
                true, color, dx, dy, radius, progress, pushToUndo = push
            )
        })

        // ── RADIUS ────────────────────────────────────────────────────────────
        binding.radius.setOnSeekBarChangeListener(createSeekListener { progress, push ->
            val color = viewModel.shadowColor.value ?: Color.GRAY
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
            val safeColor = color ?: Color.BLACK
            colorsAdapter.selectedColor = safeColor
            binding.shadowPad.handleColor = safeColor
        }

        viewModel.shadowAngle.observe(viewLifecycleOwner) { angle ->
            val safeAngle = angle ?: 135f
            val safeDist = viewModel.shadowDistance.value ?: 21f
            binding.shadowPad.setOffset(safeAngle, safeDist)
        }

        viewModel.shadowDistance.observe(viewLifecycleOwner) { dist ->
            val safeDist = dist?.toInt() ?: 21
            if (binding.distance.progress != safeDist) binding.distance.progress = safeDist
            binding.distanceSize.text = "$safeDist"
        }

        viewModel.shadowScale.observe(viewLifecycleOwner) { scale ->
            val safeScale = scale?.toInt() ?: 100
            if (binding.shadowScale.progress != safeScale) binding.shadowScale.progress = safeScale
            binding.scaleSize.text = "$safeScale%"
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

    override fun onResume() {
        super.onResume()
        viewModel.syncShadowStateFromSelected()
    }

    companion object {
        fun newInstance() = ImageShadowsFragment()
    }
}