package com.example.urduphotodesigner.ui.navigation.templates

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.UnitType
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.example.urduphotodesigner.common.canvas.sealed.HomeRow
import com.example.urduphotodesigner.common.canvas.sealed.TemplateDownloadState
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.common.utils.showGlobalSuccessSnack
import com.example.urduphotodesigner.data.model.ProgressUi
import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.data.model.toExportResultFinal
import com.example.urduphotodesigner.databinding.DialogLoadingProgressBinding
import com.example.urduphotodesigner.databinding.FragmentTemplatesBinding
import com.example.urduphotodesigner.ui.creation.CanvasSizeAdapter
import com.example.urduphotodesigner.viewmodels.FiltersViewModel
import com.example.urduphotodesigner.viewmodels.MainViewModel
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@AndroidEntryPoint
class TemplatesFragment : Fragment() {
    private var _binding: FragmentTemplatesBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()
    private val filtersVM: FiltersViewModel by activityViewModels()

    private lateinit var categoryAdapter: TemplateCategoriesAdapter
    private lateinit var canvasSizeAdapter: CanvasSizeAdapter
    private var downloadingTemplate: TemplateEntity? = null
    private var bundle: Bundle = Bundle()
    private var loadingDialog: AlertDialog? = null
    private var dialogBinding: DialogLoadingProgressBinding? = null

    private lateinit var sglm: StaggeredGridLayoutManager
    private lateinit var templatesAdapter: TemplatesAdapter

    private var allTemplates: List<TemplateEntity> = emptyList()
    private var activeCategory: String = "All"
    private var activeQuery: String = ""
    private var activeSize: CanvasSize? = null
    private var filterPanelVisible = false
    private var suppressChipClicks = false

    private enum class ListMode { SECTIONS, GRID }

    private var listMode = ListMode.SECTIONS

    private fun isGridMode(): Boolean = binding.categoriesRV.adapter === templatesAdapter

    private val sizeList = listOf(
        CanvasSize("Instagram Story", 1080f, 1920f),
        CanvasSize("Instagram Post", 1080f, 1080f),
        CanvasSize("YouTube Thumbnail", 1280f, 720f),
        CanvasSize("Facebook Cover", 820f, 312f),
        CanvasSize("YouTube Channel Art", 2560f, 1440f),
        CanvasSize("A4", 2480f, 3508f),
        CanvasSize("Letter", 2550f, 3300f),
        CanvasSize("Poster", 3600f, 5400f),
        CanvasSize("Business Card", 1050f, 600f),
        CanvasSize("Billboard", 1920f, 1080f),
        CanvasSize("Vertical Banner", 1080f, 1920f),
        CanvasSize("Horizontal Banner", 1920f, 600f),
        CanvasSize("Flyer", 2550f, 3300f),
        CanvasSize("Resume", 2480f, 3508f),
        CanvasSize("Invitation", 1500f, 2100f)
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
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

        loadingDialog = AlertDialog.Builder(requireActivity())
            .setView(dialogBinding!!.root)
            .setCancelable(false)
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

        canvasSizeAdapter = CanvasSizeAdapter(sizeList, onClick = { selected ->
            val newSize = if (filtersVM.filters.value.size?.name == selected.name) null else selected
            filtersVM.setSize(newSize)
            canvasSizeAdapter.selectedSizeName = newSize?.name ?: ""

        }, false)
        binding.sizesRV.adapter = canvasSizeAdapter

        categoryAdapter = TemplateCategoriesAdapter(
            onSeeAll = { category ->
                val args = Bundle().apply { putString("TAB_NAME", category) }
                findNavController().navigate(R.id.templatesListFragment, args)
            },
            onTemplateClick = { template, bool ->
                if (bool) {
                    val exportResult = template.toExportResultFinal()
                    lifecycleScope.launch {
                        withContext(Dispatchers.Default) {
                            viewModel.loadTemplateFromJsonFile(exportResult, requireContext())
                            bundle = Bundle().apply {
                                putSerializable("canvas_size", exportResult.canvasSize)
                                putSerializable("unit_type", UnitType.PIXELS)
                            }
                        }
                    }
                    return@TemplateCategoriesAdapter
                }
                downloadingTemplate = template
                categoryAdapter.updateTemplateProgress(
                    templateId = template.id,
                    progress = 0,
                    isDownloading = true,
                    isDownloaded = false
                )
                mainViewModel.downloadTemplate(template)
            }
        )
        binding.categoriesRV.adapter = categoryAdapter

        templatesAdapter = TemplatesAdapter { template, isDownloaded ->
            if (isDownloaded) {
                val exportResult = template.toExportResultFinal()
                lifecycleScope.launch {
                    withContext(Dispatchers.Default) {
                        viewModel.loadTemplateFromJsonFile(exportResult, requireContext())
                        bundle = Bundle().apply {
                            putSerializable("canvas_size", exportResult.canvasSize)
                            putSerializable("unit_type", UnitType.PIXELS)
                        }
                    }
                }
                return@TemplatesAdapter
            }

            downloadingTemplate = template
            templatesAdapter.updateProgress(
                template.id,
                ProgressUi(
                    progress = 0,
                    isDownloading = true,
                    isDownloaded = false
                )
            )
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
        if (!::sglm.isInitialized) {
            sglm = StaggeredGridLayoutManager(2, RecyclerView.VERTICAL).apply {
                gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }
        }
        val rv = binding.categoriesRV
        if (rv.adapter !== templatesAdapter) {
            rv.layoutManager = sglm
            rv.adapter = templatesAdapter
            rv.itemAnimator = null
            rv.isNestedScrollingEnabled = false

            val pad = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._18sdp)
            rv.setPadding(pad, rv.paddingTop, pad, rv.paddingBottom)
            rv.clipToPadding = false
        }
    }

