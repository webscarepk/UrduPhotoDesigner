package com.webscare.urducanvas.ui.editor.panels.objects

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.webscare.urducanvas.common.canvas.enums.ElementType
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
    private var prevWasInMode: Boolean = false          // track previous mode state
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

    // ── Image selection observer ──────────────────────────────────────────────
    //
    // Uses combine() so one emission covers both selection and mode changes.
    //
    // KEY FIX — why the current tab didn't enter selection mode:
    //
    // isInMultiSelectMode setter was a plain field assignment (no notify).
    // The observer set isInMultiSelectMode = true then called updateSelectionForId
    // which only notified the ONE long-pressed item. All other items on the same
    // tab never received any notify — their ViewHolders still had
    // inMultiSelectMode=false in wireClicks, so single tap still added to canvas.
    //
    // Fix: when mode changes (prevWasInMode != inMode), call applyModeToAll()
    // which does notifyItemRangeChanged(0, count, PAYLOAD_SELECTION).
    // This hits every visible ViewHolder with the lightweight payload path —
    // only radio visibility updates, no Glide reload, no shimmer restart.
    // Then separately call updateSelectionForId for the specific changed item.

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

                        // Always update the flag first
                        adapter.isInMultiSelectMode = inMode

                        if (modeChanged) {
                            // Mode flipped — notify ALL items so every ViewHolder
                            // shows/hides its radio icon and rewires its click handler
                            adapter.applyModeToAll()
                            prevWasInMode = inMode
                        }

                        if (!inMode) {
                            // Mode exited — clear all selection state
                            adapter.clearSelectionShadow()
                            prevSelectedIds = emptySet()
                        } else {
                            // Mode active — notify only changed individual items
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
                            // isInMultiSelectMode setter in EmojiAdapter already calls
                            // notifyDataSetChanged — no extra call needed here
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
                    if (isPanelExpanded) mainViewModel.togglePanelExpanded()
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
                    if (isPanelExpanded) mainViewModel.togglePanelExpanded()

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
            // After notifyDataSetChanged from isExpanded, fresh ViewHolders
            // need the current mode — applyModeToAll() ensures they all get it
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

        val paint = emojiAdapter?.getPaint() ?: return

        selectedChars.forEach { char ->
            viewLifecycleOwner.lifecycleScope.launch {
                val bmp = withContext(Dispatchers.Default) {
                    renderEmojiForCanvas(char, paint)
                }
                viewModel.addSticker(bmp, requireActivity(), ElementType.STICKER)
            }
        }
    }

    private fun renderEmojiForCanvas(
        char: String,
        paint: android.graphics.Paint
    ): android.graphics.Bitmap {
        val size     = 2048
        val bounds   = android.graphics.Rect()
        paint.getTextBounds(char, 0, char.length, bounds)
        val glyphW = bounds.width().coerceAtLeast(1)
        val glyphH = bounds.height().coerceAtLeast(1)
        val pad    = ((maxOf(glyphW, glyphH)) * 0.06f).toInt().coerceAtLeast(4)
        val outW   = glyphW + pad * 2
        val outH   = glyphH + pad * 2
        val cSize  = (maxOf(outW, outH) * 1.5f).toInt().coerceAtLeast(size)
        val full   = android.graphics.Bitmap.createBitmap(cSize, cSize, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(full)
        canvas.drawText(char, -bounds.left.toFloat() + pad, -bounds.top.toFloat() + pad, paint)
        return try {
            val cropped = android.graphics.Bitmap.createBitmap(full, 0, 0, outW.coerceAtMost(cSize), outH.coerceAtMost(cSize))
            full.recycle(); cropped
        } catch (e: IllegalArgumentException) { full }
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