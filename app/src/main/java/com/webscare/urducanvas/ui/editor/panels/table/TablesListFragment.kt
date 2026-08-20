package com.webscare.urducanvas.ui.editor.panels.table

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.common.utils.HorizontalSpringEdgeEffectFactory
import com.webscare.urducanvas.common.utils.MorphGridLayoutManager
import com.webscare.urducanvas.data.repository.TablePresetRepository
import com.webscare.urducanvas.data.repository.TablePresetStyle
import com.webscare.urducanvas.databinding.FragmentTablesListBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TablesListFragment : Fragment() {

    private var _binding: FragmentTablesListBinding? = null
    private val binding get() = _binding!!

    var onFilterResult: ((category: String, count: Int) -> Unit)? = null

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()

    private var presetsAdapter: TablePresetsMainAdapter? = null

    var category: String = ""; private set
    private var filterText: String = ""
    private var isPanelExpanded: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            category = it.getString(ARG_CATEGORY).orEmpty()
            filterText = it.getString(ARG_FILTER).orEmpty()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTablesListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tablesRV.apply {
            setHasFixedSize(false)
            setItemViewCacheSize(20)
            recycledViewPool.setMaxRecycledViews(0, 25)
            layoutManager = MorphGridLayoutManager(
                context = requireContext(),
                collapsedSpan = 3,
                expandedSpan = 3
            ).apply {
                applyFraction(binding.tablesRV, if (mainViewModel.isPanelExpanded(PanelType.TABLES)) 1f else 0f)
            }
        }

        setupSwipeRefresh()
        setupPresetsAdapter()

        val isExpandedNow = mainViewModel.isPanelExpanded(PanelType.TABLES)
        isPanelExpanded = !isExpandedNow
        onPanelExpanded(isExpandedNow)

        binding.tablesRV.post {
            if (_binding != null) {
                isPanelExpanded = !isExpandedNow
                onPanelExpanded(isExpandedNow)
                presetsAdapter?.notifyDataSetChanged()
            }
        }

        loadPresets()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.isEnabled = isPanelExpanded
        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun setupPresetsAdapter() {
        presetsAdapter = TablePresetsMainAdapter { preset ->
            val act = activity ?: return@TablePresetsMainAdapter

            val hasSelectedTable = viewModel.currentTableScope.value != null &&
                    viewModel.selectedElements.value?.any { it.type == ElementType.TABLE } == true

            if (hasSelectedTable) {
                viewModel.updateSelectedTableData { data ->
                    TablePresetRepository.applyPresetToTable(preset, data)
                }
            } else {
                viewModel.addTableElement(rows = 3, cols = 3, activity = act, presetStyle = preset)
            }

            if (mainViewModel.isPanelExpanded(PanelType.TABLES)) {
                mainViewModel.togglePanel(PanelType.TABLES)
            }
        }

        binding.tablesRV.adapter = presetsAdapter
    }

    private fun loadPresets() {
        val allForCategory = TablePresetRepository.getPresetsByCategory(category)
        val filtered = if (filterText.isBlank()) {
            allForCategory
        } else {
            allForCategory.filter {
                it.name.contains(filterText, ignoreCase = true) ||
                it.category.contains(filterText, ignoreCase = true)
            }
        }

        presetsAdapter?.submitList(filtered)
        onFilterResult?.invoke(category, filtered.size)
    }

    fun applyFilter(query: String) {
        filterText = query
        loadPresets()
    }

    fun onPanelExpanded(expanded: Boolean) {
        if (isPanelExpanded == expanded) return
        isPanelExpanded = expanded
        if (_binding == null) return

        val rv = binding.tablesRV
        if (rv.width == 0) {
            rv.post {
                if (_binding != null) {
                    isPanelExpanded = !expanded
                    onPanelExpanded(expanded)
                }
            }
            return
        }

        if (expanded) {
            binding.tablesRV.edgeEffectFactory = RecyclerView.EdgeEffectFactory()
            binding.tablesRV.translationX = 0f
        } else {
            binding.tablesRV.edgeEffectFactory = HorizontalSpringEdgeEffectFactory()
        }

        binding.swipeRefresh.isEnabled = expanded

        val lm = binding.tablesRV.layoutManager as? MorphGridLayoutManager
        if (lm != null) {
            lm.applyFraction(binding.tablesRV, if (expanded) 1f else 0f)
            if (presetsAdapter?.isExpanded != expanded) {
                binding.tablesRV.recycledViewPool.clear()
                presetsAdapter?.isExpanded = expanded
            }
        }
        binding.tablesRV.alpha = 1f
        val bottomPadding = if (expanded) (64 * resources.displayMetrics.density).toInt() else 0
        binding.tablesRV.setPadding(
            binding.tablesRV.paddingLeft,
            binding.tablesRV.paddingTop,
            binding.tablesRV.paddingRight,
            bottomPadding
        )

        val adapter = presetsAdapter ?: return
        val rvWidth = binding.tablesRV.width
        val rvPadding = binding.tablesRV.paddingLeft + binding.tablesRV.paddingRight
        val offset = if (expanded) 1f else 0f
        adapter.slideOffset = offset
        adapter.recyclerViewWidth = rvWidth
        adapter.recyclerViewPadding = rvPadding

        for (i in 0 until binding.tablesRV.childCount) {
            val child = binding.tablesRV.getChildAt(i)
            val holder = binding.tablesRV.getChildViewHolder(child) as? TablePresetsMainAdapter.PresetViewHolder
            holder?.updateSize(offset, rvWidth, rvPadding)
        }
    }

    fun onPanelSlide(offset: Float) {
        if (_binding == null) return
        val effectiveExpanded = offset >= MorphGridLayoutManager.DEFAULT_FLIP_THRESHOLD
        binding.swipeRefresh.isEnabled = effectiveExpanded
        val lm = binding.tablesRV.layoutManager as? MorphGridLayoutManager
        if (lm != null) {
            lm.applyFraction(binding.tablesRV, offset)
            if (presetsAdapter?.isExpanded != effectiveExpanded) {
                binding.tablesRV.recycledViewPool.clear()
                presetsAdapter?.isExpanded = effectiveExpanded
            }
        }

        binding.tablesRV.alpha = MorphGridLayoutManager.computeMorphAlpha(offset)

        val maxPadding = (64 * resources.displayMetrics.density).toInt()
        val currentPadding = (offset * maxPadding).toInt()
        binding.tablesRV.setPadding(
            binding.tablesRV.paddingLeft,
            binding.tablesRV.paddingTop,
            binding.tablesRV.paddingRight,
            currentPadding
        )

        val adapter = presetsAdapter ?: return
        val rvWidth = binding.tablesRV.width
        val rvPadding = binding.tablesRV.paddingLeft + binding.tablesRV.paddingRight
        adapter.slideOffset = offset
        adapter.recyclerViewWidth = rvWidth
        adapter.recyclerViewPadding = rvPadding

        for (i in 0 until binding.tablesRV.childCount) {
            val child = binding.tablesRV.getChildAt(i)
            val holder = binding.tablesRV.getChildViewHolder(child) as? TablePresetsMainAdapter.PresetViewHolder
            holder?.updateSize(offset, rvWidth, rvPadding)
        }
    }

    override fun onDestroyView() {
        _binding?.tablesRV?.adapter = null
        presetsAdapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CATEGORY = "arg_category"
        private const val ARG_FILTER   = "arg_filter"

        fun newInstance(category: String, filter: String = ""): TablesListFragment =
            TablesListFragment().apply {
                arguments = bundleOf(
                    ARG_CATEGORY to category,
                    ARG_FILTER   to filter
                )
            }
    }
}
