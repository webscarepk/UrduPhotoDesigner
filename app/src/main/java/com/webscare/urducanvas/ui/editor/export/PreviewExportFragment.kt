package com.webscare.urducanvas.ui.editor.export

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentPreviewExportBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PreviewExportFragment : Fragment() {
    private var _binding: FragmentPreviewExportBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPreviewExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imagePath = arguments?.getString("imagePath")
        if (imagePath.isNullOrEmpty()) {
            Log.e("PreviewExportFragment", "No image path provided!")
            return
        }

        val bitmap = ImageProcessor.filePathToBitmap(imagePath)
        if (bitmap != null) {
            binding.zoomableImage.setImageBitmap(bitmap)
        } else {
            Log.e("PreviewExportFragment", "Failed to decode bitmap from $imagePath")
        }

        binding.zoomableImage.onDismissProgress = { progress ->
            binding.scrim.alpha = 1f - progress
        }

        binding.zoomableImage.onDismiss = {
            findNavController().navigateUp()
        }

        binding.back.addPressEffect {
            binding.zoomableImage.triggerDismiss()
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    binding.zoomableImage.triggerDismiss()
                }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.zoomableImage.onDismiss = null
        binding.zoomableImage.onDismissProgress = null
        _binding = null
    }
}