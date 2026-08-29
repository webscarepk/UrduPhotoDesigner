package com.webscare.urducanvas.ui.editor.panels.draw

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.createBitmap
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.BrushStyle
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.common.canvas.model.StrokeData
import com.webscare.urducanvas.common.utils.BrushRenderUtils
import com.webscare.urducanvas.common.utils.PanelTabHelper
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.databinding.FragmentDrawBinding
import com.webscare.urducanvas.ui.editor.EditorFragment
import com.webscare.urducanvas.ui.editor.panels.draw.brush.BrushPagerAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class DrawFragment : Fragment() {

    private var _binding: FragmentDrawBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private val brushTabs = listOf(
        PanelTabs(0, "Style", true),
        PanelTabs(1, "Size", false),
        PanelTabs(2, "Color", false)
    )
    private val tabTitles = listOf("Style", "Size", "Color")

    private lateinit var brushPagerAdapter: BrushPagerAdapter
    private var isSyncingTabs = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDrawBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.enterDrawingMode(requireActivity())

        setupTabs()
        setupEvents()
        setupPreviewObserver()
        attachDragHandleSwipe()
        observePanelExpanded()
        observeDrawingMode()
    }

    // ── Horizontal Tabs & ViewPager2 ──────────────────────────────────────────

    private fun setupTabs() {
        brushPagerAdapter = BrushPagerAdapter(this, brushTabs)
        binding.viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.adapter = brushPagerAdapter

        PanelTabHelper.setupCustomPanelTabs(binding.tabLayout, binding.viewPager, tabTitles) { pos ->
            syncExpandedTab(pos)
        }

        PanelTabHelper.setupCustomPanelTabs(binding.tabLayoutExpanded, binding.viewPager, tabTitles) { pos ->
            syncCollapsedTab(pos)
        }

        viewModel.pagingLocked.observe(viewLifecycleOwner) { locked ->
            binding.viewPager.isUserInputEnabled = !locked
        }
    }

    private fun syncExpandedTab(position: Int) {
        if (isSyncingTabs) return
        isSyncingTabs = true
        if (binding.tabLayoutExpanded.selectedTabPosition != position) {
            binding.tabLayoutExpanded.getTabAt(position)?.select()
        }
        isSyncingTabs = false
    }

    private fun syncCollapsedTab(position: Int) {
        if (isSyncingTabs) return
        isSyncingTabs = true
        if (binding.tabLayout.selectedTabPosition != position) {
            binding.tabLayout.getTabAt(position)?.select()
        }
        isSyncingTabs = false
    }

    // ── Live Bottom Preview ───────────────────────────────────────────────────

    private fun setupPreviewObserver() {
        binding.brushPreview.post {
            if (_binding == null) return@post
            updatePreview()
        }

        viewModel.currentBrushStyle.observe(viewLifecycleOwner) { updatePreview() }
        viewModel.brushThickness.observe(viewLifecycleOwner) { updatePreview() }
        viewModel.brushHardness.observe(viewLifecycleOwner) { updatePreview() }
        viewModel.brushColor.observe(viewLifecycleOwner) { updatePreview() }
        viewModel.brushGradient.observe(viewLifecycleOwner) { updatePreview() }
    }

    private fun updatePreview() {
        val preview = _binding?.brushPreview ?: return
        val width = preview.width.takeIf { it > 0 } ?: (resources.displayMetrics.widthPixels - (56 * resources.displayMetrics.density).toInt())
        val height = preview.height.takeIf { it > 0 } ?: (44 * resources.displayMetrics.density).toInt()

        val color = viewModel.brushColor.value ?: Color.BLACK
        val thickness = (viewModel.brushThickness.value ?: 20f).coerceIn(2f, 80f)
        val hardness = viewModel.brushHardness.value ?: 1f
        val style = viewModel.currentBrushStyle.value ?: BrushStyle.ROUND_BRUSH
        val gradient = viewModel.brushGradient.value

        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                val bmp = createBitmap(width, height)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                val path = Path().apply {
                    val w = width.toFloat()
                    val h = height.toFloat()
                    moveTo(w * 0.08f, h * 0.5f)
                    cubicTo(w * 0.32f, h * 0.25f, w * 0.68f, h * 0.75f, w * 0.92f, h * 0.5f)
                }

                val stroke = StrokeData(
                    path = path,
                    color = color,
                    thickness = thickness,
                    hardness = hardness,
                    style = style,
                    gradient = gradient
                )

                BrushRenderUtils.drawStrokePreview(
                    canvas = canvas,
                    stroke = stroke,
                    paintAlpha = (hardness * 255).toInt().coerceIn(1, 255),
                    width = width,
                    height = height,
                    makePaint = BrushRenderUtils::makeStrokePaint,
                    drawBrush = BrushRenderUtils::drawBrushStroke,
                    drawPen = BrushRenderUtils::drawTaperedPenStroke
                )
                bmp
            }

            val b = _binding ?: return@launch
            b.brushPreview.setImageBitmap(bitmap)
        }
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                mainViewModel.panelSlideOffset.collect { offset ->
                    applySlideOffset(offset)
                }
            }
        }
    }

    private fun applySlideOffset(offset: Float) {
        if (_binding == null) return

        val collapsedAlpha = (1f - offset / 0.4f).coerceIn(0f, 1f)
        val expandedAlpha  = ((offset - 0.3f) / 0.7f).coerceIn(0f, 1f)

        binding.headerCollapsed.alpha = collapsedAlpha
        binding.headerExpanded.alpha  = expandedAlpha

        binding.headerCollapsed.visibility =
            if (collapsedAlpha > 0f) View.VISIBLE else View.INVISIBLE
        binding.headerExpanded.visibility =
            if (expandedAlpha > 0f) View.VISIBLE else View.INVISIBLE
    }

    private fun setupEvents() {
        // Add Brush buttons (toggle/re-arm draw mode)
        binding.addBrush.addPressEffect {
            viewModel.enterDrawingMode(requireActivity())
        }
        binding.addBrushExpanded.addPressEffect {
            viewModel.enterDrawingMode(requireActivity())
        }

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

    private fun attachDragHandleSwipe() {
        var f: Fragment? = this
        while (f != null) {
            if (f is EditorFragment) {
                f.attachDragHandle(binding.dragHandle)

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

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onPause()       { super.onPause();       viewModel.exitDrawingMode(commit = false) }
    override fun onStop()        { super.onStop();        viewModel.exitDrawingMode(commit = false) }
    override fun onDestroyView() {
        _binding?.viewPager?.adapter = null
        viewModel.exitDrawingMode(commit = false)
        super.onDestroyView()
        _binding = null
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) viewModel.exitDrawingMode(commit = false)
    }
}