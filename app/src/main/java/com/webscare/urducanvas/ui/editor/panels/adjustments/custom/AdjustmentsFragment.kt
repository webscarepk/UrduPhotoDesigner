package com.webscare.urducanvas.ui.editor.panels.adjustments.custom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.databinding.FragmentAdjustmentsBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.PanelTabsAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdjustmentsFragment : Fragment() {
    private var _binding: FragmentAdjustmentsBinding? = null
    private val binding get() = _binding!!
    private lateinit var tabs: ArrayList<PanelTabs>
    private lateinit var adapter: PanelTabsAdapter
    private lateinit var pagerAdapter: AdjustmentsPagerAdapter

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
    }

    private fun setupRecyclerViews() {

        tabs = arrayListOf(
            PanelTabs(0, "Light", true),
            PanelTabs(1, "Color", false),
            PanelTabs(2, "Detail", false)
        )

        adapter = PanelTabsAdapter { tab ->
            handleSelection(tab)
        }

        binding.categories.adapter = adapter
        adapter.submitList(ArrayList(tabs))
        handleSelection(tabs.firstOrNull())

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = AdjustmentsPagerAdapter(this, tabs)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.isSaveEnabled = false
        binding.viewPager.adapter?.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

        handleSelection(tabs.first())
    }

    private fun handleSelection(selectedCategory: com.webscare.urducanvas.data.model.PanelTabs?) {
        selectedCategory?.let { tab ->
            val selectedIndex = tabs.indexOfFirst { it.tab_name == tab.tab_name }

            // Update selected item visuals
            val updatedCategories = tabs.map {
                it.copy(is_selected = it.tab_name == tab.tab_name)
            }
            adapter.submitList(updatedCategories)

            // Switch ViewPager page
            binding.viewPager.setCurrentItem(selectedIndex, true)
        }
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