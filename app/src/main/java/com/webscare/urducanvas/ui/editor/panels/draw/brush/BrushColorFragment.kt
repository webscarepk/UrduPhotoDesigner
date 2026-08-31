package com.webscare.urducanvas.ui.editor.panels.draw.brush

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.GradientPickerTarget
import com.webscare.urducanvas.common.canvas.enums.PickerTarget
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentBrushColorBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.ColorPickerFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientEditorFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientsAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BrushColorFragment : Fragment() {

    private var _binding: FragmentBrushColorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var colorsAdapter: ColorsAdapter
    private lateinit var gradientsAdapter: GradientsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrushColorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToggle()
        setupAdapters()
        observeViewModel()
    }

    private fun setupToggle() {
        binding.solid.addPressEffect {
            if (!binding.colors.isVisible) {
                togglePanels(showGradients = false)
            }
        }

        binding.gradient.addPressEffect {
            if (!binding.gradients.isVisible) {
                togglePanels(showGradients = true)
            }
        }
    }

    private fun togglePanels(showGradients: Boolean) {
        val fadeDuration = 200L
        if (showGradients) {
            binding.colors.animate().alpha(0f).setDuration(fadeDuration).withEndAction {
                binding.colors.visibility = View.GONE
                binding.gradients.alpha = 0f
                binding.gradients.visibility = View.VISIBLE
                binding.gradients.animate().alpha(1f).setDuration(fadeDuration).start()
            }.start()

            binding.gradient.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
            binding.gradient.typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.bold)
            binding.solid.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.contrast))
            binding.solid.typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.medium)
        } else {
            binding.gradients.animate().alpha(0f).setDuration(fadeDuration).withEndAction {
                binding.gradients.visibility = View.GONE
                binding.colors.alpha = 0f
                binding.colors.visibility = View.VISIBLE
                binding.colors.animate().alpha(1f).setDuration(fadeDuration).start()
            }.start()

            binding.gradient.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.contrast))
            binding.gradient.typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.medium)
            binding.solid.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
            binding.solid.typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.bold)
        }
    }

    private fun setupAdapters() {
        colorsAdapter = ColorsAdapter(
            Constants.colorList,
            onColorSelected = { color ->
                val selectedColor = color.colorCode.toColorInt()
                viewModel.setBrushColor(selectedColor)
                viewModel.setBrushGradient(null)
                viewModel.enterDrawingMode(requireActivity())
            },
            onNoneSelected = {
                viewModel.setBrushColor(android.R.color.transparent)
                viewModel.setBrushGradient(null)
            },
            onColorPickerClicked = {
                viewModel.startPicking(PickerTarget.COLOR_PICKER_DRAW_STROKE)
                viewModel.setBrushGradient(null)
                viewModel.setPagingLocked(true)
                parentFragmentManager.beginTransaction().replace(
                    R.id.drawChildContainer, ColorPickerFragment()
                ).addToBackStack(null).commit()
            },
            onEyeDropperClicked = {
                viewModel.setBrushGradient(null)
                viewModel.startPicking(PickerTarget.EYE_DROPPER_DRAW_STROKE)
            }
        )

        gradientsAdapter = GradientsAdapter(
            gradientList = emptyList(),
            onGradientSelected = { _, item ->
                viewModel.setBrushGradient(item)
                viewModel.enterDrawingMode(requireActivity())
            },
            onGradientEditSelected = { _, item ->
                viewModel.startPickingGradient(GradientPickerTarget.DRAW_STROKE)
                viewModel.setGradient(item)
                viewModel.setPagingLocked(true)
                parentFragmentManager.beginTransaction().replace(
                    R.id.drawChildContainer, GradientEditorFragment().apply {
                        arguments = Bundle().apply {
                            putBoolean("IS_EDIT", true)
                        }
                    }
                ).addToBackStack(null).commit()
            },
            onNoneSelected = {
                viewModel.setBrushGradient(null)
            },
            onGradientPickerClicked = {
                viewModel.startPickingGradient(GradientPickerTarget.DRAW_STROKE)
                viewModel.setPagingLocked(true)
                parentFragmentManager.beginTransaction().replace(
                    R.id.drawChildContainer, GradientEditorFragment().apply {
                        arguments = Bundle().apply {
                            putBoolean("IS_EDIT", false)
                        }
                    }
                ).addToBackStack(null).commit()
            }
        )

        binding.colors.apply {
            layoutManager = GridLayoutManager(requireContext(), 4, GridLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
            adapter = colorsAdapter
        }

        binding.gradients.apply {
            layoutManager = GridLayoutManager(requireContext(), 4, GridLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
            adapter = gradientsAdapter
        }
    }

    private fun observeViewModel() {
        mainViewModel.gradients.observe(viewLifecycleOwner) { gradients ->
            gradientsAdapter.updateList(gradients)
        }

        viewModel.brushColor.observe(viewLifecycleOwner) { color ->
            colorsAdapter.selectedColor = color ?: Color.BLACK
        }

        viewModel.brushGradient.observe(viewLifecycleOwner) { gradient ->
            gradientsAdapter.selectedItem = gradient
        }
    }

    override fun onDestroyView() {
        _binding?.colors?.adapter = null
        _binding?.gradients?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): BrushColorFragment = BrushColorFragment()
    }
}