    private fun rebalanceSpans() {
        binding.categoriesRV.post {
            (binding.categoriesRV.layoutManager as? StaggeredGridLayoutManager)
                ?.invalidateSpanAssignments()
        }
    }

    private fun updateCategoriesFromData(list: List<TemplateEntity>) {
        val cats = buildList {
            add("All")
            addAll(
                list.map { it.category?.trim() ?: "Unknown" }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .sorted()
            )
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
            (0 until cg.childCount)
                .map { cg.getChildAt(it) as Chip }
                .firstOrNull { it.text.toString().equals("All", true) }
                ?.isChecked = true
        }
    }

    private fun findChipByText(group: ViewGroup, text: String): Chip? =
        (0 until group.childCount)
            .mapNotNull { group.getChildAt(it) as? Chip }
            .firstOrNull { it.text.toString().equals(text, true) }

    private fun setupHeaderUi() {
        binding.searchBar.doAfterTextChanged {
            val newQ = it?.toString().orEmpty()
            if (newQ != filtersVM.filters.value.query) {
                filtersVM.setQuery(newQ) // triggers collector → applyFilters()
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
                panel.animate()
                    .translationY(0f)
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            }
        } else {
            // hide: slide back up behind the bar
            panel.animate()
                .translationY(-panel.height.toFloat())
                .alpha(0f)
                .setDuration(180)
                .withEndAction {
                    panel.isGone = true
                }
                .start()
        }
        filterPanelVisible = !filterPanelVisible
    }

    private fun filterTemplates(
        source: List<TemplateEntity>,
        category: String,
        query: String,
        size: CanvasSize?
    ): List<TemplateEntity> {
        val byCat = if (category.equals("All", true)) {
            source
        } else {
            source.filter { it.category.equals(category, true) }
        }

        val q = query.trim().lowercase()
        val byQuery = if (q.isBlank()) byCat else byCat.filter { it.matchesQuery(q) }

        return size?.let { s -> byQuery.filter { it.matchesSize(s) } } ?: byQuery
    }

    private fun TemplateEntity.matchesSize(s: CanvasSize): Boolean {
        // round CanvasSize floats to ints
        val sw = s.width.roundToInt()
        val sh = s.height.roundToInt()

        val iw = canvas_width
        val ih = canvas_height

        // match either orientation
        return (iw == sw && ih == sh)
    }

