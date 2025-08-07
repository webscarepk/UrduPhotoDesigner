package com.example.urduphotodesigner.ui.editor.panels.text.appearance.childs.gradient

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.PickerTarget
import com.example.urduphotodesigner.common.utils.Constants
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.FragmentGradientColorListBinding
import com.example.urduphotodesigner.ui.editor.panels.text.appearance.adapters.ColorsAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GradientColorListFragment : Fragment() {
    private var _binding: FragmentGradientColorListBinding? = null
    private val binding get() = _binding!!
    private lateinit var colorsAdapter: ColorsAdapter
    private val viewModel: CanvasViewModel by activityViewModels()
    private var selectedColor: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGradientColorListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.setOnTouchListener { _, _ -> true }
        setupRecyclerView()
        initObserver()
    }

    private fun setupRecyclerView() {
        colorsAdapter = ColorsAdapter(Constants.colorList, { color ->
            selectedColor = color.colorCode.toColorInt()
            colorsAdapter.selectedColor = selectedColor
        }, {
            selectedColor = android.R.color.transparent
        }, {
            viewModel.startPicking(PickerTarget.COLOR_PICKER_GRADIENT)
            childFragmentManager
                .beginTransaction()
                .replace(R.id.gradientColorFragment, ColorPickerFragment())
                .addToBackStack(null)
                .commit()
        }, {
            viewModel.startPicking(PickerTarget.EYE_DROPPER_GRADIENT)
        })

        binding.colors.apply {
            adapter = colorsAdapter
        }

        binding.done.addPressEffect {
            viewModel.updateSelectedStopColor(selectedColor)
            parentFragment
                ?.childFragmentManager
                ?.popBackStack()
        }

        binding.delete.addPressEffect {
            viewModel.removeSelectedStop()
            parentFragment
                ?.childFragmentManager
                ?.popBackStack()
        }
    }

    private fun initObserver() {
        viewModel.gradientStopColor.observe(viewLifecycleOwner) { color ->
            selectedColor = color
            colorsAdapter.selectedColor = selectedColor
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}