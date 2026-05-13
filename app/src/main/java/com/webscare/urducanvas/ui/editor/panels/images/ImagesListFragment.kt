package com.webscare.urducanvas.ui.editor.panels.images

import android.annotation.SuppressLint
import android.os.Bundle
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
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.data.model.ImagesData
import com.webscare.urducanvas.databinding.FragmentImagesListBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
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

    private var imagesAdapter: ImagesAdapter? = null

    var category: String = ""; private set
    private var filterText: String = ""
    private var categoryImages: List<ImageEntity> = emptyList()
    private var lastImagesData: ImagesData? = null
    private var savedScrollPos: Int = 0
    private var isPanelExpanded: Boolean = false

    private var prevSelectedIds: Set<Int> = emptySet()
    private var prevWasInMode: Boolean = false

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
        }

        setupSwipeRefresh()
        setupExpandGesture()
        setupImageTab()
        observeSelectionState()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) saveScrollPos()
    }

    override fun onDestroyView() {
        saveScrollPos()
        _binding = null
        super.onDestroyView()
    }

    // ── SwipeRefreshLayout ────────────────────────────────────────────────────
    //
    // Enabled ONLY in expanded state (vertical scroll).
    // In collapsed state the RecyclerView scrolls horizontally — the vertical
    // swipe gesture is intercepted by setupExpandGesture() instead.
    // On refresh: shuffle the current list in memory — no network call.

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.apply {
            isEnabled = false   // starts disabled; enabled when panel expands
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

    // ── Swipe-up to expand gesture ────────────────────────────────────────────
    //
    // In collapsed state the RecyclerView is HORIZONTAL (GridLayoutManager
    // with HORIZONTAL orientation). Horizontal drag is consumed by the
    // RecyclerView's own scroll. Vertical drag is NOT consumed by it, so
    // we can intercept it here.
    //
    // When user swipes UP (dy > threshold) in collapsed state → expand panel.
    // In expanded state this listener is a no-op — SwipeRefreshLayout handles
    // pull-down, and the RecyclerView is vertical so it handles scroll itself.

    @SuppressLint("ClickableViewAccessibility")
    private fun setupExpandGesture() {
        val thresholdPx = 40 * resources.displayMetrics.density
        var startY = 0f
        var startX = 0f

        binding.backgrounds.setOnTouchListener { _, event ->
            // Only intercept in collapsed state — expanded RecyclerView needs
            // its touch events for vertical scroll
            if (isPanelExpanded) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startX = event.rawX
                    false   // don't consume DOWN — RecyclerView needs it for scroll init
                }
                MotionEvent.ACTION_UP -> {
                    val dy = startY - event.rawY   // positive = swipe up
                    val dx = abs(startX - event.rawX)

                    // Only expand if swipe is more vertical than horizontal
                    // so horizontal scroll still works correctly
                    if (dy > thresholdPx && dy > dx * 1.5f) {
                        mainViewModel.togglePanel(PanelType.IMAGES)
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
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

                    mainViewModel.updateImage(updated)

                    if (!isAdded) return@ImagesAdapter

                    val isBackground =
                        entity.parent_category.equals("Backgrounds", ignoreCase = true)

                    if (isBackground) {
                        viewModel.ensureBackgroundElement(requireActivity())
                        viewModel.setCanvasBackgroundImage(bitmap, requireActivity())
                    } else {
                        if (svgDrawable != null) {
                            viewModel.addSvgSticker(
                                svgDrawable, svgXml, requireActivity(), entity.is_premium
                            )
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
                                entity.is_premium
                            )
                        }
                    }
                },
                onLongPress = { entity ->
                    mainViewModel.toggleImagesSelection(entity.id)
                }
            )
        }

        binding.backgrounds.layoutManager = buildLayoutManager(isPanelExpanded)
        binding.backgrounds.adapter = imagesAdapter

        val data = mainViewModel.imagesData.value
        lastImagesData = data
        // Recents tab is populated automatically by buildImagesData when
        // any image has is_recent = true. sliceFor() handles it below.
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

        if (!expanded) {
            mainViewModel.clearImagesSelection()
            prevSelectedIds = emptySet()
            prevWasInMode   = false
            imagesAdapter?.clearSelectionShadow()
        }

        // SwipeRefreshLayout only active in expanded (vertical) state
        binding.swipeRefresh.isEnabled = expanded

        binding.backgrounds.recycledViewPool.clear()
        binding.backgrounds.layoutManager = buildLayoutManager(expanded)

        imagesAdapter?.isExpanded = expanded

        val currentMode = mainViewModel.isInImagesMultiSelectMode.value
        imagesAdapter?.isInMultiSelectMode = currentMode
        if (currentMode) imagesAdapter?.applyModeToAll()

        if (expanded) binding.backgrounds.scrollToPosition(0)
    }

    private fun buildLayoutManager(expanded: Boolean): GridLayoutManager =
        GridLayoutManager(
            requireContext(),
            3,
            if (expanded) GridLayoutManager.VERTICAL else GridLayoutManager.HORIZONTAL,
            false
        )

    // ── Called by ImagesFragment ──────────────────────────────────────────────

    fun onNewData(data: ImagesData) {
        if (lastImagesData === data) return
        lastImagesData = data

        val slice = sliceFor(data, category)
        if (slice === categoryImages) return
        categoryImages = slice
        submitImages(slice)
    }

    fun updateFilter(newFilter: String) {
        if (filterText == newFilter) return
        filterText = newFilter
        submitImages(categoryImages)
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    private fun submitImages(images: List<ImageEntity>) {
        val final = if (filterText.isBlank()) images
        else images.filter { it.matchesQuery(filterText) }

        if (_binding == null) return
        binding.noEmojis.visibility = if (final.isEmpty()) View.VISIBLE else View.GONE
        imagesAdapter?.submitList(final)
        onFilterResult?.invoke(category, final.size)
    }

    // ── Scroll ────────────────────────────────────────────────────────────────

    private fun saveScrollPos() {
        val lm = _binding?.backgrounds?.layoutManager as? GridLayoutManager ?: return
        val pos = lm.findFirstVisibleItemPosition()
        if (pos >= 0) savedScrollPos = pos
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