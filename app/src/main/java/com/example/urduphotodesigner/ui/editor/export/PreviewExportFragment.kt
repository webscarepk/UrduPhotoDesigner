package com.example.urduphotodesigner.ui.editor.export

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.urduphotodesigner.common.utils.ImageProcessor
import com.example.urduphotodesigner.databinding.FragmentPreviewExportBinding
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
        imagePath?.let {
            val bitmap = ImageProcessor.filePathToBitmap(it)
            binding.zoomableImage.setImageBitmap(bitmap)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}