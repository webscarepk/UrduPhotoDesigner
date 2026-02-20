package com.webscare.urducanvas.ui.navigation.home

import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.data.model.TemplateEntity
import com.webscare.urducanvas.databinding.FragmentSearchBinding
import com.webscare.urducanvas.ui.navigation.files.FilesAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.asFlow
import androidx.navigation.fragment.findNavController
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.data.model.ProgressUi
import com.webscare.urducanvas.data.model.toExportResultFinal
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.toExportResultFinal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class SearchFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: com.webscare.urducanvas.viewmodels.MainViewModel by activityViewModels()
    private val canvasViewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()
    private lateinit var templatesAdapter: PopularTemplatesAdapter
    private lateinit var fontsAdapter: FontsAdapter
    private lateinit var filesAdapter: com.webscare.urducanvas.ui.navigation.files.FilesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get initial text from HomeFragment
        val initialQuery = arguments?.getString("initialQuery").orEmpty()
        binding.searchBar.requestFocus()
        binding.searchBar.setText(initialQuery)
        binding.searchBar.setSelection(initialQuery.length)

        // Force keyboard to remain open
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view.postDelayed({
            imm.showSoftInput(binding.searchBar, InputMethodManager.SHOW_IMPLICIT)
        }, 150)

        setupAdapters()
        setupSearchBar()
        observeSearchResults()
    }


    private fun setupAdapters() {
        // Templates
        templatesAdapter = PopularTemplatesAdapter(onClick = { template, isDownloaded ->
            if (template.is_downloading) return@PopularTemplatesAdapter
            if (isDownloaded) {
                if (template.file_path.isNullOrEmpty()) {
                    templatesAdapter.updateProgress(
                        template.id,
                        _root_ide_package_.com.webscare.urducanvas.data.model.ProgressUi(
                            progress = 0,
                            isDownloading = true,
                            isDownloaded = false
                        )
                    )
                    mainViewModel.downloadTemplate(template)
                    return@PopularTemplatesAdapter
                } else {
                    val exportResult = template.toExportResultFinal()
                    lifecycleScope.launch {
                        withContext(Dispatchers.Default) {
                            canvasViewModel.loadTemplateFromJsonFile(exportResult, requireContext())
                        }
                    }
                }
            } else {
                templatesAdapter.updateProgress(
                    template.id,
                    _root_ide_package_.com.webscare.urducanvas.data.model.ProgressUi(
                        progress = 0,
                        isDownloading = true,
                        isDownloaded = false
                    )
                )
                mainViewModel.downloadTemplate(template)
            }
        })
        binding.popularTemplateRV.apply {
            adapter = templatesAdapter
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }

        // Fonts
        fontsAdapter = FontsAdapter(onFontClick = { font, _ ->
            // navigate to editor or font preview
        }, onDownload = { mainViewModel.downloadFont(it) })
        binding.fontsRV.apply {
            adapter = fontsAdapter
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }

        filesAdapter = _root_ide_package_.com.webscare.urducanvas.ui.navigation.files.FilesAdapter(
            emptyList(),
            isGrid = false,
            onItemClick = { /* open item */ },
            onItemLongClick = {},
            onOptionsClick = { _, _ -> },
            onRename = { _, _ -> },
            onSelectionChanged = {})
        binding.filesRV.apply {
            adapter = filesAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupSearchBar() {
        binding.back.addPressEffect {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.searchBar.windowToken, 0)
            findNavController().navigateUp()
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                mainViewModel.setQuery(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun observeSearchResults() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                mainViewModel.localTemplates,
                mainViewModel.localFonts,
                mainViewModel.localImages,
                mainViewModel.exportResults.asFlow(),
                mainViewModel.queryDebounced.debounce(250).distinctUntilChanged()
            ) { templates, fonts, images, exports, query ->
                val q = query.trim().lowercase()

                val filteredTemplates = templates.filter { t ->
                    q.isNotEmpty() && t.template_name.lowercase().contains(q)
                }

                val filteredFonts = fonts.filter { f ->
                    q.isNotEmpty() && f.font_name.lowercase().contains(q)
                }

                val filteredFiles = exports.filter { e ->
                    q.isNotEmpty() && e.fileName.lowercase().contains(q)
                }

                val filteredImages = images.filter { i ->
                    q.isNotEmpty() && i.file_name.lowercase().contains(q)
                }

                SearchResults(
                    templates = filteredTemplates,
                    fonts = filteredFonts,
                    files = filteredFiles + filteredImages
                )
            }.collectLatest { result ->
                updateUI(result)
            }
        }
    }

    private fun updateUI(result: SearchResults) {
        // Templates
        templatesAdapter.submitList(result.templates)
        binding.popularTemplate.isVisible = result.templates.isNotEmpty()
        binding.popularTemplateRV.isVisible = result.templates.isNotEmpty()

        // Fonts
        fontsAdapter.submitList(result.fonts)
        binding.popularFonts.isVisible = result.fonts.isNotEmpty()
        binding.fontsRV.isVisible = result.fonts.isNotEmpty()

        // Files
        filesAdapter.updateList(result.files)
        binding.assets.isVisible = result.files.isNotEmpty()
        binding.filesRV.isVisible = result.files.isNotEmpty()

        // If all empty → show “No Results”
        val noResults =
            result.templates.isEmpty() && result.fonts.isEmpty() && result.files.isEmpty()
//        binding.noResultsLayout.isVisible = noResults
    }

    data class SearchResults(
        val templates: List<com.webscare.urducanvas.data.model.TemplateEntity>, val fonts: List<com.webscare.urducanvas.data.model.FontEntity>, val files: List<Any>
    )

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}