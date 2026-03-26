package com.webscare.urducanvas.ui.editor.panels.background.backgrounds

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.webscare.urducanvas.databinding.FragmentBackgroundsListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BackgroundsListFragment() : androidx.fragment.app.Fragment() {
    private var _binding: FragmentBackgroundsListBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: com.webscare.urducanvas.viewmodels.MainViewModel by activityViewModels()
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()
    private lateinit var imagesAdapter: ImagesAdapter
    val tabName: String? get() = arguments?.getString("tabName")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBackgroundsListBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        initObservers()
    }

    private fun setEvents() {

        imagesAdapter = ImagesAdapter { bitmap, svgDrawable, svgString, imageEntity ->
            if (isAdded) {
                mainViewModel.updateImage(imageEntity.copy(is_recent = true))
                viewModel.ensureBackgroundElement(requireActivity())
                viewModel.setCanvasBackgroundImage(bitmap)
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

    /** ✅ Public function called from adapter.refreshData(images) */
    fun updateImages(images: List<com.webscare.urducanvas.data.model.ImageEntity>) {
        val imageList = when {
            tabName.equals("Recents", true) -> images.filter {
                it.is_recent && (it.category.equals(
                    "Backgrounds",
                    true
                ) || it.category.equals("Backgrounds Imported", true))
            }

            else -> images.filter {
                it.category.equals(
                    "Backgrounds",
                    true
                ) || it.category.equals("Backgrounds Imported", true)
            }
        }
        binding.noEmojis.visibility = if (imageList.isEmpty()) View.VISIBLE else View.GONE
        imagesAdapter.submitList(imageList)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        fun newInstance(tabName: String): BackgroundsListFragment {
            val fragment = BackgroundsListFragment()
            val bundle = Bundle().apply {
                putString("tabName", tabName)
            }
            fragment.arguments = bundle
            return fragment
        }
    }
}