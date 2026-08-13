package com.webscare.urducanvas.ui.editor.panels.images

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.common.utils.ImageProcessor.bitmapCompress
import com.webscare.urducanvas.common.utils.ImageProcessor.trimTransparentEdges
import com.webscare.urducanvas.common.utils.MorphGridLayoutManager
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.data.model.ImagesData
import com.webscare.urducanvas.databinding.FragmentImagesListBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import com.webscare.urducanvas.viewmodels.PexelsViewModel
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.PexelsCategories
import com.webscare.urducanvas.common.utils.HorizontalSpringEdgeEffectFactory
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@AndroidEntryPoint
class ImagesListFragment : Fragment() {

    private var _binding: FragmentImagesListBinding? = null
    private val binding get() = _binding!!

    var onFilterResult: ((category: String, count: Int) -> Unit)? = null

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()
    private val pexelsViewModel: PexelsViewModel by activityViewModels()

    private var imagesAdapter: ImagesAdapter? = null

    var category: String = ""; private set
    private var filterText: String = ""
    private var categoryImages: List<ImageEntity> = emptyList()
    private var lastImagesData: ImagesData? = null
    private var savedScrollPos: Int = 0
    private var isPanelExpanded: Boolean = false
    private var isInSearchMode: Boolean = false

    private var prevSelectedIds: Set<Int> = emptySet()
    private var prevWasInMode: Boolean = false

    // Track IDs already in the adapter via pagination append.
    // onNewData checks this to avoid resubmitting items already appended directly.
    private val paginatedIds = mutableSetOf<Int>()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            category   = it.getString(ARG_CATEGORY).orEmpty()
            filterText = it.getString(ARG_FILTER).orEmpty()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImagesListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.backgrounds.apply {
            setHasFixedSize(false)
            setItemViewCacheSize(20)
            recycledViewPool.setMaxRecycledViews(0, 25)
            layoutManager = MorphGridLayoutManager(
                context = requireContext(),
                collapsedSpan = 3,
                expandedSpan = 3
            ).apply {
                applyFraction(binding.backgrounds, if (mainViewModel.isPanelExpanded(PanelType.IMAGES)) 1f else 0f)
            }
        }

        setupSwipeRefresh()
        setupImageTab()
        observeSelectionState()
        setupPaginationScroll()
        observePexelsSearch()
        observePaginationAppend()
        observePaginationLoader()
        pexelsViewModel.loadTabGroupIfNeeded(category)

        val isExpandedNow = mainViewModel.isPanelExpanded(PanelType.IMAGES)
        isPanelExpanded = !isExpandedNow
        onPanelExpanded(isExpandedNow)

        binding.backgrounds.post {
            if (_binding != null) {
                isPanelExpanded = !isExpandedNow
                onPanelExpanded(isExpandedNow)
                imagesAdapter?.notifyDataSetChanged()
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) saveScrollPos()
    }

