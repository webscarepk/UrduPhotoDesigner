package com.webscare.urducanvas.ui.editor.panels.adjustments.effects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.data.model.AdjustmentPanelTabs
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.databinding.FragmentEffectsBinding
import com.webscare.urducanvas.ui.editor.panels.adjustments.AdjustmentPanelTabsAdapter
import com.webscare.urducanvas.ui.editor.panels.adjustments.custom.AdjustmentsFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.PanelTabsAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.getValue

@AndroidEntryPoint
class EffectsFragment : Fragment() {
    private var _binding: FragmentEffectsBinding? = null
    private val binding get() = _binding!!
    private var tabs = ArrayList<AdjustmentPanelTabs>()
    private lateinit var adapter: AdjustmentPanelTabsAdapter
    private lateinit var pagerAdapter: EffectsPagerAdapter

    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEffectsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupData()
        setupRecyclerViews()
        initObservers()
    }

    private fun setupData() {
        tabs.clear()
        tabs.add(AdjustmentPanelTabs(0, "Shadow", is_selected = true))
        tabs.add(AdjustmentPanelTabs(1, "Overlay", is_selected = false))
        tabs.add(AdjustmentPanelTabs(2, "Blur", is_selected = false))
        tabs.add(AdjustmentPanelTabs(3, "Stroke", is_selected = false))
    }

    private fun setupRecyclerViews() {
        adapter = AdjustmentPanelTabsAdapter { tab ->
            handleTabClick(tab)
        }
        binding.categories.adapter = adapter
        adapter.submitList(tabs)

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = EffectsPagerAdapter(this, tabs)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.isUserInputEnabled = false
    }

    private fun initObservers() {
        viewModel.selectedElements.observe(viewLifecycleOwner) { elements ->
            val element = elements.firstOrNull() ?: return@observe

            if (tabs.isEmpty()) {
                setupInitialTabs(element)
                return@observe
            }

            var isAnyChange = false
            tabs.forEachIndexed { index, tab ->
                val currentState = when (tab.tab_name) {
                    "Overlay" -> element.hasOverlay
                    "Shadow" -> element.hasShadow
                    "Stroke" -> element.hasStroke
                    "Blur" -> element.hasBlur
                    else -> false
                }

                if (tab.is_enabled != currentState) {
                    tabs[index] = tab.copy(is_enabled = currentState)
                    isAnyChange = true
                }
            }

            if (isAnyChange) {
                adapter.submitList(ArrayList(tabs))
            }
        }
    }

    private fun setupInitialTabs(element: CanvasElement) {
        tabs.clear()
        tabs.add(AdjustmentPanelTabs(0, "Shadow", is_selected = true, is_enabled = element.hasShadow))
        tabs.add(AdjustmentPanelTabs(1, "Overlay", is_selected = false, is_enabled = element.hasOverlay))
        tabs.add(AdjustmentPanelTabs(2, "Blur", is_selected = false, is_enabled = element.hasBlur))
        tabs.add(AdjustmentPanelTabs(3, "Stroke", is_selected = false, is_enabled = element.hasStroke))
        adapter.submitList(ArrayList(tabs))
    }

    private fun handleTabClick(clickedTab: AdjustmentPanelTabs) {
        val currentSelectedTab = tabs.find { it.is_selected }

        if (currentSelectedTab?.tab_name == clickedTab.tab_name) {
            viewModel.toggleFeature(clickedTab.tab_name)
        } else {
            val selectedIndex = tabs.indexOfFirst { it.tab_name == clickedTab.tab_name }

            val updatedCategories = tabs.map {
                it.copy(is_selected = it.tab_name == clickedTab.tab_name)
            }

            tabs.clear()
            tabs.addAll(updatedCategories)
            adapter.submitList(ArrayList(tabs))

            binding.viewPager.setCurrentItem(selectedIndex, true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): EffectsFragment = EffectsFragment()
    }
}