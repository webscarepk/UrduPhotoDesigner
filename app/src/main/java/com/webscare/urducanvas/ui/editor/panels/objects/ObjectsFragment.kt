package com.webscare.urducanvas.ui.editor.panels.objects

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.model.EmojiMeta
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.ImageProcessor.downsampleIfNeeded
import com.webscare.urducanvas.common.utils.ImageProcessor.trimTransparentEdges
import com.webscare.urducanvas.common.utils.SvgLoader
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.ObjectsData
import com.webscare.urducanvas.databinding.FragmentObjectsBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import com.google.android.material.tabs.TabLayout
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.ui.editor.EditorFragment
import com.webscare.urducanvas.ui.editor.panels.images.SelectedItem
import com.webscare.urducanvas.ui.editor.panels.images.ThumbnailAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ObjectsFragment : Fragment() {

    private var _binding: FragmentObjectsBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()

    private val fragmentCache = LinkedHashMap<String, ObjectsListFragment>()
    private val tabs = mutableListOf<String>()
    private var currentTabIndex = 0
    private var currentFragment: ObjectsListFragment? = null
    private var currentQuery = ""
    private var lastObjectsData: ObjectsData? = null
    private var tabListenerAttached = false

    // Stable adapter — created once, lives for the lifetime of this fragment
    private var thumbnailAdapter: ThumbnailAdapter? = null

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handlePickedUri(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentObjectsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setEvents()
        attachDragHandleSwipe()
        setupThumbnailStrip()


        val initial = mainViewModel.objectsData.value
        tabs.clear()
        tabs.addAll(initial.tabs)

        currentTabIndex = mainViewModel.lastObjectsTabCategory
            ?.let { savedCategory -> tabs.indexOf(savedCategory).takeIf { it >= 0 } }
            ?: 0

        rebuildTabLayout(selectIndex = currentTabIndex)

        if (tabs.isNotEmpty()) {
            showTab(currentTabIndex.coerceIn(0, tabs.lastIndex))
        }

        observeObjectsData()
        observePanelExpanded()
        observeSelectionMode()
    }

    override fun onDestroyView() {
        tabListenerAttached = false
        fragmentCache.clear()
        _binding = null
        super.onDestroyView()
    }

    // ── Thumbnail strip ───────────────────────────────────────────────────────

    private fun setupThumbnailStrip() {
        // FIX: onDeselect now receives SelectedItem and dispatches correctly
        thumbnailAdapter = ThumbnailAdapter(
            onDeselect = { item ->
                when (item) {
                    is SelectedItem.Image -> mainViewModel.toggleImageSelection(item.entity.id)
                    is SelectedItem.Emoji -> mainViewModel.toggleEmojiSelection(item.meta.char)
                    is SelectedItem.Shape -> { /* images panel has no shape selection */ }
                }
            }
        )
        binding.selectedThumbnails.apply {
            layoutManager = LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false
            )
            adapter = thumbnailAdapter
            isNestedScrollingEnabled = false
        }
    }

    // ── Drag handle ───────────────────────────────────────────────────────────

    private fun attachDragHandleSwipe() {
        // Walk up the fragment hierarchy to find EditorFragment and hand it our
        // drag handle so PanelSheetBehavior drives the guideline directly.
        var f: Fragment? = this
        while (f != null) {
            if (f is EditorFragment) {
                f.attachDragHandle(binding.dragHandle)

                // Also register the top-toolbar areas (collapsed + expanded headers)
                // so swiping down on the toolbar collapses the panel — same gesture
                // as dragging the handle.
                binding.root.post {
                    val b = _binding ?: return@post
                    (f as EditorFragment).panelSheetBehavior()?.let { sheet ->
                        sheet.attachAdditionalHandle(b.headerCollapsed)
                        sheet.attachAdditionalHandle(b.headerExpanded)
                    }
                }
                return
            }
            f = f.parentFragment
        }
    }

    // ── Observe expanded state ────────────────────────────────────────────────

    private fun observePanelExpanded() {
        // ── 1. Final settled state: update headers only — do NOT touch child RVs ──
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.expandedPanel
                    .map { it == PanelType.OBJECTS }
                    .collect { expanded ->
                        applyExpandedUi(expanded)
                        // Only handle selection clear on collapse — do NOT swap RV layout
                        // managers here; onPanelExpandedSmooth already did that at 75%.
                        if (!expanded) {
                            for (fragment in fragmentCache.values.toList()) fragment.onPanelExpanded(false)
                        }
                    }
            }
        }

        // ── 2. Live slide offset: drives smooth crossfade every frame ───────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.panelSlideOffset.collect { offset ->
                    applySlideOffset(offset)
                }
            }
        }
    }

    /**
     * Driven every frame by PanelSheetBehavior during drag + spring settle.
     * Only alpha/visibility — zero layout passes, zero flicker.
     */
    private fun applySlideOffset(offset: Float) {
        if (_binding == null) return

        // Collapsed header: fully visible at 0, fades out by 0.4
        val collapsedAlpha = (1f - offset / 0.4f).coerceIn(0f, 1f)
        // Expanded header: invisible until 0.3, fully visible at 1.0
        val expandedAlpha = ((offset - 0.3f) / 0.7f).coerceIn(0f, 1f)

        binding.headerCollapsed.alpha = collapsedAlpha
        binding.headerExpanded.alpha  = expandedAlpha

        // GONE when fully hidden (takes no space), INVISIBLE only mid-fade
        binding.headerCollapsed.visibility =
            if (collapsedAlpha > 0f) View.VISIBLE else View.GONE
        binding.headerExpanded.visibility =
            if (expandedAlpha > 0f) View.VISIBLE else View.GONE

        // Tab layouts mirror their respective headers
        val isSearchActive = currentQuery.isNotBlank()
        if (!isSearchActive) {
            binding.tabLayout.alpha         = collapsedAlpha
            binding.tabLayoutExpanded.alpha = expandedAlpha
            binding.tabLayout.visibility =
                if (collapsedAlpha > 0f) View.VISIBLE else View.GONE
            binding.tabLayoutExpanded.visibility =
                if (expandedAlpha > 0f) View.VISIBLE else View.GONE
        }


        // Switch child RecyclerView layout managers at 75 % of travel —
        // while the spring is still in motion so the user never sees a jump.
        val effectiveExpanded = offset >= 0.75f
        for (fragment in fragmentCache.values.toList()) {
            fragment.onPanelExpandedSmooth(effectiveExpanded)
        }
    }

    private fun applyExpandedUi(expanded: Boolean) {
        applySlideOffset(if (expanded) 1f else 0f)

        if (expanded) {
            binding.searchBarExpanded.setText(currentQuery)
            binding.searchBarExpanded.setSelection(binding.searchBarExpanded.text?.length ?: 0)
            updateExpandedSearchCross(currentQuery)
        } else {
            hideKeyboard()
            binding.searchBarExpanded.text?.clear()
        }
    }

    // ── Selection toolbar ─────────────────────────────────────────────────────

    private fun observeSelectionMode() {
        val slideDistance = 200 * resources.displayMetrics.density

        binding.selectionToolbar.apply {
            alpha        = 0f
            translationY = slideDistance
            isVisible    = false
        }

        // Toolbar show/hide driven by isInMultiSelectMode (covers both images + emojis)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.isInMultiSelectMode.collect { inMode ->
                    if (_binding == null) return@collect
                    if (inMode) {
                        binding.selectionToolbar.isVisible = true
                        binding.selectionToolbar.animate()
                            .alpha(1f).translationY(0f)
                            .setDuration(240)
                            .setInterpolator(DecelerateInterpolator(1.5f))
                            .start()
                    } else {
                        val endY = binding.selectionToolbar.height
                            .takeIf { it > 0 }?.toFloat() ?: slideDistance
                        binding.selectionToolbar.animate()
                            .alpha(0f).translationY(endY)
                            .setDuration(180)
                            .setInterpolator(AccelerateInterpolator(1.5f))
                            .withEndAction {
                                if (_binding == null) return@withEndAction
                                binding.selectionToolbar.isVisible = false
                                binding.selectionToolbar.translationY = endY
                            }.start()
                    }
                }
            }
        }

        // FIX: combine both selectedImageIds and selectedEmojiChars for
        // accurate count label and complete thumbnail strip
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    mainViewModel.selectedImageIds,
                    mainViewModel.selectedEmojiChars
                ) { imageIds, emojiChars -> Pair(imageIds, emojiChars) }
                    .collect { (imageIds, emojiChars) ->
                        if (_binding == null) return@collect

                        val totalCount = imageIds.size + emojiChars.size
                        binding.selectionCount.text = when (totalCount) {
                            0    -> ""
                            1    -> "1 selected"
                            else -> "$totalCount selected"
                        }

                        // Build SelectedItem list for thumbnail strip
                        val data = mainViewModel.objectsData.value
                        val allImages = buildList {
                            addAll(data.recents)
                            data.imagesByCategory.values.forEach { addAll(it) }
                        }

                        val imageItems = allImages
                            .filter { it.id in imageIds }
                            .distinctBy { it.id }
                            .map { SelectedItem.Image(it) }

                        val emojiItems = emojiChars.map { char ->
                            SelectedItem.Emoji(
                                meta         = EmojiMeta(char = char, name = ""),
                                cachedBitmap = null
                            )
                        }

                        thumbnailAdapter?.submitList(imageItems + emojiItems)
                    }
            }
        }
    }

    // ── Done — add all selected to canvas ─────────────────────────────────────

    private fun addAllSelectedToCanvas() {
        val data        = mainViewModel.objectsData.value
        val selectedIds = mainViewModel.selectedImageIds.value
        val allImages   = buildList {
            addAll(data.recents)
            data.imagesByCategory.values.forEach { addAll(it) }
        }
        val toAdd = allImages.filter { it.id in selectedIds }.distinctBy { it.id }

        viewLifecycleOwner.lifecycleScope.launch {
            // Add selected images
            toAdd.forEach { entity ->
                val url   = Constants.BASE_URL_GLIDE + entity.file_url
                val isSvg = entity.file_name.endsWith(".svg", ignoreCase = true)

                if (isSvg) {
                    val result = withContext(Dispatchers.IO) {
                        SvgLoader.resolve(url, entity.bitmapData)
                    }
                    result?.let { (drawable, xml) ->
                        viewModel.addSvgSticker(
                            drawable.trimTransparentEdges(), xml,
                            requireActivity(), entity.is_premium
                        )
                    }
                } else {
                    val source: Any = entity.bitmapData ?: url
                    val bitmap = withContext(Dispatchers.IO) {
                        runCatching {
                            val raw = com.bumptech.glide.Glide.with(requireActivity()).asBitmap()
                                .load(source)
                                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                                .submit().get()
                            // Apply GPU hard-limit only — preserve full detail for stickers/objects
                            downsampleIfNeeded(raw, GPU_SAFE_MAX_PX, GPU_SAFE_MAX_PX)
                        }.getOrNull()
                    }
                    bitmap?.let {
                        viewModel.addSticker(
                            it.trimTransparentEdges(),
                            requireActivity(),
                            ElementType.IMAGE,
                            entity.is_premium
                        )
                    }
                }
            }

            // Add selected emojis — delegate to each alive ObjectsListFragment
            // that is a base tab (emoji tab) and has selected chars
            for (fragment in fragmentCache.values.toList()) {
                fragment.addSelectedEmojisToCanvas()
            }

            // FIX: clear ALL selection (images + emojis)
            mainViewModel.clearAllSelection()
            if (mainViewModel.isPanelExpanded(PanelType.OBJECTS)) mainViewModel.togglePanel(PanelType.OBJECTS)
        }
    }

    // ── Tab switching ─────────────────────────────────────────────────────────

    private fun showTab(position: Int) {
        if (position < 0 || position >= tabs.size) return
        val category = tabs[position]
        currentTabIndex = position
        mainViewModel.lastObjectsTabCategory = category

        val target = fragmentCache.getOrPut(category) {
            ObjectsListFragment.newInstance(category, currentQuery).also { f ->
                f.onFilterResult = { cat, count -> onTabFilterResult(cat, count > 0) }
            }
        }

        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .apply {
                for (f in childFragmentManager.fragments) {
                    if (f !== target && !f.isHidden) hide(f)
                }
                if (!target.isAdded) add(R.id.fragmentContainer, target, category)
                else if (target.isHidden) show(target)
            }
            .commitNow()

        currentFragment = target
        val isExpanded = mainViewModel.isPanelExpanded(PanelType.OBJECTS)
        target.onPanelExpandedSmooth(isExpanded)
        target.onPanelExpanded(isExpanded)
    }

    // ── TabLayout ─────────────────────────────────────────────────────────────

    private fun rebuildTabLayout(selectIndex: Int) {
        tabListenerAttached = false

        listOf(binding.tabLayout, binding.tabLayoutExpanded).forEach { tl ->
            tl.removeAllTabs()
            tabs.forEach { category ->
                val tab = tl.newTab()
                val tabView = LayoutInflater.from(context)
                    .inflate(R.layout.custom_tab, tl, false)
                tabView.findViewById<TextView>(R.id.tabTitle).text = category
                tab.customView = tabView
                tl.addTab(tab, false)
            }
            val safe = selectIndex.coerceIn(0, tabs.lastIndex)
            tl.getTabAt(safe)?.select()
            tl.post {
                tl.getTabAt(safe)?.view?.let { v ->
                    tl.scrollTo((v.left - tl.width / 2 + v.width / 2).coerceAtLeast(0), 0)
                }
            }
        }
        attachTabListeners()
    }

    private fun attachTabListeners() {
        if (tabListenerAttached) return
        tabListenerAttached = true

        val listener = object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val pos = tab?.position ?: return
                tab.view.animate().scaleX(1f).scaleY(1f).setDuration(100)
                    .setInterpolator(OvershootInterpolator(1.2f)).start()
                val other = if (tab.parent == binding.tabLayout)
                    binding.tabLayoutExpanded else binding.tabLayout
                if (other.selectedTabPosition != pos) other.getTabAt(pos)?.select()
                showTab(pos)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.view?.animate()?.scaleX(0.9f)?.scaleY(0.9f)?.setDuration(100)?.start()
            }
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        }

        binding.tabLayout.addOnTabSelectedListener(listener)
        binding.tabLayoutExpanded.addOnTabSelectedListener(listener)
    }

    // ── Data observation ──────────────────────────────────────────────────────

    private fun observeObjectsData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.objectsData.collect { data -> onObjectsDataChanged(data) }
            }
        }
    }

    private fun onObjectsDataChanged(data: ObjectsData) {
        if (_binding == null) return
        if (lastObjectsData === data) return
        lastObjectsData = data

        val tabsChanged = data.tabs != tabs
        if (tabsChanged) {
            val currentCategory = tabs.getOrNull(currentTabIndex)
            tabs.clear()
            tabs.addAll(data.tabs)
            val newIndex = currentCategory?.let { tabs.indexOf(it) }
                ?.takeIf { it >= 0 }
                ?: currentTabIndex.coerceAtMost(tabs.lastIndex)
            currentTabIndex = newIndex
            rebuildTabLayout(selectIndex = newIndex)
        }

        for (fragment in fragmentCache.values.toList()) fragment.onNewData(data)
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun applySearch(query: String) {
        currentQuery = query
        for (fragment in fragmentCache.values.toList()) fragment.updateFilter(query)

        if (query.isBlank()) { showAllTabs(); return }

        val data = mainViewModel.objectsData.value
        listOf(binding.tabLayout, binding.tabLayoutExpanded).forEach { tl ->
            val tabStrip = tl.getChildAt(0) as? ViewGroup ?: return@forEach
            for (i in tabs.indices) {
                val category = tabs[i]
                val hasResults = when {
                    ObjectsListFragment.isBaseTab(category) ->
                        ObjectsListFragment.emojiDataForCategory(category)
                            .any { it.name.contains(query, ignoreCase = true) }
                    category.equals("Recents", ignoreCase = true) ->
                        data.recents.any { it.matchesQuery(query) }
                    else ->
                        data.imagesByCategory[category].orEmpty()
                            .any { it.matchesQuery(query) }
                }
                tabStrip.getChildAt(i)?.isVisible = hasResults
            }
        }
        jumpToFirstVisibleTab()
    }

    private fun onTabFilterResult(category: String, hasResults: Boolean) {
        if (_binding == null) return
        val i = tabs.indexOf(category).takeIf { it >= 0 } ?: return
        listOf(binding.tabLayout, binding.tabLayoutExpanded).forEach { tl ->
            val tabStrip = tl.getChildAt(0) as? ViewGroup ?: return@forEach
            val tabView  = tabStrip.getChildAt(i) ?: return@forEach
            if (tabView.isVisible == hasResults) return@forEach
            tabView.isVisible = hasResults
        }
        if (!hasResults && binding.tabLayout.selectedTabPosition == i) jumpToFirstVisibleTab()
    }

    private fun jumpToFirstVisibleTab() {
        val tabStrip = binding.tabLayout.getChildAt(0) as? ViewGroup ?: return
        for (i in tabs.indices) {
            if (tabStrip.getChildAt(i)?.isVisible == true) {
                showTab(i)
                binding.tabLayout.getTabAt(i)?.select()
                binding.tabLayoutExpanded.getTabAt(i)?.select()
                return
            }
        }
    }

    private fun showAllTabs() {
        listOf(binding.tabLayout, binding.tabLayoutExpanded).forEach { tl ->
            val tabStrip = tl.getChildAt(0) as? ViewGroup ?: return@forEach
            for (i in 0 until tabStrip.childCount) tabStrip.getChildAt(i)?.isVisible = true
        }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setEvents() {
        binding.addImage.addPressEffect { pickImage.launch("image/*") }
        binding.addImageExpanded.addPressEffect { pickImage.launch("image/*") }
        binding.closeExpanded.addPressEffect { mainViewModel.togglePanel(PanelType.OBJECTS) }

        // FIX: cancel clears ALL selection (images + emojis)
        binding.cancelSelection.addPressEffect { mainViewModel.clearAllSelection() }
        binding.doneSelection.addPressEffect   { addAllSelectedToCanvas() }

        // Search icon — expands panel, focuses expanded search bar + opens keyboard
        binding.searchIcon.addPressEffect {
            if (!mainViewModel.isPanelExpanded(PanelType.OBJECTS)) {
                mainViewModel.togglePanel(PanelType.OBJECTS)
            }
            binding.root.post {
                if (_binding == null) return@post
                binding.searchBarExpanded.requestFocus()
                binding.searchBarExpanded.setSelection(
                    binding.searchBarExpanded.text?.length ?: 0
                )
                showKeyboard(binding.searchBarExpanded)
            }
        }

        binding.searchBarExpanded.imeOptions = EditorInfo.IME_ACTION_SEARCH
        binding.searchBarExpanded.setRawInputType(InputType.TYPE_CLASS_TEXT)

        binding.searchBarExpanded.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applySearch(binding.searchBarExpanded.text.toString())
                hideKeyboard(); true
            } else false
        }

        binding.searchBarExpanded.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateExpandedSearchCross(s?.toString().orEmpty())
                applySearch(s?.toString().orEmpty())
            }
        })

        binding.searchBarExpanded.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val dr = binding.searchBarExpanded.compoundDrawables[2]
                if (dr != null && event.x >= binding.searchBarExpanded.width -
                    binding.searchBarExpanded.paddingRight - dr.bounds.width()
                ) {
                    // Clear text + search
                    binding.searchBarExpanded.text.clear()
                    applySearch("")
                    // Remove the cross drawable immediately
                    updateExpandedSearchCross("")
                    // Dismiss keyboard and remove focus
                    hideKeyboard()
                    binding.searchBarExpanded.clearFocus()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun updateExpandedSearchCross(text: String) {
        binding.searchBarExpanded.setCompoundDrawablesWithIntrinsicBounds(
            null, null,
            if (text.isNotEmpty()) ContextCompat.getDrawable(requireActivity(), R.drawable.ic_close)
            else null, null
        )
    }

    private fun showKeyboard(v: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.searchBarExpanded.clearFocus()
    }

    private fun handlePickedUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val filePath = ImageProcessor.copyUriToTempFile(requireActivity(), uri)
                    ?.absolutePath ?: return@launch
                val rawBitmap = ImageProcessor.filePathToBitmap(filePath) ?: return@launch

                // Objects panel: user explicitly chose this image as a sticker/element.
                // Preserve as much detail as possible — only apply the GPU hard-limit cap
                // (24 MP / 4899 px per side) to prevent a hard crash. No canvas-relative
                // downscale here; CanvasView's display-proxy system handles rendering perf.
                val bitmap = downsampleIfNeeded(rawBitmap, GPU_SAFE_MAX_PX, GPU_SAFE_MAX_PX)

                withContext(Dispatchers.Main) {
                    viewModel.addSticker(bitmap, requireActivity(), ElementType.IMAGE)
                }
            } catch (e: Exception) {
                Log.e("PhotoPicker", "Failed compressing image", e)
            }
        }
    }

    companion object {
        val BASE_TABS = listOf(
            "Emoticons", "Animals", "Nature", "Food", "Sports",
            "Transport", "Objects", "Alchemy", "Shapes", "Arrows", "Letters", "Flags"
        )

        // GPU hard limit: 24 MP (ARGB_8888 @ 4 bytes/px = 96 MB). Applied everywhere an
        // image enters the canvas, so we never crash regardless of source resolution.
        // CanvasView's display-proxy system keeps rendering smooth even at this size.
        private const val GPU_SAFE_MAX_PX = 4899
    }
}