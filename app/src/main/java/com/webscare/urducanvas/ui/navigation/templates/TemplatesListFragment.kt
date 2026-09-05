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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.webscare.urducanvas.R
import com.webscare.urducanvas.BuildConfig
import com.webscare.ads.WebsCareAds
import com.webscare.urducanvas.common.canvas.enums.ListViewState
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.common.canvas.sealed.HomeRow
import com.webscare.urducanvas.common.canvas.sealed.TemplateDownloadState
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.utils.showGlobalSuccessSnack
import com.webscare.urducanvas.data.model.TemplateEntity
import com.webscare.urducanvas.data.model.toExportResultFinal
import com.webscare.urducanvas.databinding.DialogLoadingProgressBinding
import com.webscare.urducanvas.databinding.FragmentTemplatesListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@AndroidEntryPoint
class TemplatesListFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentTemplatesListBinding? = null
    private val binding get() = _binding!!
    private var filterType: String? = null
    private val mainViewModel: com.webscare.urducanvas.viewmodels.MainViewModel by activityViewModels()
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()
    private lateinit var adapter: TemplatesAdapter

    private var currentCategory: String? = null
    private var currentTrend: String? = null
    private var currentSubcategory: String? = null

    private var downloadingTemplate: TemplateEntity? = null
    private var bundle: Bundle = Bundle()
    private var loadingDialog: AlertDialog? = null
    private var dialogBinding: DialogLoadingProgressBinding? = null
    private var subcategoriesList: List<String> = emptyList()
    private lateinit var sglm: StaggeredGridLayoutManager
    private var shuffleAfterRefresh = false
    private var filterJob: Job? = null

    private var baseTemplates: List<TemplateEntity> = emptyList()
    private var activeSubcategory: String = "All"
    private var activeSize: CanvasSize? = null
    private var activeQuery: String = ""
    private var activePrice: String = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filterType = arguments?.getString("FILTER_TYPE")
        currentCategory    = arguments?.getString("TAB_NAME")
        currentTrend       = arguments?.getString("TREND_NAME")
        currentSubcategory = arguments?.getString("SUBCATEGORY_NAME")
        if (!currentSubcategory.isNullOrBlank()) activeSubcategory = currentSubcategory!!
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTemplatesListBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        setupPriceChips()
        setEvents()
        setupRecycler()
        observeData()
    }

    // ─── Loading Dialog ───────────────────────────────────────────────────────

    private fun showLoadingDialog() {
        dialogBinding = DialogLoadingProgressBinding.inflate(LayoutInflater.from(requireActivity()))
        loadingDialog = AlertDialog.Builder(requireActivity()).setView(dialogBinding!!.root)
            .setCancelable(true).setOnCancelListener { viewModel.clearLoading() }.create()
        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog?.show()
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss(); loadingDialog = null; dialogBinding = null
    }



    // ─── Events ───────────────────────────────────────────────────────────────

    private fun renderState(state: ListViewState) {
        binding.apply {
            when (state) {
                ListViewState.Loading -> {
                    loadingState.root.visibility = View.VISIBLE
                    emptyState.root.visibility = View.GONE
                    swipeRefresh.visibility = View.GONE
                }
                ListViewState.Content -> {
                    loadingState.root.visibility = View.GONE
                    emptyState.root.visibility = View.GONE
                    swipeRefresh.visibility = View.VISIBLE
                }
                ListViewState.FilterEmpty -> {
                    loadingState.root.visibility = View.GONE
                    swipeRefresh.visibility = View.GONE
                    emptyState.root.visibility = View.VISIBLE
                    emptyState.errorIcon.setImageResource(R.drawable.ic_search)
                    emptyState.errorTitle.text = "No matches"
                    emptyState.errorMessage.text = "Try a different search or clear your filters"
                    emptyState.retryButton.visibility = View.VISIBLE
                    (emptyState.retryButton.getChildAt(0) as? android.widget.TextView)?.text = "Clear filters"
                    emptyState.retryButton.addPressEffect {
                        activeQuery = ""; activeSize = null; activePrice = "All"; activeSubcategory = "All"
                        binding.searchBar.setText("")
                        applyFiltersList()
                    }
                }
                ListViewState.Error -> {
                    loadingState.root.visibility = View.GONE
                    swipeRefresh.visibility = View.GONE
                    emptyState.root.visibility = View.VISIBLE
                    emptyState.errorIcon.setImageResource(R.drawable.ic_no_internet)
                    emptyState.errorTitle.text = "Couldn't load templates"
                    emptyState.errorMessage.text = "Check your connection and try again"
                    emptyState.retryButton.visibility = View.VISIBLE
                    (emptyState.retryButton.getChildAt(0) as? android.widget.TextView)?.text = "Retry"
                    emptyState.retryButton.addPressEffect { mainViewModel.retryTemplates() }
                }
                ListViewState.Empty -> {
                    loadingState.root.visibility = View.GONE
                    swipeRefresh.visibility = View.GONE
                    emptyState.root.visibility = View.VISIBLE
                    emptyState.errorIcon.setImageResource(R.drawable.ic_nothing_found)
                    emptyState.errorTitle.text = "No templates here"
                    emptyState.errorMessage.text = "There are no templates in this section yet"
                    emptyState.retryButton.visibility = View.GONE
                }
            }
        }
    }

    private fun updateListState() {
        val status = mainViewModel.templatesStatus.value
        val hasData = baseTemplates.isNotEmpty()
        val hasFilters = activeQuery.isNotBlank() || activeSize != null ||
                !activePrice.equals("All", true) || !activeSubcategory.equals("All", true)

        val state = when {
            hasData && adapter.itemCount == 0 && hasFilters -> ListViewState.FilterEmpty
            hasData -> ListViewState.Content
            status == com.webscare.urducanvas.common.canvas.enums.SectionStatus.Loading -> ListViewState.Loading
            status == com.webscare.urducanvas.common.canvas.enums.SectionStatus.Failed -> ListViewState.Error
            else -> ListViewState.Empty
        }
        renderState(state)
    }

    private fun setEvents() {
        binding.searchBar.doAfterTextChanged {
            activeQuery = it?.toString().orEmpty()
            applyFiltersList()
        }
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
                applyFiltersList()
            }
            sheet.onFilterCleared = {
                activeSize = null
                activeSubcategory = "All"
                applyFiltersList()
            }
            sheet.show(childFragmentManager, FilterBottomSheetFragment.TAG)
        }
        binding.swipeRefresh.setColorSchemeResources(R.color.appColor, R.color.black, R.color.gray)
        binding.swipeRefresh.setOnRefreshListener {
            shuffleAfterRefresh = true
            binding.templatesRV.stopScroll()
            binding.templatesRV.scrollToPosition(0)
            binding.templatesRV.suppressLayout(true)
            mainViewModel.fetchAndStoreTemplatesFromApi()
        }
        binding.back.addPressEffect { findNavController().navigateUp() }
    }



    // ─── Recycler ─────────────────────────────────────────────────────────────

    private fun setupRecycler() {
        binding.title.text = when {
            !currentSubcategory.isNullOrBlank() -> currentSubcategory
            !currentTrend.isNullOrBlank()       -> currentTrend
            else                                -> currentCategory ?: "Templates"
        }

        adapter = TemplatesAdapter { template, isDownloaded ->
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

        sglm = com.webscare.urducanvas.common.views.SafeStaggeredGridLayoutManager(2, RecyclerView.VERTICAL).apply {
            gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_NONE
            isItemPrefetchEnabled = true
        }

        val wrappedAdapter = WebsCareAds.wrapWithNativeAds(
            originalAdapter = this@TemplatesListFragment.adapter,
            adUnitId = BuildConfig.AD_NATIVE_TEMPLATES,
            interval = 6,
            startOffset = 3,
            nativeSize = com.webscare.ads.NativeSize.SMALL
        )

        binding.templatesRV.apply {
            layoutManager = sglm
            adapter = wrappedAdapter
            setHasFixedSize(true)
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            itemAnimator = null
            setItemViewCacheSize(24)
        }

        binding.templatesRV.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                binding.swipeRefresh.isEnabled = !rv.canScrollVertically(-1)
            }
        })
    }

    // ─── Filter Logic ─────────────────────────────────────────────────────────

    private fun filterTemplatesList(
        source: List<TemplateEntity>, subcategory: String, query: String, size: CanvasSize?, price: String
    ): List<TemplateEntity> {
        val bySub = if (subcategory.equals("All", true)) source
        else source.filter { it.subcategory.equals(subcategory, true) }
        val q = query.trim().lowercase()
        val byQuery = if (q.isBlank()) bySub else bySub.filter { it.matchesQuery(q) }
        val bySize = size?.let { s -> byQuery.filter { it.matchesSize(s) } } ?: byQuery
        return when (price) {
            "Free"    -> bySize.filter { !it.is_premium }
            "Premium" -> bySize.filter { it.is_premium }
            else      -> bySize
        }
    }

    private fun applyFiltersList(forceShuffle: Boolean = false) {
        filterJob?.cancel()
        filterJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val filtered = filterTemplatesList(baseTemplates, activeSubcategory, activeQuery, activeSize, activePrice)
            val result = if (forceShuffle) filtered.shuffled() else filtered
            withContext(Dispatchers.Main) {
                adapter.submitList(result) {
                    sglm.invalidateSpanAssignments()
                    if (!binding.templatesRV.canScrollVertically(-1)) binding.templatesRV.scrollToPosition(0)
                    updateListState()    // ← inside submitList callback
                }
            }
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
            mainViewModel.templatesStatus.collect { updateListState() }
        }



        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.isLoading.collect { loading ->
                if (!loading && binding.swipeRefresh.isRefreshing) {
                    binding.swipeRefresh.isRefreshing = false
                    binding.templatesRV.suppressLayout(false)
                    if (shuffleAfterRefresh) {
                        shuffleAfterRefresh = false; applyFiltersList(forceShuffle = true)
                    } else sglm.invalidateSpanAssignments()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.localTemplates.collect { all ->
                binding.swipeRefresh.isRefreshing = false
                baseTemplates = when {
                    filterType == "popular" -> all.filter { it.is_popular }

                    !currentTrend.isNullOrBlank() && !currentSubcategory.isNullOrBlank() -> {
                        val ids = mainViewModel.trendRows.value
                            .filterIsInstance<HomeRow.TrendRow>()
                            .firstOrNull { it.title.equals(currentTrend, true) }
                            ?.templates?.map { it.id }?.toSet() ?: emptySet()
                        all.filter { it.id in ids && it.subcategory?.trim()?.equals(currentSubcategory?.trim() ?: "", true) == true }
                    }

                    !currentTrend.isNullOrBlank() -> {
                        val ids = mainViewModel.trendRows.value
                            .filterIsInstance<HomeRow.TrendRow>()
                            .firstOrNull { it.title.equals(currentTrend, true) }
                            ?.templates?.map { it.id }?.toSet() ?: emptySet()
                        all.filter { it.id in ids }
                    }

                    !currentCategory.isNullOrBlank() && !currentCategory.equals("All", true) ->
                all.filter { it.category.equals(currentCategory, true) }

                    else -> all
                }

                subcategoriesList = buildList {
                    add("All")
                    addAll(baseTemplates.mapNotNull { it.subcategory?.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sorted())
                }

                if (!currentSubcategory.isNullOrBlank()) {
                    binding.filters.isVisible = false  // filter button bhi hide
                }

                applyFiltersList()
                updateListState()
            }
        }

        viewModel.isLoadingTemplate.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = false
            if (isLoading == true) showLoadingDialog()
            else if (isLoading == false) dismissLoadingDialog()
        }

        viewModel.loadingStage.observe(viewLifecycleOwner) { stage ->
            stage?.let { (msg, pct) ->
                dialogBinding?.let { it.progressBar.progress = pct; it.subtitle.text = "$msg... $pct%"; it.tvProgressPercent.text = "$pct% complete" }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.templateDownloadStates.collect { ds ->
                ds.values.forEach { state ->
                    when (state) {
                        is TemplateDownloadState.Progress ->
                            adapter.updateProgress(state.template.id,
                                com.webscare.urducanvas.data.model.ProgressUi(state.progress, true, false))
                        is TemplateDownloadState.SuccessWithTemplate -> {
                            binding.swipeRefresh.isRefreshing = false
                            val t = state.template; mainViewModel.clearTemplateDownloadState()
                            downloadingTemplate = t
                            showGlobalSuccessSnack("Template ready") {
                                val exportResult = t.toExportResultFinal()
                                viewModel.loadTemplateFromJsonFile(exportResult, requireContext()) { success ->
                                    if (success && isAdded) {
                                        findNavController().navigate(R.id.editorFragment)
                                    }
                                }
                            }
                        }
                        is TemplateDownloadState.Success -> mainViewModel.clearTemplateDownloadState()
                        is TemplateDownloadState.Error   -> { downloadingTemplate = null }
                        null -> Unit
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding?.templatesRV?.adapter = null
        loadingDialog?.dismiss()
        loadingDialog = null
        dialogBinding = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(tabName: String) = TemplatesListFragment().apply {
            arguments = Bundle().apply { putString("TAB_NAME", tabName) }
        }
    }
}