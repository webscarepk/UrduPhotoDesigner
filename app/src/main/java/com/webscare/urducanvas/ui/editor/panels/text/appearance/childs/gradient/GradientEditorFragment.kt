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
        isEdit = arguments?.getBoolean("IS_EDIT", false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
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

            // Update the bar + shader
            binding.gradientBar.gradientItem = gradient
            binding.gradientBar.doOnLayout { binding.gradientBar.invalidateShader() }

            // Update the small preview thumbnail
            binding.preview.doOnLayout {
                it.background = gradient.createGradientPreviewDrawable(
                    gradient,
                    width = it.width,
                    height = it.height,
                )
            }

            // Sync type-button tints
            when (gradient.type) {
                GradientType.LINEAR -> updateButtonTints(binding.linear)
                GradientType.RADIAL -> updateButtonTints(binding.radial)
                else -> updateButtonTints(binding.sweep)
            }

            binding.gradientBar.invalidate()

            // ─────────────────────────────────────────────────────────────────
            // KEY: push every intermediate change straight to the canvas.
            // finishPickingGradient() checks _activeGradientPicker and routes
            // to the correct setter (setElementOverlayGradient, setTextFillGradient,
            // setCanvasGradient, etc.) so the canvas redraws live on every
            // drag, colour-pick, stop add/remove, type switch, or settings change.
            // With Patches 1–6 applied to CanvasViewModel this is now safe:
            //  • OVERLAY  → setElementOverlayGradient → notifyCanvasUpdated ✓
            //  • TEXT_FILL → setTextFillGradient → applyChangesToSelectedTextElements ✓
            //  • All other targets already called notifyCanvasUpdated / reassigned LiveData ✓
            // ─────────────────────────────────────────────────────────────────
            viewModel.finishPickingGradient(gradient)
        }
    }

    private fun updateButtonTints(selected: View) {
        val contrastColor = ContextCompat.getColor(requireContext(), R.color.selection)
        val defaultColor = ContextCompat.getColor(requireContext(), R.color.white)
        listOf(binding.linear, binding.radial, binding.sweep).forEach { btn ->
            btn.backgroundTintList = ColorStateList.valueOf(
                if (btn == selected) defaultColor else contrastColor,
            )
        }
        binding.previewCard.radius = if (selected == binding.linear) 10f else 100f
    }

    private fun setEvents() {
        binding.delete.visibility = if (isEdit) View.VISIBLE else View.GONE

        binding.delete.addPressEffect {
            viewModel.finishPickingGradient(null)
            viewModel.setPagingLocked(false)
            viewModel.stopPickingGradient()
            mainViewModel.deleteGradient(viewModel.gradient.value?.id ?: -1)
            parentFragment?.childFragmentManager?.popBackStack()
        }

        // Type buttons — setType() updates _gradient LiveData → observer fires
        // → finishPickingGradient() pushes the new type to canvas immediately.
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

        binding.gradientBar.apply {
            // Stop added → ViewModel updates _gradient → observer → live canvas update
            onStopAdded = { _, color, pos ->
                viewModel.addStop(pos, color)
                mainViewModel.updateGradient(viewModel.gradient.value!!)
            }
            // Stop dragged → ViewModel updates _gradient → observer → live canvas update
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
            // Stop removed → ViewModel updates _gradient → observer → live canvas update
            onStopRemoveRequested = { idx ->
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
            parentFragment?.childFragmentManager?.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
