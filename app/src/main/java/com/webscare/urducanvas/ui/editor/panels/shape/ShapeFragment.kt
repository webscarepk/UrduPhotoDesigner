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
import com.webscare.urducanvas.ui.editor.views.RailCategoryItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ShapeFragment : Fragment() {

    private var _binding: FragmentShapeBinding? = null
    private val binding get() = _binding!!

    private lateinit var tabs: ArrayList<AdjustmentPanelTabs>
    private lateinit var pagerAdapter: ShapePanelPagerAdapter
    private val viewModel: CanvasViewModel by activityViewModels()

    private var isFillEnabled   = true
    private var isStrokeEnabled = true
    private var isCornerEnabled = true

    private val shapeCategories = listOf(
        RailCategoryItem("fill",   "Fill",   R.drawable.ic_fill,   isEnabled = true),
        RailCategoryItem("stroke", "Stroke", R.drawable.ic_stroke, isEnabled = true),
        RailCategoryItem("corner", "Corner", R.drawable.ic_corner, isEnabled = true),
        RailCategoryItem("mask",   "Mask",   R.drawable.ic_mask,   isEnabled = false)
    )

    // Image picker — add/replace image inside shape
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val rawBitmap = requireContext().contentResolver
                        .openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                    if (rawBitmap != null) {
                        val bitmap = com.webscare.urducanvas.common.utils.ImageProcessor
                            .downsampleIfNeeded(rawBitmap, com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX, com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX)
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
        binding.title.text = getString(R.string.shape_properties)
        setupRailAndPager()
        initObservers()
        setEvents()
    }

    private fun setupRailAndPager() {
        tabs = ArrayList()

        binding.collapsibleRail.bindPanelId("shape")
        binding.collapsibleRail.setCategories(shapeCategories)

        binding.collapsibleRail.onCategorySelectedListener = { catItem ->
            val index = shapeCategories.indexOfFirst { it.id == catItem.id }
            if (index >= 0 && index < tabs.size) {
                binding.viewPager.setCurrentItem(index, true)
            }
        }

        binding.collapsibleRail.onCategoryToggleChangedListener = { catItem, _ ->
            when (catItem.id) {
                "fill"   -> viewModel.toggleFillEnabled(!isFillEnabled)
                "stroke" -> viewModel.toggleStrokeEnabled(!isStrokeEnabled)
                "corner" -> viewModel.toggleCornerEnabled(!isCornerEnabled)
                "mask"   -> {
                    val element = viewModel.selectedElements.value?.firstOrNull()
                    val hasImage = element?.bitmap != null || element?.bitmapData != null
                    if (!hasImage) {
                        pickImageLauncher.launch("image/*")
                    }
                }
            }
        }

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        tabs.addAll(listOf(
            AdjustmentPanelTabs(0, "Fill",   true,  is_enabled = isFillEnabled),
            AdjustmentPanelTabs(1, "Stroke", false, is_enabled = isStrokeEnabled),
            AdjustmentPanelTabs(2, "Corner", false, is_enabled = isCornerEnabled),
            AdjustmentPanelTabs(3, "Mask",   false, is_enabled = false)
        ))

        pagerAdapter = ShapePanelPagerAdapter(this, tabs)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position in shapeCategories.indices) {
                    binding.collapsibleRail.setSelectedCategory(shapeCategories[position].id)
                }
            }
        })
    }

    private fun updateTabsFromState() {
        val hasImage = viewModel.selectedElements.value?.firstOrNull()?.let {
            it.bitmap != null || it.bitmapData != null
        } == true

        binding.collapsibleRail.setCategoryEnabled("fill",   isFillEnabled)
        binding.collapsibleRail.setCategoryEnabled("stroke", isStrokeEnabled)
        binding.collapsibleRail.setCategoryEnabled("corner", isCornerEnabled)
        binding.collapsibleRail.setCategoryEnabled("mask",   hasImage)
    }

    private fun initObservers() {
        viewModel.pagingLocked.observe(viewLifecycleOwner) { locked ->
            binding.viewPager.isUserInputEnabled = !locked
        }

        viewModel.shapeFillEnabled.observe(viewLifecycleOwner) {
            isFillEnabled = it; updateTabsFromState()
        }
        viewModel.shapeStrokeEnabled.observe(viewLifecycleOwner) {
            isStrokeEnabled = it; updateTabsFromState()
        }
        viewModel.shapeCornerEnabled.observe(viewLifecycleOwner) {
            isCornerEnabled = it; updateTabsFromState()
        }

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

    private fun setEvents() {
        binding.addImage.addPressEffect {
            pickImageLauncher.launch("image/*")
        }

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
        _binding?.viewPager?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(tabName: String) = ShapeFragment().apply {
            arguments = Bundle().apply { putString("tabName", tabName) }
        }
    }
}