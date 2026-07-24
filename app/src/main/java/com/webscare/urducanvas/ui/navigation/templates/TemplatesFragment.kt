package com.webscare.urducanvas.ui.navigation.templates

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.webscare.urducanvas.common.canvas.enums.TemplatesViewState
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.chip.Chip
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.SectionStatus
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.common.canvas.sealed.HomeRow
import com.webscare.urducanvas.common.canvas.sealed.TemplateDownloadState
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.utils.showGlobalSuccessSnack
import com.webscare.urducanvas.data.model.TemplateEntity
import com.webscare.urducanvas.data.model.toExportResultFinal
import com.webscare.urducanvas.databinding.DialogLoadingProgressBinding
import com.webscare.urducanvas.databinding.FragmentTemplatesBinding
import com.webscare.urducanvas.ui.creation.CanvasSizeAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@AndroidEntryPoint
class TemplatesFragment : androidx.fragment.app.Fragment() {

    private var _binding: FragmentTemplatesBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()

    private var currentCategory: String? = null
    private lateinit var subcategoryAdapter: TemplateCategoriesAdapter
    private lateinit var canvasSizeAdapter: CanvasSizeAdapter
    private lateinit var templatesAdapter: TemplatesAdapter
    private lateinit var wrappedSubcategoryAdapter: RecyclerView.Adapter<*>
    private lateinit var wrappedTemplatesAdapter: RecyclerView.Adapter<*>

    private var downloadingTemplate: TemplateEntity? = null
    private var bundle: Bundle = Bundle()
    private var loadingDialog: AlertDialog? = null
    private var dialogBinding: DialogLoadingProgressBinding? = null

    private var categoryTemplates: List<TemplateEntity> = emptyList()
    private var activeSubcategory: String = "All"
    private var activeQuery: String = ""
    private var activeSize: CanvasSize? = null
    private var activePrice: String = "All"
    private var trendName: String? = null
    private var filterPanelVisible = false
    private var suppressChipClicks = false
    private var suppressPriceChipClicks = false

    private enum class ListMode { SECTIONS, GRID }
    private var listMode = ListMode.SECTIONS
    private fun isGridMode(): Boolean = _binding?.categoriesRV?.adapter === wrappedTemplatesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        trendName = arguments?.getString("TREND_NAME")
        currentCategory = arguments?.getString("CATEGORY_NAME")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTemplatesBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        setupPriceChips()
        setEvents()
        observeData()
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

//    private fun setupPriceChips() {
//        val cg = binding.priceChipGroup
//        cg.removeAllViews()
//        listOf("All", "Free", "Premium").forEach { label ->
//            val chip = layoutInflater.inflate(R.layout.chip_filter_item, cg, false) as Chip
//            chip.id = View.generateViewId()
//            chip.text = label
//            chip.isCheckable = true
//            chip.isChecked = label.equals(activePrice, true)
//            cg.addView(chip)
//
//            chip.addPressEffect {
//                if (suppressPriceChipClicks) return@addPressEffect
//                val clicked = chip.text.toString()
//                if (chip.isChecked && !clicked.equals("All", true)) {
//                    suppressPriceChipClicks = true
//                    cg.clearCheck()
//                    findChipByText(cg, "All")?.isChecked = true
//                    suppressPriceChipClicks = false
//                    activePrice = "All"
//                } else {
//                    suppressPriceChipClicks = true
//                    cg.clearCheck(); chip.isChecked = true
//                    suppressPriceChipClicks = false
//                    activePrice = clicked
//                }
//                applyFilters()
//                if (filterPanelVisible) toggleFilterPanel()
//            }
//        }
//    }

    // ─── Setup ───────────────────────────────────────────────────────────────

    private fun setEvents() {
        setupHeaderUi()
        setupSizeAdapter()
        setupSubcategoryAdapter()
        setupTemplatesAdapter()
        switchToSections()
    }

    private fun setupHeaderUi() {
        binding.title.text = when {
            !currentCategory.isNullOrBlank() -> currentCategory
            !trendName.isNullOrBlank()       -> trendName
            else                                -> currentCategory ?: "Templates"
        }

        binding.back.addPressEffect { findNavController().navigateUp() }
        binding.filters.addPressEffect { toggleFilterPanel() }
        binding.searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                activeQuery = binding.searchBar.text.toString()
                applyFilters()
                binding.searchBar.clearFocus()
                (requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(binding.searchBar.windowToken, 0)
                true
            } else false
        }

