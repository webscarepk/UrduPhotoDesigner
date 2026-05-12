package com.webscare.urducanvas.ui.editor.panels.images

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.utils.ImageProcessor.bitmapCompress
import com.webscare.urducanvas.common.utils.ImageProcessor.trimTransparentEdges
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.data.model.ImagesData
import com.webscare.urducanvas.databinding.FragmentBackgroundsListBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt

@AndroidEntryPoint
class ImagesListFragment : Fragment() {

    private var _binding: FragmentBackgroundsListBinding? = null
    private val binding get() = _binding!!

    var onFilterResult: ((category: String, count: Int) -> Unit)? = null

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()

    private var imagesAdapter: ImagesAdapter? = null

    var category: String = ""; private set
    private var filterText: String = ""
    private var categoryImages: List<ImageEntity> = emptyList()
    private var lastImagesData: ImagesData? = null
    private var savedScrollPos: Int = 0

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            category   = it.getString(ARG_CATEGORY).orEmpty()
            filterText = it.getString(ARG_FILTER).orEmpty()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBackgroundsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.backgrounds.apply {
            layoutManager = GridLayoutManager(requireContext(), 3, GridLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            recycledViewPool.setMaxRecycledViews(0, 25)
        }

        setupImageTab()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) saveScrollPos()
    }

    override fun onDestroyView() {
        saveScrollPos()
        _binding = null
        super.onDestroyView()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupImageTab() {
        if (imagesAdapter == null) {
            imagesAdapter = ImagesAdapter(requireActivity(), { bitmap, svgDrawable, svgXml, entity ->
                val updated = if (svgXml != null && entity.bitmapData == null)
                    entity.copy(is_recent = true, bitmapData = svgXml)
                else
                    entity.copy(is_recent = true)

                mainViewModel.updateImage(updated)

                if (!isAdded) return@ImagesAdapter

                val isBackground = entity.parent_category.equals("Backgrounds", ignoreCase = true)

                if (isBackground) {
                    viewModel.ensureBackgroundElement(requireActivity())
                    viewModel.setCanvasBackgroundImage(bitmap, requireActivity())
                } else {
                    if (svgDrawable != null) {
                        viewModel.addSvgSticker(svgDrawable, svgXml, requireActivity(), entity.is_premium)
                    } else {
                        val resized = viewModel.canvasSize.value?.height?.roundToInt()?.let { h ->
                            viewModel.canvasSize.value?.width?.roundToInt()?.let { w ->
                                bitmapCompress(bitmap!!, w, h)
                            }
                        }
                        viewModel.addSticker(
                            resized?.trimTransparentEdges(),
                            requireActivity(),
                            ElementType.IMAGE,
                            entity.is_premium
                        )
                    }
                }
            },{})
        }
        binding.backgrounds.adapter = imagesAdapter

        // Always seed from ViewModel cache
        val data = mainViewModel.imagesData.value
        lastImagesData = data
        categoryImages = sliceFor(data, category)
        submitImages(categoryImages)

        // Restore scroll position
        if (savedScrollPos > 0) {
            binding.backgrounds.post {
                (binding.backgrounds.layoutManager as? GridLayoutManager)
                    ?.scrollToPosition(savedScrollPos)
            }
        }
    }

    // ── Called by ImagesFragment ──────────────────────────────────────────────

    fun onNewData(data: ImagesData) {
        if (lastImagesData === data) return
        lastImagesData = data

        val slice = sliceFor(data, category)
        if (slice === categoryImages) return
        categoryImages = slice
        submitImages(slice)
    }

    fun updateFilter(newFilter: String) {
        if (filterText == newFilter) return
        filterText = newFilter
        submitImages(categoryImages)
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    private fun submitImages(images: List<ImageEntity>) {
        val final = if (filterText.isBlank()) images
        else images.filter { it.matchesQuery(filterText) }

        if (_binding == null) return
        binding.noEmojis.visibility = if (final.isEmpty()) View.VISIBLE else View.GONE
        imagesAdapter?.submitList(final)
        onFilterResult?.invoke(category, final.size)
    }

    // ── Scroll ────────────────────────────────────────────────────────────────

    private fun saveScrollPos() {
        val lm = _binding?.backgrounds?.layoutManager as? GridLayoutManager ?: return
        val pos = lm.findFirstVisibleItemPosition()
        if (pos >= 0) savedScrollPos = pos
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sliceFor(data: ImagesData, cat: String): List<ImageEntity> =
        if (cat.equals("Recents", ignoreCase = true)) data.recents
        else data.imagesByCategory[cat].orEmpty()

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val ARG_CATEGORY = "arg_category"
        private const val ARG_FILTER   = "arg_filter"

        fun newInstance(category: String, initialFilter: String = "") =
            ImagesListFragment().apply {
                arguments = bundleOf(
                    ARG_CATEGORY to category,
                    ARG_FILTER   to initialFilter
                )
            }
    }
}