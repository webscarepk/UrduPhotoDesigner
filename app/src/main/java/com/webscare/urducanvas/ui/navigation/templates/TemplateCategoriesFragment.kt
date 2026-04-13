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
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.common.canvas.sealed.HomeRow
import com.webscare.urducanvas.common.canvas.sealed.TemplateDownloadState
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.utils.showGlobalSuccessSnack
import com.webscare.urducanvas.data.model.TemplateEntity
import com.webscare.urducanvas.data.model.toExportResultFinal
import com.webscare.urducanvas.databinding.DialogLoadingProgressBinding
import com.webscare.urducanvas.databinding.FragmentTemplatesCategoriesBinding
import com.webscare.urducanvas.ui.creation.CanvasSizeAdapter
import com.webscare.urducanvas.viewmodels.FiltersViewModel
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@AndroidEntryPoint
class TemplateCategoriesFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentTemplatesCategoriesBinding? = null
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
    private lateinit var templatesAdapter: TemplatesAdapter
    private var allTemplates: List<TemplateEntity> = emptyList()

    private var activeCategory: String = "All"
    private var activeQuery: String = ""
    private var activeSize: CanvasSize? = null
    private var activePrice: String = "All"

    private var filterPanelVisible = false
    private var suppressChipClicks = false
    private var suppressPriceChipClicks = false

    private enum class ListMode { SECTIONS, GRID }
    private var listMode = ListMode.SECTIONS
    private fun isGridMode(): Boolean = binding.categoriesRV.adapter === templatesAdapter

    private val sizeList = listOf(
        CanvasSize("Instagram Story", 1080f, 1920f), CanvasSize("Instagram Post", 1080f, 1080f),
        CanvasSize("YouTube Thumbnail", 1280f, 720f), CanvasSize("Facebook Cover", 820f, 312f),
        CanvasSize("YouTube Channel Art", 2560f, 1440f), CanvasSize("A4", 2480f, 3508f),
        CanvasSize("Letter", 2550f, 3300f), CanvasSize("Poster", 3600f, 5400f),
        CanvasSize("Business Card", 1050f, 600f), CanvasSize("Billboard", 1920f, 1080f),
        CanvasSize("Vertical Banner", 1080f, 1920f), CanvasSize("Horizontal Banner", 1920f, 600f),
        CanvasSize("Flyer", 2550f, 3300f), CanvasSize("Resume", 2480f, 3508f),
        CanvasSize("Invitation", 1500f, 2100f), CanvasSize("Logo", 800f, 800f)
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTemplatesCategoriesBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPriceChips()
        setEvents()
        observeTemplateCategories()
    }

    // ─── Loading Dialog ───────────────────────────────────────────────────────

    private fun showLoadingDialog() {
        dialogBinding = DialogLoadingProgressBinding.inflate(LayoutInflater.from(requireActivity()))
        loadingDialog = AlertDialog.Builder(requireActivity()).setView(dialogBinding!!.root)
            .setCancelable(true).setOnCancelListener { viewModel.clearLoading() }.create()
        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog?.show()
        dialogBinding?.title?.text = "Loading Template"
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss(); loadingDialog = null; dialogBinding = null
    }

    // ─── Price Chips (static — set up once) ──────────────────────────────────

    private fun setupPriceChips() {
        val cg = binding.priceChipGroup
        cg.removeAllViews()
        listOf("All", "Free", "Premium").forEach { label ->
            val chip = layoutInflater.inflate(R.layout.chip_filter_item, cg, false) as Chip
            chip.id = View.generateViewId()
            chip.text = label
            chip.isCheckable = true
            chip.isChecked = label.equals(activePrice, true)
            cg.addView(chip)

            chip.addPressEffect {
                if (suppressPriceChipClicks) return@addPressEffect
                val clicked = chip.text.toString()
                if (chip.isChecked && !clicked.equals("All", true)) {
                    suppressPriceChipClicks = true
                    cg.clearCheck()
                    findChipByText(cg, "All")?.isChecked = true
                    suppressPriceChipClicks = false
                    activePrice = "All"
                } else {
                    suppressPriceChipClicks = true
                    cg.clearCheck(); chip.isChecked = true
                    suppressPriceChipClicks = false
                    activePrice = clicked
                }
                applyFilters()
                if (filterPanelVisible) toggleFilterPanel()
            }
        }
    }

    // ─── Setup ───────────────────────────────────────────────────────────────

    private fun setEvents() {
        val f0 = filtersVM.filters.value
        activeCategory = f0.category; activeQuery = f0.query; activeSize = f0.size
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
                val args = Bundle().apply { putString("CATEGORY_NAME", category) }
                view?.post { findNavController().navigate(R.id.templatesFragment, args) }
            },
            onTemplateClick = { template, bool ->
                if (template.is_downloading) return@TemplateCategoriesAdapter
                if (!bool) {
                    if (template.file_path.isNullOrEmpty()) {
                        downloadingTemplate = template
                        categoryAdapter.updateTemplateProgress(template.id, 0, true, false)
                        mainViewModel.downloadTemplate(template)
                        return@TemplateCategoriesAdapter
                    }
                } else {
                    val exportResult = template.toExportResultFinal()
                    lifecycleScope.launch { withContext(Dispatchers.Default) { viewModel.loadTemplateFromJsonFile(exportResult, requireContext()) } }
                    return@TemplateCategoriesAdapter
                }
            }
        )
        binding.categoriesRV.adapter = categoryAdapter

        templatesAdapter = TemplatesAdapter { template, isDownloaded ->
            if (isDownloaded) {
                val exportResult = template.toExportResultFinal()
                lifecycleScope.launch { withContext(Dispatchers.Default) { viewModel.loadTemplateFromJsonFile(exportResult, requireContext()) } }
                return@TemplatesAdapter
            }
            downloadingTemplate = template
            mainViewModel.downloadTemplate(template)
        }

        switchToSections()
    }

    // ─── Layout Switching ─────────────────────────────────────────────────────

    private fun switchToSections() {
        val rv = binding.categoriesRV
        if (rv.adapter !== categoryAdapter) {
            rv.layoutManager = LinearLayoutManager(requireContext())
            rv.adapter = categoryAdapter; rv.itemAnimator = null
            rv.isNestedScrollingEnabled = true
            rv.setPadding(0, rv.paddingTop, 0, rv.paddingBottom); rv.clipToPadding = false
        }
    }

    private fun switchToGrid() {
        val rv = binding.categoriesRV
        if (rv.layoutManager !is StaggeredGridLayoutManager)
            rv.layoutManager = StaggeredGridLayoutManager(2, RecyclerView.VERTICAL).apply {
                gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }
        if (rv.adapter !== templatesAdapter) rv.adapter = templatesAdapter
        rv.itemAnimator = null; rv.isNestedScrollingEnabled = false
        val pad = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._18sdp)
        rv.setPadding(pad, rv.paddingTop, pad, rv.paddingBottom); rv.clipToPadding = false
    }

    private fun rebalanceSpans() {
        binding.categoriesRV.post {
            (binding.categoriesRV.layoutManager as? StaggeredGridLayoutManager)?.invalidateSpanAssignments()
        }
    }

    // ─── Category Chips ───────────────────────────────────────────────────────

    private fun updateCategoriesFromData(list: List<TemplateEntity>) {
        val cats = buildList {
            add("All")
            addAll(list.map { it.category?.trim() ?: "Unknown" }.filter { it.isNotEmpty() }.distinct().sorted())
        }
        renderCategoryChips(cats)
    }

    private fun renderCategoryChips(categories: List<String>) {
        val cg = binding.categoryChipGroup   // <-- inner ChipGroup, not the LinearLayout
        cg.isSingleSelection = true; cg.isSelectionRequired = false
        cg.removeAllViews()
        val selectedCat = filtersVM.filters.value.category

        categories.forEach { label ->
            val chip = layoutInflater.inflate(R.layout.chip_filter_item, cg, false) as Chip
            chip.id = View.generateViewId(); chip.text = label
            chip.isCheckable = true; chip.isChecked = label.equals(selectedCat, true)
            cg.addView(chip)

            chip.addPressEffect {
                if (suppressChipClicks) return@addPressEffect
                val clickedText = chip.text.toString()
                if (chip.isChecked) {
                    if (!clickedText.equals("All", true)) {
                        findChipByText(cg, "All")?.let {
                            suppressChipClicks = true; cg.clearCheck(); it.isChecked = true
                            suppressChipClicks = false; filtersVM.setCategory("All")
                        }
                    } else if (filterPanelVisible) toggleFilterPanel()
                } else {
                    suppressChipClicks = true; cg.clearCheck(); chip.isChecked = true
                    suppressChipClicks = false
                    if (clickedText != filtersVM.filters.value.category) filtersVM.setCategory(clickedText)
                    else if (filterPanelVisible) toggleFilterPanel()
                }
            }
        }

        if (!categories.any { it.equals(selectedCat, true) }) {
            filtersVM.setCategory("All")
            (0 until cg.childCount).map { cg.getChildAt(it) as Chip }
                .firstOrNull { it.text.toString().equals("All", true) }?.isChecked = true
        }
    }

    private fun findChipByText(group: ViewGroup, text: String): Chip? =
        (0 until group.childCount).mapNotNull { group.getChildAt(it) as? Chip }
            .firstOrNull { it.text.toString().equals(text, true) }

    // ─── Header & Filter Panel ────────────────────────────────────────────────

    private fun setupHeaderUi() {
        binding.searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                filtersVM.setQuery(binding.searchBar.text.toString())
                binding.searchBar.clearFocus()
                (requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(binding.searchBar.windowToken, 0)
                true
            } else false
        }
        binding.filters.addPressEffect { toggleFilterPanel() }
    }

    // binding.categoryChips is now the LinearLayout panel — toggle works identically
    private fun toggleFilterPanel() {
        val panel = binding.categoryChips
        val bar = binding.searchBar
        if (!filterPanelVisible) {
            panel.isVisible = true
            panel.doOnPreDraw {
                bar.bringToFront()
                bar.elevation = resources.getDimension(com.intuit.sdp.R.dimen._2sdp)
                panel.elevation = resources.getDimension(com.intuit.sdp.R.dimen._1sdp)
                panel.translationY = -panel.height.toFloat(); panel.alpha = 0f
                panel.animate().translationY(0f).alpha(1f).setDuration(200).start()
            }
        } else {
            panel.animate().translationY(-panel.height.toFloat()).alpha(0f).setDuration(180)
                .withEndAction { panel.isGone = true }.start()
        }
        filterPanelVisible = !filterPanelVisible
    }

    // ─── Filter Logic ─────────────────────────────────────────────────────────

    private fun filterTemplates(source: List<TemplateEntity>, category: String, query: String, size: CanvasSize?, price: String): List<TemplateEntity> {
        val byCat = if (category.equals("All", true)) source else source.filter { it.category.equals(category, true) }
        val q = query.trim().lowercase()
        val byQuery = if (q.isBlank()) byCat else byCat.filter { it.matchesQuery(q) }
        val bySize = size?.let { s -> byQuery.filter { it.matchesSize(s) } } ?: byQuery
        return when (price) {
            "Free"    -> bySize.filter { !it.is_premium }
            "Premium" -> bySize.filter { it.is_premium }
            else      -> bySize
        }
    }

    private fun TemplateEntity.matchesSize(s: CanvasSize) =
        canvas_width == s.width.roundToInt() && canvas_height == s.height.roundToInt()

    private fun TemplateEntity.matchesQuery(q: String): Boolean {
        val h = buildString {
            append(category).append(' ').append(template_name).append(' ')
            append(subcategory).append(' ').append(canvas_height).append(' ')
            append(canvas_width).append(' ').append(tags.joinToString(" "))
        }.lowercase()
        return h.contains(q)
    }

    private fun applyFilters() {
        if (activeCategory.equals("All", true)) {
            listMode = ListMode.SECTIONS; switchToSections()
            val rows = filterTemplates(allTemplates, "All", activeQuery, activeSize, activePrice)
                .groupBy { it.category?.trim() ?: "Others" }
                .map { (k, v) -> HomeRow.CategoryRow(k.ifEmpty { "Others" }, v.distinctBy { it.id }.take(10)) }
            categoryAdapter.submitList(rows)
        } else {
            listMode = ListMode.GRID; switchToGrid()
            templatesAdapter.submitList(filterTemplates(allTemplates, activeCategory, activeQuery, activeSize, activePrice))
            rebalanceSpans()
        }
    }

    // ─── Observations ─────────────────────────────────────────────────────────

    private fun observeTemplateCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                filtersVM.filters.collect { f ->
                    activeCategory = f.category; activeQuery = f.query; activeSize = f.size
                    if (binding.searchBar.text?.toString() != f.query) binding.searchBar.setText(f.query)
                    if (::canvasSizeAdapter.isInitialized) {
                        val want = f.size?.name ?: ""
                        if (canvasSizeAdapter.selectedSizeName != want) {
                            canvasSizeAdapter.selectedSizeName = want; canvasSizeAdapter.notifyDataSetChanged()
                        }
                    }
                    applyFilters()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.localTemplates.collect { templates ->
                allTemplates = templates; updateCategoriesFromData(templates); applyFilters()
            }
        }
        viewModel.loadingStage.observe(viewLifecycleOwner) { (msg, pct) ->
            dialogBinding?.apply { progressBar.progress = pct; subtitle.text = "$msg... $pct%"; tvProgressPercent.text = "$pct% complete" }
        }
        viewModel.isLoadingTemplate.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading == true) showLoadingDialog()
            else if (isLoading == false) { dismissLoadingDialog(); view?.post { findNavController().navigate(R.id.editorFragment, bundle) } }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.templateDownloadStates.collect { ds ->
                ds.values.forEach { state ->
                    when (state) {
                        is TemplateDownloadState.Progress -> {
                            if (isGridMode()) templatesAdapter.updateProgress(state.template.id,
                                com.webscare.urducanvas.data.model.ProgressUi(state.progress, true, false))
                            else categoryAdapter.updateTemplateProgress(state.template.id, state.progress, true, false)
                        }
                        is TemplateDownloadState.SuccessWithTemplate -> {
                            val t = state.template; mainViewModel.clearTemplateDownloadState()
                            if (!isGridMode()) { categoryAdapter.updateTemplateProgress(t.id, 100, false, true); categoryAdapter.notifyTemplateStateChanged(t.copy(is_downloading = false, is_downloaded = true)) }
                            showGlobalSuccessSnack("Template ready") {
                                val exportResult = t.toExportResultFinal()
                                lifecycleScope.launch { withContext(Dispatchers.Default) { viewModel.loadTemplateFromJsonFile(exportResult, requireContext()) } }
                            }
                            downloadingTemplate = null
                        }
                        is TemplateDownloadState.Error -> {
                            downloadingTemplate?.let {
                                categoryAdapter.updateTemplateProgress(it.id, 0, false, false)
                                templatesAdapter.updateProgress(it.id, com.webscare.urducanvas.data.model.ProgressUi(0, false, false))
                                downloadingTemplate = null
                            }
                        }
                        is TemplateDownloadState.Success -> mainViewModel.clearTemplateDownloadState()
                        null -> Unit
                    }
                }
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); _binding = null }
}