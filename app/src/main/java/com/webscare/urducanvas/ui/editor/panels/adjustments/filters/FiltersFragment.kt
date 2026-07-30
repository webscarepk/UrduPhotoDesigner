package com.webscare.urducanvas.ui.editor.panels.adjustments.filters

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.graphics.createBitmap
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.model.FilterItem
import com.webscare.urducanvas.common.canvas.sealed.ImageFilter
import com.webscare.urducanvas.common.utils.BitmapCache
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentFiltersBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FiltersFragment : Fragment() {

    private var _binding: FragmentFiltersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    private lateinit var filtersAdapter: ImageFiltersAdapter
    private lateinit var categoryAdapter: FilterCategoryAdapter

    private var elementId: String? = null
    private var previewBitmap: Bitmap? = null
    private var isProgrammaticScroll = false

    // Grouped strictly by category so scrolling through contiguous blocks is smooth
    private val availableFilters = listOf(
        // Basic & Portrait
        FilterItem("None", ImageFilter.None),
        FilterItem("Clarendon", ImageFilter.Clarendon),
        FilterItem("Lark", ImageFilter.Lark),
        FilterItem("Valencia", ImageFilter.Valencia),
        FilterItem("Juno", ImageFilter.Juno),
        FilterItem("Cool Tint", ImageFilter.CoolTint),
        FilterItem("Warm Tint", ImageFilter.WarmTint),

        // Cinematic
        FilterItem("Film", ImageFilter.Film),
        FilterItem("Teal & Orange", ImageFilter.TealOrange),
        FilterItem("Dramatic", ImageFilter.Dramatic),
        FilterItem("Golden Hour", ImageFilter.GoldenHour),
        FilterItem("Cyberpunk", ImageFilter.Cyberpunk),

        // Vintage
        FilterItem("Gingham", ImageFilter.Gingham),
        FilterItem("Reyes", ImageFilter.Reyes),
        FilterItem("Slumber", ImageFilter.Slumber),
        FilterItem("Sepia", ImageFilter.Sepia),
        FilterItem("Vintage", ImageFilter.Vintage),
        FilterItem("Lomo", ImageFilter.Lomo),
        FilterItem("Pastel", ImageFilter.Pastel),

        // B&W
        FilterItem("Moon", ImageFilter.Moon),
        FilterItem("Grayscale", ImageFilter.Grayscale),
        FilterItem("Black White", ImageFilter.BlackWhite),
        FilterItem("High Contrast", ImageFilter.HighContrast),

        // Artistic
        FilterItem("HDR", ImageFilter.HDR),
        FilterItem("Sharpen", ImageFilter.Sharpen),
        FilterItem("Soft Blur", ImageFilter.SoftBlur),
        FilterItem("Glow", ImageFilter.Glow),
        FilterItem("Sketch", ImageFilter.Sketch),
        FilterItem("Cartoon", ImageFilter.Cartoon),
        FilterItem("Bright Boost", ImageFilter.BrightnessBoost),
        FilterItem("Invert", ImageFilter.Invert)
    )

    private val categories = listOf("All", "Portrait", "Cinematic", "Vintage", "B&W", "Artistic")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        elementId = arguments?.getString(ARG_ELEMENT_ID) ?: elementId
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupSeekBar()
        initObservers()
    }

    private fun setupRecyclerViews() {
        filtersAdapter = ImageFiltersAdapter(
            filterList = availableFilters,
            baseBitmap = previewBitmap,
            onFilterSelected = { filterItem ->
                elementId?.let { id ->
                    val intensityProgress = binding.filterIntensitySeekBar.progress
                    val intensity = if (filterItem.filter is ImageFilter.None) 1.0f else (intensityProgress / 100f)
                    viewModel.applyImageFilter(id, filterItem.filter, intensity)
                }

                val filterCategory = if (filterItem.filter.category == "Basic") "Portrait" else filterItem.filter.category
                if (categoryAdapter.selectedCategory != filterCategory) {
                    categoryAdapter.selectedCategory = filterCategory
                    val catIndex = categories.indexOf(filterCategory)
                    if (catIndex != -1) {
                        binding.categoriesRecyclerView.smoothScrollToPosition(catIndex)
                    }
                }
            },
            onFilterReSelected = { _ ->
                // Re-tapping selected filter opens dedicated intensity view mode
                showIntensityViewMode()
            }
        )

        binding.filtersRecyclerView.apply {
            adapter = filtersAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        isProgrammaticScroll = false
                    }
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (isProgrammaticScroll) return
                    val layoutManager = layoutManager as? LinearLayoutManager ?: return
                    val firstVisiblePos = layoutManager.findFirstVisibleItemPosition()
                    if (firstVisiblePos in availableFilters.indices) {
                        val category = availableFilters[firstVisiblePos].filter.category
                        val displayCategory = if (category == "Basic") "Portrait" else category
                        if (categoryAdapter.selectedCategory != displayCategory) {
                            categoryAdapter.selectedCategory = displayCategory
                            val catIndex = categories.indexOf(displayCategory)
                            if (catIndex != -1) {
                                binding.categoriesRecyclerView.smoothScrollToPosition(catIndex)
                            }
                        }
                    }
                }
            })
        }

        categoryAdapter = FilterCategoryAdapter(categories) { categoryName ->
            isProgrammaticScroll = true
            categoryAdapter.selectedCategory = categoryName
            val catIndex = categories.indexOf(categoryName)
            if (catIndex != -1) {
                binding.categoriesRecyclerView.smoothScrollToPosition(catIndex)
            }
            if (categoryName == categories.last()) {
                binding.filtersRecyclerView.scrollToPosition(availableFilters.size - 1)
            } else {
                val layoutManager = binding.filtersRecyclerView.layoutManager as? LinearLayoutManager
                val targetIndex = if (categoryName == "All") 0 else availableFilters.indexOfFirst {
                    it.filter.category == categoryName || (categoryName == "Portrait" && it.filter.category == "Basic")
                }
                if (targetIndex != -1) {
                    layoutManager?.scrollToPositionWithOffset(targetIndex, 0)
                }
            }
        }

        binding.categoriesRecyclerView.apply {
            adapter = categoryAdapter
        }
    }

    private fun setupSeekBar() {
        binding.filterIntensitySeekBar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.intensityValueText.text = "$progress"
                if (fromUser) {
                    elementId?.let { id ->
                        val intensity = progress / 100f
                        val currentFilter = filtersAdapter.selectedFilter ?: viewModel.currentImageFilter.value
                        if (currentFilter != null && currentFilter !is ImageFilter.None) {
                            viewModel.applyImageFilter(id, currentFilter, intensity, isExplicit = false)
                        }
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let { sb ->
                    elementId?.let { id ->
                        val intensity = sb.progress / 100f
                        val currentFilter = filtersAdapter.selectedFilter ?: viewModel.currentImageFilter.value
                        if (currentFilter != null && currentFilter !is ImageFilter.None) {
                            viewModel.applyImageFilter(id, currentFilter, intensity, isExplicit = true)
                        }
                    }
                }
            }
        })

        // Tick / Done Button returns to main filters view
        binding.btnDoneIntensity.addPressEffect {
            showMainFiltersMode()
        }
    }

    private fun showIntensityViewMode() {
        if (binding.intensityAdjustView.visibility == View.VISIBLE) return

        binding.mainFiltersView.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                binding.mainFiltersView.visibility = View.GONE
                binding.intensityAdjustView.visibility = View.VISIBLE
                binding.intensityAdjustView.alpha = 0f
                binding.intensityAdjustView.animate().alpha(1f).setDuration(150).start()
            }
            .start()
    }

    private fun showMainFiltersMode() {
        if (binding.mainFiltersView.visibility == View.VISIBLE) return

        binding.intensityAdjustView.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                binding.intensityAdjustView.visibility = View.GONE
                binding.mainFiltersView.visibility = View.VISIBLE
                binding.mainFiltersView.alpha = 0f
                binding.mainFiltersView.animate().alpha(1f).setDuration(150).start()
            }
            .start()
    }

    private fun initObservers() {
        viewModel.selectedElements.observe(viewLifecycleOwner) { elements ->
            val element = elements.firstOrNull() ?: return@observe

            // Always ensure previewBitmap is created and loaded for the active element
            if (previewBitmap == null || elementId != element.id) {
                elementId = element.id

                val sourceBitmap = element.bitmap ?: BitmapCache.get(element.id)

                previewBitmap = if (sourceBitmap != null && !sourceBitmap.isRecycled) {
                    sourceBitmap.copy(sourceBitmap.config ?: Bitmap.Config.ARGB_8888, false)
                } else {
                    element.svgDrawable?.let { drawable ->
                        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 512
                        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 512
                        val bmp = createBitmap(width, height)
                        val canvas = android.graphics.Canvas(bmp)
                        drawable.setBounds(0, 0, width, height)
                        drawable.draw(canvas)
                        bmp
                    }
                }

                if (previewBitmap != null) {
                    filtersAdapter.updatePreviewBitmap(previewBitmap)
                }
            }

            // Sync intensity slider position
            val intensityProgress = (element.filterIntensity * 100).toInt().coerceIn(0, 100)
            if (binding.filterIntensitySeekBar.progress != intensityProgress) {
                binding.filterIntensitySeekBar.progress = intensityProgress
                binding.intensityValueText.text = "$intensityProgress"
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
        private const val ARG_ELEMENT_ID = "element_id"

        fun newInstance(elementId: String): FiltersFragment {
            val fragment = FiltersFragment()
            val args = Bundle().apply {
                putString(ARG_ELEMENT_ID, elementId)
            }
            fragment.arguments = args
            return fragment
        }
    }
}