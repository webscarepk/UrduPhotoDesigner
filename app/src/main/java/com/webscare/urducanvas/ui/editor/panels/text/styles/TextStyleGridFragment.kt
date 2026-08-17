package com.webscare.urducanvas.ui.editor.panels.text.styles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.data.model.PresetCategory
import com.webscare.urducanvas.data.repository.TextStylesRepository
import com.webscare.urducanvas.databinding.FragmentTextStyleGridBinding

class TextStyleGridFragment : Fragment() {

    private var _binding: FragmentTextStyleGridBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CanvasViewModel by activityViewModels()

    private var categoryName: String = ""
    private var isAddMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        categoryName = arguments?.getString(ARG_CATEGORY) ?: ""
        isAddMode = arguments?.getBoolean(ARG_IS_ADD_MODE, false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTextStyleGridBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGrid()
    }

    private fun setupGrid() {
        val category = PresetCategory.values().firstOrNull { it.name == categoryName } ?: PresetCategory.BADGES_SALE
        val presets = TextStylesRepository.getPresetsByCategory(category, requireContext())
        val adapter = TextStylesGridAdapter(presets) { preset ->
            if (isAddMode) {
                viewModel.addTextWithStyle("Your Text", preset, requireContext())
            } else {
                viewModel.applyTextStylePreset(preset)
            }
        }
        adapter.selectedPresetId = viewModel.selectedStylePresetId.value

        viewModel.selectedStylePresetId.observe(viewLifecycleOwner) { selectedId ->
            adapter.selectedPresetId = selectedId
        }

        binding.presetsGrid.layoutManager = GridLayoutManager(requireContext(), 3, RecyclerView.HORIZONTAL, false)
        binding.presetsGrid.adapter = adapter
    }

    override fun onDestroyView() {
        _binding?.presetsGrid?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CATEGORY = "arg_category"
        private const val ARG_IS_ADD_MODE = "arg_is_add_mode"

        fun newInstance(category: PresetCategory, isAddMode: Boolean = false): TextStyleGridFragment {
            val fragment = TextStyleGridFragment()
            val args = Bundle().apply {
                putString(ARG_CATEGORY, category.name)
                putBoolean(ARG_IS_ADD_MODE, isAddMode)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
