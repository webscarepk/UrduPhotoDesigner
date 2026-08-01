package com.webscare.urducanvas.ui.editor.panels.text.appearance.childs

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.GradientPickerTarget
import com.webscare.urducanvas.common.canvas.enums.PickerTarget
import com.webscare.urducanvas.common.utils.ColorPickerDialog
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentFillStrokeBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.ColorPickerFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientEditorFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientsAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FillStrokeFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentFillStrokeBinding? = null
    private val binding get() = _binding!!

    private lateinit var colorsAdapter: ColorsAdapter
    private lateinit var gradientsAdapter: GradientsAdapter
    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private var currentTab: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentTab = arguments?.getString("tab_name")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFillStrokeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupControlsVisibility()
        setupRecyclerView()
        initObservers()
        setEvents()
    }

    private fun setupRecyclerView() {
        colorsAdapter =
            ColorsAdapter(
                Constants.colorList,
                onColorSelected = { color ->
                    val selectedColor = color.colorCode.toColorInt()
                    when (currentTab?.lowercase()) {
                        "stroke" -> {
                            val width = viewModel.borderWidth.value ?: 1f
                            viewModel.clearStrokeGradients()
                            viewModel.setTextBorder(true, selectedColor, width)
                        }

                        else -> {
                            viewModel.clearFillGradients()
                            viewModel.setTextColor(selectedColor)
                        }
                    }
                },
                onNoneSelected = {
                    when (currentTab?.lowercase()) {
                        "stroke" -> {
                            viewModel.setTextBorder(false, android.R.color.transparent, 0f)
                        }

                        else -> viewModel.setTextColor(android.R.color.transparent)
                    }
                },
                onColorPickerClicked = {
                    viewModel.clearLabelGradients()
                    if (currentTab?.lowercase() == "stroke") {
                        viewModel.startPicking(PickerTarget.COLOR_PICKER_TEXT_STROKE)
                    } else {
                        viewModel.startPicking(PickerTarget.COLOR_PICKER_TEXT_FILL)
                    }
                    viewModel.setPagingLocked(true)
                    childFragmentManager.beginTransaction().replace(
                            R.id.fillStroke,
                        ColorPickerFragment()
                        ).addToBackStack(null).commit()
                },
                onEyeDropperClicked = {
                    viewModel.clearLabelGradients()
                    if (currentTab?.lowercase() == "stroke") {
                        viewModel.startPicking(PickerTarget.EYE_DROPPER_TEXT_STROKE)
                    } else {
                        viewModel.startPicking(PickerTarget.EYE_DROPPER_TEXT_FILL)
                    }
                })

        gradientsAdapter =
            GradientsAdapter(
                gradientList = emptyList(),
                onGradientSelected = { _, item ->
                    when (currentTab?.lowercase()) {
                        "stroke" -> {
                            val width = viewModel.borderWidth.value ?: 1f
                            viewModel.setTextStrokeGradient(item, width)
                        }

                        else -> {
                            viewModel.setTextFillGradient(item)
                        }
                    }
                },
                onGradientEditSelected = { _, item ->
                    when (currentTab?.lowercase()) {
                        "stroke" -> {
                            viewModel.startPickingGradient(GradientPickerTarget.TEXT_STROKE)
                        }

                        else -> viewModel.startPickingGradient(GradientPickerTarget.TEXT_FILL)
                    }
                    viewModel.setGradient(item)
                    viewModel.setPagingLocked(true)
                    childFragmentManager.beginTransaction().replace(
                            R.id.fillStroke,
                            _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientEditorFragment()
                                .apply {
                                    arguments = Bundle().apply {
                                        putBoolean("IS_EDIT", true)
                                    }
                                }).addToBackStack(null).commit()
                },
                onNoneSelected = {
                    when (currentTab?.lowercase()) {
                        "stroke" -> {
                            viewModel.clearStrokeGradients()
                        }

                        else -> viewModel.clearFillGradients()
                    }
                },
                onGradientPickerClicked = {
                    when (currentTab?.lowercase()) {
                        "stroke" -> {
                            viewModel.startPickingGradient(GradientPickerTarget.TEXT_STROKE)
                        }

                        else -> viewModel.startPickingGradient(GradientPickerTarget.TEXT_FILL)
                    }
                    viewModel.setPagingLocked(true)
                    childFragmentManager.beginTransaction().replace(
                            R.id.fillStroke,
                            GradientEditorFragment()
                                .apply {
                                    arguments = Bundle().apply {
                                        putBoolean("IS_EDIT", false)
                                    }
                                }).addToBackStack(null).commit()
                })

        binding.colors.apply {
            setHasFixedSize(true)
            adapter = colorsAdapter
        }

        binding.gradients.apply {
            setHasFixedSize(true)
            adapter = gradientsAdapter
        }
    }

    private fun initObservers() {

        viewModel.borderWidth.observe(viewLifecycleOwner) { width ->
            if (currentTab?.lowercase() == "stroke") {
                binding.borderSize.text = "${width?.toInt() ?: 0}"
                binding.border.progress = width?.toInt() ?: 0
            }
        }

        viewModel.currentTextColor.observe(viewLifecycleOwner) { color ->
            if (currentTab?.lowercase() == "fill") {
                colorsAdapter.selectedColor = color ?: Color.BLACK // Default to black if null
            }
        }

        viewModel.borderColor.observe(viewLifecycleOwner) { color ->
            if (currentTab?.lowercase() == "stroke") {
                colorsAdapter.selectedColor = color ?: Color.BLACK
            }
        }

        viewModel.fillGradient.observe(viewLifecycleOwner) { gradient ->
            gradientsAdapter.selectedItem = gradient
        }

        lifecycleScope.launch {
            mainViewModel.gradients.observe(viewLifecycleOwner) { gradients ->
                gradientsAdapter.updateList(gradients)
            }
        }
    }

    private fun setEvents() {

        binding.border.apply {
            max = 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        binding.borderSize.text = "$progress"
                        val color = viewModel.borderColor.value ?: Color.BLACK
                        viewModel.setTextBorder(true, color, progress.toFloat())
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
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

    private fun setupControlsVisibility() {
        // only show the relevant controls panel
        when (currentTab?.lowercase()) {
            "stroke" -> {
                // preserve existing width
                binding.borderCard.visibility = View.VISIBLE
                binding.borderSize.text = "${viewModel.borderWidth.value!!}"
                binding.border.progress = viewModel.borderWidth.value?.toInt()!!
                binding.gradients.layoutManager =
                    GridLayoutManager(requireContext(), 3, GridLayoutManager.HORIZONTAL, false)
                binding.colors.layoutManager =
                    GridLayoutManager(requireContext(), 3, GridLayoutManager.HORIZONTAL, false)
            }

            else -> {
                binding.gradients.layoutManager =
                    GridLayoutManager(requireContext(), 4, GridLayoutManager.HORIZONTAL, false)
                binding.colors.layoutManager =
                    GridLayoutManager(requireContext(), 4, GridLayoutManager.HORIZONTAL, false)
            }
        }
    }

    override fun onDestroyView() {
        _binding?.colors?.adapter = null
        _binding?.gradients?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPicking()
    }

    companion object {
        private const val ARG_TAB_NAME = "tab_name"

        fun newInstance(tabName: String): FillStrokeFragment {
            val fragment = FillStrokeFragment()
            val args = Bundle()
            args.putString(ARG_TAB_NAME, tabName)
            fragment.arguments = args
            return fragment
        }
    }
}