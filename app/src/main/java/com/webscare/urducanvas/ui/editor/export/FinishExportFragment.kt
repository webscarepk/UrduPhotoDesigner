package com.webscare.urducanvas.ui.editor.export

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.webscare.urducanvas.BuildConfig
import com.webscare.ads.NativeSize
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
import com.google.android.material.snackbar.Snackbar

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
        
        binding.exportSuccessNativeAd.setAdUnitIdAndSize(BuildConfig.AD_NATIVE_EXPORT_SUCCESS, NativeSize.MEDIUM)

        view.alpha = 0f
        view.translationY = 80f
        view.animate().alpha(1f).translationY(0f).setDuration(350).start()
        setEvents()
        initObservers()
    }

    private fun formatFileSize(sizeMB: Double?): String {
        val size = sizeMB ?: return "0 KB"
        return if (size < 1.0) {
            val sizeKB = size * 1024.0
            "%.0f KB".format(sizeKB)
        } else {
            "%.1f MB".format(size)
        }
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
            binding.fileSizeDetail.text = formatFileSize(result?.fileSizeMB)
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
            val context = requireContext()
            requireActivity().copyToClipboard(requireView(), "Exported Path", binding.fileLocationDetail.text.toString())
            
            // Swap icon to ic_done tinted with appColor
            val doneDrawable = ContextCompat.getDrawable(context, R.drawable.ic_done)?.mutate()
            doneDrawable?.setTint(ContextCompat.getColor(context, R.color.appColor))
            binding.fileLocationDetail.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, doneDrawable, null)
            
            // Post delayed to switch back to ic_copy tinted with black
            binding.fileLocationDetail.postDelayed({
                if (isAdded) {
                    val copyDrawable = ContextCompat.getDrawable(context, R.drawable.ic_copy)?.mutate()
                    copyDrawable?.setTint(ContextCompat.getColor(context, R.color.black))
                    binding.fileLocationDetail.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, copyDrawable, null)
                }
            }, 2000)
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

            if (!BuildConfig.IS_PROD_LOGIC) {
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
                val fileName = export.fileName ?: "Design"

                val activity = requireActivity()
                if (!PrintHelper.systemSupportsPrint()) {
                    Snackbar.make(binding.root, "Printing is not supported on this device", Snackbar.LENGTH_SHORT).show()
                    return@addPressEffect
                }

                activity.window.decorView.post {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        try {
                            PrintHelper(activity).apply {
                                scaleMode = PrintHelper.SCALE_MODE_FIT
                            }.printBitmap(fileName, bitmap)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Snackbar.make(binding.root, "Failed to start printing", Snackbar.LENGTH_SHORT).show()
                        }
                    }
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
        _binding?.previewImage?.setImageBitmap(null)
        super.onDestroyView()
        _binding = null
    }
}