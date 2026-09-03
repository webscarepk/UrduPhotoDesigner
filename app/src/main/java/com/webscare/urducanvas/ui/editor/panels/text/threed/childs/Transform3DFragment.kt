package com.webscare.urducanvas.ui.editor.panels.text.threed.childs

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
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

        setupSegmentedControl()
        initSliders()
        initProjectionPills()
        initResetButton()
        initObservers()
    }

    private fun setupSegmentedControl() {
        binding.segmentedControl.setItems(listOf("Rotation", "Perspective", "More"), defaultIndex = 0)
        binding.segmentedControl.onSegmentSelected = { index ->
            binding.layoutRotation.visibility = if (index == 0) View.VISIBLE else View.GONE
            binding.layoutPerspective.visibility = if (index == 1) View.VISIBLE else View.GONE
            binding.layoutMore.visibility = if (index == 2) View.VISIBLE else View.GONE
        }
    }

    private fun initSliders() {
        // ── ROTATION ──────────────────────────────────────────────────────────
        binding.sliderXTilt.apply {
            label = "X Tilt"
            unit = "°"
            minValue = -180
            maxValue = 180
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
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.rotation.z = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        // ── PERSPECTIVE ───────────────────────────────────────────────────────
        binding.sliderPerspective.apply {
            label = "Perspective"
            unit = ""
            minValue = 0
            maxValue = 1000
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.perspective.strength = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderFov.apply {
            label = "Field of View"
            unit = "°"
            minValue = 15
            maxValue = 120
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.perspective.fov = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        // ── MORE (PIVOT & Z POSITION) ──────────────────────────────────────────
        binding.sliderPivotX.apply {
            label = "Pivot X"
            unit = "%"
            minValue = -100
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.pivot.x = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderPivotY.apply {
            label = "Pivot Y"
            unit = "%"
            minValue = -100
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.pivot.y = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderPivotZ.apply {
            label = "Pivot Z"
            unit = "%"
            minValue = -100
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.pivot.z = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderPosZ.apply {
            label = "Z Distance"
            unit = ""
            minValue = -200
            maxValue = 200
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.position.z = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }
    }

    private fun initProjectionPills() {
        binding.btnProjectionPerspective.addPressEffect {
            viewModel.updateText3D(pushToUndo = true) { it.perspective.type = "perspective" }
            updateProjectionButtons("perspective")
        }

        binding.btnProjectionOrthographic.addPressEffect {
            viewModel.updateText3D(pushToUndo = true) { it.perspective.type = "orthographic" }
            updateProjectionButtons("orthographic")
        }
    }

    private fun updateProjectionButtons(type: String) {
        val isPersp = type == "perspective"
        binding.btnProjectionPerspective.background = ContextCompat.getDrawable(
            requireContext(),
            if (isPersp) R.drawable.bg_3d_preset_selected else R.drawable.bg_3d_preset_unselected
        )
        binding.btnProjectionPerspective.setTextColor(
            if (isPersp) "#005D28".toColorInt() else "#5F6368".toColorInt()
        )
        binding.btnProjectionPerspective.typeface = if (isPersp) {
            ResourcesCompat.getFont(requireContext(), R.font.bold) ?: Typeface.DEFAULT_BOLD
        } else {
            ResourcesCompat.getFont(requireContext(), R.font.regular) ?: Typeface.DEFAULT
        }

        binding.btnProjectionOrthographic.background = ContextCompat.getDrawable(
            requireContext(),
            if (!isPersp) R.drawable.bg_3d_preset_selected else R.drawable.bg_3d_preset_unselected
        )
        binding.btnProjectionOrthographic.setTextColor(
            if (!isPersp) "#005D28".toColorInt() else "#5F6368".toColorInt()
        )
        binding.btnProjectionOrthographic.typeface = if (!isPersp) {
            ResourcesCompat.getFont(requireContext(), R.font.bold) ?: Typeface.DEFAULT_BOLD
        } else {
            ResourcesCompat.getFont(requireContext(), R.font.regular) ?: Typeface.DEFAULT
        }
    }

    private fun initResetButton() {
        binding.btnReset.addPressEffect {
            when (binding.segmentedControl.selectedIndex) {
                0 -> viewModel.reset3DSection("rotation")
                1 -> viewModel.reset3DSection("perspective")
                2 -> viewModel.reset3DSection("more")
            }
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

            val persp = data?.perspective
            if (persp != null) {
                binding.sliderPerspective.value = persp.strength.toInt()
                binding.sliderFov.value = persp.fov.toInt()
                updateProjectionButtons(persp.type)
            }

            val pivot = data?.pivot
            if (pivot != null) {
                binding.sliderPivotX.value = pivot.x.toInt()
                binding.sliderPivotY.value = pivot.y.toInt()
                binding.sliderPivotZ.value = pivot.z.toInt()
            }

            val pos = data?.position
            if (pos != null) {
                binding.sliderPosZ.value = pos.z.toInt()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = Transform3DFragment()
    }
}
