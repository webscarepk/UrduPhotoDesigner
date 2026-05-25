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
        setupSwipeToExpand()   // ← swipe-up on RV to expand panel
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
    //
    // The RV is HORIZONTAL in collapsed mode so any upward vertical swipe is
    // free to drive the panel — no vertical scroll competes with it.
    //
    // We pass downY (from ACTION_DOWN) as the anchor to externalDragBegin so
    // every pixel of finger movement counts from the very start — not just
    // from after the slop threshold.

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeToExpand() {
        val slop = 8f * resources.displayMetrics.density

        var downX = 0f
        var downY = 0f
        var trackingPanel = false
        var decided = false

        _binding?.englishRV?.setOnTouchListener { _, event ->
            // Panel already expanded — let the vertical RV scroll normally
            if (mainViewModel.isPanelExpanded(PanelType.FONTS)) return@setOnTouchListener false

            val sheet = findPanelSheet() ?: return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    trackingPanel = false
                    decided = false
                    false   // let RV see DOWN
                }

                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - downY   // negative = finger moving up
                    val dx = event.rawX - downX

                    if (!decided) {
                        if (abs(dy) < slop && abs(dx) < slop) return@setOnTouchListener false

                        // Upward swipe that's more vertical than horizontal → take over
                        if (dy < 0f && abs(dy) > abs(dx)) {
                            trackingPanel = true
                            sheet.externalDragBegin(downRawY = downY, currentRawY = event.rawY)
                            // Cancel RV's own touch tracking (stops horizontal scroll)
                            val cancel = MotionEvent.obtain(event).also { c ->
                                c.action = MotionEvent.ACTION_CANCEL
                            }
                            _binding?.englishRV?.dispatchTouchEvent(cancel)
                            cancel.recycle()
                            // Prevent SwipeRefreshLayout / any parent from intercepting
                            _binding?.englishRV?.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        decided = true
                    }

                    if (trackingPanel) { sheet.externalDragBy(event.rawY); true } else false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val was = trackingPanel
                    if (was) sheet.externalDragEnd()
                    trackingPanel = false
                    decided = false
                    was
                }

                else -> false
            }
        }
    }

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
    }

    private fun applyExpansion(expanded: Boolean) {
        // On expand: onPanelExpandedSmooth (called via applySlideOffset at 75%)
        // already switched the layout manager while the spring was still moving.
        // Swapping it again here causes a remeasure jerk on an already-correct RV.
        // Only act on collapse to reset state.
        if (expanded) return
        val rv = safeBinding?.englishRV ?: return
        rv.recycledViewPool.clear()
        fontsAdapter.isExpanded = false
        rv.layoutManager = buildLayoutManager(false)
    }

    private fun buildLayoutManager(expanded: Boolean): GridLayoutManager =
        GridLayoutManager(
            requireContext(),
            2,
            if (expanded) GridLayoutManager.VERTICAL else GridLayoutManager.HORIZONTAL,
            false
        )

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
        val lm           = safeBinding?.englishRV?.layoutManager as? LinearLayoutManager
        val savedIndex   = lm?.findFirstVisibleItemPosition()?.takeIf { it >= 0 } ?: 0
        val savedOffset  = lm?.findViewByPosition(savedIndex)?.top ?: 0
        val scrollTarget = pendingScrollToFontId

        fontsAdapter.submitList(newList) {
            val b = safeBinding ?: return@submitList
            if (scrollTarget != null) {
                val pos = newList.indexOfFirst { it.id.toString() == scrollTarget }
                if (pos >= 0) {
                    b.englishRV.post {
                        safeBinding ?: return@post
                        (b.englishRV.layoutManager as? LinearLayoutManager)
                            ?.scrollToPositionWithOffset(pos, 0)
                    }
                }
                if (pendingScrollToFontId == scrollTarget) pendingScrollToFontId = null
            } else {
                b.englishRV.post {
                    safeBinding ?: return@post
                    (b.englishRV.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(savedIndex, savedOffset)
                }
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
        rv.layoutManager = buildLayoutManager(isExpanded)
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

        val b           = safeBinding ?: return
        val lm          = b.englishRV.layoutManager as? LinearLayoutManager
        val savedIndex  = lm?.findFirstVisibleItemPosition()?.takeIf { it >= 0 } ?: 0
        val savedOffset = lm?.findViewByPosition(savedIndex)?.top ?: 0

        val updatedList = fontsAdapter.currentList.map {
            if (it.id == font.id) it.copy(is_downloading = true) else it
        }
        fontsAdapter.submitList(updatedList) {
            safeBinding?.englishRV?.post {
                safeBinding ?: return@post
                (safeBinding?.englishRV?.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(savedIndex, savedOffset)
            }
        }
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
            mainViewModel.fontDownloadStates.collect { downloadState ->
                downloadState.values.forEach { state ->
                    when (state) {
                        is FontDownloadState.Progress -> {
                            Log.d("FONT_DEBUG", "Progress observed")
                        }
                        is FontDownloadState.SuccessWithTypeface -> {
                            val completedFont = state.fontEntity
                            Log.d("FONT_DEBUG", "SUCCESS id=${completedFont.id} lastRequested=$lastRequestedFontId")
                            if (completedFont.id == lastRequestedFontId) {
                                fontEntity                  = completedFont
                                mainViewModel.recordRecentFont(completedFont.id)
                                fontsAdapter.selectedFontId = completedFont.id.toString()
                                pendingScrollToFontId       = completedFont.id.toString()
                                viewModel.setFont(completedFont)
                                if (!standaloneMode) mainViewModel.collapsePanel()
                                lastRequestedFontId         = null
                                mainViewModel.clearFontDownloadState()
                            }
                        }
                        is FontDownloadState.Error -> {
                            Log.d("FONT_DEBUG", "ERROR observed")
                            view?.let {
                                Snackbar.make(it, "Download failed!", Snackbar.LENGTH_SHORT).show()
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