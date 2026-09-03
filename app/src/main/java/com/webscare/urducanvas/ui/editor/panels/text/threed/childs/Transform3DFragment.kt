package com.webscare.urducanvas.ui.editor.panels.text.threed.childs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.databinding.Fragment3dTransformBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class Transform3DFragment : Fragment() {

    private var _binding: Fragment3dTransformBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = Fragment3dTransformBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initSliders()
        initObservers()
    }

    private fun initSliders() {
        binding.sliderXTilt.apply {
            label = "X Tilt"
            unit = "°"
            minValue = -180
            maxValue = 180
            snapInterval = 15
            snapThreshold = 3
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.rotation.x = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderYRotate.apply {
            label = "Y Rotate"
            unit = "°"
            minValue = -180
            maxValue = 180
            snapInterval = 15
            snapThreshold = 3
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.rotation.y = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderZSpin.apply {
            label = "Z Spin"
            unit = "°"
            minValue = -180
            maxValue = 180
            snapInterval = 15
            snapThreshold = 3
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.rotation.z = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        // Perspective reads the natural way round — more means a stronger vanishing point,
        // which is a shorter camera distance, so the slider runs backwards against the
        // stored strength. At 0 the camera is far enough away to read as flat.
        binding.sliderPerspective.apply {
            label = "Perspective"
            unit = ""
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) {
                    it.perspective.strength = strengthFor(v)
                }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        // Pushes the glyphs towards or away from the camera along Z.
        binding.sliderDistance.apply {
            label = "Distance"
            unit = ""
            minValue = -100
            maxValue = 100
            snapInterval = 10
            snapThreshold = 3
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.position.z = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }
    }

    private fun initObservers() {
        viewModel.text3dData.observe(viewLifecycleOwner) { data ->

            val rot = data?.rotation
            if (rot != null) {
                binding.sliderXTilt.value = rot.x.toInt()
                binding.sliderYRotate.value = rot.y.toInt()
                binding.sliderZSpin.value = rot.z.toInt()
            }

            data?.perspective?.let { binding.sliderPerspective.value = sliderFor(it.strength) }
            data?.position?.let { binding.sliderDistance.value = it.z.toInt() }
        }
    }

    /** Slider 0-100 to the camera distance the renderer wants: 0 is far, 100 is close. */
    private fun strengthFor(sliderValue: Int): Float =
        (MAX_STRENGTH - sliderValue * ((MAX_STRENGTH - MIN_STRENGTH) / 100f))
            .coerceIn(MIN_STRENGTH, MAX_STRENGTH)

    private fun sliderFor(strength: Float): Int =
        ((MAX_STRENGTH - strength.coerceIn(MIN_STRENGTH, MAX_STRENGTH)) /
                ((MAX_STRENGTH - MIN_STRENGTH) / 100f)).toInt().coerceIn(0, 100)

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = Transform3DFragment()

        /** Camera distances the renderer clamps to; 1200 reads as good as orthographic. */
        private const val MIN_STRENGTH = 200f
        private const val MAX_STRENGTH = 1200f
    }
}
