package com.webscare.urducanvas.ui.editor.panels.text.threed.childs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.views.Text3DPadView
import com.webscare.urducanvas.databinding.Fragment3dLightingBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class Lighting3DFragment : Fragment() {

    private var _binding: Fragment3dLightingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = Fragment3dLightingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPad()
        initSliders()
        initObservers()
    }

    private fun setupPad() {
        binding.lightingPad.mode = Text3DPadView.Mode.ANGLE
        binding.lightingPad.onDragStateChanged = { dragging ->
            viewModel.setPagingLocked(dragging)
        }
        binding.lightingPad.onAngleChanged = { angle ->
            viewModel.updateText3D(pushToUndo = false) { it.lighting.angle = angle }
        }
    }

    private fun initSliders() {
        binding.sliderIntensity.apply {
            label = "Intensity"
            unit = ""
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.lighting.intensity = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderAmbient.apply {
            label = "Ambient"
            unit = ""
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.lighting.ambient = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderSoftness.apply {
            label = "Softness"
            unit = ""
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.lighting.softness = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderHighlight.apply {
            label = "Highlight"
            unit = ""
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.lighting.highlight = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }
    }

    private fun initObservers() {
        viewModel.text3dData.observe(viewLifecycleOwner) { data ->
            val light = data?.lighting
            if (light != null) {
                binding.lightingPad.angle = light.angle
                binding.sliderIntensity.value = light.intensity.toInt()
                binding.sliderAmbient.value = light.ambient.toInt()
                binding.sliderSoftness.value = light.softness.toInt()
                binding.sliderHighlight.value = light.highlight.toInt()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = Lighting3DFragment()
    }
}
