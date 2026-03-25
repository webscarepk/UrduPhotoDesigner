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
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.ImageProcessor.bitmapCompress
import com.webscare.urducanvas.common.utils.ImageProcessor.trimTransparentEdges
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.databinding.FragmentBackgroundsListBinding
import com.webscare.urducanvas.ui.editor.panels.background.backgrounds.ImagesAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class ImagesListFragment : Fragment() {

    private var _binding: FragmentBackgroundsListBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()

    private lateinit var imagesAdapter: ImagesAdapter

    private var categoryName: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentBackgroundsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.let {
            categoryName = it.getString("category") ?: ""
        }

        setEvents()
        initObservers()
    }

    private fun setEvents() {

        imagesAdapter = ImagesAdapter { bitmap, svgDrawable, imageEntity ->

            mainViewModel.updateImage(imageEntity.copy(is_recent = true))

            // -------------------------------
            // BACKGROUND IMAGE
            // -------------------------------

            if (
                imageEntity.category.equals("Backgrounds", true) ||
                imageEntity.category.equals("Backgrounds Imported", true)
            ) {

                viewModel.ensureBackgroundElement(requireActivity())
                viewModel.setCanvasBackgroundImage(bitmap)

                return@ImagesAdapter
            }

            // -------------------------------
            // NORMAL IMAGE STICKER
            // -------------------------------

            if (svgDrawable != null) {
                // ✅ SVG sticker — no bitmap involved at all
                viewModel.addSvgSticker(svgDrawable, requireActivity(), imageEntity.is_premium)
            } else {
                // Normal bitmap sticker
                val resized = viewModel.canvasSize.value?.height?.roundToInt()?.let { h ->
                    viewModel.canvasSize.value?.width?.roundToInt()?.let { w ->
                        bitmapCompress(bitmap!!, w, h)
                    }
                }
                viewModel.addSticker(resized?.trimTransparentEdges(), requireActivity(), ElementType.IMAGE, imageEntity.is_premium)
            }
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

    fun updateImages(images: List<ImageEntity>) {

        val imageList = when {

            // -------------------------------
            // RECENTS TAB
            // -------------------------------

            categoryName.equals("Recents", true) -> {

                images.filter {

                    it.is_recent &&
                            (
                                    it.category.equals("Images", true) ||
                                            it.category.equals("Images Imported", true) ||
                                            it.category.equals("Backgrounds", true) ||
                                            it.category.equals("Backgrounds Imported", true)
                                    )
                }
            }

            // -------------------------------
            // BACKGROUNDS TAB
            // -------------------------------

            categoryName.equals("Backgrounds", true) -> {

                images.filter {

                    it.category.equals("Backgrounds", true) ||
                            it.category.equals("Backgrounds Imported", true)
                }
            }

            // -------------------------------
            // IMAGES TAB
            // -------------------------------

            else -> {

                images.filter {

                    it.category.equals(categoryName, true)
                }
            }
        }

        binding.noEmojis.visibility =
            if (imageList.isEmpty()) View.VISIBLE else View.GONE

        imagesAdapter.submitList(imageList)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {

        fun newInstance(category: String): ImagesListFragment {

            val fragment = ImagesListFragment()

            val args = Bundle().apply {

                putString("category", category)
            }

            fragment.arguments = args

            return fragment
        }
    }
}