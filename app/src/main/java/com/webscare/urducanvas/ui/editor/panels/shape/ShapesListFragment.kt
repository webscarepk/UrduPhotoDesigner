package com.webscare.urducanvas.ui.editor.panels.shape

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
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
import com.webscare.urducanvas.common.utils.ImageProcessor.trimTransparentEdges
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.data.model.ShapesData
import com.webscare.urducanvas.databinding.FragmentObjectsListBinding
import com.webscare.urducanvas.ui.editor.panels.images.ImagesAdapter
import com.webscare.urducanvas.common.utils.isDarkModeEnabled
import com.webscare.urducanvas.common.utils.MorphGridLayoutManager
import com.webscare.urducanvas.common.utils.HorizontalSpringEdgeEffectFactory
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ShapesListFragment : Fragment() {

    private var _binding: FragmentObjectsListBinding? = null
    private val binding get() = _binding!!

    var onFilterResult: ((category: String, count: Int) -> Unit)? = null

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()

    private var imagesAdapter: ImagesAdapter? = null

    var category: String = ""
        private set
    private var filterText: String = ""
    private var categoryImages: List<ImageEntity> = emptyList()
    private var lastShapesData: ShapesData? = null
    private var savedScrollPos: Int = 0
    private var isPanelExpanded: Boolean = false

    private var prevSelectedIds: Set<Int> = emptySet()
    private var prevWasInMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            category = it.getString(ARG_CATEGORY).orEmpty()
            filterText = it.getString(ARG_FILTER).orEmpty()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentObjectsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.objects.apply {
            setHasFixedSize(true)
            // Larger view cache = cells scrolled just off-screen stay fully bound,
            // so scrolling back over them does NOT rebind/redecode. This is the
            // main fix for the jerk when scrolling back over already-seen cells.
            setItemViewCacheSize(24)
            itemAnimator = null // suppress flicker on dataset changes
            recycledViewPool.setMaxRecycledViews(0, 32)
            layoutManager = MorphGridLayoutManager(
                context = requireContext(),
                collapsedSpan = 3,
                expandedSpan = 3,
            ).apply {
                applyFraction(binding.objects, if (mainViewModel.isPanelExpanded(PanelType.SHAPES)) 1f else 0f)
            }
        }

        setupSwipeRefresh()
        setupImageTab()
        observeSelectionState()

        val isExpandedNow = mainViewModel.isPanelExpanded(PanelType.SHAPES)
        isPanelExpanded = !isExpandedNow
        onPanelExpanded(isExpandedNow)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            saveScrollPos()
            imagesAdapter?.cancelPreload()
        }
    }

    override fun onDestroyView() {
        saveScrollPos()
        // Stop background preload for this tab — switching away should not leave
        // rasterization/decoding running. That work stacking across visited tabs
        // was the allocation storm behind the tab-switch lag.
        imagesAdapter?.cancelPreload()
        _binding = null
        super.onDestroyView()
    }

    // ── SwipeRefreshLayout ────────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefresh?.apply {
            isEnabled = false
            setColorSchemeResources(com.webscare.urducanvas.R.color.appColor)
            setOnRefreshListener {
                categoryImages = categoryImages.shuffled()
                submitImages(categoryImages)
                isRefreshing = false
            }
        }
    }

    // ── Selection observer ────────────────────────────────────────────────────

    private fun observeSelectionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    mainViewModel.selectedShapesIds,
                    mainViewModel.isInShapesMultiSelectMode,
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
                            val added = newIds - prevSelectedIds
                            val removed = prevSelectedIds - newIds
                            added.forEach { id -> adapter.updateSelectionForId(id, true) }
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
                    val updated = if (svgXml != null && entity.bitmapData == null) {
                        entity.copy(is_recent = true, bitmapData = svgXml)
                    } else {
                        entity.copy(is_recent = true)
                    }
                    mainViewModel.updateImage(updated)
                    if (!isAdded) return@ImagesAdapter
                    if (svgDrawable != null) {
                        val trimmed = svgDrawable.trimTransparentEdges()
                        Log.d("SVG", "${svgDrawable.intrinsicWidth}x${svgDrawable.intrinsicHeight} → ${trimmed.intrinsicWidth}x${trimmed.intrinsicHeight}")
                        viewModel.addSvgSticker(
                            trimmed,
                            svgXml,
                            requireActivity(),
                            entity.is_premium,
                            applyWhiteTintInDarkMode = true,
                        )
                    } else if (bitmap != null) {
                        viewModel.addSticker(bitmap.trimTransparentEdges(), requireActivity(), ElementType.IMAGE, entity.is_premium)
                    }
                    if (mainViewModel.isPanelExpanded(PanelType.SHAPES)) mainViewModel.togglePanel(PanelType.SHAPES)
                },
                onLongPress = { entity ->
                    mainViewModel.toggleShapesSelection(entity.id)
                },
            )
        }
        imagesAdapter?.applyWhiteTint = requireContext().isDarkModeEnabled()
        binding.objects.adapter = imagesAdapter

        val data = mainViewModel.shapesData.value
        lastShapesData = data
        categoryImages = sliceFor(data, category)
        submitImages(categoryImages)

        if (savedScrollPos > 0) {
            binding.objects.post {
                (binding.objects.layoutManager as? GridLayoutManager)?.scrollToPosition(savedScrollPos)
            }
        }
    }

    // ── Panel expand/collapse ─────────────────────────────────────────────────

    fun onPanelExpanded(expanded: Boolean) {
        if (isPanelExpanded == expanded) return
        isPanelExpanded = expanded
        if (_binding == null) return

        val rv = binding.objects
        if (rv.width == 0) {
            rv.post {
                if (_binding != null) onPanelExpanded(expanded)
            }
            return
        }

        if (expanded) {
            binding.objects.edgeEffectFactory = RecyclerView.EdgeEffectFactory()
            binding.objects.translationX = 0f
        } else {
            binding.objects.edgeEffectFactory = HorizontalSpringEdgeEffectFactory()
        }

        mainViewModel.clearShapesSelection()
        prevSelectedIds = emptySet()
        prevWasInMode = false
        imagesAdapter?.clearSelectionShadow()
        binding.swipeRefresh?.isEnabled = expanded

        val lm = binding.objects.layoutManager as? MorphGridLayoutManager
        if (lm != null) {
            lm.applyFraction(binding.objects, if (expanded) 1f else 0f)
            if (imagesAdapter?.isExpanded != expanded) {
                binding.objects.recycledViewPool.clear()
                imagesAdapter?.isExpanded = expanded
            }
        }
        val bottomPadding = if (expanded) (64 * resources.displayMetrics.density).toInt() else 0
        binding.objects.setPadding(
            binding.objects.paddingLeft,
            binding.objects.paddingTop,
            binding.objects.paddingRight,
            bottomPadding,
        )

        // Sync item size on final settle state
        val adapter = imagesAdapter ?: return
        val rvWidth = binding.objects.width
        val rvPadding = binding.objects.paddingLeft + binding.objects.paddingRight
        val offset = if (expanded) 1f else 0f
        adapter.slideOffset = offset
        adapter.recyclerViewWidth = rvWidth
        adapter.recyclerViewPadding = rvPadding

        for (i in 0 until binding.objects.childCount) {
            val child = binding.objects.getChildAt(i)
            val holder = binding.objects.getChildViewHolder(child) as? ImagesAdapter.ImageViewHolder
            holder?.updateSize(offset, rvWidth, rvPadding)
        }
    }

    fun onPanelSlide(offset: Float) {
        if (_binding == null) return
        val lm = binding.objects.layoutManager as? MorphGridLayoutManager
        if (lm != null) {
            lm.applyFraction(binding.objects, offset)
            val effectiveExpanded = offset >= 0.95f
            if (imagesAdapter?.isExpanded != effectiveExpanded) {
                binding.objects.recycledViewPool.clear()
                imagesAdapter?.isExpanded = effectiveExpanded
            }
        }

        // Smoothly animate bottom padding during slide to avoid jerking
        val maxPadding = (64 * resources.displayMetrics.density).toInt()
        val currentPadding = (offset * maxPadding).toInt()
        binding.objects.setPadding(
            binding.objects.paddingLeft,
            binding.objects.paddingTop,
            binding.objects.paddingRight,
            currentPadding,
        )

        // Smoothly update size of all visible items in 60fps!
        val adapter = imagesAdapter ?: return
        val rvWidth = binding.objects.width
        val rvPadding = binding.objects.paddingLeft + binding.objects.paddingRight
        adapter.slideOffset = offset
        adapter.recyclerViewWidth = rvWidth
        adapter.recyclerViewPadding = rvPadding

        for (i in 0 until binding.objects.childCount) {
            val child = binding.objects.getChildAt(i)
            val holder = binding.objects.getChildViewHolder(child) as? ImagesAdapter.ImageViewHolder
            holder?.updateSize(offset, rvWidth, rvPadding)
        }
    }

    // ── Called by ShapesParentFragment ────────────────────────────────────────

    fun onNewData(data: ShapesData) {
        if (lastShapesData === data) return
        lastShapesData = data
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

    private fun submitImages(images: List<ImageEntity>) {
        val final = if (filterText.isBlank()) {
            images
        } else {
            images.filter { it.matchesQuery(filterText) }
        }
        if (_binding == null) return
        binding.noEmojis.visibility = if (final.isEmpty()) View.VISIBLE else View.GONE
        imagesAdapter?.submitList(final)
        onFilterResult?.invoke(category, final.size)
    }

    private fun saveScrollPos() {
        val lm = _binding?.objects?.layoutManager as? GridLayoutManager ?: return
        val pos = lm.findFirstVisibleItemPosition()
        if (pos >= 0) savedScrollPos = pos
    }

    private fun sliceFor(data: ShapesData, cat: String): List<ImageEntity> = if (cat.equals("Recents", ignoreCase = true)) {
        data.recents
    } else {
        data.imagesByCategory[cat].orEmpty()
    }

    companion object {
        private const val ARG_CATEGORY = "arg_category"
        private const val ARG_FILTER = "arg_filter"
        const val VECTORS_TAB = "Vectors"

        fun newInstance(category: String, initialFilter: String = "") = ShapesListFragment().apply {
            arguments = bundleOf(ARG_CATEGORY to category, ARG_FILTER to initialFilter)
        }
    }
}
