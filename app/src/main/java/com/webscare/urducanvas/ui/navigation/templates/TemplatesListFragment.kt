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
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.common.canvas.sealed.HomeRow
import com.webscare.urducanvas.common.canvas.sealed.TemplateDownloadState
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.utils.showGlobalSuccessSnack
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

    private val mainViewModel: com.webscare.urducanvas.viewmodels.MainViewModel by activityViewModels()
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()
    private lateinit var adapter: TemplatesAdapter
    private var currentCategory: String? = null
    private var currentTrend: String? = null
    private var downloadingTemplate: com.webscare.urducanvas.data.model.TemplateEntity? = null
    private var bundle: Bundle = Bundle()
    private var loadingDialog: AlertDialog? = null
    private var dialogBinding: DialogLoadingProgressBinding? = null
    private lateinit var sizeAdapter: com.webscare.urducanvas.ui.creation.CanvasSizeAdapter
    private lateinit var sglm: StaggeredGridLayoutManager
    private var shuffleAfterRefresh = false
    private var filterJob: Job? = null

    private var baseTemplates: List<com.webscare.urducanvas.data.model.TemplateEntity> =
        emptyList() // only the selected category
    private var activeSubcategory: String = "All"
    private var activeSize: com.webscare.urducanvas.common.canvas.model.CanvasSize? = null
    private var activeQuery: String = ""
    private var suppressChipClicks = false
    private var filterPanelVisible = false

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
        ),               // 210mm × 297mm
        CanvasSize(
            "Letter", 2550f, 3300f
        ),          // 8.5in × 11in
        CanvasSize(
            "Poster", 3600f, 5400f
        ),          // 12in × 18in
        CanvasSize(
            "Business Card", 1050f, 600f
        ), // 3.5in × 2in
        CanvasSize(
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
        )   // 5in × 7in
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentCategory = arguments?.getString("TAB_NAME")
        currentTrend = arguments?.getString("TREND_NAME")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTemplatesListBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        setupRecycler()
        observeData()
    }

    private fun setEvents() {
        binding.searchBar.doAfterTextChanged {
            activeQuery = it?.toString().orEmpty()
            applyFiltersList()
        }

        binding.filters.addPressEffect { toggleFilterPanel() }
        binding.swipeRefresh.setColorSchemeResources(
            R.color.appColor, R.color.black, R.color.gray
        )
        binding.swipeRefresh.setOnRefreshListener {
            shuffleAfterRefresh = true
            binding.templatesRV.stopScroll()
            binding.templatesRV.scrollToPosition(0)

            binding.templatesRV.suppressLayout(true)
            mainViewModel.fetchAndStoreTemplatesFromApi()
        }

        binding.back.addPressEffect { findNavController().navigateUp() }
    }

    private fun showLoadingDialog() {
        dialogBinding = DialogLoadingProgressBinding.inflate(LayoutInflater.from(requireActivity()))

        loadingDialog = AlertDialog.Builder(requireActivity()).setView(dialogBinding!!.root)
            .setCancelable(false).create()

        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog?.show()
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
        dialogBinding = null
    }

    private fun toggleFilterPanel() {
        val panel = binding.subCategoryChips
        val bar = binding.searchBar

        if (!filterPanelVisible) {
            binding.swipeRefresh.isEnabled = false
            panel.isVisible = true
            panel.doOnPreDraw {
                // keep the bar on top
                bar.bringToFront()
                bar.elevation = resources.getDimension(com.intuit.sdp.R.dimen._2sdp)
                panel.elevation = resources.getDimension(com.intuit.sdp.R.dimen._1sdp)

                // start just behind the bar
                panel.translationY = -panel.height.toFloat()
                panel.alpha = 0f
                panel.animate().translationY(0f).alpha(1f).setDuration(200).start()
            }
        } else {
            panel.animate().translationY(-panel.height.toFloat()).alpha(0f).setDuration(180)
                .withEndAction { panel.isGone = true }.start()
        }
        binding.swipeRefresh.isEnabled = true
        filterPanelVisible = !filterPanelVisible
    }

    private fun renderSubcategoryChips(subcats: List<String>) {
        val cg = binding.subCategoryChips
        cg.isSingleSelection = true
        cg.isSelectionRequired = false
        cg.removeAllViews()

        val selected = activeSubcategory
        subcats.forEach { label ->
            val chip = layoutInflater.inflate(
                R.layout.chip_filter_item, cg, false
            ) as com.google.android.material.chip.Chip
            chip.id = View.generateViewId()
            chip.text = label
            chip.isCheckable = true
            chip.isChecked = label.equals(selected, true)
            cg.addView(chip)

            // Toggle logic on click
            chip.addPressEffect {
                if (suppressChipClicks) return@addPressEffect

                val clickedText = chip.text.toString()
                val clickedIsSelected = chip.isChecked

                if (clickedIsSelected && !clickedText.equals("All", true)) {
                    // Tapped the same non-All chip -> switch to All
                    val allChip = findChipByText(cg, "All")
                    if (allChip != null) {
                        suppressChipClicks = true
                        cg.clearCheck()
                        allChip.isChecked = true
                        suppressChipClicks = false
                        activeSubcategory = "All"
                    }
                } else {
                    // Normal selection (or tapping All)
                    suppressChipClicks = true
                    cg.clearCheck()
                    chip.isChecked = true
                    suppressChipClicks = false
                    activeSubcategory = clickedText
                }

                applyFiltersList()
                if (filterPanelVisible) toggleFilterPanel()

            }
        }

        // If saved selection no longer exists, fall back to All
        if (!subcats.any { it.equals(selected, true) }) {
            val allChip = findChipByText(cg, "All")
            if (allChip != null) {
                suppressChipClicks = true
                cg.clearCheck()
                allChip.isChecked = true
                suppressChipClicks = false
                activeSubcategory = "All"
            }
        }
    }

    private fun findChipByText(
        group: ViewGroup, text: String
    ): com.google.android.material.chip.Chip? =
        (0 until group.childCount).mapNotNull { group.getChildAt(it) as? com.google.android.material.chip.Chip }
            .firstOrNull { it.text.toString().equals(text, true) }

    private fun setupRecycler() {
        binding.title.text = currentTrend ?: currentCategory ?: "Templates"

        sizeAdapter = _root_ide_package_.com.webscare.urducanvas.ui.creation.CanvasSizeAdapter(
            sizeList, onClick = { selected ->
                activeSize = if (activeSize?.name == selected.name) null else selected
                sizeAdapter.selectedSizeName = activeSize?.name ?: ""
                applyFiltersList()
            }, false
        )
        binding.sizesRV.adapter = sizeAdapter

        adapter = TemplatesAdapter { template, isDownloaded ->
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

    private fun filterTemplatesList(
        source: List<com.webscare.urducanvas.data.model.TemplateEntity>,
        subcategory: String,
        query: String,
        size: com.webscare.urducanvas.common.canvas.model.CanvasSize?
    ): List<com.webscare.urducanvas.data.model.TemplateEntity> {
        // 1) subcategory
        val bySub = if (subcategory.equals("All", true)) source
        else source.filter { it.subcategory.equals(subcategory, true) }

        // 2) search (optional)
        val q = query.trim().lowercase()
        val byQuery = if (q.isBlank()) bySub else bySub.filter { it.matchesQuery(q) }

        // 3) size
        return size?.let { s -> byQuery.filter { it.matchesSize(s) } } ?: byQuery
    }

    private fun applyFiltersList(forceShuffle: Boolean = false) {
        filterJob?.cancel()
        filterJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {

            val filtered = filterTemplatesList(
                baseTemplates, activeSubcategory, activeQuery, activeSize
            )

            val result = if (forceShuffle) filtered.shuffled() else filtered

            withContext(Dispatchers.Main) {
                adapter.submitList(result)   // ✅ NO copy()
                sglm.invalidateSpanAssignments()

                if (!binding.templatesRV.canScrollVertically(-1)) {
                    binding.templatesRV.scrollToPosition(0)
                }
            }
        }
    }

    private fun com.webscare.urducanvas.data.model.TemplateEntity.matchesQuery(q: String): Boolean {
        val haystack = buildString {
            append(category).append(' ')
            append(subcategory).append(' ')
            append(template_name).append(' ')
            append(canvas_width).append(' ')
            append(canvas_height).append(' ')
            append(tags.joinToString(" "))
        }.lowercase()
        return haystack.contains(q)
    }

    private fun com.webscare.urducanvas.data.model.TemplateEntity.matchesSize(s: com.webscare.urducanvas.common.canvas.model.CanvasSize): Boolean {
        val sw = s.width.roundToInt()
        val sh = s.height.roundToInt()
        val iw = canvas_width
        val ih = canvas_height
        // exact pixel match, portrait or landscape (if you want both orientations)
        return (iw == sw && ih == sh)
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.isLoading.collect { loading ->
                if (!loading && binding.swipeRefresh.isRefreshing) {
                    binding.swipeRefresh.isRefreshing = false
                    binding.templatesRV.suppressLayout(false)

                    if (shuffleAfterRefresh) {
                        shuffleAfterRefresh = false
                        // Submit, then fix spans
                        applyFiltersList(forceShuffle = true)
                    } else {
                        sglm.invalidateSpanAssignments()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.localTemplates.collect { all ->
                // Filter by the category sent via TAB_NAME once, keep as base
                binding.swipeRefresh.isRefreshing = false
                baseTemplates = when {
                    // If trend name passed, filter by it
                    !currentTrend.isNullOrBlank() -> {
                        val trendTemplateIds =
                            mainViewModel.trendRows.value.filterIsInstance<HomeRow.TrendRow>()
                                .firstOrNull {
                                    it.title.equals(
                                        currentTrend, true
                                    )
                                }?.templates?.map { it.id }?.toSet() ?: emptySet()

                        all.filter { it.id in trendTemplateIds }
                    }

                    // If category name passed, filter by it
                    !currentCategory.isNullOrBlank() && !currentCategory.equals("All", true) -> {
                        all.filter { it.category.equals(currentCategory, true) }
                    }

                    // Default → show all
                    else -> all
                }

                // Build subcategory chips from baseTemplates
                val subcats = buildList {
                    add("All")
                    addAll(baseTemplates.map { it.subcategory.trim() }.filter { it.isNotEmpty() }
                        .distinct().sorted())
                }
                val cg = binding.subCategoryChips
                val current =
                    (0 until cg.childCount).mapNotNull { (cg.getChildAt(it) as? com.google.android.material.chip.Chip)?.text?.toString() }
                if (current != subcats) renderSubcategoryChips(subcats)

                applyFiltersList()
            }
        }

        viewModel.isLoadingTemplate.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = false
            if (isLoading == true) {
                showLoadingDialog()
            } else if (isLoading == false) {
                dismissLoadingDialog()
                view?.post { findNavController().navigate(R.id.editorFragment, bundle) }
            }
        }

        viewModel.loadingStage.observe(viewLifecycleOwner) { stage ->
            stage?.let { (message, percent) ->
                dialogBinding?.let {
                    it.progressBar.progress = percent
                    it.subtitle.text = "$message... $percent%"
                    it.tvProgressPercent.text = "$percent% complete"
                }
            }
        }

        // 2) Download state: update only the affected row via payload; avoid submitList
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.templateDownloadStates.collect { downloadState ->
                downloadState.values.forEach { state ->
                    when (state) {
                        is TemplateDownloadState.Progress -> {
                            val t = state.template
                            downloadingTemplate = t
                            adapter.updateProgress(
                                state.template.id,
                                _root_ide_package_.com.webscare.urducanvas.data.model.ProgressUi(
                                    state.progress, isDownloading = true, isDownloaded = false
                                )
                            )
                        }

                        is TemplateDownloadState.SuccessWithTemplate -> {
                            binding.swipeRefresh.isRefreshing = false
                            val t = state.template
                            mainViewModel.clearTemplateDownloadState()
                            downloadingTemplate = t

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
                        }

                        is TemplateDownloadState.Success -> {
                            mainViewModel.clearTemplateDownloadState()
                        }

                        is TemplateDownloadState.Error -> {

                            downloadingTemplate = null
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

    companion object {
        fun newInstance(tabName: String): TemplatesListFragment {
            return TemplatesListFragment().apply {
                arguments = Bundle().apply {
                    putString("TAB_NAME", tabName)
                }
            }
        }
    }
}