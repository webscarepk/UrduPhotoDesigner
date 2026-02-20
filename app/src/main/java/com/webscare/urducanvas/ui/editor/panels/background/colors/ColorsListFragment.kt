package com.webscare.urducanvas.ui.editor.panels.background.colors

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.GradientPickerTarget
import com.webscare.urducanvas.common.canvas.enums.PickerTarget
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentFillStrokeBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.ColorPickerFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientEditorFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientsAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ColorsListFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentFillStrokeBinding? = null
    private val binding get() = _binding!!

    private lateinit var colorsAdapter: com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter
    private lateinit var gradientsAdapter: com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientsAdapter
    private val mainViewModel: com.webscare.urducanvas.viewmodels.MainViewModel by activityViewModels()
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFillStrokeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        initObservers()
    }

    private fun initObservers() {
        lifecycleScope.launch {
            mainViewModel.gradients.observe(viewLifecycleOwner) { gradients ->
                if (gradients.isNotEmpty()){
                    gradientsAdapter.updateList(gradients.reversed())
                }
            }
        }
    }

    private fun setupRecyclerView() {
        colorsAdapter =
            _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter(
                _root_ide_package_.com.webscare.urducanvas.common.utils.Constants.colorList,
                { color ->
                    viewModel.ensureBackgroundElement(
                        requireActivity()
                    )
                    viewModel.setCanvasBackgroundColor(color.colorCode.toColorInt())
                },
                {
                    viewModel.ensureBackgroundElement(
                        requireActivity()
                    )
                    viewModel.setCanvasBackgroundColor(android.R.color.transparent)
                },
                {
                    viewModel.startPicking(_root_ide_package_.com.webscare.urducanvas.common.canvas.enums.PickerTarget.COLOR_PICKER_BACKGROUND)
                    childFragmentManager
                        .beginTransaction()
                        .replace(
                            R.id.fillStroke,
                            _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.ColorPickerFragment()
                        )
                        .addToBackStack(null)
                        .commit()
                },
                {
                    viewModel.startPicking(_root_ide_package_.com.webscare.urducanvas.common.canvas.enums.PickerTarget.EYE_DROPPER_BACKGROUND)
                })
        binding.colors.apply {
            layoutManager = GridLayoutManager(requireContext(), 3, GridLayoutManager.HORIZONTAL, false)
            adapter = colorsAdapter
        }

        gradientsAdapter =
            _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientsAdapter(
                gradientList = emptyList(),
                onGradientSelected = { _, gradient ->
                    viewModel.ensureBackgroundElement(
                        requireActivity()
                    )
                    viewModel.setCanvasGradient(gradient)
                },
                onNoneSelected = {
                    viewModel.removeCanvasGradient()
                },
                onGradientEditSelected = { _, item ->
                    viewModel.setGradient(item)
                    viewModel.startPickingGradient(_root_ide_package_.com.webscare.urducanvas.common.canvas.enums.GradientPickerTarget.BACKGROUND)
                    childFragmentManager
                        .beginTransaction()
                        .replace(
                            R.id.fillStroke,
                            _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientEditorFragment()
                                .apply {
                                    arguments = Bundle().apply {
                                        putBoolean("IS_EDIT", true)
                                    }
                                })
                        .addToBackStack(null)
                        .commit()
                },
                onGradientPickerClicked = {
                    viewModel.startPickingGradient(_root_ide_package_.com.webscare.urducanvas.common.canvas.enums.GradientPickerTarget.BACKGROUND)
                    childFragmentManager
                        .beginTransaction()
                        .replace(
                            R.id.fillStroke,
                            _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientEditorFragment()
                                .apply {
                                    arguments = Bundle().apply {
                                        putBoolean("IS_EDIT", false)
                                    }
                                })
                        .addToBackStack(null)
                        .commit()
                }
            )
        binding.gradients.apply {
            layoutManager = GridLayoutManager(requireContext(), 3, GridLayoutManager.HORIZONTAL, false)
            adapter = gradientsAdapter
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
            binding.gradients.animate()
                .alpha(0f)
                .setDuration(fadeDuration)
                .withEndAction {
                    binding.gradients.visibility = View.GONE
                    // Now fade in solid after gradient is hidden
                    binding.colors.alpha = 0f
                    binding.colors.visibility = View.VISIBLE
                    binding.colors.animate()
                        .alpha(1f)
                        .setDuration(fadeDuration)
                        .start()
                }
                .start()

            binding.gradient.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.contrast))
            binding.solid.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
        } else {
            // Fade out solid and hide it
            binding.colors.animate()
                .alpha(0f)
                .setDuration(fadeDuration)
                .withEndAction {
                    binding.colors.visibility = View.GONE
                    // Now fade in gradient after solid is hidden
                    binding.gradients.alpha = 0f
                    binding.gradients.visibility = View.VISIBLE
                    binding.gradients.animate()
                        .alpha(1f)
                        .setDuration(fadeDuration)
                        .start()
                }
                .start()
            binding.gradient.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
            binding.solid.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.contrast))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPicking()
        viewModel.stopPickingGradient()
    }

    companion object {
        fun newInstance(): ColorsListFragment {
            return ColorsListFragment()
        }
    }
}