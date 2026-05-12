package com.webscare.urducanvas.ui.editor.panels.shape

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.BitmapCache
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.AdjustmentPanelTabs
import com.webscare.urducanvas.databinding.FragmentShapeBinding
import com.webscare.urducanvas.ui.editor.panels.adjustments.AdjustmentPanelTabsAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ShapeFragment : Fragment() {

    private var _binding: FragmentShapeBinding? = null
    private val binding get() = _binding!!

    private lateinit var tabs: ArrayList<AdjustmentPanelTabs>
    private lateinit var adapter: AdjustmentPanelTabsAdapter
    private lateinit var pagerAdapter: ShapePanelPagerAdapter
    private val viewModel: CanvasViewModel by activityViewModels()

    private var isFillEnabled   = false
    private var isStrokeEnabled = true
    private var isCornerEnabled = true

    // Image picker — add/replace image inside shape
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = requireContext().contentResolver
                        .openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                    if (bitmap != null) {
                        withContext(Dispatchers.Main) {
                            viewModel.addImageInsideShape(bitmap, requireActivity())
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ShapeFragment", "Image pick failed", e)
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShapeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        initObservers()
        setEvents()
    }

    // ── Tabs ─────────────────────────────────────────────────────────────────

    private fun setupTabs() {
        tabs = ArrayList()
        adapter = AdjustmentPanelTabsAdapter({ tab -> handleUserTap(tab) }, true)
        binding.categories.adapter = adapter

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        tabs.addAll(listOf(
            AdjustmentPanelTabs(0, "Fill",   true,  is_enabled = false),
            AdjustmentPanelTabs(1, "Stroke", false, is_enabled = false),
            AdjustmentPanelTabs(2, "Corner", false, is_enabled = false),
            AdjustmentPanelTabs(3, "Mask",   false, is_enabled = false)
        ))

        pagerAdapter = ShapePanelPagerAdapter(this, tabs)
        binding.viewPager.adapter = pagerAdapter

        adapter.submitList(ArrayList(tabs))

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val selected = tabs.getOrNull(position) ?: return
                selectTabVisually(selected)
                binding.categories.smoothScrollToPosition(position)
            }
        })

        navigateTo(tabs[0])
    }

    private fun handleUserTap(selected: AdjustmentPanelTabs) {
        when (selected.tab_name) {
            "Fill" -> {
                val new = !isFillEnabled
                if (!new && !isStrokeEnabled) viewModel.toggleFillEnabled(true)
                else viewModel.toggleFillEnabled(new)
            }
            "Stroke" -> {
                val new = !isStrokeEnabled
                if (!new && !isFillEnabled) viewModel.toggleStrokeEnabled(true)
                else viewModel.toggleStrokeEnabled(new)
            }
            "Corner" -> viewModel.toggleCornerEnabled(!isCornerEnabled)
            "Mask"   -> {
                val element = viewModel.selectedElements.value?.firstOrNull()
                val hasImage = element?.bitmap != null || element?.bitmapData != null
                if (!hasImage) {
                    pickImageLauncher.launch("image/*")
                    return
                }
            }
        }
        navigateTo(selected)
    }

    private fun navigateTo(selected: AdjustmentPanelTabs) {
        selectTabVisually(selected)
        val index = tabs.indexOfFirst { it.tab_name == selected.tab_name }
        if (index != -1) binding.viewPager.setCurrentItem(index, true)
    }

    private fun selectTabVisually(selected: AdjustmentPanelTabs) {
        tabs = ArrayList(tabs.map { it.copy(is_selected = it.tab_name == selected.tab_name) })
        adapter.submitList(tabs)
    }

    private fun updateTabsFromState() {
        val hasImage = viewModel.selectedElements.value?.firstOrNull()?.let {
            it.bitmap != null || it.bitmapData != null
        } == true

        tabs = arrayListOf(
            AdjustmentPanelTabs(0, "Fill",   tabs.getOrNull(0)?.is_selected ?: true,  is_enabled = isFillEnabled),
            AdjustmentPanelTabs(1, "Stroke", tabs.getOrNull(1)?.is_selected ?: false, is_enabled = isStrokeEnabled),
            AdjustmentPanelTabs(2, "Corner", tabs.getOrNull(2)?.is_selected ?: false, is_enabled = isCornerEnabled),
            AdjustmentPanelTabs(3, "Mask",   tabs.getOrNull(3)?.is_selected ?: false, is_enabled = hasImage)
        )
        adapter.submitList(ArrayList(tabs))
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun initObservers() {
        viewModel.shapeFillEnabled.observe(viewLifecycleOwner) {
            isFillEnabled = it; updateTabsFromState()
        }
        viewModel.shapeStrokeEnabled.observe(viewLifecycleOwner) {
            isStrokeEnabled = it; updateTabsFromState()
        }
        viewModel.shapeCornerEnabled.observe(viewLifecycleOwner) {
            isCornerEnabled = it; updateTabsFromState()
        }

        // addImage icon: ic_import (no image) or ic_replace (has image)
        // editImage: only visible when shape has masked image
        viewModel.selectedElements.observe(viewLifecycleOwner) { selected ->
            val element = selected?.firstOrNull()
            val hasImage = element?.bitmap != null || element?.bitmapData != null
            binding.addImage.setImageResource(
                if (hasImage) R.drawable.ic_replace else R.drawable.ic_import
            )
            binding.editImage.isVisible = hasImage
            updateTabsFromState()
        }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    private fun setEvents() {
        // Add / Replace image inside shape
        binding.addImage.addPressEffect {
            pickImageLauncher.launch("image/*")
        }

        // Edit the masked image → navigate to adjustmentsParentFragment
        binding.editImage.addPressEffect {
            val element = viewModel.selectedElements.value?.firstOrNull() ?: return@addPressEffect
            val key = element.id
            element.bitmap?.let { BitmapCache.put(key, it) }
            val bundle = Bundle().apply { putString("elementId", key) }
            val navOptions = androidx.navigation.NavOptions.Builder()
                .setLaunchSingleTop(true).build()
            findNavController()
                .navigate(R.id.adjustmentsParentFragment, bundle, navOptions)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(tabName: String) = ShapeFragment().apply {
            arguments = Bundle().apply { putString("tabName", tabName) }
        }
    }
}