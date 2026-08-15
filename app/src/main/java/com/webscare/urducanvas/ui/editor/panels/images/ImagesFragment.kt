package com.webscare.urducanvas.ui.editor.panels.images

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
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.tabs.TabLayout
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.ImageProcessor.downsampleIfNeeded
import com.webscare.urducanvas.common.utils.ImageProcessor.trimTransparentEdges
import com.webscare.urducanvas.common.utils.SvgLoader
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.ImagesData
import com.webscare.urducanvas.databinding.FragmentImagesBinding
import com.webscare.urducanvas.ui.editor.EditorFragment
import com.webscare.urducanvas.ui.editor.panels.text.fonts.imported.ImportedFontsBottomSheet.Companion.TAG
import com.webscare.urducanvas.viewmodels.MainViewModel
import com.webscare.urducanvas.viewmodels.PexelsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ImagesFragment : Fragment() {

    private var _binding: FragmentImagesBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()
    private val pexelsViewModel: PexelsViewModel by activityViewModels()

    // Fragment type is now Fragment (not ImagesListFragment) to hold both types
    private val fragmentCache = LinkedHashMap<String, ImagesListFragment>()
    private val tabs = mutableListOf<String>()
    private var currentTabIndex = 0
    private var currentFragment: ImagesListFragment? = null
    private var currentQuery = ""
    private var lastImagesData: ImagesData? = null
    private var tabListenerAttached = false

    private var thumbnailAdapter: ThumbnailAdapter? = null

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handlePickedUri(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setEvents()
        attachDragHandleSwipe()
        setupThumbnailStrip()


        val initial = mainViewModel.imagesData.value
        tabs.clear()
        tabs.addAll(initial.tabs)

        currentTabIndex = mainViewModel.lastImagesTabCategory?.let { savedCategory ->
            tabs.indexOf(savedCategory).takeIf { it >= 0 }
        } ?: 0

        if (tabs.isNotEmpty()) {
            rebuildTabLayout(selectIndex = currentTabIndex)
            showTab(currentTabIndex.coerceIn(0, tabs.lastIndex))
        }

        observeImagesData()
        observePanelExpanded()
        observeSelectionMode()
    }

    override fun onDestroyView() {
        tabListenerAttached = false
        _binding?.selectedThumbnails?.adapter = null
        thumbnailAdapter = null
        fragmentCache.clear()
        super.onDestroyView()
        _binding = null
    }

    // ── Thumbnail strip ───────────────────────────────────────────────────────

    private fun setupThumbnailStrip() {
        thumbnailAdapter = ThumbnailAdapter(
            onDeselect = { item ->
                when (item) {
                    is SelectedItem.Image -> mainViewModel.toggleImagesSelection(item.entity.id)
                    is SelectedItem.Emoji -> {}
                    is SelectedItem.Shape -> {}
                }
            })
        binding.selectedThumbnails.apply {
            layoutManager = LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false
            )
            adapter = thumbnailAdapter
            isNestedScrollingEnabled = false
        }
    }

    // ── Drag handle ───────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
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

    // ── Panel expansion ───────────────────────────────────────────────────────

    private fun observePanelExpanded() {

        // ── 1. Final settled state: update headers only — do NOT touch child RVs ──
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.expandedPanel.map { it == PanelType.IMAGES }.collect { expanded ->
                    applyExpandedUi(expanded)
                    for ((_, fragment) in fragmentCache) fragment.onPanelExpanded(expanded)
                }
            }
        }

        // ── 2. Live slide offset: drives smooth crossfade every frame ───────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                mainViewModel.panelSlideOffset.collect { offset ->
                    applySlideOffset(offset)
                }
            }
        }
    }

    /**
     * Driven every frame by PanelSheetBehavior during drag + spring settle.
     * Only alpha/scale — zero layout passes, zero flicker.
     */
    private fun applySlideOffset(offset: Float) {
        if (_binding == null) return

        val collapsedAlpha = (1f - offset / 0.4f).coerceIn(0f, 1f)
        val expandedAlpha  = ((offset - 0.3f) / 0.7f).coerceIn(0f, 1f)

        binding.headerCollapsed.alpha = collapsedAlpha
        binding.headerExpanded.alpha  = expandedAlpha
        binding.headerCollapsed.visibility = if (collapsedAlpha > 0f) View.VISIBLE else View.GONE
        binding.headerExpanded.visibility  = if (expandedAlpha  > 0f) View.VISIBLE else View.GONE

        val isSearchActive = currentQuery.isNotBlank()
        if (!isSearchActive) {
            binding.tabLayout.alpha          = collapsedAlpha
            binding.tabLayoutExpanded.alpha  = expandedAlpha
            binding.tabExpandedContainer.alpha = expandedAlpha
            binding.tabLayout.visibility     = if (collapsedAlpha > 0f) View.VISIBLE else View.GONE
            binding.tabLayoutExpanded.visibility = if (expandedAlpha > 0f) View.VISIBLE else View.GONE
            binding.tabExpandedContainer.visibility = if (expandedAlpha > 0f) View.VISIBLE else View.GONE
        }

        // Forward live offset to child fragments to drive morph transition on every frame
        for ((_, fragment) in fragmentCache) {
            fragment.onPanelSlide(offset)
        }
    }

    private fun applyExpandedUi(expanded: Boolean) {

        // Snap headers/tabs to final state
        binding.headerCollapsed.alpha      = if (!expanded) 1f else 0f
        binding.headerExpanded.alpha       = if (expanded)  1f else 0f
        binding.headerCollapsed.visibility = if (!expanded) View.VISIBLE else View.GONE
        binding.headerExpanded.visibility  = if (expanded)  View.VISIBLE else View.GONE
        binding.tabLayout.alpha            = if (!expanded) 1f else 0f
        binding.tabLayoutExpanded.alpha    = if (expanded)  1f else 0f
        binding.tabExpandedContainer.alpha = if (expanded)  1f else 0f
        binding.tabLayout.visibility       = if (!expanded) View.VISIBLE else View.GONE
        binding.tabLayoutExpanded.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.tabExpandedContainer.visibility = if (expanded) View.VISIBLE else View.GONE

        // Images-specific: search results header swap
        val isSearchActive = currentQuery.isNotBlank()
        val headerText = if (isSearchActive) "Results for '${currentQuery}'" else ""
        binding.tabLayout.isVisible = !expanded && !isSearchActive
        binding.searchResultsHeader.isVisible = !expanded && isSearchActive
        if (!expanded && isSearchActive) binding.searchResultsHeader.text = headerText
        binding.tabLayoutExpanded.isVisible = expanded && !isSearchActive
        binding.tabExpandedContainer.isVisible = expanded && !isSearchActive
        binding.searchResultsHeaderExpanded.isVisible = expanded && isSearchActive
        if (expanded && isSearchActive) binding.searchResultsHeaderExpanded.text = headerText

        if (expanded) {
            binding.searchBarExpanded.setText(currentQuery)
            binding.searchBarExpanded.setSelection(binding.searchBarExpanded.text?.length ?: 0)
            updateExpandedSearchCross(currentQuery)
        } else {
            hideKeyboard()
            binding.searchBarExpanded.clearFocus()
            binding.searchBarExpanded.text?.clear()
            if (currentQuery.isNotBlank()) {
                applySearch("")
                pexelsViewModel.clearSearch()
            }
        }

        binding.dragHandle.scaleX = 1f
        binding.dragHandle.scaleY = 1f
    }

    // ── Selection toolbar ─────────────────────────────────────────────────────

    private fun observeSelectionMode() {
        val slideDistance = 200 * resources.displayMetrics.density

        binding.selectionToolbar.apply {
            alpha = 0f
            translationY = slideDistance
            isVisible = false
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.isInImagesMultiSelectMode.collect { inMode ->
                    if (_binding == null) return@collect
                    if (inMode) {
                        binding.selectionToolbar.isVisible = true
                        binding.selectionToolbar.animate().alpha(1f).translationY(0f)
                            .setDuration(240).setInterpolator(DecelerateInterpolator(1.5f)).start()
                    } else {
                        val endY = binding.selectionToolbar.height.takeIf { it > 0 }?.toFloat()
                            ?: slideDistance
                        binding.selectionToolbar.animate().alpha(0f).translationY(endY)
                            .setDuration(180).setInterpolator(AccelerateInterpolator(1.5f))
                            .withEndAction {
                                if (_binding == null) return@withEndAction
                                binding.selectionToolbar.isVisible = false
                                binding.selectionToolbar.translationY = endY
                            }.start()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.selectedImagesIds.collect { selectedIds ->
                    if (_binding == null) return@collect

                    val count = selectedIds.size
                    binding.selectionCount.text = when (count) {
                        0 -> ""
                        1 -> "1 selected"
                        else -> "$count selected"
                    }

                    val data = mainViewModel.imagesData.value
                    val allImages = buildList {
                        addAll(data.recents)
                        data.imagesByCategory.values.forEach { addAll(it) }
                    }
                    val selected = allImages.filter { it.id in selectedIds }.distinctBy { it.id }
                        .map { SelectedItem.Image(it) }

                    thumbnailAdapter?.submitList(selected)
                }
            }
        }
    }

    // ── Done — add all selected to canvas ─────────────────────────────────────

    private fun addAllSelectedToCanvas() {
        val data = mainViewModel.imagesData.value
        val selectedIds = mainViewModel.selectedImagesIds.value
        val allImages = buildList {
            addAll(data.recents)
            data.imagesByCategory.values.forEach { addAll(it) }
        }
        val toAdd = allImages.filter { it.id in selectedIds }.distinctBy { it.id }

        viewLifecycleOwner.lifecycleScope.launch {
            toAdd.forEach { entity ->
                // resolveUrl() handles both Pexels (full URL) and own images (needs base prefix)
                val url = resolveUrl(entity)
                val isSvg = entity.file_name.endsWith(".svg", ignoreCase = true)
                val layerName = com.webscare.urducanvas.common.utils.ImageUtils.getLayerNameForEntity(
                    entity.file_name, entity.alt_text, entity.category, defaultFallback = "Image"
                )

                // Always add as image element — never as background
                if (isSvg) {
                    val result = withContext(Dispatchers.IO) {
                        SvgLoader.resolve(url, entity.bitmapData)
                    }
                    result?.let { (drawable, xml) ->
                        viewModel.addSvgSticker(
                            drawable.trimTransparentEdges(),
                            xml,
                            requireActivity(),
                            entity.is_premium,
                            customName = layerName
                        )
                    }
                } else {
                    val bitmap = withContext(Dispatchers.IO) {
                        runCatching {
                            val raw = Glide.with(requireActivity()).asBitmap()
                                .load(entity.bitmapData ?: url).centerCrop()
                                .diskCacheStrategy(DiskCacheStrategy.ALL).submit().get()
                            downsampleIfNeeded(raw, com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX, com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX)
                        }.getOrNull()
                    }
                    bitmap?.let {
                        viewModel.addSticker(
                            it.trimTransparentEdges(),
                            requireActivity(),
                            ElementType.IMAGE,
                            entity.is_premium,
                            customName = layerName
                        )
                    }
                }
            }

            mainViewModel.clearImagesSelection()
            if (mainViewModel.isPanelExpanded(PanelType.IMAGES)) mainViewModel.togglePanel(PanelType.IMAGES)
        }
    }

    // ── Tab switching ─────────────────────────────────────────────────────────

    private fun showTab(position: Int) {
        if (position < 0 || position >= tabs.size) return
        val category = tabs[position]
        currentTabIndex = position
        mainViewModel.lastImagesTabCategory = category

        val target = fragmentCache.getOrPut(category) {
            ImagesListFragment.newInstance(category, currentQuery).also { f ->
                f.onFilterResult = { cat, count -> onTabFilterResult(cat, count > 0) }
            }
        }

        childFragmentManager.beginTransaction().setReorderingAllowed(true).apply {
            for (f in childFragmentManager.fragments) {
                if (f !== target && !f.isHidden) hide(f)
            }
            if (!target.isAdded) add(R.id.fragmentContainer, target, category)
            else if (target.isHidden) show(target)
        }.commitNow()

        currentFragment = target
        val isExpanded = mainViewModel.isPanelExpanded(PanelType.IMAGES)
        target.onPanelExpanded(isExpanded)
    }

    // ── TabLayout ─────────────────────────────────────────────────────────────

    private fun rebuildTabLayout(selectIndex: Int) {
        tabListenerAttached = false

        listOf(binding.tabLayout, binding.tabLayoutExpanded).forEach { tl ->
            tl.removeAllTabs()
            tabs.forEach { category ->
                val tab = tl.newTab()
                val tabView = LayoutInflater.from(context).inflate(R.layout.view_panel_tab, tl, false)
                tabView.findViewById<TextView>(R.id.tabTitle).text = category
                tab.customView = tabView
                tl.addTab(tab, false)
            }
            val safe = selectIndex.coerceIn(0, tabs.lastIndex)
            tl.getTabAt(safe)?.select()
            for (i in 0 until tl.tabCount) {
                com.webscare.urducanvas.common.utils.PanelTabHelper.updateTabStyle(tl.getTabAt(i), i == safe)
            }
            com.webscare.urducanvas.common.utils.PanelTabHelper.scrollToTabIfOverflows(tl, safe)
        }
        attachTabListener()
    }

    private fun attachTabListener() {
        if (tabListenerAttached) return
        tabListenerAttached = true

        val listener = object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val pos = tab?.position ?: return
                listOf(binding.tabLayout, binding.tabLayoutExpanded).forEach { tl ->
                    for (i in 0 until tl.tabCount) {
                        com.webscare.urducanvas.common.utils.PanelTabHelper.updateTabStyle(tl.getTabAt(i), i == pos)
                    }
                }
                tab.view.animate().scaleX(1f).scaleY(1f).setDuration(100)
                    .setInterpolator(OvershootInterpolator(1.2f)).start()
                val other =
                    if (tab.parent == binding.tabLayout) binding.tabLayoutExpanded else binding.tabLayout
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

    private fun observeImagesData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.imagesData.collect { data -> onImagesDataChanged(data) }
            }
        }
    }

    private fun onImagesDataChanged(data: ImagesData) {
        if (_binding == null) return
        if (lastImagesData === data) return
        lastImagesData = data

        val tabsChanged = data.tabs != tabs
        if (tabsChanged) {
            val currentCategory = tabs.getOrNull(currentTabIndex)
            tabs.clear()
            tabs.addAll(data.tabs)
            if (tabs.isEmpty()) return

            val newIndex = currentCategory?.let { tabs.indexOf(it) }?.takeIf { it >= 0 } ?: 0
            currentTabIndex = newIndex
            rebuildTabLayout(selectIndex = newIndex)
            showTab(newIndex)
        }

        for ((_, fragment) in fragmentCache) fragment.onNewData(data)
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun applySearch(query: String) {
        currentQuery = query

        // ── Search UI: hide tabs, show results header ─────────────────────────
        // Both headers (collapsed + expanded) match the height of their respective
        // tab layouts exactly — RecyclerView never shifts position.
        val isExpanded = mainViewModel.isPanelExpanded(PanelType.IMAGES)
        val headerText = if (query.isNotBlank()) "Results for '${query}'" else ""

        // Search only happens in expanded mode now — collapsed has no search bar
        if (query.isNotBlank()) {
            binding.tabLayoutExpanded.isVisible = false
            binding.searchResultsHeaderExpanded.isVisible = isExpanded
            if (isExpanded) binding.searchResultsHeaderExpanded.text = headerText
            // Also hide collapsed tab layout so header is consistent
            binding.tabLayout.isVisible = false
            binding.searchResultsHeader.isVisible = false  // not used anymore
        } else {
            binding.tabLayout.isVisible = true
            binding.searchResultsHeader.isVisible = false
            binding.tabLayoutExpanded.isVisible = isExpanded
            binding.searchResultsHeaderExpanded.isVisible = false
            showAllTabs()
        }

        val currentCategory = tabs.getOrNull(currentTabIndex).orEmpty()
        val isOnRecents = currentCategory.equals("Recents", ignoreCase = true)

        // Broadcast filter to all non-Pexels fragments.
        // When on Recents: still filter ALL tabs — results appear across all tabs
        // rather than being limited to the small recents list.
        for ((cat, fragment) in fragmentCache.toMap()) {
            if (!pexelsViewModel.isPexelsTab(cat)) fragment.updateFilter(query)
        }

        // Pexels tabs: local Room search (text change = no API cost)
        // If on Recents, pick the first Pexels tab as the active search tab
        if (query.isNotBlank()) {
            val searchTab = if (isOnRecents) {
                tabs.firstOrNull { pexelsViewModel.isPexelsTab(it) } ?: currentCategory
            } else currentCategory
            pexelsViewModel.searchLocal(query, fromTab = searchTab)
        } else {
            pexelsViewModel.clearSearch()
            for ((cat, fragment) in fragmentCache.toMap()) {
                if (pexelsViewModel.isPexelsTab(cat)) fragment.updateFilter("")
            }
        }
    }

    private fun onTabFilterResult(category: String, hasResults: Boolean) {
        if (_binding == null) return
        val i = tabs.indexOf(category).takeIf { it >= 0 } ?: return
        listOf(binding.tabLayout, binding.tabLayoutExpanded).forEach { tl ->
            val tabStrip = tl.getChildAt(0) as? ViewGroup ?: return@forEach
            val tabView = tabStrip.getChildAt(i) ?: return@forEach
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

    /** Called when user presses IME search button — triggers API call if needed. */
    private fun submitSearch(query: String) {
        if (query.isBlank()) return
        val currentCategory = tabs.getOrNull(currentTabIndex).orEmpty()
        val isOnRecents = currentCategory.equals("Recents", ignoreCase = true)
        val searchTab = if (isOnRecents) {
            tabs.firstOrNull { pexelsViewModel.isPexelsTab(it) } ?: currentCategory
        } else currentCategory
        pexelsViewModel.submitSearch(query, fromTab = searchTab)
    }

    private fun showAllTabs() {
        listOf(binding.tabLayout, binding.tabLayoutExpanded).forEach { tl ->
            val tabStrip = tl.getChildAt(0) as? ViewGroup ?: return@forEach
            for (i in 0 until tabStrip.childCount) tabStrip.getChildAt(i)?.isVisible = true
        }
    }

    // ── Dispatch helpers — handles both fragment types cleanly ────────────────


    // ── Events ────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setEvents() {
        binding.addImage.addPressEffect { pickImage.launch("image/*") }
        binding.addImageExpanded.addPressEffect { pickImage.launch("image/*") }
        binding.closeExpanded.addPressEffect { mainViewModel.togglePanel(PanelType.IMAGES) }

        binding.cancelSelection.addPressEffect { mainViewModel.clearImagesSelection() }
        binding.doneSelection.addPressEffect { addAllSelectedToCanvas() }

        // Search icon in collapsed mode: expand panel → focus expanded search bar → keyboard
        // No search bar shown in collapsed mode at all — cleaner UX
        binding.searchIcon.addPressEffect {
            mainViewModel.togglePanel(PanelType.IMAGES)   // expand
            // Post so the expanded header is visible before we focus
            binding.root.post {
                binding.searchBarExpanded.requestFocus()
                binding.searchBarExpanded.setSelection(binding.searchBarExpanded.text?.length ?: 0)
                showKeyboard(binding.searchBarExpanded)
            }
        }

        binding.searchBarExpanded.imeOptions = EditorInfo.IME_ACTION_SEARCH
        binding.searchBarExpanded.setRawInputType(InputType.TYPE_CLASS_TEXT)

        binding.searchBarExpanded.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val q = binding.searchBarExpanded.text.toString()
                applySearch(q)
                submitSearch(q)   // triggers API call if local results < 5
                hideKeyboard()
                true
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
                if (dr != null && event.x >= binding.searchBarExpanded.width - binding.searchBarExpanded.paddingRight - dr.bounds.width()) {
                    binding.searchBarExpanded.text.clear()
                    applySearch("")
                    updateExpandedSearchCross("")
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
            null,
            null,
            if (text.isNotEmpty()) ContextCompat.getDrawable(requireActivity(), R.drawable.ic_close)
            else null,
            null
        )
    }

    private fun showKeyboard(v: View) {
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
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
                val fileName = com.webscare.urducanvas.common.utils.ImageUtils.getFileNameFromUri(requireActivity(), uri)
                Log.d(TAG, "handlePickedUri: $fileName")
                val filePath =
                    ImageProcessor.copyUriToTempFile(requireActivity(), uri)?.absolutePath
                        ?: return@launch
                val rawBitmap = ImageProcessor.filePathToBitmap(filePath) ?: return@launch

                tabs.getOrNull(currentTabIndex).orEmpty()
                // User picked image from gallery — always add as image element
                 val bitmap = downsampleIfNeeded(rawBitmap, com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX, com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX)

                withContext(Dispatchers.Main) {
                    viewModel.addSticker(bitmap, requireActivity(), ElementType.IMAGE, customName = fileName)
                }
            } catch (e: Exception) {
                Log.e("ImagesFragment", "Failed to import image", e)
            }
        }
    }

    companion object {
    }
}