package com.webscare.urducanvas.ui.editor.panels.objects

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.ImageProcessor.trimTransparentEdges
import com.webscare.urducanvas.databinding.FragmentObjectsListBinding
import com.webscare.urducanvas.ui.editor.panels.background.backgrounds.ImagesAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ObjectsListFragment : androidx.fragment.app.Fragment() {

    private var _binding: FragmentObjectsListBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: com.webscare.urducanvas.viewmodels.MainViewModel by activityViewModels()
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()

    private var imagesAdapter: ImagesAdapter? = null
    private var emojiAdapter: EmojiAdapter? = null

    private var allLocalImages: List<com.webscare.urducanvas.data.model.ImageEntity> = emptyList()

    private var category: String = ""
    private var filterText: String = ""

    // Resolved once in onCreate — avoids repeating the when() block everywhere
    private var baseEmojiData: List<com.webscare.urducanvas.common.canvas.model.EmojiMeta> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            category   = it.getString(ARG_CATEGORY).orEmpty()
            filterText = it.getString(ARG_FILTER).orEmpty()
        }
        if (isBaseTab(category)) {
            baseEmojiData = emojiDataForCategory(category)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentObjectsListBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.objects.setHasFixedSize(true)
        setupAdapter()
        initObservers()
    }

    // ── Adapter setup (called once) ───────────────────────────────────────────

    private fun setupAdapter() {
        if (isBaseTab(category)) {
            emojiAdapter = EmojiAdapter(requireActivity(), emptyList()) { bmp ->
                viewModel.addSticker(bmp, requireActivity(), ElementType.STICKER)
            }
            binding.objects.adapter = emojiAdapter
            // Push correctly-filtered data immediately — first frame is always right
            applyEmojiFilter(filterText)
        } else {
            imagesAdapter = ImagesAdapter { bitmap, svgDrawable, svgString, imageEntity ->
                val updatedEntity = if (svgString != null && imageEntity.bitmapData == null) {
                    imageEntity.copy(is_recent = true, bitmapData = svgString)
                } else {
                    imageEntity.copy(is_recent = true)
                }
                mainViewModel.updateImage(updatedEntity)

                if (isAdded) {
                    if (svgDrawable != null) {
                        val trimmed = svgDrawable.trimTransparentEdges()
                        Log.d("SVG", "original: ${svgDrawable.intrinsicWidth} x ${svgDrawable.intrinsicHeight}")
                        Log.d("SVG", "trimmed:  ${trimmed.intrinsicWidth} x ${trimmed.intrinsicHeight}")
                        viewModel.addSvgSticker(trimmed, svgString, requireActivity(), imageEntity.is_premium)
                    } else {
                        viewModel.addSticker(
                            bitmap?.trimTransparentEdges(),
                            requireActivity(),
                            ElementType.IMAGE,
                            imageEntity.is_premium
                        )
                    }
                }
            }
            // Empty list on attach — no stale cross-category data ever flashes
            binding.objects.adapter = imagesAdapter
            // Real data arrives via initObservers() → refreshImages()
        }
    }

    private fun initObservers() {
        if (isBaseTab(category)) return  // static data, no DB observer needed

        lifecycleScope.launch {
            mainViewModel.localImages.collect { images ->
                allLocalImages = images
                refreshImages()
            }
        }
    }

    // ── Public API (called by ObjectsPagerAdapter) ────────────────────────────

    fun updateFilter(newFilter: String) {
        if (filterText == newFilter) return
        filterText = newFilter
        if (isBaseTab(category)) applyEmojiFilter(newFilter) else refreshImages()
    }

    fun updateImages(images: List<com.webscare.urducanvas.data.model.ImageEntity>) {
        if (isBaseTab(category)) return
        allLocalImages = images
        refreshImages()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun applyEmojiFilter(filter: String) {
        lifecycleScope.launch {
            val filtered = withContext(Dispatchers.Default) {
                if (filter.isBlank()) baseEmojiData
                else baseEmojiData.filter { it.name.contains(filter, ignoreCase = true) }
            }
            if (_binding == null) return@launch
            binding.noEmojis.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            emojiAdapter?.updateData(filtered)
        }
    }

    private fun refreshImages() {
        lifecycleScope.launch {
            val filtered = withContext(Dispatchers.Default) {
                when {
                    category.equals("Recents", ignoreCase = true) ->
                        allLocalImages.filter { img ->
                            img.is_recent &&
                                    !img.category.equals("Backgrounds", ignoreCase = true) &&
                                    !img.category.equals("Backgrounds Imported", ignoreCase = true) &&
                                    !img.category.equals("Images", ignoreCase = true) &&
                                    !img.category.equals("Images Imported", ignoreCase = true) &&
                                    (filterText.isBlank() || img.alt_text?.contains(filterText, ignoreCase = true) == true)
                        }
                    else ->
                        allLocalImages.filter { img ->
                            img.category.equals(category, ignoreCase = true) &&
                                    (filterText.isBlank() || img.alt_text?.contains(filterText, ignoreCase = true) == true)
                        }
                }
            }
            if (_binding == null) return@launch
            binding.noEmojis.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            imagesAdapter?.submitList(filtered)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CATEGORY = "arg_category"
        private const val ARG_FILTER   = "arg_filter"

        val BASE_TABS = listOf(
            "Emoticons", "Animals", "Nature", "Food", "Sports",
            "Transport", "Objects", "Alchemy", "Shapes", "Arrows", "Letters", "Flags"
        )

        fun isBaseTab(tab: String) = BASE_TABS.any { it.equals(tab, ignoreCase = true) }

        /** Single source of truth for category → emoji list. */
        fun emojiDataForCategory(
            category: String
        ): List<com.webscare.urducanvas.common.canvas.model.EmojiMeta> = when (category) {
            "Emoticons"  -> Constants.META_EMOTICONS
            "Animals"    -> Constants.META_ANIMALS
            "Nature"     -> Constants.META_NATURE
            "Food"       -> Constants.META_FOOD
            "Sports"     -> Constants.META_SPORTS
            "Transport"  -> Constants.META_TRANSPORT
            "Objects"    -> Constants.META_OBJECTS
            "Alchemy"    -> Constants.META_ALCHEMY
            "Shapes"     -> Constants.META_SHAPES
            "Arrows"     -> Constants.META_ARROWS
            "Letters"    -> Constants.META_LETTERS
            "Flags"      -> Constants.META_FLAGS
            else         -> emptyList()
        }

        fun newInstance(category: String, initialFilter: String = "") =
            ObjectsListFragment().apply {
                arguments = bundleOf(
                    ARG_CATEGORY to category,
                    ARG_FILTER   to initialFilter
                )
            }
    }
}