package com.example.urduphotodesigner.ui.editor.panels.background.backgrounds

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.databinding.FragmentBackgroundsListBinding
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BackgroundsListFragment : Fragment() {
    private var _binding: FragmentBackgroundsListBinding? = null
    private val binding get() = _binding!!

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

        setEvents()
        initObservers()
    }

    private fun setEvents() {
        imagesAdapter = ImagesAdapter(){ image ->
           if (isAdded){
               viewModel.ensureBackgroundElement(
                   requireActivity(),
                   viewModel.canvasSize.value!!.width,
                   viewModel.canvasSize.value!!.height
               )
               viewModel.setCanvasBackgroundImage(image)
           }
        }
        binding.backgrounds.adapter = imagesAdapter
    }

    private fun initObservers() {

        lifecycleScope.launch {
            mainViewModel.localImages.collect { images ->
                val imageList =
                    images.filter { it.category.equals("Backgrounds", ignoreCase = true) || it.category.equals("Backgrounds Imported", ignoreCase = true) }
                imagesAdapter.submitList(imageList)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        fun newInstance(): BackgroundsListFragment {
            return BackgroundsListFragment()
        }
    }
}