package com.example.urduphotodesigner.ui.editor.panels.draw.eraser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.urduphotodesigner.data.model.PanelTabs
import com.example.urduphotodesigner.databinding.FragmentEraserBinding
import com.example.urduphotodesigner.ui.editor.panels.draw.brush.BrushFragment
import com.example.urduphotodesigner.ui.editor.panels.draw.brush.BrushPagerAdapter
import com.example.urduphotodesigner.ui.editor.panels.text.appearance.adapters.PanelTabsAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EraserFragment : Fragment() {
    private var _binding: FragmentEraserBinding? = null
    private val binding get() = _binding!!

    private lateinit var tabs: ArrayList<PanelTabs>
    private lateinit var adapter: PanelTabsAdapter
    private lateinit var pagerAdapter: EraserPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEraserBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        initObservers()
    }

    private fun setupRecyclerViews() {
        tabs = ArrayList()
        adapter = PanelTabsAdapter { tab ->
            handleSelection(tab)
        }
        binding.categories.adapter = adapter

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = EraserPagerAdapter(this, tabs)
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
            tabs.add(PanelTabs(0, "Size", true))
            tabs.add(PanelTabs(1, "Style", false))
            tabs.add(PanelTabs(2, "Color", false))

            adapter.submitList(ArrayList(tabs))
            handleSelection(tabs.firstOrNull()) // Select "All" by default
        }
    }

    private fun handleSelection(selectedCategory: PanelTabs?) {
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

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        fun newInstance(): EraserFragment {
            return EraserFragment()
        }
    }
}