    private fun TemplateEntity.matchesQuery(q: String): Boolean {
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
            val rows = filtered
                .groupBy { it.category.takeIf { it!!.isNotBlank() } ?: "Others" }
                .map { (cat, list) -> HomeRow.CategoryRow(cat, list.distinctBy { it.id }.take(10)) }

            categoryAdapter.submitList(rows)
        } else {
            listMode = ListMode.GRID
            switchToGrid()

            val filtered = filterTemplates(allTemplates, activeCategory, activeQuery, activeSize)
            submitGridPreservingOrder(filtered)
            rebalanceSpans()
        }
    }

    private fun submitGridPreservingOrder(filtered: List<TemplateEntity>) {
        val current = templatesAdapter.currentList
        if (current.isEmpty()) {
            templatesAdapter.submitList(filtered)
            return
        }
        val byId = filtered.associateBy { it.id }
        val merged = buildList {
            current.forEach { cur -> byId[cur.id]?.let { add(it) } } // keep visible order
            filtered.forEach { f -> if (current.none { it.id == f.id }) add(f) } // append new
        }
        templatesAdapter.submitList(merged)
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

                    // When categories list is (re)rendered, chips will be checked using activeCategory
                    // Apply filters to the list now
                    applyFilters()

                    // If category changed, also switch list mode (sections/grid) inside applyFilters()
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

        viewModel.isLoadingTemplate.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading == true) {
                showLoadingDialog()
            } else if (isLoading == false) {
                dismissLoadingDialog()
                findNavController().navigate(R.id.editorFragment, bundle)
            }
        }

        // download state
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.templateDownloadState.collect { state ->
                when (state) {
                    is TemplateDownloadState.Progress -> {
                        val t = state.template
                        if (isGridMode()) {
                            // Grid screen (TemplatesAdapter)
                            templatesAdapter.updateProgress(
                                t.id,
                                ProgressUi(
                                    progress = state.progress,
                                    isDownloading = true,
                                    isDownloaded = false
                                )
                            )
                            // optional: keep the grid tidy while heights change
                            rebalanceSpans()
                        } else {
                            // Sectioned screen (TemplateCategoriesAdapter)
                            categoryAdapter.updateTemplateProgress(
                                templateId = t.id,
                                progress = state.progress,
                                isDownloading = true,
                                isDownloaded = false
                            )
                        }
                    }

                    is TemplateDownloadState.SuccessWithTemplate -> {
                        val t = state.template
                        if (isGridMode()) {
                            templatesAdapter.updateProgress(
                                t.id,
                                ProgressUi(
                                    progress = 100,
                                    isDownloading = false,
                                    isDownloaded = true
                                )
                            )
                            rebalanceSpans()
                        } else {
                            categoryAdapter.updateTemplateProgress(
                                templateId = t.id,
                                progress = 100,
                                isDownloading = false,
                                isDownloaded = true,
                                filePath = t.file_path
                            )
                        }
                        mainViewModel.clearTemplateDownloadState()

                        showGlobalSuccessSnack("Template ready") {
                            val exportResult = t.toExportResultFinal()
                            lifecycleScope.launch {
                                withContext(Dispatchers.Default) {
                                    viewModel.loadTemplateFromJsonFile(
                                        exportResult,
                                        requireContext()
                                    )
                                    bundle = Bundle().apply {
                                        putSerializable("canvas_size", exportResult.canvasSize)
                                        putSerializable("unit_type", UnitType.PIXELS)
                                    }
                                }
                            }
                        }
                    }

                    is TemplateDownloadState.Error -> {
                        val t = downloadingTemplate ?: return@collect
                        if (isGridMode()) {
                            templatesAdapter.updateProgress(
                                t.id,
                                ProgressUi(
                                    progress = 0,
                                    isDownloading = false,
                                    isDownloaded = false
                                )
                            )
                        } else {
                            categoryAdapter.updateTemplateProgress(
                                templateId = t.id,
                                progress = 0,
                                isDownloading = false,
                                isDownloaded = false
                            )
                        }
                        downloadingTemplate = null
                    }

                    is TemplateDownloadState.Success -> {
                        mainViewModel.clearTemplateDownloadState()
                    }

                    null -> Unit
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}