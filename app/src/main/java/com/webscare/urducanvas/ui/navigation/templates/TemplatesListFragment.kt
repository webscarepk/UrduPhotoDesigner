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
import com.google.android.material.chip.Chip
import com.webscare.urducanvas.R
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
    private lateinit var sizeAdapter: com.webscare.urducanvas.ui.creation.CanvasSizeAdapter
    private lateinit var sglm: StaggeredGridLayoutManager
    private var shuffleAfterRefresh = false
    private var filterJob: Job? = null

    private var baseTemplates: List<TemplateEntity> = emptyList()
    private var activeSubcategory: String = "All"
    private var activeSize: CanvasSize? = null
    private var activeQuery: String = ""
    private var activePrice: String = "All"

    private var suppressChipClicks = false
    private var suppressPriceChipClicks = false
    private var filterPanelVisible = false

    private val sizeList = listOf(
        CanvasSize("Instagram Story", 1080f, 1920f), CanvasSize("Instagram Post", 1080f, 1080f),
        CanvasSize("YouTube Thumbnail", 1280f, 720f), CanvasSize("Facebook Cover", 820f, 312f),
        CanvasSize("YouTube Channel Art", 2560f, 1440f), CanvasSize("A4", 2480f, 3508f),
        CanvasSize("Letter", 2550f, 3300f), CanvasSize("Poster", 3600f, 5400f),
        CanvasSize("Business Card", 1050f, 600f), CanvasSize("Billboard", 1920f, 1080f),
        CanvasSize("Vertical Banner", 1080f, 1920f), CanvasSize("Horizontal Banner", 1920f, 600f),
        CanvasSize("Flyer", 2550f, 3300f), CanvasSize("Resume", 2480f, 3508f),
        CanvasSize("Invitation", 1500f, 2100f)
    )

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
        setupPriceChips()
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
                applyFiltersList()
                if (filterPanelVisible) toggleFilterPanel()
            }
        }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    private fun setEvents() {
        binding.searchBar.doAfterTextChanged {
            activeQuery = it?.toString().orEmpty()
            applyFiltersList()
        }
        binding.filters.addPressEffect { toggleFilterPanel() }
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

    // ─── Filter Panel ─────────────────────────────────────────────────────────

    // binding.subCategoryChips is now the LinearLayout panel
    private fun toggleFilterPanel() {
        val panel = binding.categoryChips
        val bar = binding.searchBar
        if (!filterPanelVisible) {
            binding.swipeRefresh.isEnabled = false
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
        binding.swipeRefresh.isEnabled = true
        filterPanelVisible = !filterPanelVisible
    }

    // ─── Subcategory Chips ────────────────────────────────────────────────────

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
                applyFiltersList()
                if (filterPanelVisible) toggleFilterPanel()
            }
        }

        if (!subcats.any { it.equals(activeSubcategory, true) }) {
            findChipByText(cg, "All")?.let {
                suppressChipClicks = true; cg.clearCheck(); it.isChecked = true
                suppressChipClicks = false; activeSubcategory = "All"
            }
        }
    }

    private fun findChipByText(group: ViewGroup, text: String): Chip? =
        (0 until group.childCount).mapNotNull { group.getChildAt(it) as? Chip }
            .firstOrNull { it.text.toString().equals(text, true) }

    // ─── Recycler ─────────────────────────────────────────────────────────────

    private fun setupRecycler() {
        binding.title.text = when {
            !currentSubcategory.isNullOrBlank() -> currentSubcategory
            !currentTrend.isNullOrBlank()       -> currentTrend
            else                                -> currentCategory ?: "Templates"
        }

        sizeAdapter = com.webscare.urducanvas.ui.creation.CanvasSizeAdapter(sizeList, onClick = { selected ->
            activeSize = if (activeSize?.name == selected.name) null else selected
            sizeAdapter.selectedSizeName = activeSize?.name ?: ""
            applyFiltersList()
        }, false)
        binding.sizesRV.adapter = sizeAdapter

        adapter = TemplatesAdapter { template, isDownloaded ->
            if (isDownloaded) {
                val exportResult = template.toExportResultFinal()
                lifecycleScope.launch { withContext(Dispatchers.Default) { viewModel.loadTemplateFromJsonFile(exportResult, requireContext()) } }
                return@TemplatesAdapter
            }
            downloadingTemplate = template
            mainViewModel.downloadTemplate(template)
        }

        sglm = StaggeredGridLayoutManager(2, RecyclerView.VERTICAL).apply {
            gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_NONE
            isItemPrefetchEnabled = true
        }

        binding.templatesRV.apply {
            layoutManager = sglm
            adapter = this@TemplatesListFragment.adapter
            setHasFixedSize(true)
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            itemAnimator = null
            setItemViewCacheSize(24)
        }

        binding.templatesRV.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                binding.swipeRefresh.isEnabled = !rv.canScrollVertically(-1) && !filterPanelVisible
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
                adapter.submitList(result)
                sglm.invalidateSpanAssignments()
                if (!binding.templatesRV.canScrollVertically(-1)) binding.templatesRV.scrollToPosition(0)
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
                        all.filter { it.id in ids && it.subcategory.trim().equals(currentSubcategory!!.trim(), true) }
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

                val subcats = buildList {
                    add("All")
                    addAll(baseTemplates.map { it.subcategory.trim() }
                        .filter { it.isNotEmpty() }.distinct().sorted())
                }

                if (!currentSubcategory.isNullOrBlank()) {
                    binding.filters.isVisible = false  // filter button bhi hide
                } else {
                    val cg = binding.categoryChipGroup
                    val current = (0 until cg.childCount)
                        .mapNotNull { (cg.getChildAt(it) as? Chip)?.text?.toString() }
                    if (current != subcats) renderSubcategoryChips(subcats)
                }

                applyFiltersList()
            }
        }

        viewModel.isLoadingTemplate.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = false
            if (isLoading == true) showLoadingDialog()
            else if (isLoading == false) { dismissLoadingDialog(); view?.post { findNavController().navigate(R.id.editorFragment, bundle) } }
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
                                lifecycleScope.launch { withContext(Dispatchers.Default) { viewModel.loadTemplateFromJsonFile(exportResult, requireContext()) } }
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

    override fun onDestroy() { super.onDestroy(); _binding = null }

    companion object {
        fun newInstance(tabName: String) = TemplatesListFragment().apply {
            arguments = Bundle().apply { putString("TAB_NAME", tabName) }
        }
    }
}