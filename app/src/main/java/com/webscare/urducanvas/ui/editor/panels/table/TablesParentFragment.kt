package com.webscare.urducanvas.ui.editor.panels.table

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.tabs.TabLayout
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.common.utils.PanelTabHelper
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.repository.TablePresetRepository
import com.webscare.urducanvas.databinding.FragmentTablesParentBinding
import com.webscare.urducanvas.ui.editor.EditorFragment
import com.webscare.urducanvas.ui.editor.panels.shape.TablesTabFragment
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TablesParentFragment : Fragment() {

    companion object {
        const val GRID_TAB_KEY = "__custom_grid__"
    }

    private var _binding: FragmentTablesParentBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()

    private val fragmentCache = LinkedHashMap<String, Fragment>()
    private val styleCategories = mutableListOf<String>()
    private var currentStylesTabIndex = 0
    private var currentQuery = ""
    private var tabListenerAttached = false
    private var isCustomGridMode = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTablesParentBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setEvents()
        attachDragHandleSwipe()

        styleCategories.clear()
        styleCategories.addAll(TablePresetRepository.categories)

        val savedCategory = mainViewModel.lastTablesTabCategory
        val initialIndex = if (savedCategory != null) {
            styleCategories.indexOf(savedCategory).takeIf { it >= 0 } ?: 0
        } else 0

        if (mainViewModel.isLastTablesGridMode) {
            buildGridMode()
        } else {
            buildStylesTabs(initialIndex)
        }

        observePanelExpanded()
    }

    private fun attachDragHandleSwipe() {
        val editor = parentFragment as? EditorFragment
            ?: (parentFragment as? androidx.navigation.fragment.NavHostFragment)
                ?.parentFragment as? EditorFragment
        if (editor != null) {
            editor.attachDragHandle(binding.dragHandle)
            binding.root.post {
                val b = _binding ?: return@post
                editor.panelSheetBehavior()?.let { sheet ->
                    sheet.attachAdditionalHandle(b.headerCollapsed)
                    sheet.attachAdditionalHandle(b.headerExpanded)
                    sheet.attachAdditionalHandle(b.gridHeaderCollapsed)
                    sheet.attachAdditionalHandle(b.gridHeaderExpanded)
                }
            }
        }
    }

    private fun setEvents() {
        binding.closeExpanded.addPressEffect {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(binding.searchBarExpanded.windowToken, 0)
            mainViewModel.collapsePanel()
        }

        binding.closeExpandedGrid.addPressEffect {
            mainViewModel.collapsePanel()
        }

        binding.searchIcon.addPressEffect {
            mainViewModel.setPanelExpandedType(PanelType.TABLES)
            binding.searchBarExpanded.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.searchBarExpanded, InputMethodManager.SHOW_IMPLICIT)
        }

        binding.btnGridMode.addPressEffect {
            buildGridMode()
        }

        binding.btnGridModeExpanded.addPressEffect {
            buildGridMode()
        }

        binding.backToStylesCollapsed.addPressEffect {
            buildStylesTabs(currentStylesTabIndex)
        }

        binding.backToStylesExpanded.addPressEffect {
            buildStylesTabs(currentStylesTabIndex)
        }

        binding.searchBarExpanded.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString()?.trim() ?: ""
                updateSearchCross(currentQuery)
                filterCurrentTab(currentQuery)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.searchBarExpanded.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP && currentQuery.isNotEmpty()) {
                val drawableEnd = binding.searchBarExpanded.compoundDrawablesRelative[2]
                if (drawableEnd != null) {
                    val clickAreaStart = binding.searchBarExpanded.width -
                            binding.searchBarExpanded.paddingEnd -
                            drawableEnd.bounds.width()
                    if (event.x >= clickAreaStart) {
                        binding.searchBarExpanded.text?.clear()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }

        binding.searchBarExpanded.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(binding.searchBarExpanded.windowToken, 0)
                true
            } else false
        }
    }

    private fun updateSearchCross(text: String) {
        if (text.isNotEmpty()) {
            val cross = ContextCompat.getDrawable(requireContext(), R.drawable.ic_cross)
            cross?.setTint(ContextCompat.getColor(requireContext(), R.color.black))
            binding.searchBarExpanded.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, cross, null)
        } else {
            binding.searchBarExpanded.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
        }
    }

    private fun filterCurrentTab(query: String) {
        if (isCustomGridMode) return
        val currentCategory = styleCategories.getOrNull(currentStylesTabIndex) ?: return
        val frag = fragmentCache[currentCategory]
        if (frag is TablesListFragment) {
            frag.applyFilter(query)
        }
    }

    private fun buildStylesTabs(selectIndex: Int) {
        isCustomGridMode = false
        tabListenerAttached = false
        currentStylesTabIndex = selectIndex.coerceIn(0, styleCategories.lastIndex)

        val isExpanded = mainViewModel.isPanelExpanded(PanelType.TABLES)
        applyExpandedUi(isExpanded)

        listOf(binding.tabLayout, binding.tabLayoutExpanded).forEach { tl ->
            tl.clearOnTabSelectedListeners()
            tl.removeAllTabs()
            styleCategories.forEach { category ->
                val tab = tl.newTab()
                val tabView = LayoutInflater.from(context).inflate(R.layout.view_panel_tab, tl, false)
                tabView.findViewById<TextView>(R.id.tabTitle).text = category
                tab.customView = tabView
                tab.contentDescription = category
                tl.addTab(tab, false)
            }
            tl.getTabAt(currentStylesTabIndex)?.select()
            for (i in 0 until tl.tabCount) {
                PanelTabHelper.updateTabStyle(tl.getTabAt(i), i == currentStylesTabIndex)
            }
            PanelTabHelper.scrollToTabIfOverflows(tl, currentStylesTabIndex)
        }

        attachStylesTabListener()
        showStylesTab(currentStylesTabIndex)
    }

    private fun buildGridMode() {
        isCustomGridMode = true
        tabListenerAttached = false

        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.searchBarExpanded.windowToken, 0)

        val isExpanded = mainViewModel.isPanelExpanded(PanelType.TABLES)
        applyExpandedUi(isExpanded)

        showGridTab()
    }

    private fun attachStylesTabListener() {
        if (tabListenerAttached) return
        tabListenerAttached = true

        val listener = object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val pos = tab?.position ?: return
                currentStylesTabIndex = pos
                listOf(binding.tabLayout, binding.tabLayoutExpanded).forEach { tl ->
                    for (i in 0 until tl.tabCount) {
                        PanelTabHelper.updateTabStyle(tl.getTabAt(i), i == pos)
                    }
                }
                val other = if (tab.parent === binding.tabLayout)
                    binding.tabLayoutExpanded else binding.tabLayout
                if (other.selectedTabPosition != pos) {
                    other.setScrollPosition(pos, 0f, true)
                    PanelTabHelper.scrollToTabIfOverflows(other, pos)
                    other.getTabAt(pos)?.let { otherTab ->
                        other.clearOnTabSelectedListeners()
                        otherTab.select()
                        other.addOnTabSelectedListener(this)
                    }
                }
                binding.root.post {
                    showStylesTab(pos)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        }

        binding.tabLayout.addOnTabSelectedListener(listener)
        binding.tabLayoutExpanded.addOnTabSelectedListener(listener)
    }

    private fun showStylesTab(position: Int) {
        if (_binding == null) return
        if (position < 0 || position >= styleCategories.size) return

        val prevPos = currentStylesTabIndex
        currentStylesTabIndex = position
        val category = styleCategories[position]
        mainViewModel.lastTablesTabCategory = category
        mainViewModel.isLastTablesGridMode = false

        val target: Fragment = fragmentCache.getOrPut(category) {
            TablesListFragment.newInstance(category, currentQuery)
        }

        val animEnter = if (position >= prevPos) R.anim.slide_in_right else R.anim.slide_in_left
        val animExit  = if (position >= prevPos) R.anim.slide_out_left else R.anim.slide_out_right

        childFragmentManager.beginTransaction()
            .setCustomAnimations(animEnter, animExit)
            .setReorderingAllowed(true).apply {
                for (f in childFragmentManager.fragments) {
                    if (f !== target && !f.isHidden) hide(f)
                }
                if (!target.isAdded) add(R.id.fragmentContainer, target, category)
                else if (target.isHidden) show(target)
            }.commit()

        val isExpanded = mainViewModel.isPanelExpanded(PanelType.TABLES)
        (target as? TablesListFragment)?.onPanelExpanded(isExpanded)
    }

    private fun showGridTab() {
        if (_binding == null) return
        mainViewModel.isLastTablesGridMode = true

        val target: Fragment = fragmentCache.getOrPut(GRID_TAB_KEY) {
            TablesTabFragment()
        }

        childFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fade_in_fast, R.anim.fade_out_fast)
            .setReorderingAllowed(true).apply {
                for (f in childFragmentManager.fragments) {
                    if (f !== target && !f.isHidden) hide(f)
                }
                if (!target.isAdded) add(R.id.fragmentContainer, target, GRID_TAB_KEY)
                else if (target.isHidden) show(target)
            }.commit()

        val isExpanded = mainViewModel.isPanelExpanded(PanelType.TABLES)
        (target as? TablesTabFragment)?.onPanelSlide(if (isExpanded) 1f else 0f)
    }

    private fun observePanelExpanded() {
        // 1. Final settled state: update headers and child fragments
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.expandedPanel.map { it == PanelType.TABLES }.collect { expanded ->
                    applyExpandedUi(expanded)
                    for ((_, fragment) in fragmentCache) {
                        when (fragment) {
                            is TablesListFragment -> fragment.onPanelExpanded(expanded)
                            is TablesTabFragment -> fragment.onPanelSlide(if (expanded) 1f else 0f)
                        }
                    }
                }
            }
        }

        // 2. Live slide offset: drives smooth crossfade every frame
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                mainViewModel.panelSlideOffset.collect { offset ->
                    applySlideOffset(offset)
                }
            }
        }
    }

    private fun applySlideOffset(offset: Float) {
        if (_binding == null) return

        val collapsedAlpha = (1f - offset / 0.4f).coerceIn(0f, 1f)
        val expandedAlpha  = ((offset - 0.3f) / 0.7f).coerceIn(0f, 1f)

        if (!isCustomGridMode) {
            binding.gridHeaderCollapsed.visibility = View.GONE
            binding.gridHeaderExpanded.visibility  = View.GONE

            binding.headerCollapsed.alpha = collapsedAlpha
            binding.headerExpanded.alpha  = expandedAlpha
            binding.headerCollapsed.visibility = if (collapsedAlpha > 0f) View.VISIBLE else View.GONE
            binding.headerExpanded.visibility  = if (expandedAlpha  > 0f) View.VISIBLE else View.GONE

            binding.tabLayout.alpha          = collapsedAlpha
            binding.tabLayoutExpanded.alpha  = expandedAlpha
            binding.tabExpandedContainer.alpha = expandedAlpha
            binding.tabLayout.visibility     = if (collapsedAlpha > 0f) View.VISIBLE else View.GONE
            binding.tabLayoutExpanded.visibility = if (expandedAlpha > 0f) View.VISIBLE else View.GONE
            binding.tabExpandedContainer.visibility = if (expandedAlpha > 0f) View.VISIBLE else View.GONE
        } else {
            binding.headerCollapsed.visibility = View.GONE
            binding.headerExpanded.visibility  = View.GONE
            binding.tabExpandedContainer.visibility = View.GONE

            binding.gridHeaderCollapsed.alpha = collapsedAlpha
            binding.gridHeaderExpanded.alpha  = expandedAlpha
            binding.gridHeaderCollapsed.visibility = if (collapsedAlpha > 0f) View.VISIBLE else View.GONE
            binding.gridHeaderExpanded.visibility  = if (expandedAlpha  > 0f) View.VISIBLE else View.GONE
        }

        for ((_, fragment) in fragmentCache) {
            when (fragment) {
                is TablesListFragment -> fragment.onPanelSlide(offset)
                is TablesTabFragment -> fragment.onPanelSlide(offset)
            }
        }
    }

    private fun applyExpandedUi(expanded: Boolean) {
        if (_binding == null) return

        if (!isCustomGridMode) {
            binding.gridHeaderCollapsed.visibility = View.GONE
            binding.gridHeaderExpanded.visibility  = View.GONE

            binding.headerCollapsed.alpha      = if (!expanded) 1f else 0f
            binding.headerExpanded.alpha       = if (expanded)  1f else 0f
            binding.headerCollapsed.visibility = if (!expanded) View.VISIBLE else View.GONE
            binding.headerExpanded.visibility  = if (expanded)  View.VISIBLE else View.GONE
            binding.tabLayout.alpha            = if (!expanded) 1f else 0f
            binding.tabLayoutExpanded.alpha    = if (expanded)  1f else 0f
            binding.tabExpandedContainer.alpha = if (expanded)  1f else 0f
            binding.tabLayout.visibility       = if (!expanded) View.VISIBLE else View.GONE
            binding.tabLayoutExpanded.visibility = if (expanded) View.VISIBLE else View.GONE
            binding.tabExpandedContainer.visibility = if (expanded) View.VISIBLE else View.GONE

            if (expanded) {
                binding.searchBarExpanded.setText(currentQuery)
                binding.searchBarExpanded.setSelection(binding.searchBarExpanded.text?.length ?: 0)
                updateSearchCross(currentQuery)
            }
        } else {
            binding.headerCollapsed.visibility = View.GONE
            binding.headerExpanded.visibility  = View.GONE
            binding.tabExpandedContainer.visibility = View.GONE

            binding.gridHeaderCollapsed.alpha      = if (!expanded) 1f else 0f
            binding.gridHeaderExpanded.alpha       = if (expanded)  1f else 0f
            binding.gridHeaderCollapsed.visibility = if (!expanded) View.VISIBLE else View.GONE
            binding.gridHeaderExpanded.visibility  = if (expanded)  View.VISIBLE else View.GONE
        }

        val activeKey = if (isCustomGridMode) GRID_TAB_KEY else styleCategories.getOrNull(currentStylesTabIndex)
        val visibleFrag = fragmentCache[activeKey]
        when (visibleFrag) {
            is TablesListFragment -> visibleFrag.onPanelExpanded(expanded)
            is TablesTabFragment -> visibleFrag.onPanelSlide(if (expanded) 1f else 0f)
        }
    }

    override fun onDestroyView() {
        tabListenerAttached = false
        _binding?.tabLayout?.clearOnTabSelectedListeners()
        _binding?.tabLayoutExpanded?.clearOnTabSelectedListeners()
        super.onDestroyView()
        _binding = null
    }
}
