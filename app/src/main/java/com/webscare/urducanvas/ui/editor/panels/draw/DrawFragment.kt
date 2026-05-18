package com.webscare.urducanvas.ui.editor.panels.draw

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.databinding.FragmentDrawBinding
import com.webscare.urducanvas.ui.editor.panels.draw.brush.BrushPagerAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.PanelTabsAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

@AndroidEntryPoint
class DrawFragment : Fragment() {

    private var _binding: FragmentDrawBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    // Brush sub-tabs (Style / Size / Color)
    private val brushTabs = ArrayList<PanelTabs>()
    private lateinit var categoriesAdapter: PanelTabsAdapter
    private lateinit var brushPagerAdapter: BrushPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDrawBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.enterDrawingMode(requireActivity())

        setupBrushTabs()
        setupEvents()
        attachDragHandleSwipe()
        observePanelExpanded()
        observeDrawingMode()
    }

    // ── Brush sub-tabs ────────────────────────────────────────────────────────

    private fun setupBrushTabs() {
        brushTabs.add(PanelTabs(0, "Style", true))
        brushTabs.add(PanelTabs(1, "Size",  false))
        brushTabs.add(PanelTabs(2, "Color", false))

        categoriesAdapter = PanelTabsAdapter { tab -> handleBrushTabSelection(tab) }
        binding.categories.adapter = categoriesAdapter
        categoriesAdapter.submitList(ArrayList(brushTabs))

        brushPagerAdapter = BrushPagerAdapter(this, brushTabs)
        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1
        binding.viewPager.adapter = brushPagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                handleBrushTabSelection(brushTabs[position])
                binding.categories.smoothScrollToPosition(position)
            }
        })

        viewModel.pagingLocked.observe(viewLifecycleOwner) { locked ->
            binding.viewPager.isUserInputEnabled = !locked
        }

        handleBrushTabSelection(brushTabs.first())
    }

    private fun handleBrushTabSelection(tab: PanelTabs) {
        val index = brushTabs.indexOfFirst { it.tab_name == tab.tab_name }
        categoriesAdapter.submitList(brushTabs.map {
            it.copy(is_selected = it.tab_name == tab.tab_name)
        })
        binding.viewPager.setCurrentItem(index, true)
    }

    // ── Drawing mode observer ─────────────────────────────────────────────────

    private fun observeDrawingMode() {
        viewModel.isDrawingMode.observe(viewLifecycleOwner) { isDrawingMode ->
            binding.done.isVisible         = isDrawingMode
            binding.doneExpanded.isVisible = isDrawingMode
            viewModel.getCanvasView()
                ?.setDrawingMode(isDrawingMode, viewModel.getActiveDrawSession())
            if (!isDrawingMode) mainViewModel.collapsePanelIfExpanded(PanelType.DRAW)
        }
    }

    // ── Panel expansion ───────────────────────────────────────────────────────

    private fun observePanelExpanded() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.expandedPanel
                    .map { it == PanelType.DRAW }
                    .collect { expanded ->
                        binding.headerCollapsed.isVisible = !expanded
                        binding.headerExpanded.isVisible  = expanded
                    }
            }
        }
    }

    private fun setupEvents() {
        // Collapsed header
        binding.reset.addPressEffect { viewModel.resetBrushSettings() }
        binding.done.addPressEffect  {
            viewModel.exitDrawingMode(commit = true)
            mainViewModel.collapsePanelIfExpanded(PanelType.DRAW)
        }
        // Expanded header
        binding.resetExpanded.addPressEffect { viewModel.resetBrushSettings() }
        binding.doneExpanded.addPressEffect  {
            viewModel.exitDrawingMode(commit = true)
            mainViewModel.collapsePanelIfExpanded(PanelType.DRAW)
        }
        binding.closePanel.addPressEffect { mainViewModel.collapsePanel() }
    }

    // ── Drag handle ───────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragHandleSwipe() {
        val thresholdPx = 30 * resources.displayMetrics.density
        var startY = 0f

        binding.dragHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startY = event.rawY; true }
                MotionEvent.ACTION_UP -> {
                    val dy = startY - event.rawY
                    if (abs(dy) >= thresholdPx) {
                        when {
                            dy > 0 && !mainViewModel.isPanelExpanded(PanelType.DRAW) ->
                                mainViewModel.togglePanel(PanelType.DRAW)
                            dy < 0 && mainViewModel.isPanelExpanded(PanelType.DRAW) ->
                                mainViewModel.togglePanel(PanelType.DRAW)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onPause()       { super.onPause();       viewModel.exitDrawingMode(commit = false) }
    override fun onStop()        { super.onStop();        viewModel.exitDrawingMode(commit = false) }
    override fun onDestroyView() { super.onDestroyView(); viewModel.exitDrawingMode(commit = false); _binding = null }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) viewModel.exitDrawingMode(commit = false)
    }
}