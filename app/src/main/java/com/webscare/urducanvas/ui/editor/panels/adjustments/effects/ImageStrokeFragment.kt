package com.webscare.urducanvas.ui.editor.panels.adjustments.effects

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
import com.webscare.urducanvas.databinding.FragmentImageStrokeBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.ColorPickerFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientEditorFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientsAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ImageStrokeFragment : Fragment() {
    private var _binding: FragmentImageStrokeBinding? = null
    private val binding get() = _binding!!

    private lateinit var colorsAdapter: ColorsAdapter
    private lateinit var gradientsAdapter: GradientsAdapter
    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageStrokeBinding.inflate(layoutInflater, container, false)
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
        colorsAdapter = ColorsAdapter(Constants.colorList, onColorSelected = { color ->
            val selectedColor = color.colorCode.toColorInt()
            val width = viewModel.borderWidth.value ?: 1f
            viewModel.clearStrokeGradients()
            viewModel.setImageBorder(true, selectedColor, width)
        }, onNoneSelected = {
            viewModel.setImageBorder(false, Color.TRANSPARENT, 0f)
        }, onColorPickerClicked = {
            viewModel.clearLabelGradients()
            viewModel.startPicking(PickerTarget.COLOR_PICKER_IMAGE_STROKE)

            viewModel.setPagingLocked(true)
            childFragmentManager.beginTransaction().replace(
                R.id.imageStroke, ColorPickerFragment()
            ).addToBackStack(null).commit()
        }, onEyeDropperClicked = {
            viewModel.clearLabelGradients()
            viewModel.startPicking(PickerTarget.EYE_DROPPER_IMAGE_STROKE)
        })

        gradientsAdapter =
            GradientsAdapter(gradientList = emptyList(), onGradientSelected = { _, item ->
                val width = viewModel.borderWidth.value ?: 1f
                viewModel.setImageStrokeGradient(item, width)
            }, onGradientEditSelected = { _, item ->
                viewModel.startPickingGradient(GradientPickerTarget.IMAGE_STROKE)
                viewModel.setGradient(item)
                viewModel.setPagingLocked(true)
                childFragmentManager.beginTransaction().replace(
                    R.id.imageStroke, GradientEditorFragment().apply {
                        arguments = Bundle().apply { putBoolean("IS_EDIT", true) }
                    }).addToBackStack(null).commit()
            }, onNoneSelected = {
                viewModel.clearStrokeGradients()
                viewModel.setImageBorder(false, Color.TRANSPARENT, 0f)
            }, onGradientPickerClicked = {
                viewModel.startPickingGradient(GradientPickerTarget.IMAGE_STROKE)
                viewModel.setPagingLocked(true)
                childFragmentManager.beginTransaction().replace(
                    R.id.imageStroke, GradientEditorFragment().apply {
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
            binding.borderSize.text = "${width?.toInt() ?: 0}"
            binding.border.progress = width?.toInt() ?: 0
        }

        viewModel.borderColor.observe(viewLifecycleOwner) { color ->
            colorsAdapter.selectedColor = color ?: Color.BLACK
        }

        viewModel.fillGradient.observe(viewLifecycleOwner) { gradient ->
            gradientsAdapter.selectedItem = gradient
        }

        mainViewModel.gradients.observe(viewLifecycleOwner) { gradients ->
            gradientsAdapter.updateList(gradients)
        }
    }

    private fun setEvents() {

        binding.border.apply {
            min = 1
            max = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    viewModel.setImageBorder(
                        enabled = progress > 0,
                        color = viewModel.borderColor.value ?: Color.BLACK,
                        width = progress.toFloat()
                    )
                    binding.borderSize.text = progress.toString()
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {
                    viewModel.enableFeature("Stroke")
                }

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
            binding.gradient.typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.medium)
            binding.solid.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
            binding.solid.typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.medium)
        } else {
            binding.colors.animate().alpha(0f).setDuration(fadeDuration).withEndAction {
                binding.colors.visibility = View.GONE
                binding.gradients.alpha = 0f
                binding.gradients.visibility = View.VISIBLE
                binding.gradients.animate().alpha(1f).setDuration(fadeDuration).start()
            }.start()
            binding.gradient.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
            binding.gradient.typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.medium)
            binding.solid.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.contrast))
            binding.solid.typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.medium)
        }
    }

    private fun setupControlsVisibility() {
        // only show the relevant controls panel
        binding.borderCard.visibility = View.VISIBLE
        binding.borderSize.text = "${viewModel.borderWidth.value!!}"
        binding.border.progress = viewModel.borderWidth.value?.toInt()!!
        binding.gradients.layoutManager =
            GridLayoutManager(requireContext(), 3, GridLayoutManager.HORIZONTAL, false)
        binding.colors.layoutManager =
            GridLayoutManager(requireContext(), 3, GridLayoutManager.HORIZONTAL, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPicking()
    }

    companion object {
        fun newInstance(): ImageStrokeFragment {
            val fragment = ImageStrokeFragment()
            return fragment
        }
    }
}