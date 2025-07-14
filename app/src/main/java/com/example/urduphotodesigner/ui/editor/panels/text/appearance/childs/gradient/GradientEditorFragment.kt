package com.example.urduphotodesigner.ui.editor.panels.text.appearance.childs.gradient

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.GradientType
import com.example.urduphotodesigner.databinding.FragmentGradientEditorBinding
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GradientEditorFragment : Fragment() {
    private var _binding: FragmentGradientEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

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

        binding.delete.setOnClickListener {
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

        binding.linear.setOnClickListener {
            viewModel.setType(GradientType.LINEAR)
            updateButtonTints(binding.linear)
        }
        binding.radial.setOnClickListener {
            viewModel.setType(GradientType.RADIAL)
            updateButtonTints(binding.radial)
        }

        binding.sweep.setOnClickListener {
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

        binding.swap.setOnClickListener { viewModel.swapGradientStops() }

        binding.settings.setOnClickListener {
            childFragmentManager
                .beginTransaction()
                .replace(R.id.gradientEditor, GradientSettingFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.done.setOnClickListener {
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