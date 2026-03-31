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

    // Mirrors the ViewModel state — synced from observers before any toggle runs
    private var isFillEnabled   = false
    private var isStrokeEnabled = true
    private var isCornerEnabled = true

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
        val parentTab = arguments?.getString("tabName") ?: ""
        val isEnabledForAdapter = parentTab != "Shape"

        tabs    = ArrayList()
        adapter = AdjustmentPanelTabsAdapter({ tab -> handleUserTap(tab) }, isEnabledForAdapter)

        binding.categories.adapter = adapter

        binding.viewPager.orientation    = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = ShapePanelPagerAdapter(this, tabs)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // User swiped the pager — update selection highlight only, no toggle
                val selectedCategory = tabs.getOrNull(position) ?: return
                selectTabVisually(selectedCategory)
                binding.categories.smoothScrollToPosition(position)
            }
        })
    }

    private fun initObservers() {
        lifecycleScope.launch {
            val parentTab = arguments?.getString("tabName") ?: ""
            tabs.clear()

            when (parentTab) {
                "Shape" -> tabs.add(AdjustmentPanelTabs(0, "Shape", true, is_enabled = false))
                "Style" -> {
                    tabs.add(AdjustmentPanelTabs(0, "Fill",   true,  is_enabled = false))
                    tabs.add(AdjustmentPanelTabs(1, "Stroke", false, is_enabled = false))
                    tabs.add(AdjustmentPanelTabs(2, "Corner", false, is_enabled = false))
                }
            }

            adapter.submitList(ArrayList(tabs))

            // Navigate to the first page WITHOUT toggling any state
            if (tabs.isNotEmpty()) {
                navigateTo(tabs[0])
            }
        }

        val parentTab = arguments?.getString("tabName") ?: ""

        // Sync local flags FROM ViewModel — these arrive before any user interaction
        viewModel.shapeFillEnabled.observe(viewLifecycleOwner) { enabled ->
            isFillEnabled = enabled
            updateTabsFromState(parentTab)
        }

        viewModel.shapeStrokeEnabled.observe(viewLifecycleOwner) { enabled ->
            isStrokeEnabled = enabled
            updateTabsFromState(parentTab)
        }

        viewModel.shapeCornerEnabled.observe(viewLifecycleOwner) { enabled ->
            isCornerEnabled = enabled
            updateTabsFromState(parentTab)
        }
    }

    private fun updateTabsFromState(parentTab: String) {
        if (parentTab == "Shape") {
            tabs = arrayListOf(
                AdjustmentPanelTabs(0, "Shape", tabs.getOrNull(0)?.is_selected ?: true, is_enabled = false)
            )
        } else {
            tabs = arrayListOf(
                AdjustmentPanelTabs(0, "Fill",   tabs.getOrNull(0)?.is_selected ?: true,  is_enabled = isFillEnabled),
                AdjustmentPanelTabs(1, "Stroke", tabs.getOrNull(1)?.is_selected ?: false, is_enabled = isStrokeEnabled),
                AdjustmentPanelTabs(2, "Corner", tabs.getOrNull(2)?.is_selected ?: false, is_enabled = isCornerEnabled)
            )
        }
        adapter.submitList(ArrayList(tabs))
    }

    /**
     * Called only on REAL USER TAPS on a tab chip.
     * Toggles the enabled state in the ViewModel, then updates the pager position.
     */
    private fun handleUserTap(selectedCategory: AdjustmentPanelTabs) {
        val parentTab = arguments?.getString("tabName") ?: ""

        if (parentTab != "Shape") {
            when (selectedCategory.tab_name) {
                "Fill" -> {
                    val newState = !isFillEnabled
                    // Both cannot be disabled simultaneously
                    if (!newState && !isStrokeEnabled) {
                        viewModel.toggleFillEnabled(true)
                    } else {
                        viewModel.toggleFillEnabled(newState)
                    }
                }
                "Stroke" -> {
                    val newState = !isStrokeEnabled
                    if (!newState && !isFillEnabled) {
                        viewModel.toggleStrokeEnabled(true)
                    } else {
                        viewModel.toggleStrokeEnabled(newState)
                    }
                }
                "Corner" -> {
                    viewModel.toggleCornerEnabled(!isCornerEnabled)
                }
            }
        }

        // Always switch the page regardless of the toggle outcome
        navigateTo(selectedCategory)
    }

    /**
     * Switches the ViewPager to [selectedCategory] and updates the selection
     * highlight — NO ViewModel state changes, safe to call at any time.
     */
    private fun navigateTo(selectedCategory: AdjustmentPanelTabs) {
        selectTabVisually(selectedCategory)
        val index = tabs.indexOfFirst { it.tab_name == selectedCategory.tab_name }
        if (index != -1) {
            binding.viewPager.setCurrentItem(index, true)
        }
    }

    /** Updates the is_selected flag in the tab list and notifies the adapter. */
    private fun selectTabVisually(selectedCategory: AdjustmentPanelTabs) {
        tabs = ArrayList(tabs.map { it.copy(is_selected = it.tab_name == selectedCategory.tab_name) })
        adapter.submitList(tabs)
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