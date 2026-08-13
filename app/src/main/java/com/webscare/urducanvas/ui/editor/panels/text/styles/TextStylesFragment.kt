package com.webscare.urducanvas.ui.editor.panels.text.styles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.data.model.PresetCategory
import com.webscare.urducanvas.data.model.TextStylePreset
import com.webscare.urducanvas.data.repository.TextStylesRepository
import com.webscare.urducanvas.databinding.FragmentTextStylesBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.PanelTabsAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TextStylesFragment : Fragment() {

    private var _binding: FragmentTextStylesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    private lateinit var tabs: ArrayList<PanelTabs>
    private lateinit var adapter: PanelTabsAdapter
    private lateinit var pagerAdapter: TextStylesPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTextStylesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        initObservers()
    }

    private fun setupViews() {
        tabs = ArrayList()

        adapter = PanelTabsAdapter { selectedTab ->
            if (selectedTab.id == -100) {
                saveCurrentSelectedElementStyle()
            } else {
                handleTabSelection(selectedTab)
            }
        }
        binding.categoriesRecyclerView.adapter = adapter

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = TextStylesPagerAdapter(this, tabs)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val selectedCategory = tabs.getOrNull(position) ?: return
                handleTabSelection(selectedCategory, updatePager = false)
                binding.categoriesRecyclerView.smoothScrollToPosition(position)
            }
        })
    }

    private fun initObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                rebuildTabs()
            }
        }

        viewModel.pagingLocked.observe(viewLifecycleOwner) { lock ->
            binding.viewPager.isUserInputEnabled = !lock
        }
    }

    private fun rebuildTabs() {
        tabs.clear()

        // 1. "+ Add Style" action tab
        tabs.add(PanelTabs(id = -100, tab_name = "+ Add Style", is_selected = false))

        // 2. "My Styles" if saved custom styles exist
        val savedCount = TextStylesRepository.getCustomUserSavedStyles(requireContext()).size
        if (savedCount > 0) {
            tabs.add(PanelTabs(id = PresetCategory.MY_STYLES.ordinal, tab_name = "My Styles", is_selected = false))
        }

        // 3. Preset Categories
        PresetCategory.values().filter { it != PresetCategory.MY_STYLES }.forEach { cat ->
            tabs.add(PanelTabs(id = cat.ordinal, tab_name = cat.displayName, is_selected = false))
        }

        // Default select first actual page (index 1 if My Styles or Badges & Sale)
        val defaultIndex = if (tabs.size > 1) 1 else 0
        if (tabs.isNotEmpty() && defaultIndex < tabs.size) {
            tabs[defaultIndex] = tabs[defaultIndex].copy(is_selected = true)
        }

        adapter.submitList(ArrayList(tabs))
        pagerAdapter.updateTabs(ArrayList(tabs))

        if (tabs.isNotEmpty() && defaultIndex < tabs.size) {
            binding.viewPager.setCurrentItem(defaultIndex, false)
        }
    }

    private fun handleTabSelection(selectedTab: PanelTabs, updatePager: Boolean = true) {
        val selectedIndex = tabs.indexOfFirst { it.id == selectedTab.id }
        if (selectedIndex == -1) return

        val updatedTabs = tabs.mapIndexed { i, tab ->
            tab.copy(is_selected = i == selectedIndex)
        }
        tabs.clear()
        tabs.addAll(updatedTabs)
        adapter.submitList(ArrayList(tabs))

        if (updatePager) {
            binding.viewPager.setCurrentItem(selectedIndex, true)
        }
    }

    private fun saveCurrentSelectedElementStyle() {
        val currentList = viewModel.canvasElements.value ?: return
        val selectedEl = currentList.firstOrNull { it.isSelected && it.type == com.webscare.urducanvas.common.canvas.enums.ElementType.TEXT }

        if (selectedEl == null) {
            Toast.makeText(requireContext(), "Select a text element to save its style", Toast.LENGTH_SHORT).show()
            return
        }

        val customPreset = TextStylePreset(
            id = "custom_${System.currentTimeMillis()}",
            name = "Custom Style",
            category = PresetCategory.MY_STYLES,
            textColor = selectedEl.paintColor,
            textGradient = selectedEl.fillGradient,
            strokeColor = selectedEl.strokeColor,
            strokeWidth = selectedEl.strokeWidth,
            shadowColor = selectedEl.shadowColor,
            shadowRadius = selectedEl.shadowRadius,
            shadowDx = selectedEl.shadowDx,
            shadowDy = selectedEl.shadowDy,
            hasLabel = selectedEl.hasLabel,
            labelShape = selectedEl.labelShape,
            labelColor = selectedEl.labelColor,
            labelGradient = selectedEl.labelGradient,
            labelSecondaryColor = selectedEl.labelSecondaryColor,
            labelStrokeColor = selectedEl.labelStrokeColor,
            labelStrokeWidth = selectedEl.labelStrokeWidth,
            hasGlossHighlight = selectedEl.hasGlossHighlight,
            hasFoldedRibbonFlaps = selectedEl.hasFoldedRibbonFlaps,
            isCustomUserSaved = true
        )

        val existingSaved = TextStylesRepository.getCustomUserSavedStyles(requireContext())
        val duplicateMatch = existingSaved.firstOrNull { TextStylesRepository.hasSameStyleProperties(it, customPreset) }

        if (duplicateMatch != null) {
            viewModel.selectedStylePresetId.value = duplicateMatch.id
            Toast.makeText(requireContext(), "Style with these properties already added!", Toast.LENGTH_SHORT).show()
        } else {
            TextStylesRepository.saveCustomUserStyle(requireContext(), customPreset)
            viewModel.selectedStylePresetId.value = customPreset.id
            Toast.makeText(requireContext(), "Style saved to My Styles!", Toast.LENGTH_SHORT).show()
        }

        rebuildTabs()

        // Switch to My Styles tab page
        val myStylesIndex = tabs.indexOfFirst { it.id == PresetCategory.MY_STYLES.ordinal }
        if (myStylesIndex != -1) {
            handleTabSelection(tabs[myStylesIndex], updatePager = true)
        }
    }

    override fun onDestroyView() {
        _binding?.categoriesRecyclerView?.adapter = null
        _binding?.viewPager?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = TextStylesFragment()
    }
}