    override fun onDestroyView() {
        saveScrollPos()
        imagesAdapter?.cancelPreload()
        _binding?.backgrounds?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    // ── SwipeRefreshLayout ────────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.apply {
            isEnabled = false
            setColorSchemeResources(com.webscare.urducanvas.R.color.appColor)
            setOnRefreshListener {
                shuffleCurrentList()
                isRefreshing = false
            }
        }
    }

    private fun shuffleCurrentList() {
        categoryImages = categoryImages.shuffled()
        submitImages(categoryImages)
    }



    // ── Selection observer ────────────────────────────────────────────────────

    private fun observeSelectionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    mainViewModel.selectedImagesIds,
                    mainViewModel.isInImagesMultiSelectMode
                ) { ids, inMode -> Pair(ids, inMode) }
                    .collect { (newIds, inMode) ->
                        val adapter = imagesAdapter ?: return@collect

                        val modeChanged = inMode != prevWasInMode
                        adapter.isInMultiSelectMode = inMode

                        if (modeChanged) {
                            adapter.applyModeToAll()
                            prevWasInMode = inMode
                        }

                        if (!inMode) {
                            adapter.clearSelectionShadow()
                            prevSelectedIds = emptySet()
                        } else {
                            val added   = newIds - prevSelectedIds
                            val removed = prevSelectedIds - newIds
                            added.forEach   { id -> adapter.updateSelectionForId(id, true)  }
                            removed.forEach { id -> adapter.updateSelectionForId(id, false) }
                            prevSelectedIds = newIds
                        }
                    }
            }
        }
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupImageTab() {
        if (imagesAdapter == null) {
            imagesAdapter = ImagesAdapter(
                context = requireActivity(),
                onImageSelected = { bitmap, svgDrawable, svgXml, entity ->
                    val updated = if (svgXml != null && entity.bitmapData == null)
                        entity.copy(is_recent = true, bitmapData = svgXml)
                    else entity.copy(is_recent = true)

                    // markImageAsRecent uses a direct SQL UPDATE by id —
                    // works for ALL images including Pexels search results
                    // regardless of their category or whether updateImage would find them.
                    mainViewModel.markImageAsRecent(updated.id)
                    // Also update the full entity for own images (SVG cache etc.)
                    if (updated.id < Constants.PEXELS_ID_OFFSET) {
                        mainViewModel.updateImage(updated)
                    }
                    if (mainViewModel.isPanelExpanded(PanelType.IMAGES)) mainViewModel.togglePanel(PanelType.IMAGES)

                    if (!isAdded) return@ImagesAdapter

                    val layerName = com.webscare.urducanvas.common.utils.ImageUtils.getLayerNameForEntity(
                        entity.file_name, entity.alt_text, entity.category, defaultFallback = "Image"
                    )

                    // Always add as image element — never as background
                    if (svgDrawable != null) {
                        viewModel.addSvgSticker(svgDrawable, svgXml, requireActivity(), entity.is_premium, customName = layerName)
                    } else {
                        val resized = viewModel.canvasSize.value?.height?.roundToInt()
                            ?.let { h ->
                                viewModel.canvasSize.value?.width?.roundToInt()
                                    ?.let { w -> bitmapCompress(bitmap!!, w, h) }
                            }
                        viewModel.addSticker(
                            resized?.trimTransparentEdges(),
                            requireActivity(),
                            ElementType.IMAGE,
                            entity.is_premium,
                            customName = layerName
                        )
                    }
                },
                onLongPress = { entity ->
                    if (mainViewModel.isPanelExpanded(PanelType.IMAGES)) {
                        mainViewModel.toggleImagesSelection(entity.id)
                    }
                }
            )
        }

        binding.backgrounds.adapter = imagesAdapter

        val data = mainViewModel.imagesData.value
        lastImagesData = data
        categoryImages = sliceFor(data, category)
        submitImages(categoryImages)

        if (savedScrollPos > 0) {
            binding.backgrounds.post {
                (binding.backgrounds.layoutManager as? GridLayoutManager)
                    ?.scrollToPosition(savedScrollPos)
            }
        }
    }

    // ── Panel expand/collapse ─────────────────────────────────────────────────

    fun onPanelExpanded(expanded: Boolean) {
        if (isPanelExpanded == expanded) return
        isPanelExpanded = expanded
        if (_binding == null) return

        val rv = binding.backgrounds
        if (rv.width == 0) {
            rv.post {
                if (_binding != null) {
                    isPanelExpanded = !expanded
                    onPanelExpanded(expanded)
                }
            }
            return
        }

        if (expanded) {
            binding.backgrounds.edgeEffectFactory = RecyclerView.EdgeEffectFactory()
            binding.backgrounds.translationX = 0f
        } else {
            binding.backgrounds.edgeEffectFactory = HorizontalSpringEdgeEffectFactory()
        }

        mainViewModel.clearImagesSelection()
        prevSelectedIds = emptySet()
        prevWasInMode   = false
        imagesAdapter?.clearSelectionShadow()
        binding.swipeRefresh.isEnabled = expanded

        val lm = binding.backgrounds.layoutManager as? MorphGridLayoutManager
        if (lm != null) {
            lm.applyFraction(binding.backgrounds, if (expanded) 1f else 0f)
            if (imagesAdapter?.isExpanded != expanded) {
                binding.backgrounds.recycledViewPool.clear()
                imagesAdapter?.isExpanded = expanded
            }
        }
        val bottomPadding = if (expanded) (64 * resources.displayMetrics.density).toInt() else 0
        binding.backgrounds.setPadding(
            binding.backgrounds.paddingLeft,
            binding.backgrounds.paddingTop,
            binding.backgrounds.paddingRight,
            bottomPadding
        )

        // Sync item size on final settle state
        val adapter = imagesAdapter ?: return
        val rvWidth = binding.backgrounds.width
        val rvPadding = binding.backgrounds.paddingLeft + binding.backgrounds.paddingRight
        val offset = if (expanded) 1f else 0f
        adapter.slideOffset = offset
        adapter.recyclerViewWidth = rvWidth
        adapter.recyclerViewPadding = rvPadding

        for (i in 0 until binding.backgrounds.childCount) {
            val child = binding.backgrounds.getChildAt(i)
            val holder = binding.backgrounds.getChildViewHolder(child) as? ImagesAdapter.ImageViewHolder
            holder?.updateSize(offset, rvWidth, rvPadding)
        }
    }

    fun onPanelSlide(offset: Float) {
        if (_binding == null) return
        binding.swipeRefresh.isEnabled = offset >= 0.95f
        val lm = binding.backgrounds.layoutManager as? MorphGridLayoutManager
        if (lm != null) {
            lm.applyFraction(binding.backgrounds, offset)
            val effectiveExpanded = offset >= 0.95f
            if (imagesAdapter?.isExpanded != effectiveExpanded) {
                binding.backgrounds.recycledViewPool.clear()
                imagesAdapter?.isExpanded = effectiveExpanded
            }
        }
        
        // Smoothly animate bottom padding during slide to avoid jerking
        val maxPadding = (64 * resources.displayMetrics.density).toInt()
        val currentPadding = (offset * maxPadding).toInt()
        binding.backgrounds.setPadding(
            binding.backgrounds.paddingLeft,
            binding.backgrounds.paddingTop,
            binding.backgrounds.paddingRight,
            currentPadding
        )

        // Smoothly update size of all visible items in 60fps!
        val adapter = imagesAdapter ?: return
        val rvWidth = binding.backgrounds.width
        val rvPadding = binding.backgrounds.paddingLeft + binding.backgrounds.paddingRight
        adapter.slideOffset = offset
        adapter.recyclerViewWidth = rvWidth
        adapter.recyclerViewPadding = rvPadding

        for (i in 0 until binding.backgrounds.childCount) {
            val child = binding.backgrounds.getChildAt(i)
            val holder = binding.backgrounds.getChildViewHolder(child) as? ImagesAdapter.ImageViewHolder
            holder?.updateSize(offset, rvWidth, rvPadding)
        }
    }

    // ── Called by ImagesFragment ──────────────────────────────────────────────

    fun onNewData(data: ImagesData) {
        if (lastImagesData === data) return
        lastImagesData = data

        val slice = sliceFor(data, category)
        if (slice === categoryImages) return

        // Don't override search results
        if (isInSearchMode) return

        val isPexels = PexelsCategories.isPexelsTab(category)

        if (isPexels && paginatedIds.isNotEmpty()) {
            categoryImages = slice
            return
        }

        categoryImages = slice
        submitImages(slice)
    }

    fun updateFilter(newFilter: String) {
        if (filterText == newFilter) return
        filterText = newFilter
        // Recents tab: when searching, show results from ALL images, not just recents
        // so user gets the full library filtered, not just recently used items
        val searchSource = if (category.equals("Recents", ignoreCase = true) && newFilter.isNotBlank()) {
            val data = mainViewModel.imagesData.value
            buildList {
                data.imagesByCategory.values.forEach { addAll(it) }
                addAll(data.recents)
            }.distinctBy { it.id }
        } else {
            categoryImages
        }
        submitImages(searchSource)
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    private fun submitImages(images: List<ImageEntity>) {
        val final = if (filterText.isBlank()) images
        else images.filter { it.matchesQuery(filterText) }

        if (_binding == null) return
        binding.noEmojis.visibility = if (final.isEmpty()) View.VISIBLE else View.GONE
        imagesAdapter?.submitList(final)
        onFilterResult?.invoke(category, final.size)
        // If content doesn't fill the screen, trigger pagination without scroll
        checkAndFillIfNeeded()
    }

    // ── Scroll ────────────────────────────────────────────────────────────────

    private fun saveScrollPos() {
        val lm = _binding?.backgrounds?.layoutManager as? GridLayoutManager ?: return
        val pos = lm.findFirstVisibleItemPosition()
        if (pos >= 0) savedScrollPos = pos
    }

    // ── Pagination loader — driven by PexelsViewModel state ───────────────────
    //
    // paginatingTabs: set of tab names currently loading a next page.
    //   Show loader when this category is in the set; hide when removed.
    //   This covers end-of-results and network errors automatically —
    //   loadMore() always removes the tab from the set when done, regardless
    //   of whether results were found or an error occurred.
    //
    // isSearching: true while loadMoreSearch() / submitSearch() is in-flight.
    //   Only shown for the active search tab.

    private fun observePaginationLoader() {
        if (!PexelsCategories.isPexelsTab(category)) return

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                kotlinx.coroutines.flow.combine(
                    pexelsViewModel.paginatingTabs,
                    pexelsViewModel.isSearching,
                    pexelsViewModel.activeSearchTab
                ) { paginating, searching, activeTab ->
                    val categoryLoading = category in paginating
                    val searchLoading   = searching && activeTab == category
                    // Only show when panel is expanded — no loader in collapsed horizontal mode
                    isPanelExpanded && (categoryLoading || searchLoading)
                }.collect { shouldShow ->
                    val b = _binding ?: return@collect
                    if (shouldShow) {
                        b.paginationLoader.visibility = View.VISIBLE
                        b.paginationLoader.playAnimation()
                    } else {
                        b.paginationLoader.cancelAnimation()
                        b.paginationLoader.visibility = View.GONE
                    }
                }
            }
        }
    }

    // ── Direct pagination append — bypasses Room observer ────────────────────
    //
    // When PexelsViewModel.loadMore() fetches a new page, it emits the new
    // ImageEntity list via paginationResult. This fragment observes that and
    // calls appendItems() directly on the adapter — no submitList(), no DiffUtil
    // moves, no RV jumping. Existing items stay exactly where they are.
    // The items are also saved to Room by the repo, so next launch loads from cache.

    private fun observePaginationAppend() {
        if (!PexelsCategories.isPexelsTab(category)) return

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                pexelsViewModel.paginationResult.collect { result ->
                    if (result == null) return@collect
                    if (_binding == null) return@collect
                    if (isInSearchMode) return@collect

                    val (targetTab, newItems) = result

                    // Only handle items that belong to THIS category tab
                    val myItems = newItems.filter { it.category == category }
                    if (myItems.isEmpty()) return@collect

                    // Track appended IDs so onNewData doesn't re-add them
                    paginatedIds.addAll(myItems.map { it.id })

                    // Also update categoryImages so filter/search restore works
                    categoryImages = categoryImages + myItems.filter { img ->
                        categoryImages.none { it.id == img.id }
                    }

                    // Direct append — zero DiffUtil moves on existing items
                    imagesAdapter?.appendItems(myItems)
                    Log.d("ImagesListFragment", "appendItems: +${myItems.size} to '$category'")
                    // Check again — new batch might still not fill the screen
                    checkAndFillIfNeeded()
                }
            }
        }
    }

    // ── Pexels pagination scroll listener ────────────────────────────────────
    //
    // Only active when:
    //  a) Panel is expanded (vertical scroll) AND
    //  b) This tab belongs to a Pexels super-query
    //
    // Triggers loadMore() when within 10 items of the bottom.
    // In search mode, triggers loadMoreSearch() instead.

    private fun setupPaginationScroll() {
        val isPexels = PexelsCategories.isPexelsTab(category)
        val isSearchResultTab = !isPexels &&
                category != "Recents" &&
                category != "My Images" &&
                category != "My Backgrounds"

        if (!isPexels && !isSearchResultTab) return

        binding.backgrounds.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Only trigger when scrolling downward — dy > 0 means user is going towards bottom
                if (!isPanelExpanded || dy <= 0) return
                val lm          = recyclerView.layoutManager as? GridLayoutManager ?: return
                val totalItems  = lm.itemCount
                val lastVisible = lm.findLastVisibleItemPosition()
                // Trigger when within 12 items of end
                if (totalItems > 0 && lastVisible >= totalItems - 12) {
                    triggerPagination(isPexels)
                }
            }
        })
    }

    /**
     * Called in two places:
     *  1. Scroll listener — user scrolled near bottom
     *  2. After adapter submits a list — checks if content fills the screen,
     *     if not triggers pagination immediately without needing user to scroll
     */
    private fun triggerPagination(isPexels: Boolean = PexelsCategories.isPexelsTab(category)) {
        if (!isPanelExpanded) return
        when {
            isInSearchMode -> pexelsViewModel.loadMoreSearch()
            isPexels       -> pexelsViewModel.loadMore(category)
            else           -> pexelsViewModel.loadMoreSearch()
        }
    }

    /**
     * After every submitList, check if the content fills the RecyclerView height.
     * If not — the user can never scroll, so pagination would never fire.
     * Post a check after layout pass and trigger pagination if needed.
     */
    private fun checkAndFillIfNeeded() {
        val isPexels = PexelsCategories.isPexelsTab(category)
        val isSearchResult = !isPexels && category != "Recents" &&
                category != "My Images" && category != "My Backgrounds"
        if (!isPexels && !isSearchResult) return
        if (!isPanelExpanded) return

        binding.backgrounds.post {
            if (_binding == null) return@post
            val lm = binding.backgrounds.layoutManager as? GridLayoutManager ?: return@post
            val lastVisible = lm.findLastVisibleItemPosition()
            val totalItems  = lm.itemCount
            // If all items are visible (nothing to scroll), trigger next page
            if (totalItems > 0 && lastVisible >= totalItems - 1) {
                triggerPagination(isPexels)
            }
        }
    }

    // ── Pexels search overlay ─────────────────────────────────────────────────
    //
    // KEY RULES:
    //  1. Only THIS tab shows search results — checked via activeSearchTab
    //  2. searchQuery drives isInSearchMode — cleared when query is blank
    //  3. When search clears, restore Room-backed categoryImages immediately
    //  4. Non-Pexels tabs never enter this path
    //
    // WHY activeSearchTab: searchResults is a shared StateFlow. Without the tab
    // guard, all 20 fragment instances submit results simultaneously and the
    // last one wins — causing single-image results and inconsistent behaviour.

    private fun observePexelsSearch() {
        if (!PexelsCategories.isPexelsTab(category)) return

        // Observe query + activeSearchTab — only THIS tab reacts
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                kotlinx.coroutines.flow.combine(
                    pexelsViewModel.searchQuery,
                    pexelsViewModel.activeSearchTab
                ) { query, activeTab -> Pair(query, activeTab) }
                    .collect { (query, activeTab) ->
                        val isThisTabActive = activeTab == category
                        isInSearchMode = query.isNotBlank() && isThisTabActive
                        if (!isInSearchMode) {
                            // Restore category images — search cleared or on different tab
                            submitImages(categoryImages)
                        }
                    }
            }
        }

        // Show search results — only for the active search tab
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                pexelsViewModel.searchResults.collect { results ->
                    if (_binding == null) return@collect
                    if (!isInSearchMode) return@collect
                    if (pexelsViewModel.activeSearchTab.value != category) return@collect
                    binding.noEmojis.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
                    imagesAdapter?.submitList(results.toList())
                    onFilterResult?.invoke(category, results.size)
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sliceFor(data: ImagesData, cat: String): List<ImageEntity> =
        if (cat.equals("Recents", ignoreCase = true)) data.recents
        else data.imagesByCategory[cat].orEmpty()

    companion object {
        private const val ARG_CATEGORY = "arg_category"
        private const val ARG_FILTER   = "arg_filter"

        fun newInstance(category: String, initialFilter: String = "") =
            ImagesListFragment().apply {
                arguments = bundleOf(
                    ARG_CATEGORY to category,
                    ARG_FILTER   to initialFilter
                )
            }
    }
}