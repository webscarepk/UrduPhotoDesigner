package com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.GradientType
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentGradientEditorBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GradientEditorFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentGradientEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()
    private val mainViewModel: com.webscare.urducanvas.viewmodels.MainViewModel by activityViewModels()

    private var isEdit: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // pull the boolean out of arguments
        isEdit = arguments?.getBoolean("IS_EDIT", false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGradientEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.setOnTouchListener { _, _ -> true }
        setEvents()
        initObservers()
    }

    private fun initObservers() {
        viewModel.gradient.observe(viewLifecycleOwner) { gradient ->
            binding.gradientBar.gradientItem = gradient
            binding.gradientBar.post {
                binding.gradientBar.invalidateShader()
            }
            viewModel.finishPickingGradient(gradient)
            binding.preview.doOnLayout {
                val w = it.width
                val h = it.height
                val drawable = gradient.createGradientPreviewDrawable(
                    gradient,
                    width = w,
                    height = h
                )
                it.background = drawable
            }
            // redraw your gradientBar as well
            when (gradient.type) {
                GradientType.LINEAR -> {
                    updateButtonTints(binding.linear)
                }

                GradientType.RADIAL -> {
                    updateButtonTints(binding.radial)
                }

                else -> {
                    updateButtonTints(binding.sweep)
                }
            }
            binding.gradientBar.invalidate()
        }
    }

    private fun updateButtonTints(selected: View) {
        // Colors
        val contrastColor = ContextCompat.getColor(requireContext(), R.color.contrast)
        val defaultColor = ContextCompat.getColor(requireContext(), R.color.white)

        // List your three buttons here
        val buttons = listOf(binding.linear, binding.radial, binding.sweep)

        buttons.forEach { btn ->
            val tint = if (btn == selected) defaultColor else contrastColor
            btn.backgroundTintList = ColorStateList.valueOf(tint)
        }
        if (selected == binding.linear) {
            binding.previewCard.radius = 10f
        } else {
            binding.previewCard.radius = 100f
        }
    }

    private fun setEvents() {

        binding.delete.visibility = if (isEdit) View.VISIBLE else View.GONE

        binding.delete.addPressEffect {
            viewModel.finishPickingGradient(null)
            viewModel.setPagingLocked(false)
            viewModel.stopPickingGradient()
            mainViewModel.deleteGradient(
                viewModel.gradient.value?.id ?: -1
            )
            parentFragment
                ?.childFragmentManager
                ?.popBackStack()
        }

        binding.linear.addPressEffect {
            viewModel.setType(GradientType.LINEAR)
            updateButtonTints(binding.linear)
        }
        binding.radial.addPressEffect {
            viewModel.setType(GradientType.RADIAL)
            updateButtonTints(binding.radial)
        }

        binding.sweep.addPressEffect {
            viewModel.setType(GradientType.SWEEP)
            updateButtonTints(binding.sweep)
        }

        // handle callbacks from the view:
        binding.gradientBar.apply {
            onStopAdded = { _, color, pos ->
                viewModel.addStop(pos, color)
                mainViewModel.updateGradient(viewModel.gradient.value!!)
            }
            onStopMoved = { idx, newPos ->
                viewModel.moveStop(idx, newPos)
                mainViewModel.updateGradient(viewModel.gradient.value!!)
            }
            onStopSelected = { idx ->
                viewModel.selectStop(idx)
                childFragmentManager
                    .beginTransaction()
                    .replace(R.id.gradientEditor, GradientColorListFragment())
                    .addToBackStack(null)
                    .commit()
            }
            onStopRemoved = { idx ->
                viewModel.removeStop(idx)
                mainViewModel.updateGradient(viewModel.gradient.value!!)
            }
        }

        binding.swap.addPressEffect { viewModel.swapGradientStops() }

        binding.settings.addPressEffect {
            childFragmentManager
                .beginTransaction()
                .replace(R.id.gradientEditor, GradientSettingFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.done.addPressEffect {
            if (isEdit) {
                mainViewModel.updateGradient(viewModel.gradient.value!!)
            } else {
                mainViewModel.insertGradient(viewModel.gradient.value!!)
            }
            viewModel.setPagingLocked(false)
            viewModel.stopPickingGradient()
            viewModel.clearGradient()
            parentFragment
                ?.childFragmentManager
                ?.popBackStack()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}