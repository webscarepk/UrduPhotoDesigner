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

        setupSegmentedControl()
        setupPad()
        initSliders()
        initObservers()
    }

    private fun setupSegmentedControl() {
        binding.segmentedControl.setItems(listOf("Depth", "Direction", "Bevel"), defaultIndex = 0)
        binding.segmentedControl.onSegmentSelected = { index ->
            binding.layoutDepth.visibility = if (index == 0) View.VISIBLE else View.GONE
            binding.layoutDirection.visibility = if (index == 1) View.VISIBLE else View.GONE
            binding.layoutBevel.visibility = if (index == 2) View.VISIBLE else View.GONE
        }
    }

    private fun setupPad() {
        binding.directionPad.mode = Text3DPadView.Mode.SNAP_9
        binding.directionPad.onDragStateChanged = { dragging ->
            viewModel.setPagingLocked(dragging)
        }
        binding.directionPad.onDirectionChanged = { dir ->
            viewModel.updateText3D(pushToUndo = false) { it.extrusion.direction = dir }
            updateDirectionHelp(dir)
        }
    }

    private fun initSliders() {
        // ── DEPTH SCREEN SLIDERS ──────────────────────────────────────────────
        binding.sliderDepthOnly.apply {
            label = "Depth"
            unit = ""
            minValue = 0
            maxValue = 80
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.extrusion.depth = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderDepthScaleOnly.apply {
            label = "Depth Scale"
            unit = "%"
            minValue = 40
            maxValue = 160
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.extrusion.scale = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        // ── DIRECTION SCREEN SLIDERS ──────────────────────────────────────────
        binding.sliderDirectionDepth.apply {
            label = "Depth"
            unit = ""
            minValue = 0
            maxValue = 80
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.extrusion.depth = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderDirectionScale.apply {
            label = "Depth Scale"
            unit = "%"
            minValue = 40
            maxValue = 160
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.extrusion.scale = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        // ── BEVEL SCREEN SLIDERS ──────────────────────────────────────────────
        binding.sliderBevelAmount.apply {
            label = "Bevel Amount"
            unit = ""
            minValue = 0
            maxValue = 20
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.extrusion.bevel = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderBevelSmoothness.apply {
            label = "Smoothness"
            unit = "%"
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.extrusion.smoothness = v.toFloat() }
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
                val depthInt = ext.depth.toInt()
                val scaleInt = ext.scale.toInt()
                binding.sliderDepthOnly.value = depthInt
                binding.sliderDepthScaleOnly.value = scaleInt
                binding.sliderDirectionDepth.value = depthInt
                binding.sliderDirectionScale.value = scaleInt
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
