package com.webscare.urducanvas.ui.editor.panels.text.appearance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.data.model.PanelTabs
import com.example.urduphotodesigner.databinding.FragmentAppearanceBinding
import com.example.urduphotodesigner.ui.editor.panels.text.appearance.adapters.AppearancePagerAdapter
import com.example.urduphotodesigner.ui.editor.panels.text.appearance.adapters.PanelTabsAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AppearanceFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentAppearanceBinding? = null
    private val binding get() = _binding!!

    private lateinit var tabs: ArrayList<com.webscare.urducanvas.data.model.PanelTabs>
    private lateinit var adapter: com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.PanelTabsAdapter
    private lateinit var pagerAdapter: com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.AppearancePagerAdapter
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppearanceBinding.inflate(layoutInflater, container, false)
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
                handleAppearanceTabSelection(tab)
            }
        binding.categories.adapter = adapter

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter =
            _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.AppearancePagerAdapter(
                this,
                tabs
            )
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val selectedCategory = tabs[position]
                handleAppearanceTabSelection(selectedCategory)
                binding.categories.smoothScrollToPosition(position)

                if (position >= 2) {
                    binding.categories.smoothScrollToPosition(5)
                }else{
                    binding.categories.smoothScrollToPosition(0)
                }
            }
        })
    }

    private fun initObservers() {
        lifecycleScope.launch {
            tabs.add(
                _root_ide_package_.com.webscare.urducanvas.data.model.PanelTabs(
                    0,
                    "Kasheeda",
                    true
                )
            )
            tabs.add(
                _root_ide_package_.com.webscare.urducanvas.data.model.PanelTabs(
                    1,
                    "Fill",
                    false
                )
            )
            tabs.add(
                _root_ide_package_.com.webscare.urducanvas.data.model.PanelTabs(
                    2,
                    "Stroke",
                    false
                )
            )
            tabs.add(
                _root_ide_package_.com.webscare.urducanvas.data.model.PanelTabs(
                    3,
                    "Shadow",
                    false
                )
            )
            tabs.add(
                _root_ide_package_.com.webscare.urducanvas.data.model.PanelTabs(
                    4,
                    "Label",
                    false
                )
            )
            tabs.add(
                _root_ide_package_.com.webscare.urducanvas.data.model.PanelTabs(
                    5,
                    "Effect",
                    false
                )
            )

            adapter.submitList(ArrayList(tabs))
            handleAppearanceTabSelection(tabs.firstOrNull())
        }

        viewModel.pagingLocked.observe(viewLifecycleOwner){ lock ->
            binding.viewPager.isUserInputEnabled = !lock
        }
    }

    private fun handleAppearanceTabSelection(selectedCategory: com.webscare.urducanvas.data.model.PanelTabs?) {
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
        fun newInstance(): AppearanceFragment {
            return AppearanceFragment()
        }
    }
}