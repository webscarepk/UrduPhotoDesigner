package com.webscare.urducanvas.ui.editor.panels.adjustments.custom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.data.model.AdjustmentPanelTabs
import com.webscare.urducanvas.databinding.FragmentAdjustmentsBinding
import com.webscare.urducanvas.ui.editor.panels.adjustments.AdjustmentPanelTabsAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdjustmentsFragment : Fragment() {
    private var _binding: FragmentAdjustmentsBinding? = null
    private val binding get() = _binding!!
    private lateinit var tabs: ArrayList<AdjustmentPanelTabs>
    private lateinit var adapter: AdjustmentPanelTabsAdapter
    private lateinit var pagerAdapter: AdjustmentsPagerAdapter

    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdjustmentsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupObservers()
    }

    private fun setupObservers() {
        viewModel.selectedElements.observe(viewLifecycleOwner) { elements ->
            val element = elements.firstOrNull() ?: return@observe

            val updatedTabs = tabs.map { tab ->
                val isActive = when (tab.tab_name) {
                    "Light" -> element.hasLight
                    "Color" -> element.hasColor
                    "Detail" -> element.hasDetail
                    else -> false
                }
                tab.copy(is_enabled = isActive)
            }

            tabs.clear()
            tabs.addAll(updatedTabs)
            adapter.submitList(ArrayList(tabs))
        }
    }

    private fun handleTabSelection(clickedTab: AdjustmentPanelTabs) {
        val currentSelectedTab = tabs.find { it.is_selected }

        if (currentSelectedTab?.tab_name == clickedTab.tab_name) {
            viewModel.toggleFeature(clickedTab.tab_name)
        } else {
            // Requirement 3(B): Different tab -> Switch page only
            val selectedIndex = tabs.indexOfFirst { it.tab_name == clickedTab.tab_name }

            val updatedTabs = tabs.map {
                it.copy(is_selected = it.tab_name == clickedTab.tab_name)
            }
            tabs.clear()
            tabs.addAll(updatedTabs)
            adapter.submitList(ArrayList(tabs))

            binding.viewPager.setCurrentItem(selectedIndex, true)
        }
    }

    private fun setupRecyclerViews() {

        tabs = arrayListOf(
            AdjustmentPanelTabs(0, "Light", true),
            AdjustmentPanelTabs(1, "Color", false),
            AdjustmentPanelTabs(2, "Detail", false)
        )

        adapter = AdjustmentPanelTabsAdapter ({ tab ->
            handleTabSelection(tab)
        })

        binding.categories.adapter = adapter
        adapter.submitList(ArrayList(tabs))
        handleTabSelection(tabs.first())

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = AdjustmentsPagerAdapter(this, tabs)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.isSaveEnabled = false
        binding.viewPager.adapter?.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

        handleTabSelection(tabs.first())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.viewPager.adapter = null
        _binding = null
    }

    companion object {
        fun newInstance(): AdjustmentsFragment {
            return AdjustmentsFragment()
        }
    }
}