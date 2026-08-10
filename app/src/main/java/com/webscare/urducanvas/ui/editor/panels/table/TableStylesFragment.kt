package com.webscare.urducanvas.ui.editor.panels.table

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.data.repository.TablePresetRepository
import com.webscare.urducanvas.databinding.FragmentTableStylesBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.PanelTabsAdapter

class TableStylesFragment : Fragment() {

    private var _binding: FragmentTableStylesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private lateinit var categoryAdapter: PanelTabsAdapter
    private lateinit var gridAdapter: TablePresetsGridAdapter

    private var selectedCategory: String = TablePresetRepository.categories.first()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTableStylesBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCategories()
        setupPresetsGrid()
    }

    private fun setupCategories() {
        val categoryTabs = TablePresetRepository.categories.mapIndexed { index, catName ->
            PanelTabs(index, catName, is_selected = catName.equals(selectedCategory, ignoreCase = true))
        }

        categoryAdapter = PanelTabsAdapter { clickedTab ->
            selectedCategory = clickedTab.tab_name
            val updated = TablePresetRepository.categories.mapIndexed { index, catName ->
                PanelTabs(index, catName, is_selected = catName.equals(selectedCategory, ignoreCase = true))
            }
            categoryAdapter.submitList(updated)
            loadPresetsForSelectedCategory()
        }

        binding.categories.adapter = categoryAdapter
        categoryAdapter.submitList(categoryTabs)
    }

    private fun setupPresetsGrid() {
        gridAdapter = TablePresetsGridAdapter { selectedPreset ->
            viewModel.updateSelectedTableData { data ->
                TablePresetRepository.applyPresetToTable(selectedPreset, data)
            }
        }
        binding.presetsGrid.layoutManager = GridLayoutManager(requireContext(), 2, androidx.recyclerview.widget.RecyclerView.HORIZONTAL, false)
        binding.presetsGrid.adapter = gridAdapter

        loadPresetsForSelectedCategory()
    }

    private fun loadPresetsForSelectedCategory() {
        val presets = TablePresetRepository.getPresetsByCategory(selectedCategory)
        gridAdapter.submitList(presets)
    }

    override fun onDestroyView() {
        _binding?.categories?.adapter = null
        _binding?.presetsGrid?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = TableStylesFragment()
    }
}
