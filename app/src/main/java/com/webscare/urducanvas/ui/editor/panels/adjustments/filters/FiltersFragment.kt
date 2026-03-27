package com.webscare.urducanvas.ui.editor.panels.adjustments.filters

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.canvas.model.FilterItem
import com.webscare.urducanvas.common.canvas.sealed.ImageFilter
import com.webscare.urducanvas.common.utils.BitmapCache
import com.webscare.urducanvas.databinding.FragmentFiltersBinding
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.graphics.createBitmap

@AndroidEntryPoint
class FiltersFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentFiltersBinding? = null
    private val binding get() = _binding!!
    private lateinit var filtersAdapter: ImageFiltersAdapter
    private val viewModel: CanvasViewModel by activityViewModels()
    private var previewBitmap: Bitmap? = null
    private var elementId: String? = null

    // Define your list of available filters
    private val availableFilters = listOf(
        FilterItem(
            "None",
            ImageFilter.None
        ),
        FilterItem(
            "Grayscale",
            ImageFilter.Grayscale
        ),
        FilterItem(
            "Sepia",
            ImageFilter.Sepia
        ),             // Example: rotate hue by 90 degrees
        FilterItem(
            "Invert",
            ImageFilter.Invert
        ),
        FilterItem(
            "Cool Tint",
            ImageFilter.CoolTint
        ),
        FilterItem(
            "Warm Tint",
            ImageFilter.WarmTint
        ),
        FilterItem(
            "Film",
            ImageFilter.Film
        ),
        FilterItem(
            "Teal Orange",
            ImageFilter.TealOrange
        ),
        FilterItem(
            "Black White",
            ImageFilter.BlackWhite
        ),
        FilterItem(
            "High Contrast",
            ImageFilter.HighContrast
        ),
        FilterItem(
            "Vintage",
            ImageFilter.Vintage
        ),
        FilterItem(
            "Brightness",
            ImageFilter.BrightnessBoost
        ),
        FilterItem(
            "Soft Blur",
            ImageFilter.SoftBlur
        ),
        FilterItem(
            "Sharpen",
            ImageFilter.Sharpen
        ),
        FilterItem(
            "Glow",
            ImageFilter.Glow
        ),
        FilterItem(
            "Sketch",
            ImageFilter.Sketch
        ),
        FilterItem(
            "Cartoon",
            ImageFilter.Cartoon
        ),
        FilterItem(
            "HDR",
            ImageFilter.HDR
        ),
        FilterItem(
            "Lomo",
            ImageFilter.Lomo
        ),
        FilterItem(
            "Pastel",
            ImageFilter.Pastel
        ),
        FilterItem(
            "Dramatic",
            ImageFilter.Dramatic
        ),
        FilterItem(
            "Golden Hour",
            ImageFilter.GoldenHour
        ),
        FilterItem(
            "Cyberpunk",
            ImageFilter.Cyberpunk
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { bundle ->
            elementId = arguments?.getString("elementId")
            val sourceBitmap = BitmapCache.get(elementId ?: "")
            previewBitmap = if (sourceBitmap != null && !sourceBitmap.isRecycled) {
                sourceBitmap.copy(sourceBitmap.config ?: Bitmap.Config.ARGB_8888, false)
            } else null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFiltersBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        initObservers()
    }

    private fun setupRecyclerView() {
        filtersAdapter = ImageFiltersAdapter(availableFilters, previewBitmap) { filterItem ->
            elementId?.let { id ->
                viewModel.applyImageFilter(id, filterItem.filter)
            }
        }

        binding.filtersRecyclerView.apply {
            adapter = filtersAdapter
        }
    }

    private fun initObservers() {
        viewModel.selectedElements.observe(viewLifecycleOwner) { elements ->
            val element = elements.firstOrNull() ?: return@observe

            if (elementId != element.id) {
                elementId = element.id

                // Cache bitmap if available
                element.bitmap?.let { bmp ->
                    BitmapCache.put(element.id, bmp)
                }

                // Try cache first
                val sourceBitmap = BitmapCache.get(element.id)
                previewBitmap = if (sourceBitmap != null && !sourceBitmap.isRecycled) {
                    sourceBitmap.copy(sourceBitmap.config ?: Bitmap.Config.ARGB_8888, false)
                } else {
                    // ✅ Fallback: rasterize SVG drawable if bitmap is null/missing
                    element.svgDrawable?.let { drawable ->
                        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 512
                        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 512
                        val bmp = createBitmap(width, height)
                        val canvas = android.graphics.Canvas(bmp)
                        drawable.setBounds(0, 0, width, height)
                        drawable.draw(canvas)
                        // Cache the rasterized result for reuse
                        BitmapCache.put(element.id, bmp)
                        bmp
                    }
                }

                filtersAdapter.updatePreviewBitmap(previewBitmap)
            }
        }

        viewModel.currentImageFilter.observe(viewLifecycleOwner) { currentFilter ->
            filtersAdapter.selectedFilter = currentFilter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        previewBitmap?.recycle()
        previewBitmap = null
        _binding = null
    }

    companion object {
        fun newInstance(key: String): FiltersFragment {
            val bundle = Bundle().apply { putString("elementId", key) }
            return FiltersFragment().apply { arguments = bundle }
        }
    }
}