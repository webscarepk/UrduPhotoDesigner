package com.webscare.urducanvas.ui.editor.panels.text.appearance.childs

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.GradientPickerTarget
import com.webscare.urducanvas.common.canvas.enums.LabelShape
import com.webscare.urducanvas.common.canvas.enums.PickerTarget
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentLabelsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LabelsFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentLabelsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()
    private val mainViewModel: com.webscare.urducanvas.viewmodels.MainViewModel by activityViewModels()
    private lateinit var colorsAdapter: com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter
    private lateinit var gradientsAdapter: com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientsAdapter

    private val shapesList = listOf(
        _root_ide_package_.com.webscare.urducanvas.data.model.ShapeItem(
            LabelShape.RECTANGLE_FILL, R.drawable.ic_rect_fill
        ), _root_ide_package_.com.webscare.urducanvas.data.model.ShapeItem(
            LabelShape.RECTANGLE_STROKE, R.drawable.ic_rect_stroke
        ), _root_ide_package_.com.webscare.urducanvas.data.model.ShapeItem(
            LabelShape.OVAL_FILL, R.drawable.ic_oval_fill
        ), _root_ide_package_.com.webscare.urducanvas.data.model.ShapeItem(
            LabelShape.OVAL_STROKE, R.drawable.ic_oval_stroke
        ), _root_ide_package_.com.webscare.urducanvas.data.model.ShapeItem(
            LabelShape.CIRCLE_FILL, R.drawable.ic_circle_fill
        ), _root_ide_package_.com.webscare.urducanvas.data.model.ShapeItem(
            LabelShape.CIRCLE_STROKE, R.drawable.ic_circle_stroke
        )
    )

    private val shapesAdapter =
        _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ShapesAdapter(
            shapesList
        ) { selectedShape ->
            // When a shape is selected, apply the label to the text element
            viewModel.setTextLabel(
                true, viewModel.labelColor.value!!, selectedShape
            ) // Use selected shape
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLabelsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        initObservers()
    }

    private fun setEvents() {
        colorsAdapter =
            _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter(
                Constants.colorList,
                { color ->
                    val selectedColor = color.colorCode.toColorInt()
                    viewModel.clearLabelGradients()
                    viewModel.setTextLabel(
                        true, selectedColor, viewModel.labelShape.value!!
                    )
                },
                {
                    viewModel.setTextLabel(
                        false, android.R.color.transparent, viewModel.labelShape.value!!
                    )
                },
                {
                    viewModel.clearLabelGradients()
                    viewModel.startPicking(PickerTarget.COLOR_PICKER_LABEL)
                    childFragmentManager.beginTransaction().replace(
                            R.id.labelsFragment,
                            _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.ColorPickerFragment()
                        ).addToBackStack(null).commit()
                },
                {
                    viewModel.clearLabelGradients()
                    viewModel.startPicking(PickerTarget.EYE_DROPPER_LABEL)
                })

        gradientsAdapter =
            _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientsAdapter(
                gradientList = emptyList(),
                onGradientSelected = { _, item ->
                    val labelShape = viewModel.labelShape.value ?: LabelShape.RECTANGLE_FILL
                    viewModel.setTextLabelGradient(true, labelShape, item)
                },
                onGradientEditSelected = { _, item ->
                    viewModel.setGradient(item)
                    viewModel.startPickingGradient(GradientPickerTarget.TEXT_LABEL)
                    childFragmentManager.beginTransaction().replace(
                            R.id.labelsFragment,
                            _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientEditorFragment()
                                .apply {
                                    arguments = Bundle().apply {
                                        putBoolean("IS_EDIT", true)
                                    }
                                }).addToBackStack(null).commit()
                },
                onNoneSelected = {
                    viewModel.clearLabelGradients()
                },
                onGradientPickerClicked = {
                    viewModel.startPickingGradient(GradientPickerTarget.TEXT_LABEL)
                    viewModel.setPagingLocked(true)
                    childFragmentManager.beginTransaction().replace(
                            R.id.labelsFragment,
                            _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientEditorFragment()
                                .apply {
                                    arguments = Bundle().apply {
                                        putBoolean("IS_EDIT", false)
                                    }
                                }).addToBackStack(null).commit()
                })

        binding.colors.apply {
            adapter = colorsAdapter
        }

        binding.gradients.apply {
            adapter = gradientsAdapter
        }

        binding.labels.apply {
            adapter = shapesAdapter
        }

        binding.solid.addPressEffect {
            if (!binding.colors.isVisible) {
                togglePanels()
            }
        }

        binding.gradient.addPressEffect {
            if (!binding.gradients.isVisible) {
                togglePanels()
            }
        }
    }

    private fun togglePanels() {
        val fadeDuration = 300L

        // Check if clicked panel is already visible; if so, do nothing.
        if (binding.colors.isVisible && binding.gradients.isVisible) return

        // Check which panel is visible and apply transition
        val showGradients = binding.gradients.isVisible

        // If gradient is visible, hide it and show solid; otherwise, do the opposite
        if (showGradients) {
            // Fade out gradient and hide it
            binding.gradients.animate().alpha(0f).setDuration(fadeDuration).withEndAction {
                    binding.gradients.visibility = View.GONE
                    // Now fade in solid after gradient is hidden
                    binding.colors.alpha = 0f
                    binding.colors.visibility = View.VISIBLE
                    binding.colors.animate().alpha(1f).setDuration(fadeDuration).start()
                }.start()

            binding.gradient.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.contrast))
            binding.solid.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
        } else {
            // Fade out solid and hide it
            binding.colors.animate().alpha(0f).setDuration(fadeDuration).withEndAction {
                    binding.colors.visibility = View.GONE
                    // Now fade in gradient after solid is hidden
                    binding.gradients.alpha = 0f
                    binding.gradients.visibility = View.VISIBLE
                    binding.gradients.animate().alpha(1f).setDuration(fadeDuration).start()
                }.start()
            binding.gradient.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
            binding.solid.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.contrast))
        }
    }

    private fun initObservers() {
        viewModel.labelColor.observe(viewLifecycleOwner) { color ->
            colorsAdapter.selectedColor = color ?: Color.BLACK
        }

        viewModel.labelShape.observe(viewLifecycleOwner) { shape ->
            shapesAdapter.selectedShape = shape
        }

        viewModel.labelGradient.observe(viewLifecycleOwner) { gradient ->
            gradientsAdapter.selectedItem = gradient
        }

        lifecycleScope.launch {
            mainViewModel.gradients.observe(viewLifecycleOwner) { gradients ->
                gradientsAdapter.updateList(gradients)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPicking()
    }

    companion object {
        fun newInstance(): LabelsFragment {
            val fragment = LabelsFragment()
            return fragment
        }
    }
}