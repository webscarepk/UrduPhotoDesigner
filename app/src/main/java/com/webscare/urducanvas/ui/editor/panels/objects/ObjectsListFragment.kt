package com.webscare.urducanvas.ui.editor.panels.objects

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.common.utils.EmojiBitmapRenderer
import com.webscare.urducanvas.common.utils.ImageProcessor.trimTransparentEdges
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.data.model.ObjectsData
import com.webscare.urducanvas.databinding.FragmentObjectsListBinding
import com.webscare.urducanvas.ui.editor.panels.images.ImagesAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@AndroidEntryPoint
class ObjectsListFragment : androidx.fragment.app.Fragment() {

    private var _binding: FragmentObjectsListBinding? = null
    private val binding get() = _binding!!

    var onFilterResult: ((category: String, count: Int) -> Unit)? = null

    private val mainViewModel: com.webscare.urducanvas.viewmodels.MainViewModel
            by activityViewModels()
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel
            by activityViewModels()

    private var imagesAdapter: ImagesAdapter? = null
    private var emojiAdapter: EmojiAdapter? = null

    var category: String = ""; private set
    private var filterText: String = ""
    private var categoryImages: List<ImageEntity> = emptyList()
    private var lastObjectsData: ObjectsData? = null
    private var savedScrollPos: Int = 0
    private var filterJob: Job? = null
    private var isPanelExpanded: Boolean = false

    private var prevSelectedIds: Set<Int> = emptySet()
    private var prevWasInMode: Boolean = false
    private var prevSelectedEmojiChars: Set<String> = emptySet()
    private var prevEmojiWasInMode: Boolean = false

