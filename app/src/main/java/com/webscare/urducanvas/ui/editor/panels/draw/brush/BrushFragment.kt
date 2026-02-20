package com.webscare.urducanvas.ui.editor.panels.draw.brush

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.data.model.PanelTabs
import com.example.urduphotodesigner.databinding.FragmentBrushBinding
import com.example.urduphotodesigner.ui.editor.panels.text.appearance.adapters.PanelTabsAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.getValue

@AndroidEntryPoint
class BrushFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentBrushBinding? = null
    private val binding get() = _binding!!
    private lateinit var tabs: ArrayList<com.webscare.urducanvas.data.model.PanelTabs>
    private lateinit var adapter: com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.PanelTabsAdapter
    private lateinit var pagerAdapter: BrushPagerAdapter
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrushBinding.inflate(layoutInflater, container, false)
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
            _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.PanelTabsAdapter { tab ->
                handleSelection(tab)
            }
        binding.categories.adapter = adapter

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = BrushPagerAdapter(this, tabs)
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
            tabs.add(
                _root_ide_package_.com.webscare.urducanvas.data.model.PanelTabs(
                    0,
                    "Style",
                    true
                )
            )
            tabs.add(
                _root_ide_package_.com.webscare.urducanvas.data.model.PanelTabs(
                    1,
                    "Size",
                    false
                )
            )
            tabs.add(
                _root_ide_package_.com.webscare.urducanvas.data.model.PanelTabs(
                    2,
                    "Color",
                    false
                )
            )

            adapter.submitList(ArrayList(tabs))
            handleSelection(tabs.firstOrNull()) // Select "All" by default
        }

        viewModel.pagingLocked.observe(viewLifecycleOwner){ locked ->
            binding.viewPager.isUserInputEnabled = !locked
        }
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

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        viewModel.exitDrawingMode()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPicking()
    }

    override fun onStop() {
        super.onStop()
        viewModel.exitDrawingMode()
    }

    companion object {
        fun newInstance(): BrushFragment {
            return BrushFragment()
        }
    }
}