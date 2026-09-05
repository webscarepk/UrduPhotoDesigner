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
    private var subcategoriesList: List<String> = emptyList()
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



    // ─── Setup ───────────────────────────────────────────────────────────────

    private fun setEvents() {
        setupHeaderUi()
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
        binding.filters.addPressEffect {
            val sheet = FilterBottomSheetFragment.newInstance(
                chipTitle = "Subcategories",
                chips = subcategoriesList,
                selectedChip = activeSubcategory,
                selectedSizeName = activeSize?.name
            )
            sheet.onFilterApplied = { size, subcat ->
                activeSize = size
                activeSubcategory = subcat
                applyFilters()
            }
            sheet.onFilterCleared = {
                activeSize = null
                activeSubcategory = "All"
                applyFilters()
            }
            sheet.show(childFragmentManager, FilterBottomSheetFragment.TAG)
        }
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
                    viewModel.loadTemplateFromJsonFile(exportResult, requireContext(), titleHint = "Loading Template") { success ->
                        if (success && isAdded) {
                            findNavController().navigate(R.id.editorFragment)
                        }
                    }
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
                viewModel.loadTemplateFromJsonFile(exportResult, requireContext(), titleHint = "Loading Template") { success ->
                    if (success && isAdded) {
                        findNavController().navigate(R.id.editorFragment)
                    }
                }
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
        subcategoriesList = buildList {
            add("All")
            addAll(templates.mapNotNull { it.subcategory?.trim()?.takeIf { s -> s.isNotEmpty() } }.distinct().sorted())
        }
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
            else if (isLoading == false) dismissLoadingDialog()
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
                                viewModel.loadTemplateFromJsonFile(exportResult, requireContext()) { success ->
                                    if (success && isAdded) {
                                        findNavController().navigate(R.id.editorFragment)
                                    }
                                }
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
        _binding?.categoriesRV?.adapter = null
        loadingDialog?.dismiss()
        loadingDialog = null
        dialogBinding = null
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() { super.onDestroy() }
}