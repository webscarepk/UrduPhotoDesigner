package com.webscare.urducanvas.ui.editor.panels.text.fonts

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
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
import com.webscare.urducanvas.common.canvas.sealed.FontDownloadState
import com.webscare.urducanvas.databinding.FragmentFontsListBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

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
    private var isPanelExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentLanguage = arguments?.getString(ARG_FONT_LANGUAGE) ?: "All"
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
        setupRecyclerViews()
        initObservers()

        val saved = viewModel.getFontPanelState()
        val myLang = currentLanguage ?: "All"
        if (saved.selectedLanguage == myLang) {
            currentCategory = saved.selectedCategory
        }
        rebindLatest()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun applyFilter(language: String, category: String?) {
        currentLanguage = language
        currentCategory = category
        view?.post { rebindLatest() }
    }

    /**
     * Called by FontsFragment when the panel expands or collapses.
     * Mirrors ImagesListFragment.onPanelExpanded exactly:
     *   Collapsed → GridLayoutManager(spanCount=3, HORIZONTAL) — horizontal strip
     *   Expanded  → GridLayoutManager(spanCount=3, VERTICAL)   — vertical grid
     * Only the orientation changes; spanCount stays 3 in both states.
     */
    fun onPanelExpanded(expanded: Boolean) {
        if (isPanelExpanded == expanded) return
        isPanelExpanded = expanded
        if (_binding == null) return

        val rv = safeBinding?.englishRV ?: return
        rv.recycledViewPool.clear()

        fontsAdapter.isExpanded = expanded
        rv.layoutManager = buildLayoutManager(expanded)

        if (expanded) rv.scrollToPosition(0)
    }

    private fun buildLayoutManager(expanded: Boolean): GridLayoutManager =
        GridLayoutManager(
            requireContext(),
            if (expanded) 3 else 2,
            if (expanded) GridLayoutManager.VERTICAL else GridLayoutManager.HORIZONTAL,
            false
        )

    // ── Filtering ─────────────────────────────────────────────────────────────

    private fun buildFilteredList(
        fonts: List<com.webscare.urducanvas.data.model.FontEntity>,
        queryRaw: String
    ): List<com.webscare.urducanvas.data.model.FontEntity> {
        val query = queryRaw.trim().lowercase()

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
                .sortedBy { it.font_name.lowercase() }
            val english = filtered.filter { it.font_language.equals("English", true) }
                .sortedBy { it.font_name.lowercase() }
            val merged  = mutableListOf<com.webscare.urducanvas.data.model.FontEntity>()
            val maxSize = maxOf(urdu.size, english.size)
            for (i in 0 until maxSize) {
                if (i < urdu.size)    merged.add(urdu[i])
                if (i < english.size) merged.add(english[i])
            }
            merged
        } else {
            filtered.sortedBy { it.font_name.lowercase() }
        }
    }

    private fun submitWithScrollPreservation(
        newList: List<com.webscare.urducanvas.data.model.FontEntity>
    ) {
        // Capture scroll state before async DiffUtil starts
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
                        // Works for both LinearLayoutManager and GridLayoutManager
                        // (GridLayoutManager extends LinearLayoutManager)
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

    private fun setupRecyclerViews() {
        fontsAdapter = FontsAdapter { font, isDownloaded ->
            handleFontSelection(font, isDownloaded)
        }
        val rv = _binding!!.englishRV
        // Default collapsed state → horizontal 3-row grid (matches buildLayoutManager)
        rv.layoutManager = buildLayoutManager(isPanelExpanded)
        rv.adapter = fontsAdapter
    }

    private fun handleFontSelection(
        font: com.webscare.urducanvas.data.model.FontEntity,
        isDownloaded: Boolean
    ) {
        if (isDownloaded) {
            viewModel.setFont(font)
            return
        }

        fontEntity = font
        lastRequestedFontId = font.id

        val b          = safeBinding ?: return
        val lm         = b.englishRV.layoutManager as? LinearLayoutManager
        val savedIndex = lm?.findFirstVisibleItemPosition()?.takeIf { it >= 0 } ?: 0
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

    private fun initObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    mainViewModel.localFonts,
                    mainViewModel.queryDebounced.onStart { emit("") }
                ) { fonts, queryRaw ->
                    buildFilteredList(fonts, queryRaw)
                }.collect { finalList ->
                    submitWithScrollPreservation(finalList)
                }
            }
        }

        lifecycleScope.launch {
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
                                fontsAdapter.selectedFontId = completedFont.id.toString()
                                pendingScrollToFontId       = completedFont.id.toString()
                                viewModel.setFont(completedFont)
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

        viewModel.currentFont.observe(viewLifecycleOwner) { currentFont ->
            val id = currentFont?.id?.toString()
            if (!id.isNullOrEmpty()) fontsAdapter.selectedFontId = id
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_FONT_LANGUAGE = "font_language"

        fun newInstance(fontLanguage: String) = FontsListFragment().also {
            it.arguments = Bundle().apply { putString(ARG_FONT_LANGUAGE, fontLanguage) }
        }
    }
}