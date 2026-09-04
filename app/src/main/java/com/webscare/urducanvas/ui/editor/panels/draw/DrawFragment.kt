package com.webscare.urducanvas.ui.editor.panels.draw

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.core.view.isVisible
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.PanelTabHelper
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import android.widget.TextView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.webscare.urducanvas.common.canvas.enums.BrushStyle
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.databinding.FragmentDrawBinding
import com.webscare.urducanvas.ui.editor.panels.adjustments.PanelSearchDialogFragment
import com.webscare.urducanvas.ui.editor.panels.draw.brush.BrushPagerAdapter
import com.webscare.urducanvas.ui.editor.panels.draw.eraser.EraserSizeFragment
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DrawFragment : Fragment() {

    private var _binding: FragmentDrawBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private val brushTabs = listOf(
        PanelTabs(0, "Style", true),
        PanelTabs(1, "Settings", false),
        PanelTabs(2, "Color", false)
    )
    private val tabTitles = listOf("Style", "Settings", "Color")

    private lateinit var brushPagerAdapter: BrushPagerAdapter
    private var mediator: TabLayoutMediator? = null
    private var inCategoryMode = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDrawBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabs()
        setupEraserPanel()
        setupModeSwitch()
        setupSearchBar()
        setupEvents()
        observeDrawingMode()
    }

    /**
     * The catalog runs to 68 brushes across 12 shelves, which is more than anyone wants to
     * scroll. The search icon opens the same bottom-anchored dialog the text panel uses and
     * writes into the shared query, which [BrushStyleFragment] reads.
     */
    private fun setupSearchBar() {
        binding.searchIcon.addPressEffect {
            PanelSearchDialogFragment.newInstance()
                .show(childFragmentManager, "brush_search_dialog")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.searchQuery.collect { query ->
                    val binding = _binding ?: return@collect
                    binding.searchIcon.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(
                            requireContext(),
                            if (query.isNotEmpty()) R.color.appColor else R.color.gray
                        )
                    )
                }
            }
        }
    }

    // ── Horizontal Tabs & ViewPager2 ──────────────────────────────────────────

    private fun setupTabs() {
        brushPagerAdapter = BrushPagerAdapter(this, brushTabs)
        binding.viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.adapter = brushPagerAdapter

        // Horizontal swipe on pager in brush mode is locked; tabs are navigated by tapping
        binding.viewPager.isUserInputEnabled = false

        showPanelTabs()
    }

    /**
     * Normal tab row: Style · Settings · Color.
     *
     * The Style tab carries a chevron; tapping it while it is already selected drills into
     * the brush shelves, the same gesture the text panel uses to get from its preset groups
     * into preset categories.
     */
    private fun showPanelTabs() {
        val b = _binding ?: return
        inCategoryMode = false
        viewModel.setBrushCategoryFilter(null)

        mediator?.detach()
        b.tabLayout.clearOnTabSelectedListeners()
        b.tabLayout.removeAllTabs()

        // Search only applies to the brush catalog, so the icon follows the Style tab and
        // the query is dropped on the way out — otherwise Settings would open filtered.
        mediator = PanelTabHelper.setupCustomPanelTabs(b.tabLayout, b.viewPager, tabTitles) { position ->
            b.searchIcon.isVisible = position == 0
            if (position != 0) mainViewModel.setQuery("")
        }

        b.tabLayout.post { markStyleTabChevron() }

        b.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = Unit
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) {
                // Deferred: TabLayout is mid-way through iterating its listener list, and
                // rebuilding the row clears that list underneath it.
                if (tab.position == STYLE_TAB) b.tabLayout.post { showCategoryTabs() }
            }
        })
    }

    private fun markStyleTabChevron() {
        val b = _binding ?: return
        for (i in 0 until b.tabLayout.tabCount) {
            b.tabLayout.getTabAt(i)?.customView
                ?.findViewById<View>(R.id.tabChevron)
                ?.isVisible = (i == STYLE_TAB)
        }
    }

    /**
     * Category row: `[← Style]  All  Basic  Ink  Urdu & Arabic  …`
     *
     * The pager stays parked on Style — this is a filter over that one page, not a fourth
     * page — so the mediator is detached while the row is showing category names it knows
     * nothing about, and reattached on the way back.
     */
    private fun showCategoryTabs() {
        val b = _binding ?: return
        inCategoryMode = true

        mediator?.detach()
        mediator = null
        b.tabLayout.clearOnTabSelectedListeners()
        b.tabLayout.removeAllTabs()
        b.viewPager.setCurrentItem(STYLE_TAB, false)
        b.searchIcon.isVisible = true

        val inflater = LayoutInflater.from(requireContext())

        val breadcrumb = b.tabLayout.newTab()
        val crumbView = inflater.inflate(R.layout.view_panel_tab_breadcrumb, b.tabLayout, false)
        crumbView.findViewById<TextView>(R.id.tabTitle).text = getString(R.string.style)
        breadcrumb.customView = crumbView
        b.tabLayout.addTab(breadcrumb, false)

        val categories = BrushStyle.categories
        val labels = listOf(getString(R.string.all)) + categories.map { it.displayName }
        labels.forEach { label ->
            val tab = b.tabLayout.newTab()
            val view = inflater.inflate(R.layout.view_panel_tab, b.tabLayout, false)
            view.findViewById<TextView>(R.id.tabTitle).text = label
            tab.customView = view
            b.tabLayout.addTab(tab, false)
        }

        val current = viewModel.brushCategoryFilter.value
        val selectPos = if (current == null) 1 else categories.indexOf(current) + 2
        b.tabLayout.getTabAt(selectPos)?.select()
        applyCategoryTabStyles(selectPos)

        b.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val b2 = _binding ?: return
                if (tab.position == 0) {
                    b2.tabLayout.post { showPanelTabs() }
                    return
                }
                viewModel.setBrushCategoryFilter(categories.getOrNull(tab.position - 2))
                applyCategoryTabStyles(tab.position)
                PanelTabHelper.scrollToTabIfOverflows(b2.tabLayout, tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) {
                val b2 = _binding ?: return
                if (tab.position == 0) b2.tabLayout.post { showPanelTabs() }
            }
        })
    }

    private fun applyCategoryTabStyles(selectedPos: Int) {
        val b = _binding ?: return
        for (i in 0 until b.tabLayout.tabCount) {
            // Position 0 is the breadcrumb chip; it carries its own styling and must not
            // be dimmed as an unselected tab.
            if (i == 0) continue
            PanelTabHelper.updateTabStyle(b.tabLayout.getTabAt(i), i == selectedPos)
        }
    }

    private fun setupEraserPanel() {
        var eraserFrag = childFragmentManager.findFragmentById(R.id.eraserContainer) as? EraserSizeFragment
        if (eraserFrag == null) {
            eraserFrag = EraserSizeFragment()
            childFragmentManager.beginTransaction()
                .replace(R.id.eraserContainer, eraserFrag)
                .commitAllowingStateLoss()
        }
    }

    private fun setupModeSwitch() {
        binding.btnModeBrush.addPressEffect {
            viewModel.setEraserActive(false)
        }

        binding.btnModeEraser.addPressEffect {
            viewModel.setEraserActive(true)
        }

        viewModel.isEraserActive.observe(viewLifecycleOwner) { isEraser ->
            updateModeSwitchUI(isEraser == true)
        }
    }

    private fun updateModeSwitchUI(isEraser: Boolean) {
        val context = context ?: return
        val white = ContextCompat.getColor(context, R.color.white)
        val contrast = ContextCompat.getColor(context, R.color.contrast)

        if (isEraser) {
            binding.btnModeBrush.backgroundTintList = ColorStateList.valueOf(contrast)
            binding.btnModeEraser.backgroundTintList = ColorStateList.valueOf(white)

            binding.tabLayout.visibility = View.GONE
            binding.viewPager.visibility = View.GONE
            binding.searchIcon.visibility = View.GONE
            binding.eraserContainer.visibility = View.VISIBLE
        } else {
            binding.btnModeBrush.backgroundTintList = ColorStateList.valueOf(white)
            binding.btnModeEraser.backgroundTintList = ColorStateList.valueOf(contrast)

            binding.tabLayout.visibility = View.VISIBLE
            binding.viewPager.visibility = View.VISIBLE
            binding.searchIcon.visibility =
                if (binding.viewPager.currentItem == 0) View.VISIBLE else View.GONE
            binding.eraserContainer.visibility = View.GONE
        }
    }

    // ── Drawing mode observer ─────────────────────────────────────────────────

    private fun observeDrawingMode() {
        viewModel.isDrawingMode.observe(viewLifecycleOwner) { isDrawingMode ->
            viewModel.getCanvasView()
                ?.setDrawingMode(isDrawingMode, viewModel.getActiveDrawSession())
        }
    }

    private fun setupEvents() {
        // Add Brush button (toggle/re-arm draw mode)
        binding.addBrush.addPressEffect {
            viewModel.enterDrawingMode(requireActivity())
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onPause()       { super.onPause();       viewModel.exitDrawingMode(commit = false) }
    override fun onStop()        { super.onStop();        viewModel.exitDrawingMode(commit = false) }
    override fun onDestroyView() {
        mediator?.detach()
        mediator = null
        _binding?.viewPager?.adapter = null
        viewModel.exitDrawingMode(commit = false)
        mainViewModel.setQuery("")
        super.onDestroyView()
        _binding = null
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) viewModel.exitDrawingMode(commit = false)
    }

    companion object {
        private const val STYLE_TAB = 0
    }
}