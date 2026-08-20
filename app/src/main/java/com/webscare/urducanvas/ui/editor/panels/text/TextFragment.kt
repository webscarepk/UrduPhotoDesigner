package com.webscare.urducanvas.ui.editor.panels.text

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.common.canvas.sealed.FontDownloadState
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.data.model.orderWithUrduFirst
import com.webscare.urducanvas.data.model.shuffleWithUrduFirst
import com.webscare.urducanvas.common.utils.MorphGridLayoutManager
import com.webscare.urducanvas.common.utils.HorizontalSpringEdgeEffectFactory
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.databinding.FragmentTextBinding
import com.webscare.urducanvas.ui.editor.EditorFragment
import com.webscare.urducanvas.ui.editor.panels.text.fonts.FontsAdapter
import com.webscare.urducanvas.ui.editor.panels.text.fonts.imported.ImportedFontsBottomSheet
import com.webscare.urducanvas.data.model.PresetCategory
import com.webscare.urducanvas.data.model.TextStylePreset
import com.webscare.urducanvas.data.repository.TextStylesRepository
import android.content.res.ColorStateList
import com.webscare.urducanvas.ui.editor.panels.text.styles.TextStylesMainAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TextFragment : Fragment() {

    private var _binding: FragmentTextBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var fontsAdapter: FontsAdapter
    private lateinit var stylesAdapter: TextStylesMainAdapter

    // ── Styles / Presets Mode State ──────────────────────────────────────────
    private var isStylesMode: Boolean = false
    private var inPresetCategoryMode: Boolean = false
    private var selectedPresetGroup: String = "All"
    private var selectedPresetCategory: String? = null

    // ── Tab state ─────────────────────────────────────────────────────────────
    // Single TabLayout, two visual states:
    //
    // LANGUAGE state  →  "All | Urdu | English | Imported"
    //   Tapping a language that has categories switches to CATEGORY state.
    //
    // CATEGORY state  →  "[Urdu]  Bold  Condensed  Decorated …"
    //   First tab is the selected language name (pinned, visually distinct).
    //   Tapping the pinned language tab returns to LANGUAGE state.

    private var inCategoryMode: Boolean = false
    private var selectedLanguage: String = "All"
    private var selectedCategory: String? = null
    private var currentQuery: String = ""
    private var languageCategoryMap: Map<String, List<String>> = emptyMap()
    private var languageList: List<String> = emptyList()

    // Prevents re-entrant listener calls when syncing collapsed ↔ expanded tabs
    private var tabListenerAttached = false

    // Held so we can clearOnTabSelectedListeners() before re-adding on tab rebuild
    private var tabListener: TabLayout.OnTabSelectedListener? = null

    // Re-entrance guard: prevents the sync call from triggering a second full handler run
    private var isSyncingTabs = false

    // ── Download tracking ─────────────────────────────────────────────────────
    private var pendingFontEntity: FontEntity? = null
    private var lastRequestedFontId: Int? = null
    private var pendingScrollToFontId: String? = null
    private var isDownloadingFont = false

    private val isPanelExpanded: Boolean
        get() = mainViewModel.isPanelExpanded(PanelType.FONTS)

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTextBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        restoreTabState()
        setupSwipeRefresh()
        setupEvents()
        setupModeSwitch()
        attachDragHandleSwipe()
        observePanelExpanded()
        observeFontData()
        observeDownloadStates()
        observeCurrentFont()
    }

    // Reads persisted tab/scroll state from ViewModel back into local fields.
    // Must run before observeFontData() so the first showLanguageTabs() /
    // showCategoryTabs() call already sees the correct mode.
    private fun restoreTabState() {
        selectedLanguage = mainViewModel.lastFontsLanguage
        selectedCategory = mainViewModel.lastFontsCategory
        inCategoryMode = mainViewModel.lastFontsInCategoryMode
        // scroll is restored inside submitFonts() using lastFontsScrollIndex/Offset
    }

    override fun onDestroyView() {
        tabListenerAttached = false
        clearTabListeners()
        _binding?.fontsRV?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RecyclerView
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        fontsAdapter = FontsAdapter { font, isDownloaded ->
            handleFontSelection(font, isDownloaded)
        }
        stylesAdapter = TextStylesMainAdapter { preset ->
            viewModel.addTextWithStyle(
                requireActivity().getString(R.string.dummyText),
                preset,
                requireActivity()
            )
        }

        binding.fontsRV.apply {
            layoutManager = MorphGridLayoutManager(
                context = requireContext(),
                collapsedSpan = 3,
                expandedSpan = 3
            ).apply {
                applyFraction(binding.fontsRV, if (isPanelExpanded) 1f else 0f)
            }
            adapter = if (isStylesMode) stylesAdapter else fontsAdapter
        }
        fontsAdapter.isExpanded = isPanelExpanded
        stylesAdapter.isExpanded = isPanelExpanded
    }

    private fun setupSwipeRefresh() {
        // Swipe-to-shuffle only works in expanded state —
        // disabled in collapsed so it doesn't conflict with horizontal RV scroll
        binding.swipeRefresh.isEnabled = isPanelExpanded
        binding.swipeRefresh.setColorSchemeColors(
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.appColor)
        )
        binding.swipeRefresh.setOnRefreshListener {
            if (isStylesMode) {
                val shuffled = stylesAdapter.currentList.shuffled()
                stylesAdapter.submitList(shuffled) {
                    binding.swipeRefresh.isRefreshing = false
                    binding.fontsRV.scrollToPosition(0)
                }
            } else {
                val shuffled = fontsAdapter.currentList.shuffleWithUrduFirst()
                fontsAdapter.submitList(shuffled) {
                    binding.swipeRefresh.isRefreshing = false
                    binding.fontsRV.scrollToPosition(0)
                }
            }
        }
    }



    // ─────────────────────────────────────────────────────────────────────────
    // Tab Level Animation (Horizontal Slide + Fade Transition)
    // ─────────────────────────────────────────────────────────────────────────

    private fun animateTabTransition(forward: Boolean, onHalfway: () -> Unit) {
        val tabLayouts = listOfNotNull(_binding?.tabLayout, _binding?.tabLayoutExpanded)
        if (tabLayouts.isEmpty()) {
            onHalfway()
            return
        }

        val density = resources.displayMetrics.density
        val outDistance = if (forward) -24f * density else 24f * density
        val inDistance = if (forward) 24f * density else -24f * density
        val fadeOutDuration = 90L
        val fadeInDuration = 140L

        var executed = false
        tabLayouts.forEach { tl ->
            tl.animate()
                .translationX(outDistance)
                .alpha(0f)
                .setDuration(fadeOutDuration)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    if (!executed) {
                        executed = true
                        onHalfway()
                    }
                    tl.translationX = inDistance
                    tl.alpha = 0f
                    tl.animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setDuration(fadeInDuration)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                }
                .start()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LANGUAGE state  →  "All | Urdu | English | Imported"
    // ─────────────────────────────────────────────────────────────────────────

    private fun showLanguageTabs(animate: Boolean = false) {
        val rebuildAction = {
            inCategoryMode = false
            tabListenerAttached = false
            clearTabListeners()

            listOf(binding.tabLayout, binding.tabLayoutExpanded).forEach { tl ->
                tl.removeAllTabs()
                languageList.forEach { lang ->
                    val tab = tl.newTab()
                    val tabView = LayoutInflater.from(context).inflate(R.layout.view_panel_tab, tl, false)
                    tabView.findViewById<TextView>(R.id.tabTitle).text = lang
                    tab.customView = tabView
                    tl.addTab(tab, false)
                }
                val idx = languageList.indexOf(selectedLanguage).coerceAtLeast(0)
                tl.getTabAt(idx)?.select()
                updateTextTabStyles(tl, idx)
                com.webscare.urducanvas.common.utils.PanelTabHelper.scrollToTabIfOverflows(tl, idx)
            }

            attachTabListener()
        }

        if (animate) {
            animateTabTransition(forward = false, onHalfway = rebuildAction)
        } else {
            rebuildAction()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CATEGORY state  →  "[← Urdu]  All  Bold  Condensed  Nastaliq …"
    //
    // Tab 0 = Breadcrumb Back Chip: [← Language] (light green bg, dark green text)
    // Tab 1 = "All"
    // Tab 2+ = individual categories
    // ─────────────────────────────────────────────────────────────────────────

    private fun showCategoryTabs(categories: List<String>, animate: Boolean = false) {
        val rebuildAction = {
            inCategoryMode = true
            tabListenerAttached = false
            clearTabListeners()

            val realCategories = categories.filter { it != "All" }
            val allCategories = listOf("All") + realCategories

            val targetCat: String? = when {
                selectedCategory != null && categories.contains(selectedCategory) -> selectedCategory
                else -> null  // null = "All"
            }
            selectedCategory = targetCat

            listOf(binding.tabLayout, binding.tabLayoutExpanded).forEach { tl ->
                tl.removeAllTabs()

                // Tab 0 — Breadcrumb Back Chip: [← Language]
                val breadcrumbTab = tl.newTab()
                val breadcrumbView = LayoutInflater.from(context).inflate(R.layout.view_panel_tab_breadcrumb, tl, false)
                breadcrumbView.findViewById<TextView>(R.id.tabTitle).text = selectedLanguage
                breadcrumbTab.customView = breadcrumbView
                tl.addTab(breadcrumbTab, false)

                // Tab 1 = "All", Tab 2+ = individual categories
                allCategories.forEach { cat ->
                    val tab = tl.newTab()
                    val tabView = LayoutInflater.from(context).inflate(R.layout.view_panel_tab, tl, false)
                    tabView.findViewById<TextView>(R.id.tabTitle).text = cat
                    tab.customView = tabView
                    tl.addTab(tab, false)
                }

                // Pinned breadcrumb (pos 0) stays full scale
                tl.getTabAt(0)?.view?.apply { scaleX = 1f; scaleY = 1f }

                val selectPos = if (targetCat == null) {
                    1  // "All" tab
                } else {
                    val idx = allCategories.indexOf(targetCat)
                    if (idx >= 0) idx + 1 else 1
                }

                tl.getTabAt(selectPos)?.select()
                updateTextTabStyles(tl, selectPos)
                com.webscare.urducanvas.common.utils.PanelTabHelper.scrollToTabIfOverflows(tl, selectPos)
            }

            attachTabListener()
        }

        if (animate) {
            animateTabTransition(forward = true, onHalfway = rebuildAction)
        } else {
            rebuildAction()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared tab listener — handles both language/category and preset group/category mode
    // ─────────────────────────────────────────────────────────────────────────

    private fun attachTabListener() {
        if (tabListenerAttached) return
        tabListenerAttached = true

        val listener = object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (isSyncingTabs) return
                val pos = tab?.position ?: return

                // Sync the other TabLayout (collapsed ↔ expanded)
                val other =
                    if (tab.parent == binding.tabLayout) binding.tabLayoutExpanded else binding.tabLayout
                if (other.selectedTabPosition != pos) {
                    isSyncingTabs = true
                    other.getTabAt(pos)?.select()
                    isSyncingTabs = false
                }

                if (isStylesMode) {
                    if (inPresetCategoryMode) {
                        // pos 0 = [← Styles] breadcrumb chip — returns to preset group list
                        if (pos == 0) {
                            selectedPresetCategory = null
                            inPresetCategoryMode = false
                            showPresetGroupTabs(animate = true)
                            rebindStyles()
                            return
                        }

                        val cat =
                            (tab.customView?.findViewById<TextView>(R.id.tabTitle))?.text?.toString()
                                ?: tab.text?.toString() ?: return

                        selectedPresetCategory = if (cat == "All") null else cat

                        updateTextTabStyles(binding.tabLayout, pos)
                        updateTextTabStyles(binding.tabLayoutExpanded, pos)
                        com.webscare.urducanvas.common.utils.PanelTabHelper.scrollToTabIfOverflows(binding.tabLayout, pos)
                        com.webscare.urducanvas.common.utils.PanelTabHelper.scrollToTabIfOverflows(binding.tabLayoutExpanded, pos)
                        rebindStyles()
                    } else {
                        val grp =
                            (tab.customView?.findViewById<TextView>(R.id.tabTitle))?.text?.toString()
                                ?: tab.text?.toString() ?: return
                        selectedPresetGroup = grp
                        selectedPresetCategory = null

                        updateTextTabStyles(binding.tabLayout, pos)
                        updateTextTabStyles(binding.tabLayoutExpanded, pos)
                        com.webscare.urducanvas.common.utils.PanelTabHelper.scrollToTabIfOverflows(binding.tabLayout, pos)
                        com.webscare.urducanvas.common.utils.PanelTabHelper.scrollToTabIfOverflows(binding.tabLayoutExpanded, pos)

                        if (grp == "Styles") {
                            val cats = PresetCategory.values().filter { it != PresetCategory.MY_STYLES }.map { it.displayName }
                            showPresetCategoryTabs(cats, animate = true)
                        }
                        rebindStyles()
                    }
                    return
                }

                if (inCategoryMode) {
                    // pos 0 = [← Language] breadcrumb chip — returns to language list
                    if (pos == 0) {
                        selectedCategory = null
                        mainViewModel.lastFontsCategory = null
                        mainViewModel.lastFontsInCategoryMode = false
                        showLanguageTabs(animate = true)
                        rebindFonts()
                        return
                    }

                    val cat =
                        (tab.customView?.findViewById<TextView>(R.id.tabTitle))?.text?.toString()
                            ?: return

                    selectedCategory = if (cat == "All") null else cat
                    mainViewModel.lastFontsCategory = selectedCategory
                    mainViewModel.lastFontsInCategoryMode = true

                    updateTextTabStyles(binding.tabLayout, pos)
                    updateTextTabStyles(binding.tabLayoutExpanded, pos)
                    rebindFonts()
                } else {
                    val lang =
                        (tab.customView?.findViewById<TextView>(R.id.tabTitle))?.text?.toString()
                            ?: return
                    selectedLanguage = lang
                    selectedCategory = null

                    updateTextTabStyles(binding.tabLayout, pos)
                    updateTextTabStyles(binding.tabLayoutExpanded, pos)

                    val cats = languageCategoryMap[lang] ?: emptyList()
                    val hasCats =
                        cats.isNotEmpty() && lang != "All" && lang != "Recents" && lang != "Imported"

                    mainViewModel.lastFontsLanguage = lang
                    mainViewModel.lastFontsCategory = null
                    mainViewModel.lastFontsInCategoryMode = hasCats

                    if (hasCats) {
                        showCategoryTabs(cats, animate = true)
                    }
                    rebindFonts()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                if (isStylesMode) {
                    if (inPresetCategoryMode && tab?.position == 0) {
                        selectedPresetCategory = null
                        inPresetCategoryMode = false
                        showPresetGroupTabs(animate = true)
                        rebindStyles()
                        return
                    }
                    if (!inPresetCategoryMode) {
                        val grp =
                            (tab?.customView?.findViewById<TextView>(R.id.tabTitle))?.text?.toString()
                                ?: tab?.text?.toString() ?: return
                        if (grp == "Styles") {
                            val cats = PresetCategory.values().filter { it != PresetCategory.MY_STYLES }.map { it.displayName }
                            showPresetCategoryTabs(cats, animate = true)
                        }
                    }
                    return
                }

                if (inCategoryMode && tab?.position == 0) {
                    selectedCategory = null
                    mainViewModel.lastFontsCategory = null
                    mainViewModel.lastFontsInCategoryMode = false
                    showLanguageTabs(animate = true)
                    rebindFonts()
                    return
                }

                if (!inCategoryMode) {
                    val lang =
                        (tab?.customView?.findViewById<TextView>(R.id.tabTitle))?.text?.toString()
                            ?: return
                    val cats = languageCategoryMap[lang] ?: emptyList()
                    if (cats.isNotEmpty() && lang != "All" && lang != "Recents" && lang != "Imported") {
                        mainViewModel.lastFontsInCategoryMode = true
                        showCategoryTabs(cats, animate = true)
                    }
                }
            }
        }

        tabListener = listener
        binding.tabLayout.addOnTabSelectedListener(listener)
        binding.tabLayoutExpanded.addOnTabSelectedListener(listener)
    }

    private fun clearTabListeners() {
        isSyncingTabs = false
        val listener = tabListener ?: return
        _binding?.tabLayout?.removeOnTabSelectedListener(listener)
        _binding?.tabLayoutExpanded?.removeOnTabSelectedListener(listener)
        tabListener = null
    }

    fun selectImportedTab() {
        inCategoryMode = false
        selectedCategory = null
        selectedLanguage = "Imported"

        mainViewModel.lastFontsLanguage = "Imported"
        mainViewModel.lastFontsCategory = null
        mainViewModel.lastFontsInCategoryMode = false

        val fonts = mainViewModel.localFonts.value
        languageCategoryMap = buildLanguageCategoryMap(fonts)
        languageList = buildLanguageList(fonts)

        showLanguageTabs(animate = false)
        rebindFonts()

        val idx = languageList.indexOf("Imported")
        if (idx >= 0) {
            listOf(_binding?.tabLayout, _binding?.tabLayoutExpanded).forEach { tl ->
                tl?.post {
                    tl.getTabAt(idx)?.select()
                    com.webscare.urducanvas.common.utils.PanelTabHelper.scrollToTabIfOverflows(tl, idx)
                }
            }
        }
    }

    private fun updateTextTabStyles(tl: TabLayout, selectedIdx: Int) {
        val context = tl.context
        val boldFont = ResourcesCompat.getFont(context, R.font.bold) ?: Typeface.DEFAULT_BOLD
        val regularFont = ResourcesCompat.getFont(context, R.font.regular) ?: Typeface.DEFAULT
        val isCatMode = if (isStylesMode) inPresetCategoryMode else inCategoryMode

        for (i in 0 until tl.tabCount) {
            val tab = tl.getTabAt(i) ?: continue
            val customView = tab.customView ?: continue
            val titleView = customView.findViewById<TextView>(R.id.tabTitle) ?: continue
            val indicatorView = customView.findViewById<View>(R.id.tabIndicator)
            val isSelected = i == selectedIdx

            if (isCatMode && i == 0) {
                // Breadcrumb Back Chip: [← Language] or [← Styles]
                // Styled with distinct chip background, dark green bold text, no underline indicator
                titleView.setTextColor(ContextCompat.getColor(context, R.color.appColor))
                titleView.typeface = boldFont
                indicatorView?.visibility = View.GONE
            } else if (isSelected) {
                titleView.setTextColor(ContextCompat.getColor(context, R.color.tab_selected_text))
                titleView.typeface = boldFont
                indicatorView?.visibility = View.VISIBLE
            } else {
                titleView.setTextColor(ContextCompat.getColor(context, R.color.tab_unselected_text))
                titleView.typeface = regularFont
                indicatorView?.visibility = View.GONE
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Font filtering
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildFilteredList(fonts: List<FontEntity>, query: String): List<FontEntity> {
        val q = query.trim().lowercase()

        // "Recents" tab — show recently used fonts in recency order
        if (selectedLanguage == "Recents") {
            val recent = mainViewModel.recentFonts.value
            return if (q.isEmpty()) recent else {
                val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
                recent.filter { f ->
                    val hay = buildString {
                        append(f.font_name); append(' ')
                        append(f.file_name); append(' ')
                        append(f.font_category); append(' ')
                        append(f.alt_text ?: "")
                    }.lowercase()
                    tokens.all { it in hay }
                }
            }
        }

        val byLanguage = when (selectedLanguage) {
            "All" -> fonts
            "Imported" -> fonts.filter {
                it.font_language.equals("Imported", true) && it.font_category.equals(
                    "Imported",
                    true
                )
            }

            else -> fonts.filter {
                it.font_language.equals(selectedLanguage, ignoreCase = true)
            }
        }

        val byCategory = when (val cat = selectedCategory) {
            null, "All" -> byLanguage
            else -> byLanguage.filter { it.font_category.equals(cat, ignoreCase = true) }
        }

        val filtered = if (q.isEmpty()) byCategory else {
            val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
            byCategory.filter { f ->
                val hay = buildString {
                    append(f.font_name); append(' ')
                    append(f.file_name); append(' ')
                    append(f.font_category); append(' ')
                    append(f.alt_text ?: "")
                }.lowercase()
                tokens.all { it in hay }
            }
        }

        return filtered.orderWithUrduFirst()
    }

    private fun rebindFonts() {
        val fonts = mainViewModel.localFonts.value
        submitFonts(buildFilteredList(fonts, currentQuery))
    }

    private fun submitFonts(list: List<FontEntity>) {
        if (isDownloadingFont) return
        val lm = binding.fontsRV.layoutManager as? LinearLayoutManager
        val savedIdx = lm?.findFirstVisibleItemPosition()?.takeIf { it >= 0 } ?: 0
        val savedOff = lm?.findViewByPosition(savedIdx)?.top ?: 0
        val scrollTo = pendingScrollToFontId
        val isFirstLoad = fontsAdapter.currentList.isEmpty()

        // Persist scroll position so it survives fragment recreation
        if (savedIdx > 0) {
            mainViewModel.lastFontsScrollIndex = savedIdx
            mainViewModel.lastFontsScrollOffset = savedOff
        }

        fontsAdapter.submitList(list) {
            if (_binding == null) return@submitList
            if (scrollTo != null) {
                val pos = list.indexOfFirst { it.id.toString() == scrollTo }
                if (pos >= 0) {
                    if (_binding == null) return@submitList
                    (binding.fontsRV.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                        pos,
                        0
                    )
                }
                if (pendingScrollToFontId == scrollTo) pendingScrollToFontId = null
            } else if (isFirstLoad) {
                // Prefer live scroll position; fall back to ViewModel-persisted value
                // on first load after recreation (when savedIdx is still 0)
                val restoreIdx = if (savedIdx > 0) savedIdx else mainViewModel.lastFontsScrollIndex
                val restoreOff = if (savedIdx > 0) savedOff else mainViewModel.lastFontsScrollOffset
                if (_binding == null) return@submitList
                (binding.fontsRV.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                    restoreIdx,
                    restoreOff
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Font selection / download  (mirrors FontsListFragment exactly)
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleFontSelection(font: FontEntity, isDownloaded: Boolean) {
        if (isDownloaded) {
            mainViewModel.recordRecentFont(font.id)
            viewModel.setFont(font)
            mainViewModel.collapsePanel()
            return
        }
        pendingFontEntity = font
        lastRequestedFontId = font.id
        isDownloadingFont = true

        fontsAdapter.addDownloadingId(font.id)
        mainViewModel.downloadFont(font)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Observers
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeFontData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    mainViewModel.localFonts,
                    mainViewModel.queryDebounced.onStart { emit("") },
                    mainViewModel.recentFonts
                ) { fonts, query, _ -> Pair(fonts, query) }.collect { (fonts, query) ->
                    currentQuery = query
                    languageCategoryMap = buildLanguageCategoryMap(fonts)
                    languageList = buildLanguageList(fonts)

                    // Only rebuild tabs if still in language mode.
                    // If we restored inCategoryMode = true from ViewModel,
                    // rebuild category tabs instead so the UI matches state.
                    if (inCategoryMode) {
                        val cats = languageCategoryMap[selectedLanguage] ?: emptyList()
                        if (cats.isNotEmpty()) showCategoryTabs(cats)
                        else showLanguageTabs()
                    } else {
                        showLanguageTabs()
                    }

                    submitFonts(buildFilteredList(fonts, query))
                }
            }
        }
    }

    /** All → Recents (if any) → real languages (sorted) → Imported last */
    private fun buildLanguageList(fonts: List<FontEntity>): List<String> {
        // Preferred display order — Urdu first, then English, then anything else alphabetically
        val preferredOrder = listOf("Urdu", "English")
        val allLangs = fonts.map { it.font_language.trim() }
            .filter { it.isNotBlank() && !it.equals("Imported", ignoreCase = true) }.distinct()
        val preferred = preferredOrder.filter { pref ->
            allLangs.any { it.equals(pref, ignoreCase = true) }
        }
        val rest =
            allLangs.filter { lang -> preferredOrder.none { it.equals(lang, ignoreCase = true) } }
                .sorted()
        val hasImported = fonts.any { it.font_language.equals("Imported", ignoreCase = true) }
        val hasRecents = mainViewModel.recentFonts.value.isNotEmpty()
        return buildList {
            add("All")
            if (hasRecents) add("Recents")
            addAll(preferred)
            addAll(rest)
            if (hasImported) add("Imported")
        }
    }

    /** language → sorted distinct category list with "All" prepended; "All", "Recents", and "Imported" → empty */
    private fun buildLanguageCategoryMap(fonts: List<FontEntity>): Map<String, List<String>> {
        val map = mutableMapOf(
            "All" to emptyList<String>(), "Recents" to emptyList(), "Imported" to emptyList()
        )
        fonts.groupBy { it.font_language.trim() }.filter { (lang, _) ->
            lang.isNotBlank() && !lang.equals("Imported", ignoreCase = true)
        }.forEach { (lang, langFonts) ->
            val catNames = langFonts.map { it.font_category.trim() }.filter { it.isNotBlank() }.distinct().sorted()
            map[lang] = catNames
        }
        return map
    }

    private fun observeDownloadStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.fontDownloadStates.collect { states ->
                states.values.forEach { state ->
                    when (state) {
                        is FontDownloadState.SuccessWithTypeface -> {
                            val done = state.fontEntity
                            if (done.id == lastRequestedFontId) {
                                isDownloadingFont = false
                                fontsAdapter.clearDownloadingId(done.id)
                                mainViewModel.recordRecentFont(done.id)
                                fontsAdapter.selectedFontId = done.id.toString()
                                viewModel.setFont(done)
                                mainViewModel.collapsePanel()
                                lastRequestedFontId = null
                                pendingFontEntity = null
                                mainViewModel.clearFontDownloadState()
                            }
                        }

                        is FontDownloadState.Error -> {
                            val failedFont = state.fontEntity
                            isDownloadingFont = false
                            fontsAdapter.clearDownloadingId(failedFont.id)
                            view?.let {
                                Snackbar.make(it, "Download failed!", Snackbar.LENGTH_SHORT).show()
                            }
                            pendingFontEntity = null
                            lastRequestedFontId = null
                            mainViewModel.clearFontDownloadState()
                        }

                        else -> {
                            pendingFontEntity?.let { font ->
                                if (font.is_downloaded) viewModel.setFont(font)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeCurrentFont() {
        viewModel.currentFont.observe(viewLifecycleOwner) { font ->
            val id = font?.id?.toString()
            if (!id.isNullOrEmpty()) fontsAdapter.selectedFontId = id
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Panel expand / collapse
    // ─────────────────────────────────────────────────────────────────────────

    private fun observePanelExpanded() {
        // ── 1. Final settled state: swap layout manager, update headers ─────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.expandedPanel.map { it == PanelType.FONTS }
                    .collect { expanded -> applyExpandedUi(expanded) }
            }
        }

        // ── 2. Live slide offset: drives smooth crossfade every frame ───────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                mainViewModel.panelSlideOffset.collect { offset ->
                    applySlideOffset(offset)
                }
            }
        }
    }

    /**
     * Driven every frame by PanelSheetBehavior during drag + spring settle.
     * Only alpha/visibility — zero layout passes, zero flicker.
     */
    private fun applySlideOffset(offset: Float) {
        if (_binding == null) return

        val density = resources.displayMetrics.density
        val isSearchActive = currentQuery.isNotBlank()
        val targetExpandedHeightDp = if (isSearchActive) 76 else 118
        // Smoothly animate header container space height from 42dp to expanded header height (118dp with tabs, 76dp with search)
        val heightPx = (42 * density + (targetExpandedHeightDp * density - 42 * density) * offset).toInt()
        val lp = binding.headerSpace.layoutParams
        if (lp.height != heightPx) {
            lp.height = heightPx
            binding.headerSpace.layoutParams = lp
        }

        // Crossfade headers smoothly at 0.5f threshold
        val collapsedAlpha = (1f - offset / 0.5f).coerceIn(0f, 1f)
        val expandedAlpha = ((offset - 0.5f) / 0.5f).coerceIn(0f, 1f)

        binding.headerCollapsed.alpha = collapsedAlpha
        binding.headerExpanded.alpha = expandedAlpha

        binding.headerCollapsed.visibility = if (collapsedAlpha > 0f) View.VISIBLE else View.GONE
        binding.headerExpanded.visibility = if (expandedAlpha > 0f) View.VISIBLE else View.GONE

        // Tab layouts mirror their respective headers
        if (!isSearchActive) {
            binding.tabLayout.alpha = collapsedAlpha
            binding.tabLayoutExpanded.alpha = expandedAlpha
            binding.tabExpandedContainer.alpha = expandedAlpha
            binding.tabLayout.visibility = if (collapsedAlpha > 0f) View.VISIBLE else View.GONE
            binding.tabLayoutExpanded.visibility =
                if (expandedAlpha > 0f) View.VISIBLE else View.GONE
            binding.tabExpandedContainer.visibility =
                if (expandedAlpha > 0f) View.VISIBLE else View.GONE
        } else {
            binding.tabLayout.visibility = View.GONE
            binding.tabLayoutExpanded.visibility = View.GONE
            binding.tabExpandedContainer.visibility = View.GONE
        }

        val rv = binding.fontsRV
        if (rv.width == 0) {
            rv.post {
                if (_binding != null) applySlideOffset(offset)
            }
            return
        }

        // Sync layout manager fraction with drag offset on every frame
        val lm = binding.fontsRV.layoutManager as? MorphGridLayoutManager
        if (lm != null) {
            lm.applyFraction(binding.fontsRV, offset)
            val effectiveExpanded = offset >= MorphGridLayoutManager.DEFAULT_FLIP_THRESHOLD
            if (!isStylesMode) {
                if (fontsAdapter.isExpanded != effectiveExpanded) {
                    binding.fontsRV.recycledViewPool.clear()
                    fontsAdapter.isExpanded = effectiveExpanded
                }
            } else {
                if (stylesAdapter.isExpanded != effectiveExpanded) {
                    binding.fontsRV.recycledViewPool.clear()
                    stylesAdapter.isExpanded = effectiveExpanded
                }
            }
        }

        // Apply smooth crossfade alpha around orientation flip to eliminate jumping
        binding.fontsRV.alpha = MorphGridLayoutManager.computeMorphAlpha(offset)

        // Smoothly update size of all visible items in 60fps!
        val rvWidth = binding.fontsRV.width
        val rvPadding = binding.fontsRV.paddingLeft + binding.fontsRV.paddingRight
        if (!isStylesMode) {
            fontsAdapter.slideOffset = offset
            fontsAdapter.recyclerViewWidth = rvWidth
            fontsAdapter.recyclerViewPadding = rvPadding

            for (i in 0 until binding.fontsRV.childCount) {
                val child = binding.fontsRV.getChildAt(i)
                val holder = binding.fontsRV.getChildViewHolder(child) as? FontsAdapter.FontViewHolder
                holder?.updateSize(offset, rvWidth, rvPadding)
            }
        } else {
            stylesAdapter.slideOffset = offset
            stylesAdapter.recyclerViewWidth = rvWidth
            stylesAdapter.recyclerViewPadding = rvPadding

            for (i in 0 until binding.fontsRV.childCount) {
                val child = binding.fontsRV.getChildAt(i)
                val holder = binding.fontsRV.getChildViewHolder(child) as? TextStylesMainAdapter.PresetViewHolder
                holder?.updateSize(offset, rvWidth, rvPadding)
            }
        }
    }

    private fun applyExpandedUi(expanded: Boolean) {
        binding.fontsRV.alpha = 1f
        if (expanded) {
            binding.fontsRV.edgeEffectFactory = RecyclerView.EdgeEffectFactory()
            binding.fontsRV.translationX = 0f
        } else {
            binding.fontsRV.edgeEffectFactory = HorizontalSpringEdgeEffectFactory()
        }

        val isSearchActive = currentQuery.isNotBlank()
        binding.headerCollapsed.alpha = if (!expanded) 1f else 0f
        binding.headerExpanded.alpha = if (expanded) 1f else 0f
        binding.headerCollapsed.isVisible = !expanded
        binding.headerExpanded.isVisible = expanded

        if (!isSearchActive) {
            binding.tabLayout.alpha = if (!expanded) 1f else 0f
            binding.tabLayoutExpanded.alpha = if (expanded) 1f else 0f
            binding.tabExpandedContainer.alpha = if (expanded) 1f else 0f
            binding.tabLayout.isVisible = !expanded
            binding.tabLayoutExpanded.isVisible = expanded
            binding.tabExpandedContainer.isVisible = expanded
        } else {
            binding.tabLayout.isVisible = false
            binding.tabLayoutExpanded.isVisible = false
            binding.tabExpandedContainer.isVisible = false
        }

        val density = resources.displayMetrics.density
        val targetExpandedHeightDp = if (isSearchActive) 76 else 118
        val finalHeightPx = if (expanded) (targetExpandedHeightDp * density).toInt() else (42 * density).toInt()
        val lp = binding.headerSpace.layoutParams
        if (lp.height != finalHeightPx) {
            lp.height = finalHeightPx
            binding.headerSpace.layoutParams = lp
        }

        // Enable swipe-to-shuffle only in expanded state
        binding.swipeRefresh.isEnabled = expanded

        val lm = binding.fontsRV.layoutManager as? MorphGridLayoutManager
        if (lm != null) {
            lm.applyFraction(binding.fontsRV, if (expanded) 1f else 0f)
            if (!isStylesMode) {
                if (fontsAdapter.isExpanded != expanded) {
                    binding.fontsRV.recycledViewPool.clear()
                    fontsAdapter.isExpanded = expanded
                }
            } else {
                if (stylesAdapter.isExpanded != expanded) {
                    binding.fontsRV.recycledViewPool.clear()
                    stylesAdapter.isExpanded = expanded
                }
            }
        }

        // Sync item size on final settle state
        val rvWidth = binding.fontsRV.width
        val rvPadding = binding.fontsRV.paddingLeft + binding.fontsRV.paddingRight
        val offset = if (expanded) 1f else 0f
        if (!isStylesMode) {
            fontsAdapter.slideOffset = offset
            fontsAdapter.recyclerViewWidth = rvWidth
            fontsAdapter.recyclerViewPadding = rvPadding

            for (i in 0 until binding.fontsRV.childCount) {
                val child = binding.fontsRV.getChildAt(i)
                val holder = binding.fontsRV.getChildViewHolder(child) as? FontsAdapter.FontViewHolder
                holder?.updateSize(offset, rvWidth, rvPadding)
            }
        } else {
            stylesAdapter.slideOffset = offset
            stylesAdapter.recyclerViewWidth = rvWidth
            stylesAdapter.recyclerViewPadding = rvPadding

            for (i in 0 until binding.fontsRV.childCount) {
                val child = binding.fontsRV.getChildAt(i)
                val holder = binding.fontsRV.getChildViewHolder(child) as? TextStylesMainAdapter.PresetViewHolder
                holder?.updateSize(offset, rvWidth, rvPadding)
            }
        }

        if (expanded) {
            // Sync expanded search bar with whatever is in collapsed bar
            binding.searchBarExpanded.setText(currentQuery)
            binding.searchBarExpanded.setSelection(
                binding.searchBarExpanded.text?.length ?: 0
            )
            updateExpandedSearchCross(currentQuery)
        } else {
            // Only reset the search bar — deliberately do NOT reset inCategoryMode,
            // selectedLanguage, or selectedCategory so tab state survives collapse.
            hideKeyboard()
            binding.searchBarExpanded.text?.clear()
            mainViewModel.setQuery("")
            currentQuery = ""

            // Persist current tab state to ViewModel so it survives
            // any future fragment recreation after collapse
            if (!isStylesMode) {
                mainViewModel.lastFontsLanguage = selectedLanguage
                mainViewModel.lastFontsCategory = selectedCategory
                mainViewModel.lastFontsInCategoryMode = inCategoryMode
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drag handle
    // ─────────────────────────────────────────────────────────────────────────

    private fun attachDragHandleSwipe() {
        // Walk up the fragment hierarchy to find EditorFragment and hand it our
        // drag handle so PanelSheetBehavior drives the guideline directly.
        var f: Fragment? = this
        while (f != null) {
            if (f is EditorFragment) {
                f.attachDragHandle(binding.dragHandle)

                // Also register the top-toolbar areas (collapsed + expanded headers)
                // so swiping down on the toolbar collapses the panel — same gesture
                // as dragging the handle.
                binding.root.post {
                    val b = _binding ?: return@post
                    (f as EditorFragment).panelSheetBehavior()?.let { sheet ->
                        sheet.attachAdditionalHandle(b.headerCollapsed)
                        sheet.attachAdditionalHandle(b.headerExpanded)
                    }
                }
                return
            }
            f = f.parentFragment
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Events
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupEvents() {
        binding.closePanel.addPressEffect { mainViewModel.collapsePanel() }

        // Collapsed header buttons
        binding.addText.addPressEffect {
            viewModel.addText(requireActivity().getString(R.string.dummyText), requireActivity())
            mainViewModel.collapsePanel()
        }

        binding.addFont.addPressEffect {
            ImportedFontsBottomSheet.newInstance()
                .show(childFragmentManager, ImportedFontsBottomSheet.TAG)
        }

        // Expanded header buttons (same actions, different IDs)
        binding.addTextExpanded.addPressEffect {
            viewModel.addText(requireActivity().getString(R.string.dummyText), requireActivity())
            mainViewModel.collapsePanel()
        }

        binding.addFontExpanded.addPressEffect {
            ImportedFontsBottomSheet.newInstance()
                .show(childFragmentManager, ImportedFontsBottomSheet.TAG)
        }

        // ── Collapsed search icon — tapping expands panel then focuses search ──
        binding.searchIcon.addPressEffect {
            // Expand the panel; applyExpandedUi fires and reveals searchBarExpanded
            if (!isPanelExpanded) mainViewModel.togglePanel(PanelType.FONTS)
            binding.root.post {
                if (_binding == null) return@post
                binding.searchBarExpanded.requestFocus()
                binding.searchBarExpanded.setSelection(
                    binding.searchBarExpanded.text?.length ?: 0
                )
                showKeyboard(binding.searchBarExpanded)
            }
        }

        // ── Expanded search ──────────────────────────────────────────────────
        binding.searchBarExpanded.imeOptions = EditorInfo.IME_ACTION_SEARCH
        binding.searchBarExpanded.setRawInputType(InputType.TYPE_CLASS_TEXT)

        binding.searchBarExpanded.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applySearch(binding.searchBarExpanded.text.toString())
                hideKeyboard(); true
            } else false
        }

        binding.searchBarExpanded.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateExpandedSearchCross(s?.toString().orEmpty())
                applySearch(s?.toString().orEmpty())
            }
        })

        binding.searchBarExpanded.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val dr = binding.searchBarExpanded.compoundDrawables[2]
                if (dr != null && event.x >= binding.searchBarExpanded.width - binding.searchBarExpanded.paddingRight - dr.bounds.width()) {
                    binding.searchBarExpanded.text.clear()
                    applySearch("")
                    updateExpandedSearchCross("")
                    hideKeyboard()
                    binding.searchBarExpanded.clearFocus()
                    return@setOnTouchListener true
                }
            }
            false
        }

    }

    private fun applySearch(query: String) {
        currentQuery = query
        val isSearchActive = query.isNotBlank()
        if (isPanelExpanded) {
            binding.tabLayoutExpanded.isVisible = !isSearchActive
            binding.tabExpandedContainer.isVisible = !isSearchActive
            val density = resources.displayMetrics.density
            val targetExpandedHeightDp = if (isSearchActive) 76 else 118
            val finalHeightPx = (targetExpandedHeightDp * density).toInt()
            val lp = binding.headerSpace.layoutParams
            if (lp.height != finalHeightPx) {
                lp.height = finalHeightPx
                binding.headerSpace.layoutParams = lp
            }
        }
        if (!isStylesMode) {
            mainViewModel.setQuery(query)
            rebindFonts()
        } else {
            rebindStyles()
        }
    }

    private fun updateExpandedSearchCross(text: String) {
        binding.searchBarExpanded.setCompoundDrawablesWithIntrinsicBounds(
            null,
            null,
            if (text.isNotEmpty()) ContextCompat.getDrawable(requireActivity(), R.drawable.ic_close)
            else null,
            null
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mode Switch: Fonts vs Presets (Segmented Chip Switcher)
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupModeSwitch() {
        binding.btnModeFonts.addPressEffect { setMode(false) }
        binding.btnModeStyles.addPressEffect { setMode(true) }
        binding.btnModeFontsExpanded.addPressEffect { setMode(false) }
        binding.btnModeStylesExpanded.addPressEffect { setMode(true) }
    }

    private fun setMode(stylesMode: Boolean) {
        if (isStylesMode == stylesMode) return
        isStylesMode = stylesMode

        val context = requireContext()
        val white = ContextCompat.getColor(context, R.color.white)
        val contrast = ContextCompat.getColor(context, R.color.contrast)
        val black = ContextCompat.getColor(context, R.color.black)
        val mediumFont = ResourcesCompat.getFont(context, R.font.medium)

        if (stylesMode) {
            // Highlight Presets
            binding.btnModeStyles.backgroundTintList = ColorStateList.valueOf(white)
            binding.btnModeStyles.typeface = mediumFont
            binding.btnModeStyles.setTextColor(black)

            binding.btnModeFonts.backgroundTintList = ColorStateList.valueOf(contrast)
            binding.btnModeFonts.typeface = mediumFont
            binding.btnModeFonts.setTextColor(black)

            binding.btnModeStylesExpanded.backgroundTintList = ColorStateList.valueOf(white)
            binding.btnModeStylesExpanded.typeface = mediumFont
            binding.btnModeStylesExpanded.setTextColor(black)

            binding.btnModeFontsExpanded.backgroundTintList = ColorStateList.valueOf(contrast)
            binding.btnModeFontsExpanded.typeface = mediumFont
            binding.btnModeFontsExpanded.setTextColor(black)

            // Hide font-specific add button
            binding.addFont.visibility = View.GONE
            binding.addFontExpanded.visibility = View.GONE

            // Switch RecyclerView adapter to stylesAdapter
            val lm = binding.fontsRV.layoutManager as? MorphGridLayoutManager
            if (lm != null) {
                lm.expandedSpan = 3
                lm.applyFraction(binding.fontsRV, if (isPanelExpanded) 1f else 0f)
            }
            binding.fontsRV.recycledViewPool.clear()
            stylesAdapter.isExpanded = isPanelExpanded
            stylesAdapter.slideOffset = if (isPanelExpanded) 1f else 0f
            stylesAdapter.recyclerViewWidth = binding.fontsRV.width
            stylesAdapter.recyclerViewPadding = binding.fontsRV.paddingLeft + binding.fontsRV.paddingRight
            binding.fontsRV.adapter = stylesAdapter

            // Show preset category/group tabs in TabLayout
            if (inPresetCategoryMode) {
                val cats = PresetCategory.values().filter { it != PresetCategory.MY_STYLES }.map { it.displayName }
                showPresetCategoryTabs(cats)
            } else {
                showPresetGroupTabs()
            }
            rebindStyles()
        } else {
            // Highlight Fonts
            binding.btnModeFonts.backgroundTintList = ColorStateList.valueOf(white)
            binding.btnModeFonts.typeface = mediumFont
            binding.btnModeFonts.setTextColor(black)

            binding.btnModeStyles.backgroundTintList = ColorStateList.valueOf(contrast)
            binding.btnModeStyles.typeface = mediumFont
            binding.btnModeStyles.setTextColor(black)

            binding.btnModeFontsExpanded.backgroundTintList = ColorStateList.valueOf(white)
            binding.btnModeFontsExpanded.typeface = mediumFont
            binding.btnModeFontsExpanded.setTextColor(black)

            binding.btnModeStylesExpanded.backgroundTintList = ColorStateList.valueOf(contrast)
            binding.btnModeStylesExpanded.typeface = mediumFont
            binding.btnModeStylesExpanded.setTextColor(black)

            // Restore font-specific add button
            binding.addFont.visibility = View.VISIBLE
            binding.addFontExpanded.visibility = View.VISIBLE

            // Switch RecyclerView adapter to fontsAdapter
            val lm = binding.fontsRV.layoutManager as? MorphGridLayoutManager
            if (lm != null) {
                lm.expandedSpan = 3
                lm.applyFraction(binding.fontsRV, if (isPanelExpanded) 1f else 0f)
            }
            binding.fontsRV.recycledViewPool.clear()
            fontsAdapter.isExpanded = isPanelExpanded
            fontsAdapter.slideOffset = if (isPanelExpanded) 1f else 0f
            fontsAdapter.recyclerViewWidth = binding.fontsRV.width
            fontsAdapter.recyclerViewPadding = binding.fontsRV.paddingLeft + binding.fontsRV.paddingRight
            binding.fontsRV.adapter = fontsAdapter

            // Show font language/category tabs
            if (inCategoryMode) {
                val cats = languageCategoryMap[selectedLanguage] ?: emptyList()
                showCategoryTabs(cats)
            } else {
                showLanguageTabs()
            }
            rebindFonts()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRESETS GROUP state  →  "All | Styles | My Styles"
    // ─────────────────────────────────────────────────────────────────────────

    private fun showPresetGroupTabs(animate: Boolean = false) {
        val rebuildAction = {
            inPresetCategoryMode = false
            tabListenerAttached = false
            clearTabListeners()

            val groups = mutableListOf("All", "Styles")
            val customStyles = TextStylesRepository.getCustomUserSavedStyles(requireContext())
            if (customStyles.isNotEmpty()) {
                groups.add(PresetCategory.MY_STYLES.displayName)
            }

            listOf(binding.tabLayout, binding.tabLayoutExpanded).forEach { tl ->
                tl.removeAllTabs()
                groups.forEach { grp ->
                    val tab = tl.newTab()
                    val tabView = LayoutInflater.from(context).inflate(R.layout.view_panel_tab, tl, false)
                    tabView.findViewById<TextView>(R.id.tabTitle).text = grp
                    tab.customView = tabView
                    tl.addTab(tab, false)
                }
                val idx = groups.indexOf(selectedPresetGroup).coerceAtLeast(0)
                tl.getTabAt(idx)?.select()
                updateTextTabStyles(tl, idx)
                com.webscare.urducanvas.common.utils.PanelTabHelper.scrollToTabIfOverflows(tl, idx)
            }

            attachTabListener()
        }

        if (animate) {
            animateTabTransition(forward = false, onHalfway = rebuildAction)
        } else {
            rebuildAction()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRESETS CATEGORY state  →  "[← Styles]  All  3D  Layers  Emboss …"
    //
    // Tab 0 = Breadcrumb Back Chip: [← Styles]
    // Tab 1 = "All"
    // Tab 2+ = individual categories
    // ─────────────────────────────────────────────────────────────────────────

    private fun showPresetCategoryTabs(categories: List<String>, animate: Boolean = false) {
        val rebuildAction = {
            inPresetCategoryMode = true
            tabListenerAttached = false
            clearTabListeners()

            val realCategories = categories.filter { it != "All" }
            val allCategories = listOf("All") + realCategories

            val targetCat: String? = when {
                selectedPresetCategory != null && categories.contains(selectedPresetCategory) -> selectedPresetCategory
                else -> null  // null = "All"
            }
            selectedPresetCategory = targetCat

            listOf(binding.tabLayout, binding.tabLayoutExpanded).forEach { tl ->
                tl.removeAllTabs()

                // Tab 0 — Breadcrumb Back Chip: [← Styles]
                val breadcrumbTab = tl.newTab()
                val breadcrumbView = LayoutInflater.from(context).inflate(R.layout.view_panel_tab_breadcrumb, tl, false)
                breadcrumbView.findViewById<TextView>(R.id.tabTitle).text = "Styles"
                breadcrumbTab.customView = breadcrumbView
                tl.addTab(breadcrumbTab, false)

                // Tab 1 = "All", Tab 2+ = individual preset categories
                allCategories.forEach { cat ->
                    val tab = tl.newTab()
                    val tabView = LayoutInflater.from(context).inflate(R.layout.view_panel_tab, tl, false)
                    tabView.findViewById<TextView>(R.id.tabTitle).text = cat
                    tab.customView = tabView
                    tl.addTab(tab, false)
                }

                // Pinned breadcrumb (pos 0) stays full scale
                tl.getTabAt(0)?.view?.apply { scaleX = 1f; scaleY = 1f }

                val selectPos = if (targetCat == null) 1 else {
                    val idx = allCategories.indexOf(targetCat)
                    if (idx >= 0) idx + 1 else 1
                }

                tl.getTabAt(selectPos)?.select()
                updateTextTabStyles(tl, selectPos)
                com.webscare.urducanvas.common.utils.PanelTabHelper.scrollToTabIfOverflows(tl, selectPos)
            }

            attachTabListener()
        }

        if (animate) {
            animateTabTransition(forward = true, onHalfway = rebuildAction)
        } else {
            rebuildAction()
        }
    }

    private fun rebindStyles() {
        val allPresets = TextStylesRepository.getAllPresets(requireContext())
        val filteredByCategory = if (inPresetCategoryMode) {
            // In Category mode under "Styles"
            if (selectedPresetCategory == null || selectedPresetCategory == "All") {
                allPresets.filter { it.category != PresetCategory.MY_STYLES }
            } else {
                val cat = PresetCategory.values().firstOrNull { it.displayName.equals(selectedPresetCategory, ignoreCase = true) }
                if (cat != null) TextStylesRepository.getPresetsByCategory(cat, requireContext())
                else allPresets
            }
        } else {
            // In Presets Group mode
            when (selectedPresetGroup) {
                "All" -> allPresets
                PresetCategory.MY_STYLES.displayName -> TextStylesRepository.getCustomUserSavedStyles(requireContext())
                "Styles" -> allPresets.filter { it.category != PresetCategory.MY_STYLES }
                else -> allPresets
            }
        }

        val q = currentQuery.trim().lowercase()
        val finalFiltered = if (q.isEmpty()) filteredByCategory else {
            filteredByCategory.filter { p ->
                p.name.lowercase().contains(q) || p.category.displayName.lowercase().contains(q)
            }
        }
        stylesAdapter.submitList(ArrayList(finalFiltered)) {
            binding.fontsRV.scrollToPosition(0)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Keyboard
    // ─────────────────────────────────────────────────────────────────────────

    private fun showKeyboard(v: View) {
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(
            v,
            InputMethodManager.SHOW_IMPLICIT
        )
    }

    private fun hideKeyboard() {
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.searchBarExpanded.clearFocus()
    }
}