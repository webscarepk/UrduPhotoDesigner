package com.webscare.urducanvas.ui.editor.panels.table

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.data.repository.TablePresetRepository
import com.webscare.urducanvas.databinding.FragmentTableStyleGridBinding

class TableStyleGridFragment : Fragment() {

    private var _binding: FragmentTableStyleGridBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CanvasViewModel by activityViewModels()

    private var categoryName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        categoryName = arguments?.getString(ARG_CATEGORY) ?: TablePresetRepository.categories.first()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTableStyleGridBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGrid()
    }

    private fun setupGrid() {
        val presets = TablePresetRepository.getPresetsByCategory(categoryName)
        val adapter = TablePresetsGridAdapter { selectedPreset ->
            viewModel.updateSelectedTableData { data ->
                TablePresetRepository.applyPresetToTable(selectedPreset, data)
            }
        }

        binding.presetsGrid.layoutManager = GridLayoutManager(requireContext(), 3, RecyclerView.HORIZONTAL, false)
        binding.presetsGrid.adapter = adapter
        adapter.submitList(presets)
    }

    override fun onDestroyView() {
        _binding?.presetsGrid?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CATEGORY = "arg_category"

        fun newInstance(category: String): TableStyleGridFragment {
            val fragment = TableStyleGridFragment()
            val args = Bundle().apply {
                putString(ARG_CATEGORY, category)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