    private var baseEmojiData: List<com.webscare.urducanvas.common.canvas.model.EmojiMeta> =
        emptyList()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            category   = it.getString(ARG_CATEGORY).orEmpty()
            filterText = it.getString(ARG_FILTER).orEmpty()
        }
        if (isBaseTab(category)) {
            baseEmojiData = emojiDataForCategory(category)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentObjectsListBinding.inflate(layoutInflater, container, false)
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
        setupExpandGesture()    // ← NEW: swipe-up on RecyclerView to expand

        if (isBaseTab(category)) {
            setupEmojiTab()
            observeEmojiSelectionState()
        } else {
            setupImageTab()
            observeSelectionState()
        }
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
                shuffleCurrentList()
                isRefreshing = false
            }
        }
    }

    private fun shuffleCurrentList() {
        if (isBaseTab(category)) {
            emojiAdapter?.updateData(baseEmojiData.shuffled())
        } else {
            categoryImages = categoryImages.shuffled()
            submitImages(categoryImages)
        }
    }

    // ── Swipe-up to expand gesture ────────────────────────────────────────────
    //
    // Same logic as ImagesListFragment.setupExpandGesture():
    //
    // In collapsed state the RecyclerView is HORIZONTAL — it consumes
    // left/right drag itself. Vertical drag is not consumed, so we intercept
    // upward vertical swipes to expand the panel.
    //
    // In expanded state returns false immediately — the vertical RecyclerView
    // and SwipeRefreshLayout handle all touch events themselves.
    //
    // Directional guard: dy > dx * 1.5f ensures we only trigger on gestures
    // that are more vertical than horizontal, so horizontal scroll works fine.

    @SuppressLint("ClickableViewAccessibility")
    private fun setupExpandGesture() {
        val thresholdPx = 40 * resources.displayMetrics.density
        var startY = 0f
        var startX = 0f

        binding.objects.setOnTouchListener { _, event ->
            // Expanded: let RecyclerView and SwipeRefreshLayout handle everything
            if (isPanelExpanded) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startX = event.rawX
                    false   // don't consume — RecyclerView needs it for scroll init
                }
                MotionEvent.ACTION_UP -> {
                    val dy = startY - event.rawY   // positive = finger moved up
                    val dx = abs(startX - event.rawX)

                    if (dy > thresholdPx && dy > dx * 1.5f) {
                        mainViewModel.togglePanel(PanelType.OBJECTS)
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    // ── Image selection observer ──────────────────────────────────────────────

    private fun observeSelectionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    mainViewModel.selectedImageIds,
                    mainViewModel.isInMultiSelectMode
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

    // ── Emoji selection observer ──────────────────────────────────────────────

    private fun observeEmojiSelectionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    mainViewModel.selectedEmojiChars,
                    mainViewModel.isInMultiSelectMode
                ) { chars, inMode -> Pair(chars, inMode) }
                    .collect { (newChars, inMode) ->
                        val adapter = emojiAdapter ?: return@collect

                        val modeChanged = inMode != prevEmojiWasInMode
                        adapter.isInMultiSelectMode = inMode

                        if (modeChanged) {
                            prevEmojiWasInMode = inMode
                        }

                        if (!inMode) {
                            adapter.clearSelectionShadow()
                            prevSelectedEmojiChars = emptySet()
                        } else {
                            val added   = newChars - prevSelectedEmojiChars
                            val removed = prevSelectedEmojiChars - newChars
                            added.forEach   { char -> adapter.updateSelectionForChar(char, true)  }
                            removed.forEach { char -> adapter.updateSelectionForChar(char, false) }
                            prevSelectedEmojiChars = newChars
                        }
                    }
            }
        }
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupEmojiTab() {
        if (emojiAdapter == null) {
            emojiAdapter = EmojiAdapter(
                context        = requireActivity(),
                initialEmojis  = baseEmojiData,
                onEmojiClicked = { bmp ->
                    if (isPanelExpanded) mainViewModel.togglePanel(PanelType.OBJECTS)
                    viewModel.addSticker(bmp, requireActivity(), ElementType.STICKER)
                },
                onEmojiLongPress = { emoji ->
                    mainViewModel.toggleEmojiSelection(emoji.char)
                }
            )
        }
        binding.objects.layoutManager = buildLayoutManager(isPanelExpanded)
        binding.objects.adapter = emojiAdapter

        if (filterText.isBlank()) {
            onFilterResult?.invoke(category, baseEmojiData.size)
        } else {
            applyEmojiFilter(filterText)
        }
    }

    private fun setupImageTab() {
        if (imagesAdapter == null) {
            imagesAdapter = ImagesAdapter(
                context = requireActivity(),
                onImageSelected = { bitmap, svgDrawable, svgXml, entity ->
                    if (isPanelExpanded) mainViewModel.togglePanel(PanelType.OBJECTS)

                    val updated = if (svgXml != null && entity.bitmapData == null)
                        entity.copy(is_recent = true, bitmapData = svgXml)
                    else entity.copy(is_recent = true)

                    mainViewModel.updateImage(updated)

                    if (isAdded) {
                        if (svgDrawable != null) {
                            val trimmed = svgDrawable.trimTransparentEdges()
                            Log.d("SVG",
                                "${svgDrawable.intrinsicWidth}x${svgDrawable.intrinsicHeight}" +
                                        " → ${trimmed.intrinsicWidth}x${trimmed.intrinsicHeight}")
                            viewModel.addSvgSticker(
                                trimmed, svgXml, requireActivity(), entity.is_premium
                            )
                        } else {
                            viewModel.addSticker(
                                bitmap?.trimTransparentEdges(),
                                requireActivity(),
                                ElementType.IMAGE,
                                entity.is_premium
                            )
                        }
                    }
                },
                onLongPress = { entity ->
                    mainViewModel.toggleImageSelection(entity.id)
                }
            )
        }
        binding.objects.layoutManager = buildLayoutManager(isPanelExpanded)
        binding.objects.adapter = imagesAdapter

        if (categoryImages.isEmpty()) {
            val data = mainViewModel.objectsData.value
            lastObjectsData = data
            categoryImages = sliceFor(data, category)
        }
        submitImages(categoryImages)

        if (savedScrollPos > 0) {
            binding.objects.post {
                (binding.objects.layoutManager as? GridLayoutManager)
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
            mainViewModel.clearImageSelection()
            mainViewModel.clearEmojiSelection()
            prevSelectedIds        = emptySet()
            prevWasInMode          = false
            prevSelectedEmojiChars = emptySet()
            prevEmojiWasInMode     = false
            imagesAdapter?.clearSelectionShadow()
            emojiAdapter?.clearSelectionShadow()
        }

        binding.swipeRefresh?.isEnabled = expanded
        binding.objects.recycledViewPool.clear()
        binding.objects.layoutManager = buildLayoutManager(expanded)

        if (isBaseTab(category)) {
            emojiAdapter?.isExpanded = expanded
        } else {
            imagesAdapter?.isExpanded = expanded
            val currentMode = mainViewModel.isInMultiSelectMode.value
            imagesAdapter?.isInMultiSelectMode = currentMode
            if (currentMode) imagesAdapter?.applyModeToAll()
        }

        if (expanded) binding.objects.scrollToPosition(0)
    }

    fun addSelectedEmojisToCanvas() {
        if (!isBaseTab(category)) return
        val selectedChars = mainViewModel.selectedEmojiChars.value
        if (selectedChars.isEmpty()) return

        selectedChars.forEach { char ->
            viewLifecycleOwner.lifecycleScope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    EmojiBitmapRenderer.render(char, sizePx = 512)
                }
                viewModel.addSticker(bmp, requireActivity(), ElementType.STICKER)
            }
        }
    }

    private fun buildLayoutManager(expanded: Boolean): GridLayoutManager =
        GridLayoutManager(
            requireContext(),
            3,
            if (expanded) GridLayoutManager.VERTICAL else GridLayoutManager.HORIZONTAL,
            false
        )

    // ── Called by ObjectsFragment ─────────────────────────────────────────────

    fun onNewData(data: ObjectsData) {
        if (isBaseTab(category)) return
        if (lastObjectsData === data) return
        lastObjectsData = data

        binding.swipeRefresh?.isRefreshing = false

        val slice = sliceFor(data, category)
        if (slice === categoryImages) return
        categoryImages = slice
        submitImages(slice)
    }

    fun updateFilter(newFilter: String) {
        if (filterText == newFilter) return
        filterText = newFilter
        if (isBaseTab(category)) {
            applyEmojiFilter(newFilter)
        } else {
            submitImages(categoryImages)
        }
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    private fun applyEmojiFilter(filter: String) {
        filterJob?.cancel()

        if (filter.isBlank()) {
            if (_binding == null) return
            binding.noEmojis.visibility =
                if (baseEmojiData.isEmpty()) View.VISIBLE else View.GONE
            emojiAdapter?.updateData(baseEmojiData)
            onFilterResult?.invoke(category, baseEmojiData.size)
            return
        }

        filterJob = viewLifecycleOwner.lifecycleScope.launch {
            val filtered = withContext(Dispatchers.Default) {
                baseEmojiData.filter { it.name.contains(filter, ignoreCase = true) }
            }
            if (_binding == null) return@launch
            binding.noEmojis.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            emojiAdapter?.updateData(filtered)
            onFilterResult?.invoke(category, filtered.size)
        }
    }

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
        if (isBaseTab(category)) return
        val lm = _binding?.objects?.layoutManager as? GridLayoutManager ?: return
        val pos = lm.findFirstVisibleItemPosition()
        if (pos >= 0) savedScrollPos = pos
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sliceFor(data: ObjectsData, cat: String): List<ImageEntity> =
        if (cat.equals("Recents", ignoreCase = true)) data.recents
        else data.imagesByCategory[cat].orEmpty()

    companion object {
        private const val ARG_CATEGORY = "arg_category"
        private const val ARG_FILTER   = "arg_filter"

        fun isBaseTab(tab: String) =
            ObjectsFragment.BASE_TABS.any { it.equals(tab, ignoreCase = true) }

        fun emojiDataForCategory(
            category: String
        ): List<com.webscare.urducanvas.common.canvas.model.EmojiMeta> = when (category) {
            "Emoticons"  -> com.webscare.urducanvas.common.utils.Constants.META_EMOTICONS
            "Animals"    -> com.webscare.urducanvas.common.utils.Constants.META_ANIMALS
            "Nature"     -> com.webscare.urducanvas.common.utils.Constants.META_NATURE
            "Food"       -> com.webscare.urducanvas.common.utils.Constants.META_FOOD
            "Sports"     -> com.webscare.urducanvas.common.utils.Constants.META_SPORTS
            "Transport"  -> com.webscare.urducanvas.common.utils.Constants.META_TRANSPORT
            "Objects"    -> com.webscare.urducanvas.common.utils.Constants.META_OBJECTS
            "Alchemy"    -> com.webscare.urducanvas.common.utils.Constants.META_ALCHEMY
            "Shapes"     -> com.webscare.urducanvas.common.utils.Constants.META_SHAPES
            "Arrows"     -> com.webscare.urducanvas.common.utils.Constants.META_ARROWS
            "Letters"    -> com.webscare.urducanvas.common.utils.Constants.META_LETTERS
            "Flags"      -> com.webscare.urducanvas.common.utils.Constants.META_FLAGS
            else         -> emptyList()
        }

        fun newInstance(category: String, initialFilter: String = "") =
            ObjectsListFragment().apply {
                arguments = bundleOf(
                    ARG_CATEGORY to category,
                    ARG_FILTER   to initialFilter
                )
            }
    }
}