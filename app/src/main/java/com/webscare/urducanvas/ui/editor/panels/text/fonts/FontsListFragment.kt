package com.webscare.urducanvas.ui.editor.panels.text.fonts

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.common.canvas.sealed.FontDownloadState
import com.webscare.urducanvas.common.utils.MorphGridLayoutManager
import com.webscare.urducanvas.databinding.FragmentFontsListBinding
import com.webscare.urducanvas.ui.editor.EditorFragment
import com.webscare.urducanvas.ui.editor.PanelSheetBehavior
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.math.abs

@AndroidEntryPoint
class FontsListFragment : androidx.fragment.app.Fragment() {

    private var _binding: FragmentFontsListBinding? = null
    private val safeBinding get() = _binding

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()

    private lateinit var fontsAdapter: FontsAdapter
    private var fontEntity: com.webscare.urducanvas.data.model.FontEntity? = null
    private var lastRequestedFontId: Int? = null
    private var isDownloadingFont = false

    private var currentLanguage: String? = null
    private var currentCategory: String? = null
    private var pendingScrollToFontId: String? = null

    private var savedScrollIndex: Int = 0
    private var savedScrollOffset: Int = 0

    private var standaloneMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentLanguage = arguments?.getString(ARG_FONT_LANGUAGE) ?: "All"
        standaloneMode  = arguments?.getBoolean(ARG_STANDALONE_MODE, false) ?: false
        currentCategory = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFontsListBinding.inflate(layoutInflater, container, false)
        return _binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeExpansion()
        observeFontData()
        observeDownloadStates()
        observeCurrentFont()
    }

    fun applyFilter(language: String, category: String?) {
        currentLanguage = language
        currentCategory = category
        view?.post { rebindLatest() }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) saveScrollPos() else restoreScrollPos()
    }

    override fun onDestroyView() {
        saveScrollPos()
        super.onDestroyView()
        _binding = null
    }

    private fun saveScrollPos() {
        val lm = _binding?.englishRV?.layoutManager as? LinearLayoutManager ?: return
        val pos = lm.findFirstVisibleItemPosition().takeIf { it >= 0 } ?: return
        savedScrollIndex  = pos
        savedScrollOffset = lm.findViewByPosition(pos)?.top ?: 0
    }

    private fun restoreScrollPos() {
        _binding?.englishRV?.post {
            (_binding?.englishRV?.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(savedScrollIndex, savedScrollOffset)
        }
    }

    // ── Swipe-up on englishRV to expand panel ─────────────────────────────────


    private fun findPanelSheet(): PanelSheetBehavior? {
        var f: androidx.fragment.app.Fragment? = this
        while (f != null) {
            if (f is EditorFragment) return f.panelSheetBehavior()
            f = f.parentFragment
        }
        return null
    }

    // ── Expansion observer ────────────────────────────────────────────────────

    private fun observeExpansion() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.expandedPanel
                    .collect { expanded -> applyExpansion(expanded == PanelType.FONTS) }
            }
        }

        // Live slide offset: drives smooth layout transition on slide
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                mainViewModel.panelSlideOffset.collect { offset ->
                    applySlideOffset(offset)
                }
            }
        }
    }

    private fun applySlideOffset(offset: Float) {
        val rv = safeBinding?.englishRV ?: return
        val lm = rv.layoutManager as? MorphGridLayoutManager
        if (lm != null) {
            lm.applyFraction(rv, offset)
            val effectiveExpanded = offset >= 0.95f
            if (fontsAdapter.isExpanded != effectiveExpanded) {
                rv.recycledViewPool.clear()
                fontsAdapter.isExpanded = effectiveExpanded
            }
        }

        // Smoothly update size of all visible items in 60fps!
        val rvWidth = rv.width
        val rvPadding = rv.paddingLeft + rv.paddingRight
        fontsAdapter.slideOffset = offset
        fontsAdapter.recyclerViewWidth = rvWidth
        fontsAdapter.recyclerViewPadding = rvPadding

        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            val holder = rv.getChildViewHolder(child) as? FontsAdapter.FontViewHolder
            holder?.updateSize(offset, rvWidth, rvPadding)
        }
    }

    private fun applyExpansion(expanded: Boolean) {
        val rv = safeBinding?.englishRV ?: return
        if (rv.width == 0) {
            rv.post {
                applyExpansion(expanded)
            }
            return
        }
        val lm = rv.layoutManager as? MorphGridLayoutManager
        if (lm != null) {
            lm.applyFraction(rv, if (expanded) 1f else 0f)
            if (fontsAdapter.isExpanded != expanded) {
                rv.recycledViewPool.clear()
                fontsAdapter.isExpanded = expanded
            }
        }

        // Sync item size on final settle state
        val rvWidth = rv.width
        val rvPadding = rv.paddingLeft + rv.paddingRight
        val offset = if (expanded) 1f else 0f
        fontsAdapter.slideOffset = offset
        fontsAdapter.recyclerViewWidth = rvWidth
        fontsAdapter.recyclerViewPadding = rvPadding

        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            val holder = rv.getChildViewHolder(child) as? FontsAdapter.FontViewHolder
            holder?.updateSize(offset, rvWidth, rvPadding)
        }
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    private fun buildFilteredList(
        fonts: List<com.webscare.urducanvas.data.model.FontEntity>,
        queryRaw: String
    ): List<com.webscare.urducanvas.data.model.FontEntity> {
        val query = queryRaw.trim().lowercase()

        if (currentLanguage == "Recents") {
            val recent = mainViewModel.recentFonts.value
            return if (query.isEmpty()) recent else {
                val tokens = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
                recent.filter { f ->
                    val haystack = buildString {
                        append(f.font_name); append(' ')
                        append(f.file_name); append(' ')
                        append(f.font_category); append(' ')
                        append(f.alt_text ?: "")
                    }.lowercase()
                    tokens.all { it in haystack }
                }
            }
        }

        val byLanguage = when (val lang = currentLanguage ?: "All") {
            "All"      -> fonts
            "Imported" -> fonts.filter {
                it.font_language.equals("Imported", true) &&
                        it.font_category.equals("Imported", true)
            }
            else       -> fonts.filter { it.font_language.equals(lang, ignoreCase = true) }
        }

        val byCategory = when (val cat = currentCategory) {
            null -> byLanguage
            else -> byLanguage.filter { it.font_category.equals(cat, ignoreCase = true) }
        }

        val filtered = if (query.isEmpty()) byCategory else {
            val tokens = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
            byCategory.filter { f ->
                val haystack = buildString {
                    append(f.font_name); append(' ')
                    append(f.file_name); append(' ')
                    append(f.font_category); append(' ')
                    append(f.alt_text ?: "")
                }.lowercase()
                tokens.all { it in haystack }
            }
        }

        return if (currentCategory == null && currentLanguage == "All") {
            val urdu    = filtered.filter { it.font_language.equals("Urdu", true) }
                .sortedBy { it.font_name?.lowercase() }
            val english = filtered.filter { it.font_language.equals("English", true) }
                .sortedBy { it.font_name?.lowercase() }
            val merged  = mutableListOf<com.webscare.urducanvas.data.model.FontEntity>()
            val maxSize = maxOf(urdu.size, english.size)
            for (i in 0 until maxSize) {
                if (i < urdu.size)    merged.add(urdu[i])
                if (i < english.size) merged.add(english[i])
            }
            merged
        } else {
            filtered.sortedBy { it.font_name?.lowercase() }
        }
    }

    private fun submitWithScrollPreservation(
        newList: List<com.webscare.urducanvas.data.model.FontEntity>
    ) {
        if (isDownloadingFont) return
        val lm           = safeBinding?.englishRV?.layoutManager as? LinearLayoutManager
        val savedIndex   = lm?.findFirstVisibleItemPosition()?.takeIf { it >= 0 } ?: 0
        val savedOffset  = lm?.findViewByPosition(savedIndex)?.top ?: 0
        val scrollTarget = pendingScrollToFontId

        fontsAdapter.submitList(newList) {
            val b = safeBinding ?: return@submitList
            if (scrollTarget != null) {
                val pos = newList.indexOfFirst { it.id.toString() == scrollTarget }
                if (pos >= 0) {
                    (b.englishRV.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(pos, 0)
                }
                if (pendingScrollToFontId == scrollTarget) pendingScrollToFontId = null
            } else {
                (b.englishRV.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(savedIndex, savedOffset)
            }
        }
    }

    private fun rebindLatest() {
        val fonts = mainViewModel.localFonts.value
        val query = mainViewModel.rawQuery.value
        submitWithScrollPreservation(buildFilteredList(fonts, query))
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        fontsAdapter = FontsAdapter { font, isDownloaded ->
            handleFontSelection(font, isDownloaded)
        }
        val isExpanded = mainViewModel.isPanelExpanded(PanelType.FONTS)
        val rv = _binding!!.englishRV
        rv.layoutManager = MorphGridLayoutManager(
            context = requireContext(),
            collapsedSpan = 3,
            expandedSpan = 3
        ).apply {
            applyFraction(rv, if (isExpanded) 1f else 0f)
        }
        fontsAdapter.isExpanded = isExpanded
        rv.adapter = fontsAdapter
    }

    private fun handleFontSelection(
        font: com.webscare.urducanvas.data.model.FontEntity,
        isDownloaded: Boolean
    ) {
        if (isDownloaded) {
            mainViewModel.recordRecentFont(font.id)
            viewModel.setFont(font)
            if (!standaloneMode) mainViewModel.collapsePanel()
            return
        }

        fontEntity = font
        lastRequestedFontId = font.id
        isDownloadingFont = true

        fontsAdapter.addDownloadingId(font.id)
        mainViewModel.downloadFont(font)
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observeFontData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    mainViewModel.localFonts,
                    mainViewModel.queryDebounced.onStart { emit("") },
                    mainViewModel.recentFonts
                ) { fonts, queryRaw, _ ->
                    buildFilteredList(fonts, queryRaw)
                }.collect { finalList ->
                    submitWithScrollPreservation(finalList)
                }
            }
        }
    }

    private fun observeDownloadStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {  // ← add this
                mainViewModel.fontDownloadStates.collect { downloadState ->
                    downloadState.values.forEach { state ->
                        when (state) {
                            is FontDownloadState.Progress -> {
                                Log.d("FONT_DEBUG", "Progress observed")
                            }
                            is FontDownloadState.SuccessWithTypeface -> {
                                val completedFont = state.fontEntity
                                Log.d("FONT_DEBUG", "SUCCESS id=${completedFont.id} lastRequested=$lastRequestedFontId")
                                isDownloadingFont = false
                                fontsAdapter.clearDownloadingId(completedFont.id)
                                if (completedFont.id == lastRequestedFontId) {
                                    fontEntity                  = completedFont
                                    mainViewModel.recordRecentFont(completedFont.id)
                                    fontsAdapter.selectedFontId = completedFont.id.toString()
                                    viewModel.setFont(completedFont)
                                    if (!standaloneMode) mainViewModel.collapsePanel()
                                    lastRequestedFontId         = null
                                    mainViewModel.clearFontDownloadState()
                                }
                            }
                            is FontDownloadState.Error -> {
                                val failedFont = state.fontEntity
                                Log.d("FONT_DEBUG", "ERROR observed")
                                isDownloadingFont = false
                                fontsAdapter.clearDownloadingId(failedFont.id)
                                view?.let {
                                    if (it.isAttachedToWindow) {
                                        Snackbar.make(it, "Download failed!", Snackbar.LENGTH_SHORT).show()
                                    }
                                }
                                fontEntity            = null
                                pendingScrollToFontId = null
                                lastRequestedFontId   = null
                                mainViewModel.clearFontDownloadState()
                            }
                            else -> {
                                fontEntity?.let { font ->
                                    if (font.is_downloaded) viewModel.setFont(font)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeCurrentFont() {
        viewModel.currentFont.observe(viewLifecycleOwner) { currentFont ->
            val id = currentFont?.id?.toString()
            if (!id.isNullOrEmpty()) fontsAdapter.selectedFontId = id
        }
    }

    companion object {
        private const val ARG_FONT_LANGUAGE   = "font_language"
        private const val ARG_STANDALONE_MODE = "standalone_mode"

        fun newInstance(fontLanguage: String, standaloneMode: Boolean = false) =
            FontsListFragment().also {
                it.arguments = Bundle().apply {
                    putString(ARG_FONT_LANGUAGE, fontLanguage)
                    putBoolean(ARG_STANDALONE_MODE, standaloneMode)
                }
            }
    }
}