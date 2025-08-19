package com.example.urduphotodesigner.ui.editor.panels.objects

import android.graphics.Bitmap
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
import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.databinding.FragmentObjectsListBinding
import com.example.urduphotodesigner.ui.editor.panels.background.backgrounds.ImagesAdapter
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
            category   = it.getString(ARG_CATEGORY).orEmpty()
            filterText = it.getString(ARG_FILTER).orEmpty()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentObjectsListBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        initObservers()
    }

    private fun setEvents() {
        imagesAdapter = ImagesAdapter { image ->
            val resized = bitmapCompress(image)
            viewModel.addSticker(resized, requireActivity())
        }

        val data: List<EmojiMeta> = when (category) {
            "Emoticons"   -> Constants.META_EMOTICONS
            "Animals"     -> Constants.META_ANIMALS
            "Nature"      -> Constants.META_NATURE
            "Food"        -> Constants.META_FOOD
            "Sports"      -> Constants.META_SPORTS
            "Transport"   -> Constants.META_TRANSPORT
            "Objects"     -> Constants.META_OBJECTS
            "Alchemy"     -> Constants.META_ALCHEMY
            "Shapes"      -> Constants.META_SHAPES
            "Arrows"      -> Constants.META_ARROWS
            "Letters"      -> Constants.META_LETTERS
            "Flags"       -> Constants.META_FLAGS
            else          -> emptyList()
        }
        val filtered = data.filter { it.name.contains(filterText, true) }
        if (filtered.isEmpty()){
            binding.noEmojis.visibility = View.VISIBLE
        }else{
            binding.noEmojis.visibility = View.GONE
        }
        val emojiAdapter = EmojiAdapter(requireActivity(), filtered) { bmp ->
            viewModel.addSticker(bmp, requireActivity())
        }

        binding.objects.apply {
            adapter = when (category) {
                "Stickers" -> imagesAdapter
                else -> emojiAdapter
            }
            setHasFixedSize(true)
        }

        if (category == "Stickers") refreshImages()
    }

    fun updateFilter(newFilter: String) {
        filterText = newFilter
        setEvents()
    }

    private fun bitmapCompress(image: Bitmap): Bitmap {
        val canvasWidth = 300
        val canvasHeight = 300

        val widthRatio = canvasWidth.toFloat() / image.width
        val heightRatio = canvasHeight.toFloat() / image.height
        val minScale = minOf(1f, widthRatio, heightRatio)

        val newWidth = (image.width * minScale).toInt()
        val newHeight = (image.height * minScale).toInt()

        val resized = Bitmap.createScaledBitmap(image, newWidth, newHeight, true)
        return resized
    }

    private fun initObservers() {

        lifecycleScope.launch {
            mainViewModel.localImages.collect { images ->
                allLocalImages = images
                if (category == "Stickers") refreshImages()
            }
        }
    }

    private fun refreshImages() {
        val filtered = allLocalImages
            .filter { img ->
                img.category.equals("Images Imported", true) || img.category.equals("Images", true) || img.category.equals("Stickers", true) &&
                        img.alt_text.contains(filterText, ignoreCase = true)
            }
        if (filtered.isEmpty()){
            binding.noEmojis.visibility = View.VISIBLE
        }else{
            binding.noEmojis.visibility = View.GONE
        }
        imagesAdapter.submitList(filtered)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        private const val ARG_CATEGORY = "arg_category"
        private const val ARG_FILTER   = "arg_filter"

        fun newInstance(category: String, initialFilter: String = "") =
            ObjectsListFragment().apply {
                arguments = bundleOf(
                    ARG_CATEGORY to category,
                    ARG_FILTER   to initialFilter
                )
            }
    }
}