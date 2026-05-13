package com.webscare.urducanvas.ui.editor.panels.shape

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
import com.webscare.urducanvas.common.utils.ImageProcessor.trimTransparentEdges
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.data.model.ShapesData
import com.webscare.urducanvas.databinding.FragmentObjectsListBinding
import com.webscare.urducanvas.ui.editor.panels.images.ImagesAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

@AndroidEntryPoint
class ShapesListFragment : Fragment() {

    private var _binding: FragmentObjectsListBinding? = null
    private val binding get() = _binding!!

    var onFilterResult: ((category: String, count: Int) -> Unit)? = null

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()

    private var imagesAdapter: ImagesAdapter? = null

    var category: String = ""; private set
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
            category   = it.getString(ARG_CATEGORY).orEmpty()
            filterText = it.getString(ARG_FILTER).orEmpty()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentObjectsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.objects.apply {
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

    // ── Swipe-up to expand ────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupExpandGesture() {
        val thresholdPx = 40 * resources.displayMetrics.density
        var startY = 0f
        var startX = 0f

        binding.objects.setOnTouchListener { _, event ->
            if (isPanelExpanded) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startY = event.rawY; startX = event.rawX; false }
                MotionEvent.ACTION_UP -> {
                    val dy = startY - event.rawY
                    val dx = abs(startX - event.rawX)
                    if (dy > thresholdPx && dy > dx * 1.5f) {
                        mainViewModel.togglePanel(PanelType.SHAPES)
                        true
                    } else false
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
                    mainViewModel.selectedShapesIds,
                    mainViewModel.isInShapesMultiSelectMode
                ) { ids, inMode -> Pair(ids, inMode) }
                    .collect { (newIds, inMode) ->
                        val adapter = imagesAdapter ?: return@collect
                        val modeChanged = inMode != prevWasInMode
                        adapter.isInMultiSelectMode = inMode
                        if (modeChanged) { adapter.applyModeToAll(); prevWasInMode = inMode }
                        if (!inMode) {
                            adapter.clearSelectionShadow(); prevSelectedIds = emptySet()
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
                    if (svgDrawable != null) {
                        val trimmed = svgDrawable.trimTransparentEdges()
                        Log.d("SVG", "${svgDrawable.intrinsicWidth}x${svgDrawable.intrinsicHeight} → ${trimmed.intrinsicWidth}x${trimmed.intrinsicHeight}")
                        viewModel.addSvgSticker(trimmed, svgXml, requireActivity(), entity.is_premium)
                    } else if (bitmap != null) {
                        viewModel.addSticker(bitmap.trimTransparentEdges(), requireActivity(), ElementType.IMAGE, entity.is_premium)
                    }
                },
                onLongPress = { entity ->
                    mainViewModel.toggleShapesSelection(entity.id)
                }
            )
        }

        binding.objects.layoutManager = buildLayoutManager(isPanelExpanded)
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

        if (!expanded) {
            mainViewModel.clearShapesSelection()
            prevSelectedIds = emptySet()
            prevWasInMode   = false
            imagesAdapter?.clearSelectionShadow()
        }

        binding.swipeRefresh?.isEnabled = expanded
        binding.objects.recycledViewPool.clear()
        binding.objects.layoutManager = buildLayoutManager(expanded)
        imagesAdapter?.isExpanded = expanded

        val currentMode = mainViewModel.isInShapesMultiSelectMode.value
        imagesAdapter?.isInMultiSelectMode = currentMode
        if (currentMode) imagesAdapter?.applyModeToAll()

        if (expanded) binding.objects.scrollToPosition(0)
    }

    private fun buildLayoutManager(expanded: Boolean): GridLayoutManager =
        GridLayoutManager(
            requireContext(), 3,
            if (expanded) GridLayoutManager.VERTICAL else GridLayoutManager.HORIZONTAL,
            false
        )

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
        val final = if (filterText.isBlank()) images
        else images.filter { it.matchesQuery(filterText) }
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

    private fun sliceFor(data: ShapesData, cat: String): List<ImageEntity> =
        if (cat.equals("Recents", ignoreCase = true)) data.recents
        else data.imagesByCategory[cat].orEmpty()

    companion object {
        private const val ARG_CATEGORY = "arg_category"
        private const val ARG_FILTER   = "arg_filter"
        const val VECTORS_TAB = "Vectors"

        fun newInstance(category: String, initialFilter: String = "") =
            ShapesListFragment().apply {
                arguments = bundleOf(ARG_CATEGORY to category, ARG_FILTER to initialFilter)
            }
    }
}