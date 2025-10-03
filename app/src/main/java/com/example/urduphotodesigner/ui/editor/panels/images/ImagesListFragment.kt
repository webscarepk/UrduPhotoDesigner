package com.example.urduphotodesigner.ui.editor.panels.images

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.utils.ImageProcessor.bitmapCompress
import com.example.urduphotodesigner.common.utils.ImageProcessor.trimTransparentEdges
import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.databinding.FragmentBackgroundsListBinding
import com.example.urduphotodesigner.ui.editor.panels.background.backgrounds.ImagesAdapter
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    private fun setEvents() {
        imagesAdapter = ImagesAdapter() { image, imageEntity ->

            mainViewModel.updateImage(imageEntity.copy(is_recent = true))

            val resized = viewModel.canvasSize.value?.height?.roundToInt()?.let {
                viewModel.canvasSize.value?.width?.let { it1 ->
                    bitmapCompress(
                        image,
                        it1.roundToInt(),
                        it
                    )
                }
            }
            viewModel.addSticker(resized?.trimTransparentEdges(), requireActivity())
        }
        binding.backgrounds.adapter = imagesAdapter
    }

    private fun initObservers() {
        lifecycleScope.launch {
            mainViewModel.localImages.collect { images ->
                updateImages(images)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    fun updateImages(images: List<ImageEntity>) {
        val imageList = when {
            categoryName.equals("Recents", true) -> images.filter {
                it.is_recent &&
                        (it.category.equals("Images", true) || it.category.equals("Images Imported", true))
            }
            else -> images.filter {
                it.category.equals(categoryName, ignoreCase = true)
            }
        }
        binding.noEmojis.visibility = if (imageList.isEmpty()) View.VISIBLE else View.GONE
        imagesAdapter.submitList(imageList)
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