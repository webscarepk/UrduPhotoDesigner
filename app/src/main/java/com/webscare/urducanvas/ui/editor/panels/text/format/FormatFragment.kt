package com.webscare.urducanvas.ui.editor.panels.text.format

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.databinding.FragmentFormatBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.PanelTabsAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FormatFragment : Fragment() {
    private var _binding: FragmentFormatBinding? = null
    private val binding get() = _binding!!

    private lateinit var tabs: ArrayList<PanelTabs>
    private lateinit var adapter: PanelTabsAdapter
    private lateinit var pagerAdapter: FormatPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFormatBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        initObservers()
    }

    private fun setupRecyclerViews() {
        tabs = ArrayList()
        adapter =
            PanelTabsAdapter { tab ->
                handleFontSelection(tab)
            }
        binding.categories.adapter = adapter

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = FormatPagerAdapter(this, tabs)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val selectedCategory = tabs[position]
                handleFontSelection(selectedCategory)
                binding.categories.smoothScrollToPosition(position)
            }
        })

    }

    private fun initObservers() {
        lifecycleScope.launch {
            tabs.add(
                PanelTabs(
                    0,
                    "Spacing",
                    true
                )
            )
            tabs.add(
                PanelTabs(
                    1,
                    "Casing",
                    false
                )
            )
            tabs.add(
                PanelTabs(
                    2,
                    "Decoration",
                    false
                )
            )
            tabs.add(
                PanelTabs(
                    3,
                    "Alignment",
                    false
                )
            )

            adapter.submitList(ArrayList(tabs))
            handleFontSelection(tabs.firstOrNull()) // Select "All" by default
        }
    }

    private fun handleFontSelection(selectedCategory: PanelTabs?) {
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
        _binding = null
    }

    companion object {
        fun newInstance(): FormatFragment {
            return FormatFragment()
        }
    }
}