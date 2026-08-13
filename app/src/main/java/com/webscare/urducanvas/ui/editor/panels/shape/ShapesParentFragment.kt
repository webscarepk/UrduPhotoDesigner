package com.webscare.urducanvas.ui.editor.panels.shape

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
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
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.ImageProcessor.trimTransparentEdges
import com.webscare.urducanvas.common.utils.SvgLoader
import com.webscare.urducanvas.common.utils.isDarkModeEnabled
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.ShapesData
import com.webscare.urducanvas.databinding.FragmentShapesParentBinding
import com.webscare.urducanvas.ui.editor.EditorFragment
import com.webscare.urducanvas.ui.editor.panels.images.SelectedItem
import com.webscare.urducanvas.ui.editor.panels.images.ThumbnailAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ShapesParentFragment : Fragment() {

    companion object {
        const val TABLES_TAB = "Tables"
    }

    private var _binding: FragmentShapesParentBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()

    private val fragmentCache = LinkedHashMap<String, Fragment>()
    private val tabs = mutableListOf<String>()
    private var currentTabIndex = 0
    private var currentQuery = ""
    private var lastShapesData: ShapesData? = null
    private var tabListenerAttached = false

    private var lastSlideExpanded: Boolean? = null  // guards applySlideOffset thrash

    private var thumbnailAdapter: ThumbnailAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShapesParentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        setEvents()
        attachDragHandleSwipe()
        setupThumbnailStrip()

        val initial = mainViewModel.shapesData.value
        tabs.clear()
        tabs.add(TABLES_TAB)
        tabs.add(ShapesListFragment.VECTORS_TAB)
        tabs.addAll(initial.tabs.filter { it != TABLES_TAB && it != ShapesListFragment.VECTORS_TAB })

        val requestedTab = arguments?.getString("TARGET_TAB") ?: mainViewModel.lastShapesTabCategory
        currentTabIndex = requestedTab?.let { saved ->
            tabs.indexOf(saved).takeIf { it >= 0 }
        } ?: 0

        if (tabs.isNotEmpty()) {
            rebuildTabLayout(selectIndex = currentTabIndex)
            showTab(currentTabIndex)
        }

        observeShapesData()
        observePanelExpanded()
        observeSelectionMode()
    }

    override fun onDestroyView() {
        tabListenerAttached = false
        lastSlideExpanded = null
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
                if (item is SelectedItem.Image) {
                    mainViewModel.toggleShapesSelection(item.entity.id)
                }
            })
        binding.selectedThumbnails.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
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

    // ── Panel expansion ───────────────────────────────────────────────────────

    private fun observePanelExpanded() {
        // ── 1. Final settled state: update headers only — do NOT touch child RVs ──
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.expandedPanel.collect { panel ->
                    val expanded = panel == PanelType.SHAPES
                    applyExpandedUi(expanded)
                    for ((_, fragment) in fragmentCache) {
                        when (fragment) {
                            is ShapesListFragment -> fragment.onPanelExpanded(expanded)
                            is VectorsTabFragment -> fragment.onPanelExpanded(expanded)
                        }
                    }
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
     * Only alpha/visibility — zero layout passes, zero flicker.
     */
    private fun applySlideOffset(offset: Float) {
        if (_binding == null) return

        // Collapsed header: fully visible at 0, fades out by 0.4
        val collapsedAlpha = (1f - offset / 0.4f).coerceIn(0f, 1f)
        // Expanded header: invisible until 0.3, fully visible at 1.0
        val expandedAlpha  = ((offset - 0.3f) / 0.7f).coerceIn(0f, 1f)

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


        // Forward live offset to child fragments to drive morph transition on every frame
        for ((_, fragment) in fragmentCache) {
            when (fragment) {
                is ShapesListFragment -> fragment.onPanelSlide(offset)
                is VectorsTabFragment -> fragment.onPanelSlide(offset)
                is TablesTabFragment -> fragment.onPanelSlide(offset)
            }
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
        binding.tabLayout.visibility       = if (!expanded) View.VISIBLE else View.GONE
        binding.tabLayoutExpanded.visibility = if (expanded) View.VISIBLE else View.GONE


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
            alpha = 0f
            translationY = slideDistance
            isVisible = false
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.isInShapesMultiSelectMode.collect { inMode ->
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
                mainViewModel.selectedShapesIds.collect { selectedIds ->
                    if (_binding == null) return@collect
                    val count = selectedIds.size
                    binding.selectionCount.text = when (count) {
                        0 -> ""
                        1 -> "1 selected"
                        else -> "$count selected"
                    }

                    val data = mainViewModel.shapesData.value
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
        val data = mainViewModel.shapesData.value
        val selectedIds = mainViewModel.selectedShapesIds.value
        val allImages = buildList {
            addAll(data.recents)
            data.imagesByCategory.values.forEach { addAll(it) }
        }
        val toAdd = allImages.filter { it.id in selectedIds }.distinctBy { it.id }

        viewLifecycleOwner.lifecycleScope.launch {
            toAdd.forEach { entity ->
                val url = Constants.BASE_URL_GLIDE + entity.file_url
                val isSvg = entity.file_name.endsWith(".svg", ignoreCase = true)
                val layerName = com.webscare.urducanvas.common.utils.ImageUtils.getLayerNameForEntity(
                    entity.file_name, entity.alt_text, entity.category, defaultFallback = "Shape"
                )

                if (isSvg) {
                    val result = withContext(Dispatchers.IO) {
                        SvgLoader.resolve(url, entity.bitmapData, applyWhiteTint = requireContext().isDarkModeEnabled())
                    }
                    result?.let { (drawable, xml) ->
                        viewModel.addSvgSticker(
                            drawable.trimTransparentEdges(),
                            xml,
                            requireActivity(),
                            entity.is_premium,
                            applyWhiteTintInDarkMode = true,
                            customName = layerName
                        )
                    }
                } else {
                    val bitmap = withContext(Dispatchers.IO) {
                        runCatching {
                            Glide.with(requireActivity()).asBitmap().load(entity.bitmapData ?: url)
                                .diskCacheStrategy(DiskCacheStrategy.ALL).submit().get()
                        }.getOrNull()
                    }
                    bitmap?.let {
                        viewModel.addSticker(
                            it.trimTransparentEdges(),
                            requireActivity(),
                            com.webscare.urducanvas.common.canvas.enums.ElementType.IMAGE,
                            entity.is_premium,
                            customName = layerName
                        )
                    }
                }
            }

            mainViewModel.clearShapesSelection()
            if (mainViewModel.isPanelExpanded(PanelType.SHAPES)) {
                mainViewModel.togglePanel(PanelType.SHAPES)
            }
        }
    }

    // ── Tab switching ─────────────────────────────────────────────────────────

    /**
     * Switch tabs. Synchronous and idempotent. No Handler debounce queue (which
     * could back up and replay many switches, producing the multi-second freeze).
     */
    private fun showTab(position: Int) {
        if (_binding == null) return
        if (position < 0 || position >= tabs.size) return

        val category = tabs[position]

        // Idempotent: if this tab is already the visible one, do nothing.
        val existing = fragmentCache[category]
        if (currentTabIndex == position && existing != null &&
            existing.isAdded && !existing.isHidden) {
            return
        }

        currentTabIndex = position
        mainViewModel.lastShapesTabCategory = category

        val target: Fragment = when (category) {
            TABLES_TAB -> fragmentCache.getOrPut(category) { TablesTabFragment() }
            ShapesListFragment.VECTORS_TAB -> fragmentCache.getOrPut(category) { VectorsTabFragment() }
            else -> fragmentCache.getOrPut(category) {
                ShapesListFragment.newInstance(category, currentQuery).also { f ->
                    f.onFilterResult = { cat, count -> onTabFilterResult(cat, count > 0) }
                }
            }
        }

        // Plain hide/show (keeps fragments warm; loading stays instant). commit()
        // is async but cheap; no queue to back up because showTab is idempotent.
        childFragmentManager.beginTransaction().setReorderingAllowed(true).apply {
            for ((_, f) in fragmentCache) {
                if (f !== target && f.isAdded && !f.isHidden) hide(f)
            }
            if (!target.isAdded) add(R.id.fragmentContainer, target, category)
            else if (target.isHidden) show(target)
        }.commit()

        val expanded = mainViewModel.isPanelExpanded(PanelType.SHAPES)
        when (target) {
            is ShapesListFragment -> target.onPanelExpanded(expanded)
            is VectorsTabFragment -> target.onPanelExpanded(expanded)
        }
    }

    // ── TabLayout ─────────────────────────────────────────────────────────────

    private var lastBuiltTabs: List<String> = emptyList()

    private fun rebuildTabLayout(selectIndex: Int) {
        // Skip the expensive 2xN custom-tab inflation if the tab set is identical.
        // (removeAllTabs + re-inflate for BOTH layouts on every data emit was the
        // heavy main-thread work behind the multi-second freeze.)
        if (lastBuiltTabs == tabs && binding.tabLayout.tabCount == tabs.size) {
            val safe = selectIndex.coerceIn(0, tabs.lastIndex)
            binding.tabLayout.clearOnTabSelectedListeners()
            binding.tabLayoutExpanded.clearOnTabSelectedListeners()
            binding.tabLayout.getTabAt(safe)?.select()
            binding.tabLayoutExpanded.getTabAt(safe)?.select()
            tabListenerAttached = false
            attachTabListener()
            return
        }
        lastBuiltTabs = tabs.toList()
        tabListenerAttached = false

        listOf(binding.tabLayout, binding.tabLayoutExpanded).forEach { tl ->
            tl.removeAllTabs()
            if (tabs.isEmpty()) return@forEach
            tabs.forEach { category ->
                val tab = tl.newTab()
                val tabView = LayoutInflater.from(context).inflate(R.layout.view_panel_tab, tl, false)
                tabView.findViewById<TextView>(R.id.tabTitle).text = category
                tab.customView = tabView
                tab.contentDescription = category
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
        attachTabListener()
    }

    private fun attachTabListener() {
        if (tabListenerAttached) return
        tabListenerAttached = true

        binding.tabLayout.clearOnTabSelectedListeners()
        binding.tabLayoutExpanded.clearOnTabSelectedListeners()

        // ONE listener per layout. NO cross-layout .select() — that mirror call was
        // re-entering the listener and, combined with onShapesDataChanged ->
        // rebuildTabLayout, could back up a queue of tab rebuilds that froze the
        // main thread for seconds. We sync the other layout's selected INDEX only
        // when it is safe (setScrollPosition does not fire onTabSelected).
        val listener = object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val pos = tab?.position ?: return
                // Keep the other layout visually in sync WITHOUT firing its listener.
                val other = if (tab.parent === binding.tabLayout)
                    binding.tabLayoutExpanded else binding.tabLayout
                if (other.selectedTabPosition != pos) {
                    other.setScrollPosition(pos, 0f, true)
                    other.getTabAt(pos)?.let { otherTab ->
                        // select WITHOUT firing listeners: temporarily detach them
                        other.clearOnTabSelectedListeners()
                        otherTab.select()
                        other.addOnTabSelectedListener(this)
                    }
                }
                binding.root.post {
                    showTab(pos)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        }

        binding.tabLayout.addOnTabSelectedListener(listener)
        binding.tabLayoutExpanded.addOnTabSelectedListener(listener)
    }

    // ── Data observation ──────────────────────────────────────────────────────

    private fun observeShapesData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.shapesData.collect { data -> onShapesDataChanged(data) }
            }
        }
    }

    private fun onShapesDataChanged(data: ShapesData) {
        if (_binding == null) return
        if (lastShapesData === data) return
        lastShapesData = data

        val newApiTabs = data.tabs.filter { it != TABLES_TAB && it != ShapesListFragment.VECTORS_TAB }
        val newTabs = mutableListOf(TABLES_TAB, ShapesListFragment.VECTORS_TAB) + newApiTabs

        if (newTabs != tabs) {
            val currentCategory = tabs.getOrNull(currentTabIndex)
            tabs.clear()
            tabs.addAll(newTabs)
            if (tabs.isEmpty()) return
            val newIndex = currentCategory?.let { tabs.indexOf(it) }?.takeIf { it >= 0 } ?: 0
            currentTabIndex = newIndex
            rebuildTabLayout(selectIndex = newIndex)
            showTab(newIndex)
        }

        for ((cat, frag) in fragmentCache) {
            if (cat != ShapesListFragment.VECTORS_TAB && frag is ShapesListFragment) {
                frag.onNewData(data)
            }
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun applySearch(query: String) {
        currentQuery = query

        for ((cat, frag) in fragmentCache) {
            if (cat != ShapesListFragment.VECTORS_TAB && frag is ShapesListFragment) {
                frag.updateFilter(query)
            }
        }

        if (query.isBlank()) {
            showAllTabs(); return
        }

        val data = mainViewModel.shapesData.value
        listOf(binding.tabLayout, binding.tabLayoutExpanded).forEach { tl ->
            val tabStrip = tl.getChildAt(0) as? ViewGroup ?: return@forEach
            for (i in tabs.indices) {
                val category = tabs[i]
                val hasResults = when (category) {
                    ShapesListFragment.VECTORS_TAB -> true
                    else -> data.imagesByCategory[category].orEmpty().any { it.matchesQuery(query) }
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

    private fun showAllTabs() {
        listOf(binding.tabLayout, binding.tabLayoutExpanded).forEach { tl ->
            val tabStrip = tl.getChildAt(0) as? ViewGroup ?: return@forEach
            for (i in 0 until tabStrip.childCount) tabStrip.getChildAt(i)?.isVisible = true
        }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setEvents() {
        binding.closeExpanded.addPressEffect { mainViewModel.togglePanel(PanelType.SHAPES) }
        binding.cancelSelection.addPressEffect { mainViewModel.clearShapesSelection() }
        binding.doneSelection.addPressEffect { addAllSelectedToCanvas() }

        // Search icon — expands panel, focuses expanded search bar + opens keyboard
        binding.searchIcon.addPressEffect {
            if (!mainViewModel.isPanelExpanded(PanelType.SHAPES)) {
                mainViewModel.togglePanel(PanelType.SHAPES)
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

        // ── Expanded search ──────────────────────────────────────────────
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
}