        binding.searchBar.doAfterTextChanged { text ->
            if (text.isNullOrEmpty()) {
                activeQuery = ""
                applyFilters()
            }
        }
    }

    private fun setupSizeAdapter() {
        canvasSizeAdapter = com.webscare.urducanvas.ui.creation.CanvasSizeAdapter(emptyList(), onClick = { selected ->
            activeSize = if (activeSize?.name == selected.name) null else selected
            canvasSizeAdapter.selectedSizeName = activeSize?.name ?: ""
            applyFilters()
        }, false)
        binding.sizesRV.adapter = canvasSizeAdapter
    }

    private fun setupSubcategoryAdapter() {
        subcategoryAdapter = TemplateCategoriesAdapter(
            onSeeAll = { subcategory ->
                val args = Bundle().apply {
                    putString("TAB_NAME", currentCategory)
                    putString("SUBCATEGORY_NAME", subcategory)
                    putString("TREND_NAME", trendName)
                }
                view?.post { findNavController().navigate(R.id.templatesListFragment, args) }
            },
            onTemplateClick = { template, isDownloaded ->
                if (template.is_downloading) return@TemplateCategoriesAdapter
                if (!isDownloaded) {
                    if (template.file_path.isNullOrEmpty()) {
                        downloadingTemplate = template
                        subcategoryAdapter.updateTemplateProgress(template.id, 0, true, false)
                        mainViewModel.downloadTemplate(template)
                        return@TemplateCategoriesAdapter
                    }
                } else {
                    val exportResult = template.toExportResultFinal()
                    lifecycleScope.launch { withContext(Dispatchers.Default) { viewModel.loadTemplateFromJsonFile(exportResult, requireContext()) } }
                }
            }
        )
        wrappedSubcategoryAdapter = com.webscare.ads.WebsCareAds.wrapWithNativeAds(
            originalAdapter = subcategoryAdapter,
            adUnitId = com.webscare.urducanvas.BuildConfig.AD_NATIVE_CATEGORIES,
            interval = 4,
            startOffset = 2,
            nativeSize = com.webscare.ads.NativeSize.SMALL
        )
        binding.categoriesRV.adapter = wrappedSubcategoryAdapter
    }

    private fun setupTemplatesAdapter() {
        templatesAdapter = TemplatesAdapter { template, isDownloaded ->
            if (isDownloaded) {
                val exportResult = template.toExportResultFinal()
                lifecycleScope.launch { withContext(Dispatchers.Default) { viewModel.loadTemplateFromJsonFile(exportResult, requireContext()) } }
                return@TemplatesAdapter
            }
            downloadingTemplate = template
            mainViewModel.downloadTemplate(template)
        }
        wrappedTemplatesAdapter = com.webscare.ads.WebsCareAds.wrapWithNativeAds(
            originalAdapter = templatesAdapter,
            adUnitId = com.webscare.urducanvas.BuildConfig.AD_NATIVE_TEMPLATES,
            interval = 6,
            startOffset = 3,
            nativeSize = com.webscare.ads.NativeSize.SMALL
        )
    }

    // ─── Layout Switching ─────────────────────────────────────────────────────

    private fun switchToSections() {
        val rv = binding.categoriesRV
        if (rv.adapter !== wrappedSubcategoryAdapter || rv.layoutManager !is com.webscare.urducanvas.common.views.SafeLinearLayoutManager) {
            rv.layoutManager = com.webscare.urducanvas.common.views.SafeLinearLayoutManager(requireContext())
            rv.adapter = wrappedSubcategoryAdapter; rv.itemAnimator = null
            rv.isNestedScrollingEnabled = true
            rv.setPadding(0, rv.paddingTop, 0, rv.paddingBottom); rv.clipToPadding = false
        }
    }

    private fun switchToGrid() {
        val rv = binding.categoriesRV
        if (rv.layoutManager !is com.webscare.urducanvas.common.views.SafeStaggeredGridLayoutManager)
            rv.layoutManager = com.webscare.urducanvas.common.views.SafeStaggeredGridLayoutManager(2, RecyclerView.VERTICAL).apply {
                gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }
        if (rv.adapter !== wrappedTemplatesAdapter) rv.adapter = wrappedTemplatesAdapter
        rv.itemAnimator = null; rv.isNestedScrollingEnabled = false
        val pad = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._18sdp)
        rv.setPadding(pad, rv.paddingTop, pad, rv.paddingBottom); rv.clipToPadding = false
    }

    private fun rebalanceSpans() {
        binding.categoriesRV.post {
            (binding.categoriesRV.layoutManager as? StaggeredGridLayoutManager)?.invalidateSpanAssignments()
        }
    }

    // ─── Subcategory Chips ────────────────────────────────────────────────────

    private fun buildSubcategoryChips(templates: List<TemplateEntity>) {
        val subcats = buildList {
            add("All")
            addAll(templates.mapNotNull { it.subcategory?.trim()?.takeIf { s -> s.isNotEmpty() } }.distinct().sorted())
        }
        renderSubcategoryChips(subcats)
    }

    private fun renderSubcategoryChips(subcats: List<String>) {
        // categoryChipGroup is the inner ChipGroup inside the LinearLayout panel
        val cg = binding.categoryChipGroup
        cg.isSingleSelection = true; cg.isSelectionRequired = false
        cg.removeAllViews()

        subcats.forEach { label ->
            val chip = layoutInflater.inflate(R.layout.chip_filter_item, cg, false) as Chip
            chip.id = View.generateViewId(); chip.text = label
            chip.isCheckable = true; chip.isChecked = label.equals(activeSubcategory, true)
            cg.addView(chip)

            chip.addPressEffect {
                if (suppressChipClicks) return@addPressEffect
                val clickedText = chip.text.toString()
                if (chip.isChecked && !clickedText.equals("All", true)) {
                    findChipByText(cg, "All")?.let {
                        suppressChipClicks = true; cg.clearCheck(); it.isChecked = true
                        suppressChipClicks = false; activeSubcategory = "All"
                    }
                } else {
                    suppressChipClicks = true; cg.clearCheck(); chip.isChecked = true
                    suppressChipClicks = false; activeSubcategory = clickedText
                }
                applyFilters()
                if (filterPanelVisible) toggleFilterPanel()
            }
        }
    }

    private fun findChipByText(group: ViewGroup, text: String): Chip? =
        (0 until group.childCount).mapNotNull { group.getChildAt(it) as? Chip }
            .firstOrNull { it.text.toString().equals(text, true) }

    // ─── Filter Panel ─────────────────────────────────────────────────────────

    private fun toggleFilterPanel() {
        val panel   = binding.categoryChips
        val bar     = binding.searchBar
        val overlay = binding.topFadeOverlay
        val root    = binding.root

        if (!filterPanelVisible) {
            // searchBar and its sibling filters button must sit ABOVE the panel
            bar.bringToFront()
            binding.filters.bringToFront()
            bar.translationZ     = resources.getDimension(com.intuit.sdp.R.dimen._2sdp)
            binding.filters.translationZ = resources.getDimension(com.intuit.sdp.R.dimen._2sdp)
            // gradient overlay must NOT cover the chips panel
            overlay.translationZ = 0f
            panel.translationZ   = 1f

            root.clipChildren  = false
            root.clipToPadding = false

            panel.isVisible = true
            panel.doOnPreDraw {
                // panel.top is its natural Y position (below searchBar).
                // We want the panel to start with its BOTTOM edge sitting exactly
                // at bar.bottom — i.e. fully tucked behind the search bar.
                // Offset needed = -(panel.top - bar.bottom + panel.height)
                val startY = -(panel.top - bar.bottom + panel.height).toFloat()
                panel.translationY = startY
                panel.animate()
                    .translationY(0f)
                    .setDuration(300)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(2.2f))
                    .start()
            }
        } else {
            panel.doOnPreDraw {
                val endY = -(panel.top - bar.bottom + panel.height).toFloat()
                panel.animate()
                    .translationY(endY)
                    .setDuration(240)
                    .setInterpolator(android.view.animation.AccelerateInterpolator(2f))
                    .withEndAction {
                        panel.isGone      = true
                        panel.translationY = 0f
                        overlay.translationZ = resources.getDimension(com.intuit.sdp.R.dimen._2sdp)
                        root.clipChildren  = true
                        root.clipToPadding = true
                    }
                    .start()
            }
        }
        filterPanelVisible = !filterPanelVisible
    }

    // ─── Filter Logic ─────────────────────────────────────────────────────────

    private fun applyFilters() {
        val filtered = filterTemplates(categoryTemplates, activeSubcategory, activeQuery, activeSize, activePrice)

        if (activeSubcategory.equals("All", true) && activeQuery.isBlank()
            && activeSize == null && activePrice.equals("All", true)
        ) {
            listMode = ListMode.SECTIONS; switchToSections()
            val rows = filtered.groupBy { it.subcategory?.trim()?.ifEmpty { "Others" } ?: "Others" }
                .map { (title, templates) ->
                    HomeRow.CategoryRow(title, templates.distinctBy { it.id }.take(10))
                }
            subcategoryAdapter.submitList(rows) { updateScreenState(mainViewModel.templatesStatus.value) }
        } else {
            listMode = ListMode.GRID; switchToGrid()
            templatesAdapter.submitList(filtered) {
                rebalanceSpans()
                updateScreenState(mainViewModel.templatesStatus.value)
            }
        }
    }

    private fun filterTemplates(
        source: List<TemplateEntity>, subcategory: String, query: String, size: CanvasSize?, price: String
    ): List<TemplateEntity> {
        val bySub = if (subcategory.equals("All", true)) source
        else source.filter { it.subcategory?.trim().orEmpty().equals(subcategory, true) }
        val q = query.trim().lowercase()
        val byQuery = if (q.isBlank()) bySub else bySub.filter { it.matchesQuery(q) }
        val bySize = size?.let { s -> byQuery.filter { it.matchesSize(s) } } ?: byQuery
        return when (price) {
            "Free"    -> bySize.filter { !it.is_premium }
            "Premium" -> bySize.filter { it.is_premium }
            else      -> bySize
        }
    }

    private fun TemplateEntity.matchesQuery(q: String): Boolean {
        val h = buildString {
            append(category).append(' ').append(subcategory).append(' ')
            append(template_name).append(' ').append(canvas_width).append(' ')
            append(canvas_height).append(' ').append(tags.joinToString(" "))
        }.lowercase()
        return h.contains(q)
    }

    private fun TemplateEntity.matchesSize(s: CanvasSize) =
        canvas_width == s.width.roundToInt() && canvas_height == s.height.roundToInt()

    // ─── Data Observation ─────────────────────────────────────────────────────

    private fun observeData() {

        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                mainViewModel.templatesStatus,
                mainViewModel.localTemplates
            ) { status, _ -> status }.collect { status ->
                updateScreenState(status)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.localCanvasSizes.collect { entities ->
                if (entities.isEmpty()) return@collect
                val sizes = entities.map {
                    CanvasSize(id = it.id, name = it.name, width = it.width, height = it.height)
                }
                canvasSizeAdapter.submitList(sizes)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.localTemplates.collect { allTemplates ->
                categoryTemplates = when {
                    !trendName.isNullOrBlank() -> {
                        val ids = mainViewModel.trendRows.value
                            .filterIsInstance<HomeRow.TrendRow>()
                            .firstOrNull { it.title.equals(trendName, true) }
                            ?.templates?.map { it.id }?.toSet() ?: emptySet()
                        allTemplates.filter { it.id in ids }
                    }
                    !currentCategory.isNullOrBlank() ->
                        allTemplates.filter {
                            it.category?.trim().equals(currentCategory!!.trim(), true)
                        }
                    else -> allTemplates
                }
                buildSubcategoryChips(categoryTemplates)
                applyFilters()
                updateScreenState(mainViewModel.templatesStatus.value)
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
                            else subcategoryAdapter.updateTemplateProgress(state.template.id, state.progress, true, false)
                        }
                        is TemplateDownloadState.SuccessWithTemplate -> {
                            val t = state.template; mainViewModel.clearTemplateDownloadState()
                            val finalTemplate = t.copy(is_downloading = false, is_downloaded = true)
                            if (!isGridMode()) {
                                subcategoryAdapter.updateTemplateProgress(t.id, 100, false, true)
                                subcategoryAdapter.notifyTemplateStateChanged(finalTemplate)
                            }
                            showGlobalSuccessSnack("Template ready") {
                                val exportResult = t.toExportResultFinal()
                                lifecycleScope.launch { withContext(Dispatchers.Default) { viewModel.loadTemplateFromJsonFile(exportResult, requireContext()) } }
                            }
                            downloadingTemplate = null
                        }
                        is TemplateDownloadState.Error -> {
                            downloadingTemplate?.let {
                                subcategoryAdapter.updateTemplateProgress(it.id, 0, false, false)
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

    private fun renderState(state: TemplatesViewState) {
        android.util.Log.d("TemplatesState", "Rendering: $state")
        binding.apply {
            when (state) {
                TemplatesViewState.Loading -> {
                    loadingState.root.visibility = View.VISIBLE
                    emptyState.root.visibility = View.GONE
                    categoriesRV.visibility = View.GONE
                    topFadeOverlay.visibility = View.GONE
                }
                TemplatesViewState.Content -> {
                    loadingState.root.visibility = View.GONE
                    emptyState.root.visibility = View.GONE
                    categoriesRV.visibility = View.VISIBLE
                    topFadeOverlay.visibility = View.VISIBLE
                }
                TemplatesViewState.Empty -> {
                    loadingState.root.visibility = View.GONE
                    categoriesRV.visibility = View.GONE
                    topFadeOverlay.visibility = View.GONE
                    emptyState.root.visibility = View.VISIBLE
                    emptyState.errorIcon.setImageResource(R.drawable.ic_nothing_found)
                    emptyState.errorTitle.text = "No templates yet"
                    emptyState.errorMessage.text = "There are no templates in this category yet. Check back soon!"
                    emptyState.retryButton.visibility = View.GONE
                }
                TemplatesViewState.FilterEmpty -> {
                    loadingState.root.visibility = View.GONE
                    categoriesRV.visibility = View.GONE
                    topFadeOverlay.visibility = View.GONE
                    emptyState.root.visibility = View.VISIBLE
                    emptyState.errorIcon.setImageResource(R.drawable.ic_search)
                    emptyState.errorTitle.text = "No matches"
                    emptyState.errorMessage.text = "Try a different search or clear your filters"
                    emptyState.retryButton.visibility = View.VISIBLE
                    // Reuse retry button as "Clear filters"
                    (emptyState.retryButton.getChildAt(0) as? android.widget.TextView)?.text = "Clear filters"
                    emptyState.retryButton.addPressEffect {
                        activeQuery = ""
                        activeSize = null
                        activePrice = "All"
                        activeSubcategory = "All"
                        binding.searchBar.setText("")
                        canvasSizeAdapter.selectedSizeName = ""
                        canvasSizeAdapter.notifyDataSetChanged()
                        applyFilters()
                    }
                }
                TemplatesViewState.Error -> {
                    loadingState.root.visibility = View.GONE
                    categoriesRV.visibility = View.GONE
                    topFadeOverlay.visibility = View.GONE
                    emptyState.root.visibility = View.VISIBLE
                    emptyState.errorIcon.setImageResource(R.drawable.ic_no_internet)
                    emptyState.errorTitle.text = "Couldn't load templates"
                    emptyState.errorMessage.text = "Check your connection and try again"
                    emptyState.retryButton.visibility = View.VISIBLE
                    (emptyState.retryButton.getChildAt(0) as? android.widget.TextView)?.text = "Retry"
                    emptyState.retryButton.addPressEffect {
                        mainViewModel.retryTemplates()
                    }
                }
            }
        }
    }

    private fun updateScreenState(status: SectionStatus) {
        if (_binding == null) return
        val hasCategoryData = categoryTemplates.isNotEmpty()
        val hasFiltersApplied = activeQuery.isNotBlank()
                || activeSize != null
                || !activePrice.equals("All", true)
                || !activeSubcategory.equals("All", true)

        val state = when {
            hasCategoryData && currentVisibleCount() == 0 && hasFiltersApplied ->
                TemplatesViewState.FilterEmpty
            hasCategoryData ->
                TemplatesViewState.Content
            status == SectionStatus.Loading ->
                TemplatesViewState.Loading
            status == SectionStatus.Failed ->
                TemplatesViewState.Error
            else ->
                TemplatesViewState.Empty
        }
        android.util.Log.d("TemplatesState",
            "status=$status, categoryTemplates=${categoryTemplates.size}, " +
                    "visibleCount=${currentVisibleCount()}, filtersApplied=$hasFiltersApplied, " +
                    "→ state=$state")
        renderState(state)
    }

    private fun currentVisibleCount(): Int {
        return if (isGridMode()) templatesAdapter.itemCount
        else subcategoryAdapter.itemCount
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() { super.onDestroy() }
}