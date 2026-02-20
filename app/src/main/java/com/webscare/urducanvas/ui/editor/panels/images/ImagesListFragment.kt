package com.webscare.urducanvas.ui.editor.panels.images

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.utils.ImageProcessor.bitmapCompress
import com.webscare.urducanvas.common.utils.ImageProcessor.trimTransparentEdges
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.databinding.FragmentBackgroundsListBinding
import com.webscare.urducanvas.ui.editor.panels.background.backgrounds.ImagesAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import com.webscare.urducanvas.common.utils.ImageProcessor.trimTransparentEdges
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class ImagesListFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentBackgroundsListBinding? = null
    private val binding get() = _binding!!
    private var categoryName: String = ""

    private val mainViewModel: com.webscare.urducanvas.viewmodels.MainViewModel by activityViewModels()
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()
    private lateinit var imagesAdapter: com.webscare.urducanvas.ui.editor.panels.background.backgrounds.ImagesAdapter

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
        imagesAdapter =
            _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.background.backgrounds.ImagesAdapter() { image, imageEntity ->

                mainViewModel.updateImage(imageEntity.copy(is_recent = true))

                val resized = viewModel.canvasSize.value?.height?.roundToInt()?.let {
                    viewModel.canvasSize.value?.width?.let { it1 ->
                        _root_ide_package_.com.webscare.urducanvas.common.utils.ImageProcessor.bitmapCompress(
                            image,
                            it1.roundToInt(),
                            it
                        )
                    }
                }
                viewModel.addSticker(
                    resized?.trimTransparentEdges(),
                    requireActivity(),
                    _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ElementType.IMAGE
                )
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

    fun updateImages(images: List<com.webscare.urducanvas.data.model.ImageEntity>) {
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