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
import androidx.core.content.ContextCompat
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
import com.webscare.urducanvas.common.utils.MorphGridLayoutManager
import com.webscare.urducanvas.common.utils.HorizontalSpringEdgeEffectFactory
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.databinding.FragmentTextBinding
import com.webscare.urducanvas.ui.editor.EditorFragment
import com.webscare.urducanvas.ui.editor.panels.text.fonts.FontsAdapter
import com.webscare.urducanvas.ui.editor.panels.text.fonts.imported.ImportedFontsBottomSheet
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
        binding.fontsRV.apply {
            layoutManager = MorphGridLayoutManager(
                context = requireContext(),
                collapsedSpan = 3,
                expandedSpan = 3
            ).apply {
                applyFraction(binding.fontsRV, if (isPanelExpanded) 1f else 0f)
            }
            adapter = fontsAdapter
        }
        fontsAdapter.isExpanded = isPanelExpanded
    }

    private fun setupSwipeRefresh() {
        // Swipe-to-shuffle only works in expanded state —
        // disabled in collapsed so it doesn't conflict with horizontal RV scroll
        binding.swipeRefresh.isEnabled = isPanelExpanded
        binding.swipeRefresh.setColorSchemeColors(
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.appColor)
        )
        binding.swipeRefresh.setOnRefreshListener {
            val shuffled = fontsAdapter.currentList.shuffled()
            fontsAdapter.submitList(shuffled) {
                binding.swipeRefresh.isRefreshing = false
                binding.fontsRV.scrollToPosition(0)
            }
        }
    }



    // ─────────────────────────────────────────────────────────────────────────
    // LANGUAGE state  →  "All | Urdu | English | Imported"
    // ─────────────────────────────────────────────────────────────────────────

    private fun showLanguageTabs() {
        inCategoryMode = false
        tabListenerAttached = false
        // Remove old listener before rebuilding to prevent double-fire
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
            applyTabScales(tl, idx)
        }

        attachTabListener()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CATEGORY state  →  "[Urdu]  Bold  Condensed  Decorated …"
    //
    // The selected language stays as the FIRST tab (pinned label).
    // It is visually distinct (appColor tint, full scale always).
    // Tapping it returns to language state — handled in onTabReselected.
    // ─────────────────────────────────────────────────────────────────────────

    private fun showCategoryTabs(categories: List<String>) {
        inCategoryMode = true
        tabListenerAttached = false
        // Remove old listener before rebuilding to prevent double-fire
        clearTabListeners()

        // Full list: "All" always first, then the real categories
        // pos 0 = pinned language label (tap-back)
        // pos 1 = "All" (show everything for this language)
        // pos 2+ = individual categories
        val allCategories = listOf("All") + categories

        // Resolve which position to select BEFORE touching tabs,
        // so selectedCategory is correct when rebindFonts() runs later.
        val targetCat: String? = when {
            selectedCategory != null && categories.contains(selectedCategory) -> selectedCategory
            else -> null  // null = "All"
        }
        selectedCategory = targetCat

        listOf(binding.tabLayout, binding.tabLayoutExpanded).forEach { tl ->
            tl.removeAllTabs()

            // Tab 0 — pinned language label, acts as "you are here" + tap-back
            val pinnedTab = tl.newTab()
            val pinnedView = LayoutInflater.from(context).inflate(R.layout.view_panel_tab, tl, false)
            pinnedView.findViewById<TextView>(R.id.tabTitle).apply {
                text = selectedLanguage
                setTextColor(ContextCompat.getColor(requireContext(), R.color.appColor))
            }
            pinnedTab.customView = pinnedView
            tl.addTab(pinnedTab, false)

            // Tab 1 = "All", Tab 2+ = individual categories
            allCategories.forEach { cat ->
                val tab = tl.newTab()
                val tabView = LayoutInflater.from(context).inflate(R.layout.view_panel_tab, tl, false)
                tabView.findViewById<TextView>(R.id.tabTitle).text = cat
                tab.customView = tabView
                tl.addTab(tab, false)
            }

            // Pinned tab (pos 0) always stays full scale
            tl.getTabAt(0)?.view?.apply { scaleX = 1f; scaleY = 1f }

            // Select correct position:
            // targetCat == null → "All" → pos 1
            // targetCat != null → find in allCategories (+1 for pinned tab offset)
            val selectPos = if (targetCat == null) {
                1  // "All" tab
            } else {
                val idx = allCategories.indexOf(targetCat)
                if (idx >= 0) idx + 1 else 1
            }

            // Select WITHOUT triggering the listener (listener not attached yet)
            tl.getTabAt(selectPos)?.select()
            applyTabScales(tl, selectPos)
        }

        // Attach listener AFTER all tabs are built and selection is set
        attachTabListener()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared tab listener — handles both language and category mode
    // ─────────────────────────────────────────────────────────────────────────

    private fun attachTabListener() {
        if (tabListenerAttached) return
        tabListenerAttached = true

        val listener = object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // Skip if this call was triggered by our own sync — avoid double handling
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

                if (inCategoryMode) {
                    // pos 0 = pinned language label — returns to language list
                    if (pos == 0) {
                        selectedCategory = null
                        // Persist: back to language mode
                        mainViewModel.lastFontsCategory = null
                        mainViewModel.lastFontsInCategoryMode = false
                        showLanguageTabs()
                        rebindFonts()
                        return
                    }

                    val cat =
                        (tab.customView?.findViewById<TextView>(R.id.tabTitle))?.text?.toString()
                            ?: return

                    // pos 1 = "All" tab → null means no category filter
                    selectedCategory = if (cat == "All") null else cat

                    // Persist category selection
                    mainViewModel.lastFontsCategory = selectedCategory
                    mainViewModel.lastFontsInCategoryMode = true

                    tab.view.animate().scaleX(1f).scaleY(1f).setDuration(100)
                        .setInterpolator(OvershootInterpolator(1.2f)).start()

                    rebindFonts()
                } else {
                    // LANGUAGE mode
                    val lang =
                        (tab.customView?.findViewById<TextView>(R.id.tabTitle))?.text?.toString()
                            ?: return
                    selectedLanguage = lang
                    selectedCategory = null

                    tab.view.animate().scaleX(1f).scaleY(1f).setDuration(100)
                        .setInterpolator(OvershootInterpolator(1.2f)).start()

                    val cats = languageCategoryMap[lang] ?: emptyList()
                    val hasCats =
                        cats.isNotEmpty() && lang != "All" && lang != "Recents" && lang != "Imported"

                    // Persist language selection
                    mainViewModel.lastFontsLanguage = lang
                    mainViewModel.lastFontsCategory = null
                    mainViewModel.lastFontsInCategoryMode = hasCats

                    if (hasCats) showCategoryTabs(cats)
                    rebindFonts()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // Pinned language tab (pos 0 in category mode) never shrinks
                if (inCategoryMode && tab?.position == 0) return
                tab?.view?.animate()?.scaleX(0.9f)?.scaleY(0.9f)?.setDuration(100)?.start()
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // In category mode: tapping the pinned language tab (pos 0)
                // fires onTabReselected if it was already "selected" visually.
                // We treat it the same as onTabSelected — return to languages.
                if (inCategoryMode && tab?.position == 0) {
                    selectedCategory = null
                    // Persist: back to language mode
                    mainViewModel.lastFontsCategory = null
                    mainViewModel.lastFontsInCategoryMode = false
                    showLanguageTabs()
                    rebindFonts()
                    return
                }
                // In language mode: reselecting a language that has categories
                // drops into category mode (user tapped the same language again)
                if (!inCategoryMode) {
                    val lang =
                        (tab?.customView?.findViewById<TextView>(R.id.tabTitle))?.text?.toString()
                            ?: return
                    val cats = languageCategoryMap[lang] ?: emptyList()
                    if (cats.isNotEmpty() && lang != "All" && lang != "Recents" && lang != "Imported") {
                        mainViewModel.lastFontsInCategoryMode = true
                        showCategoryTabs(cats)
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

        showLanguageTabs()
        rebindFonts()

        val idx = languageList.indexOf("Imported")
        if (idx >= 0) {
            listOf(_binding?.tabLayout, _binding?.tabLayoutExpanded).forEach { tl ->
                tl?.post {
                    tl.getTabAt(idx)?.select()
                    tl.setScrollPosition(idx, 0f, true)
                }
            }
        }
    }

    private fun applyTabScales(tl: TabLayout, selectedIdx: Int) {
        for (i in 0 until tl.tabCount) {
            tl.getTabAt(i)?.view?.apply {
                scaleX = if (i == selectedIdx) 1f else 0.9f
                scaleY = if (i == selectedIdx) 1f else 0.9f
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

        // "All" tab — interleave Urdu + English for visual variety
        return if (selectedLanguage == "All" && selectedCategory == null) {
            val urdu = filtered.filter { it.font_language.equals("Urdu", true) }
                .sortedBy { it.font_name?.lowercase() }
            val english = filtered.filter { it.font_language.equals("English", true) }
                .sortedBy { it.font_name?.lowercase() }
            val merged = mutableListOf<FontEntity>()
            val max = maxOf(urdu.size, english.size)
            for (i in 0 until max) {
                if (i < urdu.size) merged.add(urdu[i])
                if (i < english.size) merged.add(english[i])
            }
            merged
        } else {
            filtered.sortedBy { it.font_name?.lowercase() }
        }
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
            map[lang] = if (catNames.isNotEmpty()) listOf("All") + catNames else emptyList()
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
        // Smoothly animate header container space height from 38dp to 104dp
        val heightPx = (38 * density + (104 * density - 38 * density) * offset).toInt()
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
        val isSearchActive = currentQuery.isNotBlank()
        if (!isSearchActive) {
            binding.tabLayout.alpha = collapsedAlpha
            binding.tabLayoutExpanded.alpha = expandedAlpha
            binding.tabLayout.visibility = if (collapsedAlpha > 0f) View.VISIBLE else View.GONE
            binding.tabLayoutExpanded.visibility =
                if (expandedAlpha > 0f) View.VISIBLE else View.GONE
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
            val effectiveExpanded = offset >= 0.95f
            if (fontsAdapter.isExpanded != effectiveExpanded) {
                binding.fontsRV.recycledViewPool.clear()
                fontsAdapter.isExpanded = effectiveExpanded
            }
        }

        // Smoothly update size of all visible items in 60fps!
        val rvWidth = binding.fontsRV.width
        val rvPadding = binding.fontsRV.paddingLeft + binding.fontsRV.paddingRight
        fontsAdapter.slideOffset = offset
        fontsAdapter.recyclerViewWidth = rvWidth
        fontsAdapter.recyclerViewPadding = rvPadding

        for (i in 0 until binding.fontsRV.childCount) {
            val child = binding.fontsRV.getChildAt(i)
            val holder = binding.fontsRV.getChildViewHolder(child) as? FontsAdapter.FontViewHolder
            holder?.updateSize(offset, rvWidth, rvPadding)
        }
    }

    private fun applyExpandedUi(expanded: Boolean) {
        if (expanded) {
            binding.fontsRV.edgeEffectFactory = RecyclerView.EdgeEffectFactory()
            binding.fontsRV.translationX = 0f
        } else {
            binding.fontsRV.edgeEffectFactory = HorizontalSpringEdgeEffectFactory()
        }

        binding.headerCollapsed.isVisible = !expanded
        binding.headerExpanded.isVisible = expanded
        binding.tabLayoutExpanded.isVisible = expanded

        val density = resources.displayMetrics.density
        val finalHeightPx = if (expanded) (104 * density).toInt() else (38 * density).toInt()
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
            if (fontsAdapter.isExpanded != expanded) {
                binding.fontsRV.recycledViewPool.clear()
                fontsAdapter.isExpanded = expanded
            }
        }

        // Sync item size on final settle state
        val rvWidth = binding.fontsRV.width
        val rvPadding = binding.fontsRV.paddingLeft + binding.fontsRV.paddingRight
        val offset = if (expanded) 1f else 0f
        fontsAdapter.slideOffset = offset
        fontsAdapter.recyclerViewWidth = rvWidth
        fontsAdapter.recyclerViewPadding = rvPadding

        for (i in 0 until binding.fontsRV.childCount) {
            val child = binding.fontsRV.getChildAt(i)
            val holder = binding.fontsRV.getChildViewHolder(child) as? FontsAdapter.FontViewHolder
            holder?.updateSize(offset, rvWidth, rvPadding)
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
            mainViewModel.lastFontsLanguage = selectedLanguage
            mainViewModel.lastFontsCategory = selectedCategory
            mainViewModel.lastFontsInCategoryMode = inCategoryMode
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
        mainViewModel.setQuery(query)
        rebindFonts()
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