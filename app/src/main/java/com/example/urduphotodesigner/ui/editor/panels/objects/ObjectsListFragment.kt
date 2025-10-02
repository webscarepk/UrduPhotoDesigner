package com.example.urduphotodesigner.ui.editor.panels.objects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.model.EmojiMeta
import com.example.urduphotodesigner.common.utils.Constants
import com.example.urduphotodesigner.common.utils.ImageProcessor.bitmapCompress
import com.example.urduphotodesigner.common.utils.ImageProcessor.trimTransparentEdges
import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.databinding.FragmentObjectsListBinding
import com.example.urduphotodesigner.ui.editor.panels.background.backgrounds.ImagesAdapter
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class ObjectsListFragment : Fragment() {
    private var _binding: FragmentObjectsListBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()
    private lateinit var imagesAdapter: ImagesAdapter
    private var allLocalImages: List<ImageEntity> = emptyList()

    private var category: String = ""
    private var filterText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            category = it.getString(ARG_CATEGORY).orEmpty()
            filterText = it.getString(ARG_FILTER).orEmpty()
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

        setEvents()
        initObservers()
    }

    private fun isBaseTab(tab: String): Boolean {
        return listOf(
            "Emoticons",
            "Animals",
            "Nature",
            "Food",
            "Sports",
            "Transport",
            "Objects",
            "Alchemy",
            "Shapes",
            "Arrows",
            "Letters",
            "Flags"
        ).any { it.equals(tab, true) }
    }

    private fun setEvents() {
        imagesAdapter = ImagesAdapter { image, imageEntity ->

            mainViewModel.updateImage(imageEntity.copy(is_recent = true))

            val resized = viewModel.canvasSize.value?.height?.roundToInt()?.let { h ->
                viewModel.canvasSize.value?.width?.let { w ->
                    bitmapCompress(image, w.roundToInt(), h)
                }
            }
            viewModel.addSticker(resized?.trimTransparentEdges(), requireActivity())
        }

        if (isBaseTab(category)) {
            val data: List<EmojiMeta> = when (category) {
                "Emoticons" -> Constants.META_EMOTICONS
                "Animals" -> Constants.META_ANIMALS
                "Nature" -> Constants.META_NATURE
                "Food" -> Constants.META_FOOD
                "Sports" -> Constants.META_SPORTS
                "Transport" -> Constants.META_TRANSPORT
                "Objects" -> Constants.META_OBJECTS
                "Alchemy" -> Constants.META_ALCHEMY
                "Shapes" -> Constants.META_SHAPES
                "Arrows" -> Constants.META_ARROWS
                "Letters" -> Constants.META_LETTERS
                "Flags" -> Constants.META_FLAGS
                else -> emptyList()
            }

            val filtered = data.filter { it.name.contains(filterText, true) }
            binding.noEmojis.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE

            val emojiAdapter = EmojiAdapter(requireActivity(), filtered) { bmp ->
                viewModel.addSticker(bmp, requireActivity())
            }
            binding.objects.adapter = emojiAdapter
        } else {
            binding.objects.adapter = imagesAdapter
            refreshImages(category)
        }

        binding.objects.setHasFixedSize(true)
    }

    fun updateFilter(newFilter: String) {
        filterText = newFilter
        setEvents()
    }

    private fun initObservers() {
        lifecycleScope.launch {
            mainViewModel.localImages.collect { images ->
                allLocalImages = images
                if (!isBaseTab(category)) {
                    refreshImages(category)
                }
            }
        }
    }

    private fun refreshImages(tabName: String) {
        val filtered = when {
            tabName.equals("Recents", true) -> allLocalImages.filter { img ->
                img.is_recent && !(
                        img.category.equals("Backgrounds", true) ||
                                img.category.equals("Backgrounds Imported", true) ||
                                img.category.equals("Images", true) ||
                                img.category.equals("Images Imported", true)
                        ) && (filterText.isBlank() || img.alt_text?.contains(filterText, true) == true)
            }

            else -> allLocalImages.filter { img ->
                img.category.equals(tabName, true) &&
                        (filterText.isBlank() || img.alt_text?.contains(filterText, true) == true)
            }
        }

        binding.noEmojis.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        imagesAdapter.submitList(filtered)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        private const val ARG_CATEGORY = "arg_category"
        private const val ARG_FILTER = "arg_filter"

        fun newInstance(category: String, initialFilter: String = "") =
            ObjectsListFragment().apply {
                arguments = bundleOf(
                    ARG_CATEGORY to category, ARG_FILTER to initialFilter
                )
            }
    }
}