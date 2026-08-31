package com.webscare.urducanvas.ui.editor.panels.draw

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.PanelTabHelper
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.databinding.FragmentDrawBinding
import com.webscare.urducanvas.ui.editor.panels.draw.brush.BrushPagerAdapter
import com.webscare.urducanvas.ui.editor.panels.draw.eraser.EraserSizeFragment
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

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
        setupEvents()
        observeDrawingMode()
    }

    // ── Horizontal Tabs & ViewPager2 ──────────────────────────────────────────

    private fun setupTabs() {
        brushPagerAdapter = BrushPagerAdapter(this, brushTabs)
        binding.viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.adapter = brushPagerAdapter

        PanelTabHelper.setupCustomPanelTabs(binding.tabLayout, binding.viewPager, tabTitles)

        // Horizontal swipe on pager in brush mode is locked; tabs are navigated by tapping
        binding.viewPager.isUserInputEnabled = false
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
            binding.eraserContainer.visibility = View.VISIBLE
        } else {
            binding.btnModeBrush.backgroundTintList = ColorStateList.valueOf(white)
            binding.btnModeEraser.backgroundTintList = ColorStateList.valueOf(contrast)

            binding.tabLayout.visibility = View.VISIBLE
            binding.viewPager.visibility = View.VISIBLE
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