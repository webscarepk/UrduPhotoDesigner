package com.example.urduphotodesigner.ui.editor.panels.images

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.databinding.FragmentBackgroundsListBinding
import com.example.urduphotodesigner.ui.editor.panels.background.backgrounds.ImagesAdapter
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.core.graphics.scale

@AndroidEntryPoint
class ImagesListFragment : Fragment() {
    private var _binding: FragmentBackgroundsListBinding? = null
    private val binding get() = _binding!!
    private var categoryName: String = ""

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()
    private lateinit var imagesAdapter: ImagesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBackgroundsListBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.let {
            categoryName = it.getString("category")!!
        }
        setEvents()
        initObservers()
    }

    fun Bitmap.trimTransparentEdges(): Bitmap {
        val width = this.width
        val height = this.height
        val pixels = IntArray(width * height)
        this.getPixels(pixels, 0, width, 0, 0, width, height)

        var top = height
        var left = width
        var right = 0
        var bottom = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val alpha = pixels[x + y * width] shr 24 and 0xff
                if (alpha > 0) { // pixel is not transparent
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }

        return if (right < left || bottom < top) {
            this // image fully transparent, return original
        } else {
            Bitmap.createBitmap(this, left, top, right - left + 1, bottom - top + 1)
        }
    }

    private fun setEvents() {
        imagesAdapter = ImagesAdapter(){ image ->
            val resized = viewModel.canvasSize.value?.height?.roundToInt()?.let { viewModel.canvasSize.value?.width?.let { it1 -> bitmapCompress(image, it1.roundToInt(), it) } }
            viewModel.addSticker(resized?.trimTransparentEdges(), requireActivity())
        }
        binding.backgrounds.adapter = imagesAdapter
    }

    private fun initObservers() {

        lifecycleScope.launch {
            mainViewModel.localImages.collect { images ->
                val imageList =
                    images.filter { it.category.equals(categoryName, ignoreCase = true) }
                binding.noEmojis.visibility = if (imageList.isEmpty())View.VISIBLE else View.GONE
                imagesAdapter.submitList(imageList)
            }
        }
    }

    private fun bitmapCompress(image: Bitmap, canvasWidth: Int, canvasHeight: Int): Bitmap {
        // Calculate ratios
        val widthRatio = canvasWidth.toFloat() / image.width
        val heightRatio = canvasHeight.toFloat() / image.height
        val scale = minOf(widthRatio, heightRatio)

        // Optional: limit the scale factor (to avoid extremely huge bitmaps)
        val maxScale = 3f  // allow up to 3× enlargement
        val finalScale = scale.coerceAtMost(maxScale)

        val newWidth = (image.width * finalScale).toInt().coerceAtLeast(1)
        val newHeight = (image.height * finalScale).toInt().coerceAtLeast(1)

        return image.scale(newWidth, newHeight)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        fun newInstance(tabName: String): ImagesListFragment {
            val fragment = ImagesListFragment()
            val args = Bundle()
            args.putString("category", tabName)
            fragment.arguments = args
            return fragment
        }
    }
}