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
import com.webscare.urducanvas.ui.editor.views.RailCategoryItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TextStylesFragment : Fragment() {

    private var _binding: FragmentTextStylesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    private lateinit var tabs: ArrayList<PanelTabs>
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

        binding.collapsibleRail.bindPanelId("text_styles")

        binding.collapsibleRail.onCategorySelectedListener = { catItem ->
            val tabId = catItem.id.toIntOrNull() ?: -1
            if (tabId == -100) {
                saveCurrentSelectedElementStyle()
            } else {
                val index = tabs.indexOfFirst { it.id == tabId }
                if (index >= 0) {
                    handleTabSelection(tabs[index])
                }
            }
        }

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = TextStylesPagerAdapter(this, tabs)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val selectedCategory = tabs.getOrNull(position) ?: return
                binding.collapsibleRail.setSelectedCategory(selectedCategory.id.toString())
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

        // 1. "Add Style" action tab
        tabs.add(PanelTabs(id = -100, tab_name = "Add Style", is_selected = false))

        // 2. "My Styles" if saved custom styles exist
        val savedCount = TextStylesRepository.getCustomUserSavedStyles(requireContext()).size
        if (savedCount > 0) {
            tabs.add(PanelTabs(id = PresetCategory.MY_STYLES.ordinal, tab_name = "My Styles", is_selected = false))
        }

        // 3. Preset Categories
        PresetCategory.values().filter { it != PresetCategory.MY_STYLES }.forEach { cat ->
            tabs.add(PanelTabs(id = cat.ordinal, tab_name = cat.displayName, is_selected = false))
        }

        val defaultIndex = if (tabs.size > 1) 1 else 0
        if (tabs.isNotEmpty() && defaultIndex < tabs.size) {
            tabs[defaultIndex] = tabs[defaultIndex].copy(is_selected = true)
        }

        pagerAdapter.updateTabs(ArrayList(tabs))

        val railItems = tabs.map { tab ->
            RailCategoryItem(
                id = tab.id.toString(),
                label = tab.tab_name,
                iconRes = null,
                isActionButton = (tab.id == -100)
            )
        }
        val defaultSelectedId = if (tabs.isNotEmpty() && defaultIndex < tabs.size) tabs[defaultIndex].id.toString() else null
        binding.collapsibleRail.setCategories(railItems, defaultSelectedId)

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

        binding.collapsibleRail.setSelectedCategory(selectedTab.id.toString())

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
            hasUnderStroke = selectedEl.hasUnderStroke,
            underStrokeColor = selectedEl.underStrokeColor,
            underStrokeWidth = selectedEl.underStrokeWidth,
            has3dExtrude = selectedEl.has3dExtrude,
            extrudeColor = selectedEl.extrudeColor,
            extrudeDepth = selectedEl.extrudeDepth,
            extrudeDx = selectedEl.extrudeDx,
            extrudeDy = selectedEl.extrudeDy,
            hasDoubleExtrude = selectedEl.hasDoubleExtrude,
            extrudeStep2Color = selectedEl.extrudeStep2Color,
            extrudeStep2Depth = selectedEl.extrudeStep2Depth,
            extrudeStep2Dx = selectedEl.extrudeStep2Dx,
            extrudeStep2Dy = selectedEl.extrudeStep2Dy,
            hasAnaglyph = selectedEl.hasAnaglyph,
            anaglyphOffset = selectedEl.anaglyphOffset,
            anaglyphColor1 = selectedEl.anaglyphColor1,
            anaglyphColor2 = selectedEl.anaglyphColor2,
            hasBevel = selectedEl.hasBevel,
            bevelHighlightColor = selectedEl.bevelHighlightColor,
            bevelShadowColor = selectedEl.bevelShadowColor,
            bevelDepth = selectedEl.bevelDepth,
            hasEmboss = selectedEl.hasEmboss,
            isDebossed = selectedEl.isDebossed,
            embossDepth = selectedEl.embossDepth,
            embossHighlightColor = selectedEl.embossHighlightColor,
            embossShadowColor = selectedEl.embossShadowColor,
            hasOuterGlow = selectedEl.hasOuterGlow,
            outerGlowColor = selectedEl.outerGlowColor,
            outerGlowRadius = selectedEl.outerGlowRadius,
            outerGlowOpacity = selectedEl.outerGlowOpacity,
            hasInnerGlow = selectedEl.hasInnerGlow,
            innerGlowColor = selectedEl.innerGlowColor,
            innerGlowRadius = selectedEl.innerGlowRadius,
            innerGlowOpacity = selectedEl.innerGlowOpacity,
            shadowColor = selectedEl.shadowColor,
            shadowRadius = selectedEl.shadowRadius,
            shadowDx = selectedEl.shadowDx,
            shadowDy = selectedEl.shadowDy,
            shadowOpacity = selectedEl.shadowOpacity,
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

        val myStylesIndex = tabs.indexOfFirst { it.id == PresetCategory.MY_STYLES.ordinal }
        if (myStylesIndex != -1) {
            handleTabSelection(tabs[myStylesIndex], updatePager = true)
        }
    }

    override fun onDestroyView() {
        _binding?.viewPager?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = TextStylesFragment()
    }
}
