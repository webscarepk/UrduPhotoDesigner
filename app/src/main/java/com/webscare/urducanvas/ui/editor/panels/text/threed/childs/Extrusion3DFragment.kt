package com.webscare.urducanvas.ui.editor.panels.text.threed.childs

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.views.Text3DPadView
import com.webscare.urducanvas.databinding.Fragment3dExtrusionBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class Extrusion3DFragment : Fragment() {

    private var _binding: Fragment3dExtrusionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = Fragment3dExtrusionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPad()
        initSliders()
        initObservers()
    }

    private fun setupPad() {
        binding.directionPad.mode = Text3DPadView.Mode.SNAP_9
        binding.directionPad.onDragStateChanged = { dragging ->
            viewModel.setPagingLocked(dragging)
        }
        binding.directionPad.onDirectionChanged = { dir ->
            viewModel.updateText3D(pushToUndo = false, enableSection = CanvasViewModel.Text3DSection.EXTRUSION) { it.extrusion.direction = dir }
            updateDirectionHelp(dir)
        }
    }

    private fun initSliders() {
        binding.sliderDirectionDepth.apply {
            label = "Depth"
            unit = ""
            minValue = 0
            maxValue = 80
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false, enableSection = CanvasViewModel.Text3DSection.EXTRUSION) { it.extrusion.depth = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderBevelAmount.apply {
            label = "Bevel Amount"
            unit = ""
            minValue = 0
            maxValue = 20
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false, enableSection = CanvasViewModel.Text3DSection.EXTRUSION) { it.extrusion.bevel = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderBevelSmoothness.apply {
            label = "Smoothness"
            unit = "%"
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false, enableSection = CanvasViewModel.Text3DSection.EXTRUSION) { it.extrusion.smoothness = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }
    }

    private fun updateDirectionHelp(dir: String) {
        val prettyName = dir.replace("-", " ").replaceFirstChar { it.uppercase() }
        val help = "<b>$prettyName</b> — drag the pad, it snaps to the nine offsets. Centre casts depth straight back with none."
        binding.tvDirectionHelp.text = Html.fromHtml(help, Html.FROM_HTML_MODE_LEGACY)
    }

    private fun initObservers() {
        viewModel.text3dData.observe(viewLifecycleOwner) { data ->
            val ext = data?.extrusion
            if (ext != null) {
                binding.sliderDirectionDepth.value = ext.depth.toInt()
                binding.directionPad.direction = ext.direction
                updateDirectionHelp(ext.direction)

                binding.sliderBevelAmount.value = ext.bevel.toInt()
                binding.sliderBevelSmoothness.value = ext.smoothness.toInt()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = Extrusion3DFragment()
    }
}
