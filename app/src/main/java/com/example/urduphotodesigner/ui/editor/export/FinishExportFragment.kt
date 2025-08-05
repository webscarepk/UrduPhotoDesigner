package com.example.urduphotodesigner.ui.editor.export

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.utils.ImageProcessor
import com.example.urduphotodesigner.databinding.FragmentFinishExportBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FinishExportFragment : Fragment() {
    private var _binding: FragmentFinishExportBinding? = null
    private val binding get() = _binding!!

    val viewModel: CanvasViewModel by activityViewModels()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFinishExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        initObservers()
    }

    private fun initObservers() {
        viewModel.exportResult.observe(viewLifecycleOwner) { result ->
            binding.fileName.text = result?.fileName
            binding.fileNameDetail.text = result?.fileName
            binding.fileType.text = "${result?.format} File"
            binding.fileSizeDetail.text = "%.1f MB".format(result?.fileSizeMB)
            binding.fileResolutionDetail.text = result?.resolution
            binding.fileQualityDetail.text = result?.quality
            binding.fileLocationDetail.text = result?.imagePath
            binding.exportDate.text = result?.exportDate
            binding.previewImage.setImageBitmap(ImageProcessor.filePathToBitmap(result?.imagePath!!))
        }
    }

    private fun setEvents() {
        binding.back.setOnClickListener { findNavController().navigateUp() }
        binding.btnExportAnother.setOnClickListener { findNavController().navigateUp() }
        binding.backToHome.setOnClickListener {
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.homeFragment, false) // clear everything above Home
                .build()
            findNavController().navigate(R.id.homeFragment, null, navOptions)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}