package com.webscare.urducanvas.ui.editor.panels.shape

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.data.model.AdjustmentPanelTabs
import com.webscare.urducanvas.databinding.FragmentShapeBinding
import com.webscare.urducanvas.ui.editor.panels.adjustments.AdjustmentPanelTabsAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ShapeFragment : Fragment() {
    private var _binding: FragmentShapeBinding? = null
    private val binding get() = _binding!!
    private lateinit var tabs: ArrayList<AdjustmentPanelTabs>
    private lateinit var adapter: AdjustmentPanelTabsAdapter
    private lateinit var pagerAdapter: ShapePanelPagerAdapter
    private val viewModel: CanvasViewModel by activityViewModels()

    private var isFillEnabled = true
    private var isStrokeEnabled = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShapeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        initObservers()
    }

    private fun setupRecyclerViews() {
        tabs = ArrayList()
        adapter = AdjustmentPanelTabsAdapter { tab ->
            handleSelection(tab)
        }
        binding.categories.adapter = adapter

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = ShapePanelPagerAdapter(this, tabs)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val selectedCategory = tabs[position]
                handleSelection(selectedCategory)
                binding.categories.smoothScrollToPosition(position)
            }
        })
    }

    private fun initObservers() {
        lifecycleScope.launch {
            val parentTab = arguments?.getString("tabName") ?: ""
            tabs.clear()

            when (parentTab) {
                "Shape" -> {
                    tabs.add(AdjustmentPanelTabs(0, "Basic", true, is_enabled = false))
                    tabs.add(AdjustmentPanelTabs(1, "Geometry", false, is_enabled = false))
                }

                "Style", "Color" -> {
                    tabs.add(AdjustmentPanelTabs(0, "Fill", true, is_enabled = false))
                    tabs.add(AdjustmentPanelTabs(1, "Stroke", false, is_enabled = false))
                }
            }

            adapter.submitList(ArrayList(tabs))
            // Ensure the ViewPager and first selection match
            if (tabs.isNotEmpty()) {
                handleSelection(tabs[0])
            }
        }

        val parentTab = arguments?.getString("tabName") ?: ""

        // 1. Listen to ViewModel to keep UI in sync with Canvas state
        viewModel.shapeFillEnabled.observe(viewLifecycleOwner) { enabled ->
            isFillEnabled = enabled
            updateTabsFromState(parentTab)
        }

        viewModel.shapeStrokeEnabled.observe(viewLifecycleOwner) { enabled ->
            isStrokeEnabled = enabled
            updateTabsFromState(parentTab)
        }
    }

    private fun updateTabsFromState(parentTab: String) {
        if (parentTab == "Shape") {
            // For "Shape" tab, we don't show eye/enabled states, just selection
            tabs = arrayListOf(
                AdjustmentPanelTabs(
                    0, "Basic", tabs.getOrNull(0)?.is_selected ?: true, is_enabled = false
                ), AdjustmentPanelTabs(
                    1, "Geometry", tabs.getOrNull(1)?.is_selected ?: false, is_enabled = false
                )
            )
        } else {
            // For "Style" and "Color", we map Fill/Stroke to the actual enabled state
            tabs = arrayListOf(
                AdjustmentPanelTabs(
                    0, "Fill", tabs.getOrNull(0)?.is_selected ?: true, is_enabled = isFillEnabled
                ), AdjustmentPanelTabs(
                    1,
                    "Stroke",
                    tabs.getOrNull(1)?.is_selected ?: false,
                    is_enabled = isStrokeEnabled
                )
            )
        }
        adapter.submitList(ArrayList(tabs))
    }

    private fun handleSelection(selectedCategory: AdjustmentPanelTabs) {
        val parentTab = arguments?.getString("tabName") ?: ""

        // Toggle Logic (Only for Style/Color tabs)
        if (parentTab != "Shape") {
            if (selectedCategory.tab_name == "Fill") {
                val newState = !isFillEnabled
                // Rule: Both cannot be disabled
                if (!newState && !isStrokeEnabled) {
                    viewModel.toggleFillEnabled(true)
                } else {
                    viewModel.toggleFillEnabled(newState)
                }
            } else if (selectedCategory.tab_name == "Stroke") {
                val newState = !isStrokeEnabled
                if (!newState && !isFillEnabled) {
                    viewModel.toggleStrokeEnabled(true)
                } else {
                    viewModel.toggleStrokeEnabled(newState)
                }
            }
        }

        // Standard ViewPager switching logic
        val selectedIndex = tabs.indexOfFirst { it.tab_name == selectedCategory.tab_name }
        if (selectedIndex != -1) {
            val updatedCategories = tabs.map {
                it.copy(is_selected = it.tab_name == selectedCategory.tab_name)
            }
            tabs = ArrayList(updatedCategories)
            adapter.submitList(tabs)
            binding.viewPager.setCurrentItem(selectedIndex, true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        fun newInstance(tabName: String): ShapeFragment {
            val fragment = ShapeFragment()
            val args = Bundle()
            args.putString("tabName", tabName)
            fragment.arguments = args
            return fragment
        }
    }
}