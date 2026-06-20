package com.webscare.urducanvas.ui.editor.export

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import com.webscare.urducanvas.BuildConfig
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.print.PrintHelper
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.utils.Utils.copyToClipboard
import com.webscare.urducanvas.databinding.FragmentFinishExportBinding
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import androidx.core.graphics.scale

@AndroidEntryPoint
class FinishExportFragment : androidx.fragment.app.Fragment() {
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
            val tip = if (result?.pdfPath != null) {
                requireActivity().getString(R.string.exportTip_pdf)
            } else {
                requireActivity().getString(R.string.exportTip_image)
            }
            binding.tip.text = tip
            binding.fileName.text = result?.fileName
            binding.fileNameDetail.text = result?.fileName
            binding.fileType.text = "${result?.format} File"
            binding.fileSizeDetail.text = "%.1f MB".format(result?.fileSizeMB)
            binding.fileResolutionDetail.text = result?.resolution
            binding.fileQualityDetail.text = result?.quality
            binding.fileLocationDetail.text = result?.pdfPath ?: result?.imagePath
            result?.imagePath?.let { path ->
                ImageProcessor.filePathToBitmap(path)?.let { bitmap ->
                    binding.previewImage.setImageBitmap(bitmap)
                }
            }        }
    }

    private fun setEvents() {
        binding.preview.addPressEffect {
            val export = viewModel.exportResult.value ?: return@addPressEffect
            val bundle = Bundle().apply {
                putString("imagePath", export.imagePath)
            }
            view?.post {
                findNavController().navigate(R.id.previewExportFragment, bundle)
            }
        }

        binding.fileLocationDetail.addPressEffect {
            requireActivity().copyToClipboard(requireView(),"Exported Path", binding.fileLocationDetail.text.toString())
        }

        binding.back.addPressEffect { findNavController().navigateUp() }
        binding.btnExportAnother.addPressEffect { findNavController().popBackStack(R.id.editorFragment, false) }
        binding.backToHome.addPressEffect {
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.homeFragment, false)
                .build()
            view?.post { findNavController().navigate(R.id.homeFragment, null, navOptions) }
        }

        // 🔹 Share logic
        // 🔹 Share logic
        binding.share.addPressEffect {
            val export = viewModel.exportResult.value ?: return@addPressEffect

            if (BuildConfig.DEBUG) {
                // Debug: zip json + thumbnail image and share
                val jsonFile = File(export.jsonPath)
                val imageFile = File(export.imagePath)

                if (!jsonFile.exists() || !imageFile.exists()) return@addPressEffect

                val thumbnailFile = createThumbnail(imageFile, export.imagePath)
                if (thumbnailFile == null) return@addPressEffect

                val downloadFolder = android.os.Environment
                    .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val zipFile = File(downloadFolder, "design_${System.currentTimeMillis()}.zip")
                createZipFromFiles(listOf(jsonFile, thumbnailFile), zipFile)

                // Clean up temp thumbnail
                thumbnailFile.delete()

                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    zipFile
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Share Design Zip"))

            } else {
                // Release: share the final exported file (image or PDF) — what the user
                // actually made. Project sharing (.urdc) is on the Export Settings screen.
                val filePath = export.pdfPath ?: export.imagePath
                val file = File(filePath)
                if (!file.exists()) return@addPressEffect

                val mimeType = when {
                    filePath.endsWith(".pdf", true) -> "application/pdf"
                    filePath.endsWith(".png", true) -> "image/png"
                    filePath.endsWith(".jpg", true) || filePath.endsWith(".jpeg", true) -> "image/jpeg"
                    filePath.endsWith(".webp", true) -> "image/webp"
                    else -> "image/*"
                }
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Share"))
            }
        }

        // 🔹 Open logic (PDF or Image)
        binding.open.addPressEffect {
            val export = viewModel.exportResult.value ?: return@addPressEffect
            val filePath = export.pdfPath ?: export.imagePath
            val file = File(filePath)
            if (!file.exists()) return@addPressEffect

            // Use FileProvider to give safe Uri
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            val mimeType = if (filePath.endsWith(".pdf", true)) {
                "application/pdf"
            } else {
                "image/*"   // restrict to image viewers only
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open with"))
        }

        // 🔹 Print logic
        binding.print.addPressEffect {
            val export = viewModel.exportResult.value ?: return@addPressEffect

            export.pdfPath?.let { pdfPath ->
                val pdfFile = File(pdfPath)
                if (pdfFile.exists()) {
                    val uri = FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.fileprovider",
                        pdfFile
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = uri
                        type = "application/pdf"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Print PDF"))
                }
            } ?: run {
                val imagePath = export.imagePath ?: return@addPressEffect
                val bitmap = ImageProcessor.filePathToBitmap(imagePath) ?: return@addPressEffect

                // ✅ Fix: PrintHelper requires a live Activity, not just any context.
                // requireActivity() can be detached by the time the press animation ends.
                // Grab the activity reference before the lambda and guard it.
                val activity = activity ?: return@addPressEffect
                if (!activity.isFinishing && !activity.isDestroyed) {
                    val printHelper = PrintHelper(activity).apply {
                        scaleMode = PrintHelper.SCALE_MODE_FIT
                    }
                    printHelper.printBitmap(export.fileName ?: "Design", bitmap)
                }
            }
        }
    }

    private fun createThumbnail(originalFile: File, imagePath: String): File? {
        val original = ImageProcessor.filePathToBitmap(imagePath) ?: return null

        val maxDim = 512
        val scale = maxDim.toFloat() / maxOf(original.width, original.height)
        val thumbWidth = (original.width * scale).toInt()
        val thumbHeight = (original.height * scale).toInt()

        val thumbnail = original.scale(thumbWidth, thumbHeight)

        val thumbFile = File(requireContext().cacheDir, "thumb_${originalFile.nameWithoutExtension}.jpg")
        thumbFile.outputStream().use { out ->
            thumbnail.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
        }
        thumbnail.recycle()
        original.recycle()

        return thumbFile
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}