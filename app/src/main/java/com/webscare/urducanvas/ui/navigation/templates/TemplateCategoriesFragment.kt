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
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.chip.Chip
import com.webscare.urducanvas.MainActivity
import com.webscare.urducanvas.BuildConfig
import com.webscare.ads.WebsCareAds
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.CatViewState
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.common.canvas.sealed.HomeRow
import com.webscare.urducanvas.common.canvas.sealed.TemplateDownloadState
import com.webscare.urducanvas.common.utils.SpringEdgeEffectFactory
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
    private lateinit var wrappedCategoryAdapter: RecyclerView.Adapter<*>
    private lateinit var wrappedGridAdapter: RecyclerView.Adapter<*>
    private var categoriesList: List<String> = emptyList()
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

    private enum class ListMode { SECTIONS, GRID }
    private var listMode = ListMode.SECTIONS
    private fun isGridMode(): Boolean = binding.categoriesRV.adapter === wrappedGridAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTemplatesCategoriesBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        setupPriceChips()
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

    private fun renderState(state: CatViewState) {
        if (_binding == null) return
        binding.apply {
            when (state) {
                CatViewState.Loading -> {
                    loadingState.root.visibility = View.VISIBLE
                    emptyState.root.visibility = View.GONE
                    categoriesRV.visibility = View.GONE
                    topFadeOverlay.visibility = View.GONE
                }
                CatViewState.Content -> {
                    loadingState.root.visibility = View.GONE
                    emptyState.root.visibility = View.GONE
                    categoriesRV.visibility = View.VISIBLE
                    topFadeOverlay.visibility = View.VISIBLE
                }
                CatViewState.FilterEmpty -> {
                    loadingState.root.visibility = View.GONE
                    categoriesRV.visibility = View.GONE
                    topFadeOverlay.visibility = View.GONE
                    emptyState.root.visibility = View.VISIBLE
                    emptyState.errorIcon.setImageResource(R.drawable.ic_search)
                    emptyState.errorTitle.text = "No matches"
                    emptyState.errorMessage.text = "Try a different search or clear your filters"
                    emptyState.retryButton.visibility = View.VISIBLE
                    (emptyState.retryButton.getChildAt(0) as? android.widget.TextView)?.text = "Clear filters"
                    emptyState.retryButton.addPressEffect {
                        filtersVM.clearFilters()
                        binding.searchBar.setText("")
                    }
                }
                CatViewState.Error -> {
                    loadingState.root.visibility = View.GONE
                    categoriesRV.visibility = View.GONE
                    topFadeOverlay.visibility = View.GONE
                    emptyState.root.visibility = View.VISIBLE
                    emptyState.errorIcon.setImageResource(R.drawable.ic_no_internet)
                    emptyState.errorTitle.text = "Couldn't load templates"
                    emptyState.errorMessage.text = "Check your connection and try again"
                    emptyState.retryButton.visibility = View.VISIBLE
                    (emptyState.retryButton.getChildAt(0) as? android.widget.TextView)?.text = "Retry"
                    emptyState.retryButton.addPressEffect { mainViewModel.retryTemplates() }
                }
                CatViewState.Empty -> {
                    loadingState.root.visibility = View.GONE
                    categoriesRV.visibility = View.GONE
                    topFadeOverlay.visibility = View.GONE
                    emptyState.root.visibility = View.VISIBLE
                    emptyState.errorIcon.setImageResource(R.drawable.ic_nothing_found)
                    emptyState.errorTitle.text = "No templates yet"
                    emptyState.errorMessage.text = "Check back soon for new templates"
                    emptyState.retryButton.visibility = View.GONE
                }
            }
        }
    }

    private fun updateCatState() {
        if (_binding == null) return   // view already destroyed — discard stale callback
        val status = mainViewModel.templatesStatus.value
        val hasData = allTemplates.isNotEmpty()
        val hasFilters = !activeQuery.isBlank() || activeSize != null ||
                !activePrice.equals("All", true) || !activeCategory.equals("All", true)
        val visibleCount = if (isGridMode()) templatesAdapter.itemCount else categoryAdapter.itemCount

        val state = when {
            hasData && visibleCount == 0 && hasFilters -> CatViewState.FilterEmpty
            hasData -> CatViewState.Content
            status == com.webscare.urducanvas.common.canvas.enums.SectionStatus.Loading -> CatViewState.Loading
            status == com.webscare.urducanvas.common.canvas.enums.SectionStatus.Failed -> CatViewState.Error
            else -> CatViewState.Empty
        }
        renderState(state)
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss(); loadingDialog = null; dialogBinding = null
    }



    // ─── Setup ───────────────────────────────────────────────────────────────

    private fun setEvents() {
        val f0 = filtersVM.filters.value
        activeCategory = f0.category; activeQuery = f0.query; activeSize = f0.size
        binding.searchBar.setText(f0.query)
        setupHeaderUi()

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
                    viewModel.loadTemplateFromJsonFile(exportResult, requireContext()) { success ->
                        if (success && isAdded) {
                            findNavController().navigate(R.id.editorFragment)
                        }
                    }
                    return@TemplateCategoriesAdapter
                }
            }
        )
        
        wrappedCategoryAdapter = WebsCareAds.wrapWithNativeAds(
            originalAdapter = categoryAdapter,
            adUnitId = BuildConfig.AD_NATIVE_CATEGORIES,
            interval = 4,
            startOffset = 2,
            nativeSize = com.webscare.ads.NativeSize.SMALL
        )
        binding.categoriesRV.adapter = wrappedCategoryAdapter

        templatesAdapter = TemplatesAdapter { template, isDownloaded ->
            if (isDownloaded) {
                val exportResult = template.toExportResultFinal()
                viewModel.loadTemplateFromJsonFile(exportResult, requireContext()) { success ->
                    if (success && isAdded) {
                        findNavController().navigate(R.id.editorFragment)
                    }
                }
                return@TemplatesAdapter
            }
            downloadingTemplate = template
            mainViewModel.downloadTemplate(template)
        }
        
        wrappedGridAdapter = WebsCareAds.wrapWithNativeAds(
            originalAdapter = templatesAdapter,
            adUnitId = BuildConfig.AD_NATIVE_TEMPLATES,
            interval = 6,
            startOffset = 3,
            nativeSize = com.webscare.ads.NativeSize.SMALL
        )

        binding.categoriesRV.edgeEffectFactory = SpringEdgeEffectFactory()
        switchToSections()
    }

    // ─── Layout Switching ─────────────────────────────────────────────────────

    private fun switchToSections() {
        val rv = binding.categoriesRV
        if (rv.adapter !== wrappedCategoryAdapter || rv.layoutManager !is com.webscare.urducanvas.common.views.SafeLinearLayoutManager) {
            rv.layoutManager = com.webscare.urducanvas.common.views.SafeLinearLayoutManager(requireContext())
            rv.adapter = wrappedCategoryAdapter; rv.itemAnimator = null
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
        if (rv.adapter !== wrappedGridAdapter) rv.adapter = wrappedGridAdapter
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
        categoriesList = buildList {
            add("All")
            addAll(list.map { it.category?.trim() ?: "Unknown" }.filter { it.isNotEmpty() }.distinct().sorted())
        }
    }

    // ─── Header & Filter Panel ────────────────────────────────────────────────

    private fun setupHeaderUi() {
        binding.back.addPressEffect { findNavController().navigateUp() }

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

        binding.searchBar.doAfterTextChanged { text ->
            if (text.isNullOrEmpty()) filtersVM.setQuery("")
        }

        binding.filters.addPressEffect {
            val sheet = FilterBottomSheetFragment.newInstance(
                chipTitle = getString(R.string.categories),
                chips = categoriesList,
                selectedChip = filtersVM.filters.value.category,
                selectedSizeName = filtersVM.filters.value.size?.name
            )
            sheet.onFilterApplied = { size, cat ->
                filtersVM.setSize(size)
                filtersVM.setCategory(cat)
            }
            sheet.onFilterCleared = {
                filtersVM.setSize(null)
                filtersVM.setCategory("All")
            }
            sheet.show(childFragmentManager, FilterBottomSheetFragment.TAG)
        }
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
        if (_binding == null) return   // view already destroyed — discard stale callback
        val filtered = filterTemplates(allTemplates, activeCategory, activeQuery, activeSize, activePrice)

        if (activeCategory.equals("All", true)) {
            listMode = ListMode.SECTIONS; switchToSections()
            val rows = filtered.groupBy { it.category?.trim() ?: "Others" }
                .map { (k, v) -> HomeRow.CategoryRow(k.ifEmpty { "Others" }, v.distinctBy { it.id }.take(10)) }
            categoryAdapter.submitList(rows) { updateCatState() }
        } else {
            listMode = ListMode.GRID; switchToGrid()
            templatesAdapter.submitList(filtered) { rebalanceSpans(); updateCatState() }
        }
    }

    // ─── Observations ─────────────────────────────────────────────────────────

    private fun observeTemplateCategories() {


        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                filtersVM.filters.collect { f ->
                    activeCategory = f.category; activeQuery = f.query; activeSize = f.size
                    if (binding.searchBar.text?.toString() != f.query) binding.searchBar.setText(f.query)
                    applyFilters()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.templatesStatus.collect { updateCatState() }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.localTemplates.collect { templates ->
                allTemplates = templates
                updateCategoriesFromData(templates)
                applyFilters()
                updateCatState()
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
                            else categoryAdapter.updateTemplateProgress(state.template.id, state.progress, true, false)
                        }
                        is TemplateDownloadState.SuccessWithTemplate -> {
                            val t = state.template; mainViewModel.clearTemplateDownloadState()
                            if (!isGridMode()) { categoryAdapter.updateTemplateProgress(t.id, 100, false, true); categoryAdapter.notifyTemplateStateChanged(t.copy(is_downloading = false, is_downloaded = true)) }
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