package com.webscare.urducanvas.ui.editor.panels.text.fonts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.sealed.FontDownloadState
import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.databinding.FragmentFontsListBinding
import com.example.urduphotodesigner.viewmodels.MainViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.collections.forEach

@AndroidEntryPoint
class FontsListFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentFontsListBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: com.webscare.urducanvas.viewmodels.MainViewModel by activityViewModels()
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()
    private lateinit var fontsAdapter: FontsAdapter
    private var fontEntity: com.webscare.urducanvas.data.model.FontEntity? = null

    private var currentLanguage: String? = null
    private var currentCategory: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentLanguage = arguments?.getString(ARG_FONT_LANGUAGE) ?: "All"
        currentCategory = null // default to "all categories" under that language
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFontsListBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        initObservers()
    }

    fun applyFilter(language: String, category: String?) {
        currentLanguage = language
        currentCategory = category // null = all categories in language
        view?.post { rebindLatest() }
    }

    private fun rebindLatest() {
        lifecycleScope.launch {
            val fonts = mainViewModel.localFonts.value
            val queryRaw = mainViewModel.rawQuery.value

            val query = queryRaw.trim().lowercase()

            val byLanguage = when (val lang = currentLanguage ?: "All") {
                "All" -> fonts
                else -> fonts.filter { it.font_language.equals(lang, ignoreCase = true) }
            }

            val byCategory = when (val cat = currentCategory) {
                null -> byLanguage
                else -> byLanguage.filter {
                    it.font_category.equals(cat, ignoreCase = true)
                }
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

            val finalList =
                if (currentCategory == null && currentLanguage == "All") {

                    // 1) Separate Urdu & English
                    val urdu = filtered
                        .filter { it.font_language.equals("Urdu", true) }
                        .sortedBy { it.font_name.lowercase() }

                    val english = filtered
                        .filter { it.font_language.equals("English", true) }
                        .sortedBy { it.font_name.lowercase() }

                    // 2) Interleave for horizontal grid
                    val merged = mutableListOf<com.webscare.urducanvas.data.model.FontEntity>()
                    val maxSize = maxOf(urdu.size, english.size)

                    for (i in 0 until maxSize) {
                        if (i < urdu.size) merged.add(urdu[i])      // TOP ROW
                        if (i < english.size) merged.add(english[i]) // BOTTOM ROW
                    }

                    merged
                } else {
                    // Normal case → sort all fonts alphabetically
                    filtered.sortedBy { it.font_name.lowercase() }
                }

            fontsAdapter.submitList(finalList)

        }
    }

    private fun setupRecyclerViews() {
        fontsAdapter = FontsAdapter { font, isDownloaded ->
            handleFontSelection(font, isDownloaded)
        }
        binding.englishRV.adapter = fontsAdapter
    }

    private fun handleFontSelection(font: com.webscare.urducanvas.data.model.FontEntity, isDownloaded: Boolean) {
        if (isDownloaded) {
            viewModel.setFont(font)
        } else {
            fontEntity = font
            val updatedList = fontsAdapter.currentList.map {
                if (it.id == font.id) it.copy(is_downloading = true) else it
            }
            fontsAdapter.submitList(updatedList)
            mainViewModel.downloadFont(font)
        }
    }

    private fun initObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    mainViewModel.localFonts,
                    mainViewModel.queryDebounced.onStart { emit("") }
                ) { fonts, queryRaw ->
                    val query = queryRaw.trim().lowercase()

                    // 0) Language filter
                    val byLanguage = when (val lang = currentLanguage ?: "All") {
                        "All" -> fonts
                        "Imported" -> fonts.filter {
                            it.font_language.equals("Imported", true) &&
                                    it.font_category.equals("Imported", true)
                        }
                        else -> fonts.filter { it.font_language.equals(lang, ignoreCase = true) }
                    }

                    // 1) Category filter (null means all in that language)
                    val byCategory = when (val cat = currentCategory) {
                        null -> byLanguage
                        else -> byLanguage.filter {
                            it.font_category.equals(cat, ignoreCase = true)
                        }
                    }

                    // 2) Query filter
                    if (query.isEmpty()) return@combine byCategory
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
                }.collect { filtered ->
                    val finalList =
                        if (currentCategory == null && currentLanguage == "All") {

                            // 1) Separate Urdu & English
                            val urdu = filtered
                                .filter { it.font_language.equals("Urdu", true) }
                                .sortedBy { it.font_name.lowercase() }

                            val english = filtered
                                .filter { it.font_language.equals("English", true) }
                                .sortedBy { it.font_name.lowercase() }

                            // 2) Interleave for horizontal grid
                            val merged = mutableListOf<com.webscare.urducanvas.data.model.FontEntity>()
                            val maxSize = maxOf(urdu.size, english.size)

                            for (i in 0 until maxSize) {
                                if (i < urdu.size) merged.add(urdu[i])      // TOP ROW
                                if (i < english.size) merged.add(english[i]) // BOTTOM ROW
                            }

                            merged
                        } else {
                            // Normal case → sort all fonts alphabetically
                            filtered.sortedBy { it.font_name.lowercase() }
                        }

                    fontsAdapter.submitList(finalList)
                }
            }
        }

        lifecycleScope.launch {
            mainViewModel.fontDownloadStates.collect { downloadState ->
                downloadState.values.forEach { state ->
                    when (state) {
                        is com.webscare.urducanvas.common.canvas.sealed.FontDownloadState.Progress -> {
                            fontEntity = state.fontEntity
                            fontEntity?.let { font ->
                                fontsAdapter.selectedFontId = font.id.toString()
                            }
                        }

                        is com.webscare.urducanvas.common.canvas.sealed.FontDownloadState.SuccessWithTypeface -> {
                            fontEntity = state.fontEntity
                            viewModel.setFont(fontEntity!!)
                            fontEntity?.let { font ->
                                fontsAdapter.selectedFontId = font.id.toString()
                            }
                            mainViewModel.clearFontDownloadState()
                        }

                        is com.webscare.urducanvas.common.canvas.sealed.FontDownloadState.Success -> {
                            fontEntity?.let { font ->
                                if (font.is_downloaded) {
                                    viewModel.setFont(font)
                                }
                            }
                        }

                        is com.webscare.urducanvas.common.canvas.sealed.FontDownloadState.Error -> {
                            view?.let {
                                Snackbar.make(it, "Download failed!", Snackbar.LENGTH_SHORT).show()
                            }
                            fontEntity = null
                        }

                        else -> {}
                    }
                }
            }
        }

        viewModel.currentFont.observe(viewLifecycleOwner) { currentFont ->
            val currentId = currentFont?.id?.toString()
            if (!currentId.isNullOrEmpty()) {
                fontsAdapter.selectedFontId = currentId
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        private const val ARG_FONT_LANGUAGE = "font_language"

        fun newInstance(fontLanguage: String): FontsListFragment {
            val fragment = FontsListFragment()
            val args = Bundle()
            args.putString(ARG_FONT_LANGUAGE, fontLanguage)
            fragment.arguments = args
            return fragment
        }
    }
}
