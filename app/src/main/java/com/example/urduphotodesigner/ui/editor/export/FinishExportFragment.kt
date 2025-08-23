package com.example.urduphotodesigner.ui.editor.export

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
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.common.utils.Utils.copyToClipboard
import com.example.urduphotodesigner.databinding.FragmentFinishExportBinding
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

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
            binding.fileLocationDetail.text = result?.pdfPath ?: result?.imagePath
            binding.exportDate.text = result?.exportDate
            binding.previewImage.setImageBitmap(ImageProcessor.filePathToBitmap(result?.imagePath!!))
        }
    }

    private fun setEvents() {

        binding.fileLocationDetail.addPressEffect { requireActivity().copyToClipboard("Exported Path", binding.fileLocationDetail.text.toString()) }
        binding.back.addPressEffect { findNavController().navigateUp() }
        binding.btnExportAnother.addPressEffect { findNavController().navigateUp() }
        binding.backToHome.addPressEffect {
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.homeFragment, false) // clear everything above Home
                .build()
            findNavController().navigate(R.id.homeFragment, null, navOptions)
        }

        binding.share.addPressEffect {
            val export = viewModel.exportResult.value ?: return@addPressEffect

            val jsonFile = File(export.jsonPath)
            val imageFile = File(export.imagePath)

            if (!jsonFile.exists() || !imageFile.exists()) {
                // Handle error gracefully
                return@addPressEffect
            }

            val zipFile = File(requireContext().cacheDir, "design_${System.currentTimeMillis()}.zip")
            createZipFromFiles(listOf(jsonFile, imageFile), zipFile)

            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                zipFile
            )

            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(android.content.Intent.createChooser(intent, "Share Design Zip"))
        }

    }

    fun createZipFromFiles(files: List<File>, outputZip: File) {
        java.util.zip.ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
            files.forEach { file ->
                FileInputStream(file).use { fis ->
                    val entry = java.util.zip.ZipEntry(file.name)
                    zos.putNextEntry(entry)
                    fis.copyTo(zos)
                    zos.closeEntry()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}