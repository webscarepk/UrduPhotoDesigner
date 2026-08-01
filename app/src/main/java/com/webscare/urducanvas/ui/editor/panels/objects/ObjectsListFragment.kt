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
import com.webscare.urducanvas.common.utils.MorphGridLayoutManager
import com.webscare.urducanvas.common.utils.HorizontalSpringEdgeEffectFactory
import com.webscare.urducanvas.ui.editor.panels.images.ImagesAdapter
import com.webscare.urducanvas.common.utils.isDarkModeEnabled
import androidx.recyclerview.widget.RecyclerView
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
            layoutManager = MorphGridLayoutManager(
                context = requireContext(),
                collapsedSpan = 3,
                expandedSpan = 3
            ).apply {
                applyFraction(binding.objects, if (mainViewModel.isPanelExpanded(PanelType.OBJECTS)) 1f else 0f)
            }
        }

        setupSwipeRefresh()

        if (isBaseTab(category)) {
            setupEmojiTab()
            observeEmojiSelectionState()
        } else {
            setupImageTab()
            observeSelectionState()
        }

        val isExpandedNow = mainViewModel.isPanelExpanded(PanelType.OBJECTS)
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
        imagesAdapter?.cancelPreload()
        _binding?.objects?.adapter = null
        super.onDestroyView()
        _binding = null
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
                initialEmojis  = emptyList(),
                onEmojiClicked = { bmp ->
                    if (isPanelExpanded) mainViewModel.togglePanel(PanelType.OBJECTS)
                    viewModel.addSticker(bmp, requireActivity(), ElementType.STICKER, customName = "Emoji")
                },
                onEmojiLongPress = { emoji ->
                    mainViewModel.toggleEmojiSelection(emoji.char)
                }
            )
        }
        binding.objects.adapter = emojiAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            val emojiList = withContext(Dispatchers.Default) {
                emojiDataForCategory(category)
            }
            baseEmojiData = emojiList
            if (_binding != null) {
                if (filterText.isBlank()) {
                    emojiAdapter?.updateData(emojiList)
                    onFilterResult?.invoke(category, emojiList.size)
                } else {
                    applyEmojiFilter(filterText)
                }
            }
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
                        val layerName = com.webscare.urducanvas.common.utils.ImageUtils.getLayerNameForEntity(
                            entity.file_name, entity.alt_text, entity.category, defaultFallback = "Sticker"
                        )

                        if (svgDrawable != null) {
                            val trimmed = try { svgDrawable.trimTransparentEdges() } catch (e: Throwable) { svgDrawable }
                            Log.d("SVG",
                                "${svgDrawable.intrinsicWidth}x${svgDrawable.intrinsicHeight}" +
                                        " → ${trimmed.intrinsicWidth}x${trimmed.intrinsicHeight}")
                            viewModel.addSvgSticker(
                                trimmed, svgXml, requireActivity(), entity.is_premium,
                                applyWhiteTintInDarkMode = true,
                                customName = layerName
                            )
                        } else {
                            val trimmedBmp = try { bitmap?.trimTransparentEdges() } catch (e: Throwable) { bitmap }
                            viewModel.addSticker(
                                trimmedBmp,
                                requireActivity(),
                                ElementType.IMAGE,
                                entity.is_premium,
                                customName = layerName
                            )
                        }
                    }
                },
                onLongPress = { entity ->
                    if (mainViewModel.isPanelExpanded(PanelType.OBJECTS)) {
                        mainViewModel.toggleImageSelection(entity.id)
                    }
                }
            )
        }
        imagesAdapter?.applyWhiteTint = requireContext().isDarkModeEnabled()
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

        val rv = binding.objects
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
            binding.objects.edgeEffectFactory = RecyclerView.EdgeEffectFactory()
            binding.objects.translationX = 0f
        } else {
            binding.objects.edgeEffectFactory = HorizontalSpringEdgeEffectFactory()
        }

        mainViewModel.clearImageSelection()
        mainViewModel.clearEmojiSelection()
        prevSelectedIds        = emptySet()
        prevWasInMode          = false
        prevSelectedEmojiChars = emptySet()
        prevEmojiWasInMode     = false
        imagesAdapter?.clearSelectionShadow()
        emojiAdapter?.clearSelectionShadow()

        binding.swipeRefresh?.isEnabled = expanded
        val lm = binding.objects.layoutManager as? MorphGridLayoutManager
        if (lm != null) {
            lm.applyFraction(binding.objects, if (expanded) 1f else 0f)
            if (isBaseTab(category)) {
                if (emojiAdapter?.isExpanded != expanded) {
                    binding.objects.recycledViewPool.clear()
                    emojiAdapter?.isExpanded = expanded
                }
            } else {
                if (imagesAdapter?.isExpanded != expanded) {
                    binding.objects.recycledViewPool.clear()
                    imagesAdapter?.isExpanded = expanded
                }
            }
        }

        // Sync item size on final settle state
        val rvWidth   = binding.objects.width
        val rvPadding = binding.objects.paddingLeft + binding.objects.paddingRight
        val offset    = if (expanded) 1f else 0f

        if (isBaseTab(category)) {
            val ea = emojiAdapter ?: return
            ea.slideOffset         = offset
            ea.recyclerViewWidth   = rvWidth
            ea.recyclerViewPadding = rvPadding
            for (i in 0 until binding.objects.childCount) {
                val child  = binding.objects.getChildAt(i)
                val holder = binding.objects.getChildViewHolder(child) as? EmojiAdapter.EmojiViewHolder
                holder?.updateSize(offset, rvWidth, rvPadding)
            }
        } else {
            val ia = imagesAdapter ?: return
            ia.slideOffset         = offset
            ia.recyclerViewWidth   = rvWidth
            ia.recyclerViewPadding = rvPadding
            for (i in 0 until binding.objects.childCount) {
                val child  = binding.objects.getChildAt(i)
                val holder = binding.objects.getChildViewHolder(child) as? ImagesAdapter.ImageViewHolder
                holder?.updateSize(offset, rvWidth, rvPadding)
            }
        }
    }

    fun onPanelSlide(offset: Float) {
        if (_binding == null) return
        binding.swipeRefresh?.isEnabled = offset >= 0.95f
        val lm = binding.objects.layoutManager as? MorphGridLayoutManager
        if (lm != null) {
            lm.applyFraction(binding.objects, offset)
            val effectiveExpanded = offset >= 0.95f
            if (isBaseTab(category)) {
                if (emojiAdapter?.isExpanded != effectiveExpanded) {
                    binding.objects.recycledViewPool.clear()
                    emojiAdapter?.isExpanded = effectiveExpanded
                }
            } else {
                if (imagesAdapter?.isExpanded != effectiveExpanded) {
                    binding.objects.recycledViewPool.clear()
                    imagesAdapter?.isExpanded = effectiveExpanded
                }
            }
        }

        val rvWidth   = binding.objects.width
        val rvPadding = binding.objects.paddingLeft + binding.objects.paddingRight

        // Smoothly update size of all visible items in 60fps!
        if (isBaseTab(category)) {
            val ea = emojiAdapter ?: return
            ea.slideOffset         = offset
            ea.recyclerViewWidth   = rvWidth
            ea.recyclerViewPadding = rvPadding
            for (i in 0 until binding.objects.childCount) {
                val child  = binding.objects.getChildAt(i)
                val holder = binding.objects.getChildViewHolder(child) as? EmojiAdapter.EmojiViewHolder
                holder?.updateSize(offset, rvWidth, rvPadding)
            }
        } else {
            val ia = imagesAdapter ?: return
            ia.slideOffset         = offset
            ia.recyclerViewWidth   = rvWidth
            ia.recyclerViewPadding = rvPadding
            for (i in 0 until binding.objects.childCount) {
                val child  = binding.objects.getChildAt(i)
                val holder = binding.objects.getChildViewHolder(child) as? ImagesAdapter.ImageViewHolder
                holder?.updateSize(offset, rvWidth, rvPadding)
            }
        }
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
                viewModel.addSticker(bmp, requireActivity(), ElementType.STICKER, customName = "Emoji")
            }
        }
    }

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