package com.webscare.urducanvas.ui.navigation.templates

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.chip.Chip
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.common.canvas.sealed.HomeRow
import com.webscare.urducanvas.common.canvas.sealed.TemplateDownloadState
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.utils.showGlobalSuccessSnack
import com.webscare.urducanvas.data.model.toExportResultFinal
import com.webscare.urducanvas.databinding.DialogLoadingProgressBinding
import com.webscare.urducanvas.databinding.FragmentTemplatesBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@AndroidEntryPoint
class TemplatesFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentTemplatesBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: com.webscare.urducanvas.viewmodels.MainViewModel by activityViewModels()
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()
    private val filtersVM: com.webscare.urducanvas.viewmodels.FiltersViewModel by activityViewModels()

    private lateinit var categoryAdapter: TemplateCategoriesAdapter
    private lateinit var canvasSizeAdapter: com.webscare.urducanvas.ui.creation.CanvasSizeAdapter
    private var downloadingTemplate: com.webscare.urducanvas.data.model.TemplateEntity? = null
    private var bundle: Bundle = Bundle()
    private var loadingDialog: AlertDialog? = null
    private var dialogBinding: DialogLoadingProgressBinding? = null
    private lateinit var templatesAdapter: TemplatesAdapter
    private var allTemplates: List<com.webscare.urducanvas.data.model.TemplateEntity> = emptyList()
    private var activeCategory: String = "All"
    private var activeQuery: String = ""
    private var activeSize: com.webscare.urducanvas.common.canvas.model.CanvasSize? = null
    private var filterPanelVisible = false
    private var suppressChipClicks = false

    private enum class ListMode { SECTIONS, GRID }

    private var listMode = ListMode.SECTIONS

    private fun isGridMode(): Boolean = binding.categoriesRV.adapter === templatesAdapter

    private val sizeList = listOf(
        CanvasSize(
            "Instagram Story", 1080f, 1920f
        ), CanvasSize(
            "Instagram Post", 1080f, 1080f
        ), CanvasSize(
            "YouTube Thumbnail", 1280f, 720f
        ), CanvasSize(
            "Facebook Cover", 820f, 312f
        ), CanvasSize(
            "YouTube Channel Art", 2560f, 1440f
        ), CanvasSize(
            "A4", 2480f, 3508f
        ), CanvasSize(
            "Letter", 2550f, 3300f
        ), CanvasSize(
            "Poster", 3600f, 5400f
        ), CanvasSize(
            "Business Card", 1050f, 600f
        ), CanvasSize(
            "Billboard", 1920f, 1080f
        ), CanvasSize(
            "Vertical Banner", 1080f, 1920f
        ), CanvasSize(
            "Horizontal Banner", 1920f, 600f
        ), CanvasSize(
            "Flyer", 2550f, 3300f
        ), CanvasSize(
            "Resume", 2480f, 3508f
        ), CanvasSize(
            "Invitation", 1500f, 2100f
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTemplatesBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        observeTemplateCategories()
    }

    private fun showLoadingDialog() {
        dialogBinding = DialogLoadingProgressBinding.inflate(LayoutInflater.from(requireActivity()))

        loadingDialog = AlertDialog.Builder(requireActivity()).setView(dialogBinding!!.root)
            .setCancelable(true)
            .setOnCancelListener { dialog ->
                viewModel.clearLoading()
            }
            .create()

        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog?.show()

        dialogBinding?.title?.text = "Loading Template"
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
        dialogBinding = null
    }

    private fun setEvents() {
        val f0 = filtersVM.filters.value
        activeCategory = f0.category
        activeQuery = f0.query
        activeSize = f0.size

        // 2) reflect in UI immediately
        binding.searchBar.setText(f0.query)

        setupHeaderUi()

        canvasSizeAdapter =
            _root_ide_package_.com.webscare.urducanvas.ui.creation.CanvasSizeAdapter(
                sizeList, onClick = { selected ->
                    val newSize =
                        if (filtersVM.filters.value.size?.name == selected.name) null else selected
                    filtersVM.setSize(newSize)
                    canvasSizeAdapter.selectedSizeName = newSize?.name ?: ""

                }, false
            )
        binding.sizesRV.adapter = canvasSizeAdapter

        categoryAdapter = TemplateCategoriesAdapter(onSeeAll = { category ->
            val args = Bundle().apply { putString("TAB_NAME", category) }
            view?.post { findNavController().navigate(R.id.templatesListFragment, args) }
        }, onTemplateClick = { template, bool ->
            if (template.is_downloading) return@TemplateCategoriesAdapter
            if (!bool) {
                if (template.file_path.isNullOrEmpty()) {
                    downloadingTemplate = template
                    categoryAdapter.updateTemplateProgress(
                        template.id, progress = 0, isDownloading = true, isDownloaded = false
                    )
                    mainViewModel.downloadTemplate(template)
                    return@TemplateCategoriesAdapter
                }
            } else {
                val exportResult = template.toExportResultFinal()
                lifecycleScope.launch {
                    withContext(Dispatchers.Default) {
                        viewModel.loadTemplateFromJsonFile(exportResult, requireContext())
                    }
                }
                return@TemplateCategoriesAdapter
            }
        })
        binding.categoriesRV.adapter = categoryAdapter

        templatesAdapter = TemplatesAdapter { template, isDownloaded ->
            if (isDownloaded) {
                val exportResult = template.toExportResultFinal()
                lifecycleScope.launch {
                    withContext(Dispatchers.Default) {
                        viewModel.loadTemplateFromJsonFile(exportResult, requireContext())
                    }
                }
                return@TemplatesAdapter
            }

            downloadingTemplate = template

            mainViewModel.downloadTemplate(template)
        }

        switchToSections()
    }

    private fun switchToSections() {
        val rv = binding.categoriesRV
        if (rv.adapter !== categoryAdapter) {
            rv.layoutManager = LinearLayoutManager(requireContext())
            rv.adapter = categoryAdapter
            rv.itemAnimator = null
            rv.isNestedScrollingEnabled = true

            rv.setPadding(0, rv.paddingTop, 0, rv.paddingBottom)
            rv.clipToPadding = false
        }
    }

    private fun switchToGrid() {

        val rv = binding.categoriesRV

        if (rv.layoutManager !is StaggeredGridLayoutManager) {

            val newManager = StaggeredGridLayoutManager(
                2, RecyclerView.VERTICAL
            ).apply {
                gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }

            rv.layoutManager = newManager
        }

        if (rv.adapter !== templatesAdapter) {
            rv.adapter = templatesAdapter
        }

        rv.itemAnimator = null
        rv.isNestedScrollingEnabled = false

        val pad = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._18sdp)
        rv.setPadding(pad, rv.paddingTop, pad, rv.paddingBottom)
        rv.clipToPadding = false
    }

    private fun rebalanceSpans() {
        binding.categoriesRV.post {
            (binding.categoriesRV.layoutManager as? StaggeredGridLayoutManager)?.invalidateSpanAssignments()
        }
    }

    private fun updateCategoriesFromData(list: List<com.webscare.urducanvas.data.model.TemplateEntity>) {
        val cats = buildList {
            add("All")
            addAll(list.map { it.category?.trim() ?: "Unknown" }.filter { it.isNotEmpty() }
                .distinct().sorted())
        }
        renderCategoryChips(cats)
    }

    private fun renderCategoryChips(categories: List<String>) {
        val cg = binding.categoryChips
        cg.isSingleSelection = true
        cg.isSelectionRequired = false
        cg.removeAllViews()

        val selectedCat = filtersVM.filters.value.category   // ← use VM

        categories.forEach { label ->
            val chip = layoutInflater.inflate(R.layout.chip_filter_item, cg, false) as Chip
            chip.id = View.generateViewId()
            chip.text = label
            chip.isCheckable = true
            chip.isChecked = label.equals(selectedCat, true)
            cg.addView(chip)

            chip.addPressEffect {
                if (suppressChipClicks) return@addPressEffect

                val clickedText = chip.text.toString()
                val clickedIsSelected = chip.isChecked

                if (clickedIsSelected) {
                    // Tapped the already-selected chip
                    if (!clickedText.equals("All", true)) {
                        // Toggle back to All
                        val allChip = findChipByText(cg, "All")
                        if (allChip != null) {
                            suppressChipClicks = true
                            cg.clearCheck()
                            allChip.isChecked = true
                            suppressChipClicks = false
                            filtersVM.setCategory("All")
                        }
                    } else {
                        // "All" tapped again -> just close panel if open
                        if (filterPanelVisible) toggleFilterPanel()
                    }
                } else {
                    // Normal selection of a different chip
                    suppressChipClicks = true
                    cg.clearCheck()
                    chip.isChecked = true
                    suppressChipClicks = false
                    if (clickedText != filtersVM.filters.value.category) {
                        filtersVM.setCategory(clickedText)
                    } else if (filterPanelVisible) {
                        toggleFilterPanel()
                    }
                }
            }
        }

        // If current active disappeared, default to All
        if (!categories.any { it.equals(selectedCat, true) }) {
            filtersVM.setCategory("All")
            (0 until cg.childCount).map { cg.getChildAt(it) as Chip }
                .firstOrNull { it.text.toString().equals("All", true) }?.isChecked = true
        }
    }

    private fun findChipByText(group: ViewGroup, text: String): Chip? =
        (0 until group.childCount).mapNotNull { group.getChildAt(it) as? Chip }
            .firstOrNull { it.text.toString().equals(text, true) }

    private fun setupHeaderUi() {
        binding.searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.searchBar.text.toString()
                filtersVM.setQuery(query)

                // keyboard hide
                binding.searchBar.clearFocus()
                val imm =
                    requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(binding.searchBar.windowToken, 0)

                true
            } else {
                false
            }
        }

        binding.filters.addPressEffect { toggleFilterPanel() }
    }

    private fun toggleFilterPanel() {
        val panel = binding.categoryChips
        val bar = binding.searchBar

        if (!filterPanelVisible) {
            // show: ensure it's measured, start just above itself
            panel.isVisible = true
            panel.doOnPreDraw {
                // keep the bar visually on top
                bar.bringToFront()
                bar.elevation = resources.getDimension(com.intuit.sdp.R.dimen._2sdp)
                panel.elevation = resources.getDimension(com.intuit.sdp.R.dimen._1sdp)

                panel.translationY = -panel.height.toFloat()  // hidden behind the bar
                panel.alpha = 0f
                panel.animate().translationY(0f).translationY(0f).alpha(1f).setDuration(200).start()
            }
        } else {
            // hide: slide back up behind the bar
            panel.animate().translationY(-panel.height.toFloat()).alpha(0f).setDuration(180)
                .withEndAction {
                    panel.isGone = true
                }.start()
        }
        filterPanelVisible = !filterPanelVisible
    }

    private fun filterTemplates(
        source: List<com.webscare.urducanvas.data.model.TemplateEntity>,
        category: String,
        query: String,
        size: com.webscare.urducanvas.common.canvas.model.CanvasSize?
    ): List<com.webscare.urducanvas.data.model.TemplateEntity> {
        val byCat = if (category.equals("All", true)) {
            source
        } else {
            source.filter { it.category.equals(category, true) }
        }

        val q = query.trim().lowercase()
        val byQuery = if (q.isBlank()) byCat else byCat.filter { it.matchesQuery(q) }

        return size?.let { s -> byQuery.filter { it.matchesSize(s) } } ?: byQuery
    }

    private fun com.webscare.urducanvas.data.model.TemplateEntity.matchesSize(s: com.webscare.urducanvas.common.canvas.model.CanvasSize): Boolean {
        // round CanvasSize floats to ints
        val sw = s.width.roundToInt()
        val sh = s.height.roundToInt()

        val iw = canvas_width
        val ih = canvas_height

        // match either orientation
        return (iw == sw && ih == sh)
    }

    private fun com.webscare.urducanvas.data.model.TemplateEntity.matchesQuery(q: String): Boolean {
        val haystack = buildString {
            append(category).append(' ')
            // add fields you want searchable:
            append(template_name).append(' ')
            append(subcategory).append(' ')
            append(canvas_height).append(' ')
            append(canvas_width).append(' ')
            append(tags.joinToString(" ")).append(' ')
        }.lowercase()
        return haystack.contains(q)
    }

    private fun applyFilters() {
        if (activeCategory.equals("All", true)) {
            listMode = ListMode.SECTIONS
            switchToSections()

            val filtered = filterTemplates(allTemplates, "All", activeQuery, activeSize)

            // Safely group by category
            val rows = filtered.groupBy { it.category?.trim() ?: "Others" }.map { entry ->
                val title = entry.key.ifEmpty { "Others" }
                HomeRow.CategoryRow(
                    title, entry.value.distinctBy { it.id }.take(10)
                )
            }

            categoryAdapter.submitList(rows)
        } else {
            listMode = ListMode.GRID
            switchToGrid()

            val filtered = filterTemplates(allTemplates, activeCategory, activeQuery, activeSize)
            templatesAdapter.submitList(filtered)
            rebalanceSpans()
        }
    }

    private fun observeTemplateCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                filtersVM.filters.collect { f ->
                    // keep local state in sync
                    val categoryChanged = activeCategory != f.category
                    activeCategory = f.category
                    activeQuery = f.query
                    activeSize = f.size

                    // update UI if out of sync (avoid loops)
                    if (binding.searchBar.text?.toString() != f.query) {
                        binding.searchBar.setText(f.query)
                    }
                    if (::canvasSizeAdapter.isInitialized) {
                        val want = f.size?.name ?: ""
                        if (canvasSizeAdapter.selectedSizeName != want) {
                            canvasSizeAdapter.selectedSizeName = want
                            canvasSizeAdapter.notifyDataSetChanged()
                        }
                    }

                    applyFilters()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.localTemplates.collect { templates ->
                allTemplates = templates
                updateCategoriesFromData(templates)
                applyFilters()
            }
        }

        viewModel.loadingStage.observe(viewLifecycleOwner) { (message, percent) ->
            dialogBinding?.apply {
                progressBar.progress = percent
                subtitle.text = "$message... $percent%"
                tvProgressPercent.text = "$percent% complete"
            }
        }

        viewModel.isLoadingTemplate.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading == true) {
                showLoadingDialog()
            } else if (isLoading == false) {
                dismissLoadingDialog()
                view?.post { findNavController().navigate(R.id.editorFragment, bundle) }
            }
        }

        // download state
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.templateDownloadStates.collect { downloadState ->
                downloadState.values.forEach { state ->
                    when (state) {
                        is TemplateDownloadState.Progress -> {
                            if (isGridMode()) {
                                templatesAdapter.updateProgress(
                                    state.template.id,
                                    _root_ide_package_.com.webscare.urducanvas.data.model.ProgressUi(
                                        state.progress, isDownloading = true, isDownloaded = false
                                    )
                                )
                            } else {
                                categoryAdapter.updateTemplateProgress(
                                    state.template.id,
                                    state.progress,
                                    isDownloading = true,
                                    isDownloaded = false
                                )
                            }
                        }

                        is TemplateDownloadState.SuccessWithTemplate -> {
                            val t = state.template
                            mainViewModel.clearTemplateDownloadState()
                            val finalState =
                                _root_ide_package_.com.webscare.urducanvas.data.model.ProgressUi(
                                    100, isDownloading = false, isDownloaded = true
                                )
                            val finalTemplate = t.copy(is_downloading = false, is_downloaded = true)

                            if (isGridMode()) {
//                                val updated = templatesAdapter.currentList.toMutableList()
//                                val index = updated.indexOfFirst { it.id == t.id }
//                                if (index != -1) {
//                                    updated[index] = finalTemplate
//                                    templatesAdapter.submitList(updated)
//                                }

                            } else {
                                categoryAdapter.updateTemplateProgress(
                                    t.id, 100, isDownloading = false, isDownloaded = true
                                )

                                categoryAdapter.notifyTemplateStateChanged(finalTemplate)
                            }

                            showGlobalSuccessSnack("Template ready") {
                                val exportResult = t.toExportResultFinal()
                                lifecycleScope.launch {
                                    withContext(Dispatchers.Default) {
                                        viewModel.loadTemplateFromJsonFile(
                                            exportResult, requireContext()
                                        )
                                    }
                                }
                            }
                            downloadingTemplate = null
                        }

                        is TemplateDownloadState.Error -> {
                            downloadingTemplate?.let { t ->
                                val finalState =
                                    _root_ide_package_.com.webscare.urducanvas.data.model.ProgressUi(
                                        0, isDownloading = false, isDownloaded = false
                                    )

                                categoryAdapter.updateTemplateProgress(
                                    downloadingTemplate!!.id,
                                    0,
                                    isDownloading = false,
                                    isDownloaded = false
                                )
                                templatesAdapter.updateProgress(
                                    downloadingTemplate!!.id, finalState
                                )

                                downloadingTemplate = null
                            }

                        }

                        is TemplateDownloadState.Success -> {
                            mainViewModel.clearTemplateDownloadState()
                        }

                        null -> Unit
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}