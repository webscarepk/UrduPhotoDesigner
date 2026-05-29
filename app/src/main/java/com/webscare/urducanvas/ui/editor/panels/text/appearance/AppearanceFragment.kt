package com.webscare.urducanvas.ui.editor.panels.text.appearance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.databinding.FragmentAppearanceBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.AppearancePagerAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.PanelTabsAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AppearanceFragment : Fragment() {
    private var _binding: FragmentAppearanceBinding? = null
    private val binding get() = _binding!!

    private lateinit var tabs: ArrayList<PanelTabs>
    private lateinit var adapter: PanelTabsAdapter
    private lateinit var pagerAdapter: AppearancePagerAdapter
    private val viewModel: CanvasViewModel by activityViewModels()

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
            PanelTabsAdapter { tab ->
                handleAppearanceTabSelection(tab)
            }
        binding.categories.adapter = adapter

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter =
            AppearancePagerAdapter(
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
        // CRITICAL: use viewLifecycleOwner.lifecycleScope, NOT bare lifecycleScope.
        // The bare fragment lifecycleScope survives view destruction and can re-emit
        // during Activity.onStart while FragmentMaxLifecycleEnforcer is mid-commitNow(),
        // which causes "FragmentManager is already executing transactions".
        // repeatOnLifecycle(STARTED) ensures the block only runs when the view is
        // fully started — never during a transitional FragmentManager state.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tabs.add(PanelTabs(0, "Kasheeda", true))
                tabs.add(PanelTabs(1, "Fill",     false))
                tabs.add(PanelTabs(2, "Stroke",   false))
                tabs.add(PanelTabs(3, "Shadow",   false))
                tabs.add(PanelTabs(4, "Label",    false))
                tabs.add(PanelTabs(5, "Effect",   false))

                adapter.submitList(ArrayList(tabs))
                handleAppearanceTabSelection(tabs.firstOrNull())
            }
        }

        viewModel.pagingLocked.observe(viewLifecycleOwner) { lock ->
            binding.viewPager.isUserInputEnabled = !lock
        }
    }

    private fun handleAppearanceTabSelection(selectedCategory: PanelTabs?) {
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