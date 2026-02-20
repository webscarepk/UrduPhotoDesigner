package com.webscare.urducanvas.ui.editor.panels.adjustments.filters

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.model.FilterItem
import com.example.urduphotodesigner.common.canvas.sealed.ImageFilter
import com.example.urduphotodesigner.common.utils.BitmapCache
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.FragmentFiltersBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FiltersFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentFiltersBinding? = null
    private val binding get() = _binding!!
    private lateinit var filtersAdapter: ImageFiltersAdapter
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()
    private var previewBitmap: Bitmap? = null
    private var elementId: String? = null

    // Define your list of available filters
    private val availableFilters = listOf(
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "None",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.None
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Grayscale",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.Grayscale
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Sepia",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.Sepia
        ),             // Example: rotate hue by 90 degrees
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Invert",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.Invert
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Cool Tint",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.CoolTint
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Warm Tint",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.WarmTint
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Film",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.Film
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Teal Orange",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.TealOrange
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Black White",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.BlackWhite
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "High Contrast",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.HighContrast
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Vintage",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.Vintage
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Brightness",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.BrightnessBoost
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Soft Blur",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.SoftBlur
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Sharpen",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.Sharpen
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Glow",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.Glow
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Sketch",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.Sketch
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Cartoon",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.Cartoon
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "HDR",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.HDR
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Lomo",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.Lomo
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Pastel",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.Pastel
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Dramatic",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.Dramatic
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Golden Hour",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.GoldenHour
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.FilterItem(
            "Cyberpunk",
            _root_ide_package_.com.webscare.urducanvas.common.canvas.sealed.ImageFilter.Cyberpunk
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { bundle ->
            elementId = arguments?.getString("elementId")
            previewBitmap = _root_ide_package_.com.webscare.urducanvas.common.utils.BitmapCache.get(elementId ?: "")
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
        viewModel.currentImageFilter.observe(viewLifecycleOwner) { currentFilter ->
            filtersAdapter.selectedFilter = currentFilter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(key: String): FiltersFragment {
            val bundle = Bundle().apply { putString("elementId", key) }
            return FiltersFragment().apply { arguments = bundle }
        }
    }
}