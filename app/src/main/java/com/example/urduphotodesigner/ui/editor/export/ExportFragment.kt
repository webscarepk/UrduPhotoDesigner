package com.example.urduphotodesigner.ui.editor.export

import android.Manifest
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.ExportViewType
import com.example.urduphotodesigner.common.canvas.model.ExportOptions
import com.example.urduphotodesigner.common.utils.ImageProcessor
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.common.views.CanvasView
import com.example.urduphotodesigner.data.model.ExportResult
import com.example.urduphotodesigner.databinding.FragmentExportBinding
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class ExportFragment : Fragment() {
    private var _binding: FragmentExportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private var exportResult: ExportResult? = null
    private lateinit var canvasView: CanvasView
    private var rotateDrawable: AnimatedVectorDrawable? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) exportCanvasInternal()
            else showTopBanner("Permission denied to save image. Please enable it in settings.")
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setEvents()
        initObservers()
    }

    private fun setEvents() = with(binding) {

        stopIconRotation()
        btnExport.addPressEffect { startExport() }

        back.addPressEffect { findNavController().navigateUp() }

        btnReset.addPressEffect {
            viewModel.resetExportOptions()
            showTopBanner("Settings reset to defaults")
        }

        resolutionButton.addPressEffect {
            ExportOptionsFragment.newInstance(ExportViewType.RESOLUTION)
                .show(parentFragmentManager, "resolution_sheet")
        }

        qualityButton.addPressEffect {
            ExportOptionsFragment.newInstance(ExportViewType.QUALITY)
                .show(parentFragmentManager, "quality_sheet")
        }

        formatButton.addPressEffect {
            ExportOptionsFragment.newInstance(ExportViewType.FORMAT)
                .show(parentFragmentManager, "format_sheet")
        }
    }

    private fun initObservers() {
        viewModel.fetchExportOptionsFromDataStore()

        viewModel.canvasView.observe(viewLifecycleOwner) { canvas ->
            lifecycleScope.launch(Dispatchers.Main) {
                canvasView = canvas
                Log.d(
                    "ExportFragmentCanvasView",
                    "Received exportResult: ${viewModel.exportResult.value}"
                )
            }
        }

        viewModel.exportResult.observe(viewLifecycleOwner) { result ->
            Log.d(
                "ExportFragmentExportResult",
                "Received exportResult: ${viewModel.exportResult.value}"
            )
            result?.let {
                exportResult = it
                renderExportResult(it)
            }
        }

        viewModel.exportOptions.observe(viewLifecycleOwner) { options ->
            if (!isAdded) return@observe
            lifecycleScope.launch(Dispatchers.Main) {
                updateExportOptionsUI(options)
                renderPreview()
                Log.d(
                    "ExportFragmentExportOptions",
                    "Received exportResult: ${viewModel.exportResult.value}"
                )
            }
        }
    }

    private fun renderExportResult(result: ExportResult) = with(binding) {

        resolutionValue.text = result.resolution
        qualityValue.text = result.quality
        formatValue.text = "${result.format} • .${result.format.lowercase()}"

        tvExportSummaryDetails.text = "${result.resolution} • ${result.quality} • ${result.format}"
        resolution.text = result.resolution
        format.text = result.format
    }

    private fun updateExportOptionsUI(options: ExportOptions) = with(binding) {
        resolutionValue.text = "${options.resolution.name} • ${options.resolution.label}"
        qualityValue.text = "${options.quality.label} • ${options.quality.quality}%"
        formatValue.text = "${options.format.name} • .${options.format.name.lowercase()}"

        tvExportSummaryDetails.text =
            "${options.resolution.name} • ${options.quality.label} • ${options.format.name}"

        resolution.text = options.resolution.name
        format.text = options.format.name
    }

    private fun renderPreview() {
        val canvas = viewModel.canvasView.value ?: return
        val options = viewModel.exportOptions.value ?: return

        lifecycleScope.launch(Dispatchers.Default) {
            val (bitmap, _) = canvas.exportCanvasThumbnail()

            val sizeMB = getDisplayFileSizeMB(exportResult, options, bitmap)

            withContext(Dispatchers.Main) {
                binding.previewImage.setImageBitmap(bitmap)
                binding.exportPreviewProgress.visibility = View.GONE
                binding.tvExportSize.text = "%.1f MB".format(sizeMB)
                binding.fileSize.text = "%.1f MB".format(sizeMB)
            }
        }
    }

    private fun startExport() = with(binding) {
        binding.btnExport.isEnabled = false
        binding.btnExport.alpha = 0.7f

        btnExport.isEnabled = false
        btnExport.text = "Exporting..."
        startIconRotation()

        exportProgress.visibility = View.VISIBLE
        tvProgressPercent.text = "Exporting..."
        progressBar.progress = 30

        root.postDelayed({ exportCanvas() }, 300)
    }

    private fun exportCanvas() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                exportCanvasInternal()
            } else {
                requestPermissionLauncher.launch(permission)
            }
        } else {
            exportCanvasInternal()
        }
    }

    private fun exportCanvasInternal() {
        val options = viewModel.exportOptions.value ?: return
        startRotationAnimation(binding.view4)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var bitmap: Bitmap? = null
                var json: String? = null
                var videoTempPath: String? = null

                if (options.format.name.equals("MP4", true)) {
                    // 🎥 Export video
                    val exportFile = File(
                        requireContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                        "design_${System.currentTimeMillis()}.mp4"
                    )
                    videoTempPath = exportFile.absolutePath

                    canvasView.exportCanvasToMp4(
                        path = videoTempPath,
                        durationMs = 5000,
                        fps = 30
                    ) { percent ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            updateProgress(percent, "Exporting video…")
                        }
                    }

                    // Still generate bitmap + json for preview/export project
                    val (bmp, jsonString) = canvasView.exportCanvas(options) { _, _ -> }
                    bitmap = bmp
                    json = jsonString

                } else {
                    // 🖼️ Export canvas for Image/PDF
                    val (bmp, jsonString) = canvasView.exportCanvas(options) { percent, stage ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            val mapped = (percent * 0.7).toInt()
                            updateProgress(mapped, stage)
                        }
                    }
                    bitmap = bmp
                    json = jsonString
                }

                // 📝 Save actual export (Image / PDF / Video)
                updateProgressSafe(75, "Saving file…")
                val (uri, absPath, fileSizeMB) = saveExportFile(
                    bitmap = bitmap,
                    options = options,
                    videoTempPath = videoTempPath
                )

                // 🌄 Always save a preview image locally (not in gallery) for project reference
                val previewBitmap = bitmap.scale(800, (bitmap.height * (800f / bitmap.width)).toInt())
                val imagePath = previewBitmap.let {
                    ImageProcessor.bitmapToFilePath(requireActivity(), it)
                }

                // Save JSON
                val jsonPath = saveJson(json)

                val exportDate = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                val fileBaseName = "project_${System.currentTimeMillis()}"
                val fileName = "$fileBaseName.proj"
                val result = exportResult?.apply {
                    this.imagePath = imagePath
                    this.jsonPath = jsonPath
                    this.videoPath = if (options.format.name.equals("MP4", true)) absPath else null
                    this.pdfPath = if (options.format.name.equals("PDF", true)) absPath else null
                    this.fileName = fileName
                    this.fileSizeMB = fileSizeMB
                    this.resolution = options.resolution.label
                    this.format = options.format.name
                    this.quality = options.quality.label
                    this.updatedDate = exportDate
                    this.canvasSize = viewModel.canvasSize.value!!
                    this.isExported = true
                } ?: ExportResult(
                    imagePath = imagePath,
                    jsonPath = jsonPath,
                    fileName = fileName,
                    videoPath = if (options.format.name.equals("MP4", true)) absPath else null,
                    pdfPath = if (options.format.name.equals("PDF", true)) absPath else null,
                    fileSizeMB = fileSizeMB,
                    resolution = options.resolution.label,
                    format = options.format.name,
                    quality = options.quality.label,
                    canvasSize = viewModel.canvasSize.value!!,
                    exportDate = exportDate,
                    updatedDate = exportDate,
                ).also { exportResult = it }

                withContext(Dispatchers.Main) {
                    viewModel.setExportResult(result)
                    mainViewModel.insertExportResult(result)

                    updateProgress(100, "Export complete")
                    binding.exportProgress.postDelayed({
                        binding.exportProgress.visibility = View.GONE
                    }, 300)

                    stopRotationAnimation(binding.view4)
                    stopIconRotation()
                    binding.btnExport.isEnabled = true
                    binding.btnExport.alpha = 1.0f
                    binding.btnExport.text = "Export"

                    showTopBanner("${options.format.name} Export complete")
                    findNavController().navigate(R.id.finishExportFragment)
                }
            } catch (e: Exception) {
                Log.e("ExportFragment", "Export failed", e)
                withContext(Dispatchers.Main) {
                    stopRotationAnimation(binding.view4)
                    stopIconRotation()
                    binding.btnExport.isEnabled = true
                    binding.btnExport.alpha = 1.0f
                    binding.btnExport.text = "Export"
                    showTopBanner("Export failed: ${e.message}")
                }
            }
        }
    }

    // Helper to update progress from IO thread
    private suspend fun updateProgressSafe(percent: Int, stage: String) {
        withContext(Dispatchers.Main) {
            updateProgress(percent, stage)
        }
    }

    private fun showTopBanner(message: String) {
        val banner = binding.root.findViewById<View>(R.id.topBanner)
        val bannerText = banner.findViewById<TextView>(R.id.bannerText)

        bannerText.text = message
        banner.visibility = View.VISIBLE
        banner.alpha = 0f
        banner.translationY = -banner.height.toFloat()

        banner.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .withEndAction {
                banner.postDelayed({
                    banner.animate()
                        .alpha(0f)
                        .translationY(-banner.height.toFloat())
                        .setDuration(300)
                        .withEndAction { banner.visibility = View.GONE }
                        .start()
                }, 2000)
            }
            .start()
    }

    private fun startRotationAnimation(view: View) {
        view.animate()
            .rotationBy(360f)
            .setDuration(1000)
            .setInterpolator(null)
            .setListener(null)
            .withEndAction {
                // Loop the rotation
                if (view.visibility == View.VISIBLE) {
                    startRotationAnimation(view)
                }
            }
            .start()
    }

    private fun stopRotationAnimation(view: View) {
        view.animate().cancel()
        view.rotation = 0f
    }

    private fun estimateBitmapSize(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat?,
        quality: Int
    ): Long {
        val stream = ByteArrayOutputStream()
        bitmap.compress(format!!, quality, stream)
        return stream.size().toLong()
    }

    private fun getDisplayFileSizeMB(
        exportResult: ExportResult?,
        options: ExportOptions,
        bitmap: Bitmap? = null
    ): Double {
        // 1. Agar exportResult me fileSize already save hai
        if (exportResult?.fileSizeMB != null && exportResult.fileSizeMB > 0) {
            return exportResult.fileSizeMB
        }

        // 2. Agar koi path available hai aur file exist karti hai
        val path = exportResult?.imagePath ?: exportResult?.pdfPath
        if (!path.isNullOrEmpty()) {
            val file = File(path)
            if (file.exists()) {
                return file.length().toDouble() / (1024.0 * 1024.0)
            }
        }

        // 3. Fallback estimation (agar abhi tak file generate nahi hui)
        return when {
            options.format.name.equals("PDF", true) -> {
                // rough estimation for PDF
                val canvasSize = viewModel.canvasSize.value
                if (canvasSize != null) {
                    (canvasSize.width * canvasSize.height * 3.0) / (1024.0 * 1024.0)
                } else {
                    0.0
                }
            }
            bitmap != null -> {
                estimateBitmapSize(bitmap, options.format.format, options.quality.quality) /
                        (1024.0 * 1024.0)
            }
            else -> 0.0
        }
    }

    private fun saveJson(json: String): String {
        val file = File(exportResult?.jsonPath!!)
        file.writeText(json)
        return file.absolutePath
    }

    private suspend fun saveExportFile(
        bitmap: Bitmap?,
        options: ExportOptions,
        videoTempPath: String? = null
    ): Triple<Uri?, String?, Double> = withContext(Dispatchers.IO) {
        when {
            options.format.name.equals("PDF", ignoreCase = true) -> {
                val filename = "design_${System.currentTimeMillis()}.pdf"
                val contentValues = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, filename)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
                    put(
                        MediaStore.Files.FileColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/UrduDesigner"
                    )
                }

                val uri = requireContext().contentResolver.insert(
                    MediaStore.Files.getContentUri("external"), contentValues
                )

                var absPath: String? = null
                uri?.let {
                    requireContext().contentResolver.openOutputStream(it)?.use { stream ->
                        val pdfDoc = PdfDocument()

                        // 👇 use bitmap’s width & height instead of fixed A4
                        val pageInfo = PdfDocument.PageInfo.Builder(
                            bitmap!!.width,
                            bitmap.height,
                            1
                        ).create()

                        val page = pdfDoc.startPage(pageInfo)

                        // Draw without scaling → no borders
                        page.canvas.drawBitmap(bitmap, 0f, 0f, null)

                        pdfDoc.finishPage(page)
                        pdfDoc.writeTo(stream)
                        pdfDoc.close()
                    }
                    absPath = ImageProcessor.copyPdfUriToTempFile(requireContext(), it)?.absolutePath
                }

                val sizeMB = absPath?.let { File(it).length().toDouble() / (1024.0 * 1024.0) } ?: 0.0
                Triple(uri, absPath, sizeMB)
            }

            options.format.name.equals("MP4", ignoreCase = true) && videoTempPath != null -> {
                // save MP4 into gallery
                val filename = "design_${System.currentTimeMillis()}.mp4"
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES + "/UrduDesigner"
                    )
                }

                val uri = requireContext().contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues
                )

                var absPath: String? = null
                uri?.let {
                    requireContext().contentResolver.openOutputStream(it)?.use { stream ->
                        File(videoTempPath).inputStream().use { input ->
                            input.copyTo(stream)
                        }
                    }
                    absPath = ImageProcessor.copyVideoUriToTempFile(requireContext(), it)?.absolutePath
                }

                val sizeMB = absPath?.let { File(it).length().toDouble() / (1024.0 * 1024.0) } ?: 0.0
                Triple(uri, absPath, sizeMB)
            }

            else -> {
                // Image save (PNG, JPEG, WEBP)
                val formatExt = when (options.format.format) {
                    Bitmap.CompressFormat.PNG -> "png"
                    Bitmap.CompressFormat.JPEG -> "jpg"
                    Bitmap.CompressFormat.WEBP -> "webp"
                    else -> "png"
                }
                val mimeType = when (options.format.format) {
                    Bitmap.CompressFormat.PNG -> "image/png"
                    Bitmap.CompressFormat.JPEG -> "image/jpeg"
                    Bitmap.CompressFormat.WEBP -> "image/webp"
                    else -> "image/png"
                }
                val filename = "design_${System.currentTimeMillis()}.$formatExt"

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/UrduDesigner"
                    )
                }

                val uri = requireContext().contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
                )

                var absPath: String? = null
                uri?.let {
                    requireContext().contentResolver.openOutputStream(it)?.use { stream ->
                        bitmap?.compress(options.format.format!!, options.quality.quality, stream)
                    }
                    absPath = ImageProcessor.copyUriToTempFile(requireContext(), it)?.absolutePath
                }

                val sizeMB = absPath?.let { File(it).length().toDouble() / (1024.0 * 1024.0) } ?: 0.0
                Triple(uri, absPath, sizeMB)
            }
        }
    }

    private fun startIconRotation() {
        binding.btnExport.setIconResource(R.drawable.ic_rotate_animated)
        rotateDrawable = binding.btnExport.icon as? AnimatedVectorDrawable
        rotateDrawable?.start()
        binding.btnExport.icon.setTint(ContextCompat.getColor(requireContext(), R.color.white))
        binding.btnExport.isEnabled = false
        binding.btnExport.alpha = 0.6f
    }

    private fun stopIconRotation() {
        rotateDrawable?.stop()
        binding.btnExport.setIconResource(R.drawable.ic_export)
        binding.btnExport.icon.setTint(ContextCompat.getColor(requireContext(), R.color.white))
        binding.btnExport.isEnabled = true
        binding.btnExport.alpha = 1f
    }

    private fun updateProgress(percent: Int, message: String) = with(binding) {
        progressBar.progress = percent
        tvProgressPercent.text = "$message ($percent%)"
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}