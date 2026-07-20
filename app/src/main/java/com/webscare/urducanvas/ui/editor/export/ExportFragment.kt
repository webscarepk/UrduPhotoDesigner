package com.webscare.urducanvas.ui.editor.export

import android.Manifest
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
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
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.webscare.urducanvas.BuildConfig
import com.webscare.ads.WebsCareAds
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.io.ProjectCodec
import com.webscare.urducanvas.common.canvas.enums.ExportViewType
import com.webscare.urducanvas.common.canvas.model.ExportOptions
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.views.CanvasView
import com.webscare.urducanvas.data.model.ExportResult
import com.webscare.urducanvas.databinding.FragmentExportBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import com.webscare.urducanvas.viewmodels.SubscriptionsViewModel
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
class ExportFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentExportBinding? = null
    private val binding get() = _binding!!
    private val subscriptionViewModel: SubscriptionsViewModel by viewModels()
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
        super.onViewCreated(view, savedInstanceState)
        
        // Preload export interstitial ad
        WebsCareAds.preloadInterstitial(requireContext(), BuildConfig.AD_INTERSTITIAL_EXPORT)

        view.alpha = 0f
        view.translationY = 80f
        view.animate().alpha(1f).translationY(0f).setDuration(350).start()
        setEvents()
        initObservers()
    }

    private fun setEvents() = with(binding) {

        Log.d("ExportFragmentOnCreate", "Received exportResult: ${viewModel.exportResult.value}")
        stopIconRotation()
        btnExport.addPressEffect { startExport() }

        back.addPressEffect { findNavController().navigateUp() }

        btnShare.addPressEffect {
            if (isPremiumLocked()) {
                findNavController().navigate(R.id.subscriptionsFragment)
                return@addPressEffect
            }

            val export = viewModel.exportResult.value
            val projectPath = export?.jsonPath
            if (projectPath.isNullOrBlank()) {
                showTopBanner("Export the project first to share it")
                return@addPressEffect
            }
            val sourceFile = File(projectPath)
            if (!sourceFile.exists()) {
                showTopBanner("Project file not found")
                return@addPressEffect
            }

            // In release, always share as .urdc — even if the source is a plain .json
            // template downloaded from the server. Wrap it on the fly into a temp .urdc.
            val fileToShare: File = if (!BuildConfig.DEBUG &&
                !ProjectCodec.isUrdcFile(sourceFile)) {
                val tmp = File(
                    requireContext().cacheDir,
                    sourceFile.nameWithoutExtension + "." + ProjectCodec.FILE_EXTENSION
                )
                ProjectCodec.wrapJsonFile(plainJsonFile = sourceFile, target = tmp)
                tmp
            } else {
                sourceFile
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                fileToShare
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, "Share project"))
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

        // LiveData — already safe, viewLifecycleOwner used
        viewModel.canvasView.observe(viewLifecycleOwner) { canvas ->
            if (canvas == null) return@observe
            canvasView = canvas
            val options = viewModel.exportOptions.value
            if (options != null && canvas.canvasWidth > 0 && canvas.canvasHeight > 0) {
                renderPreview()
            }
        }

        viewModel.exportResult.observe(viewLifecycleOwner) { result ->
            Log.d("ExportFragmentExportResult", "Received exportResult: $result")
            result?.let {
                exportResult = it
                renderExportResult(it)
            }
        }

        viewModel.exportOptions.observe(viewLifecycleOwner) { options ->
            if (!isAdded) return@observe
            // FIX: was plain lifecycleScope — now tied to view lifecycle
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                updateExportOptionsUI(options)
                val canvas = viewModel.canvasView.value
                if (canvas != null && canvas.canvasWidth > 0 && canvas.canvasHeight > 0) {
                    renderPreview()
                }
            }
        }

        viewModel.hasPremiumAsset.observe(viewLifecycleOwner) { hasPremium ->
            updatePremiumBannerVisibility(hasPremium)
        }

        // FIX: was missing repeatOnLifecycle — coroutine would outlive view
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                subscriptionViewModel.isSubscribed.collect {
                    val hasPremiumAsset = viewModel.hasPremiumAsset.value == true
                    updatePremiumBannerVisibility(hasPremiumAsset)
                    viewModel.exportOptions.value?.let { opts -> updateExportOptionsUI(opts) }
                }
            }
        }
    }

    private fun updatePremiumBannerVisibility(hasPremium: Boolean) = with(binding) {
        val isFromPremiumTemplate = viewModel.exportResult.value?.isFromPremiumTemplate == true
        val locked = isPremiumLocked()

        premiumAssets.isVisible = (hasPremium || isFromPremiumTemplate)
                && !subscriptionViewModel.isSubscribed.value

        if (locked) {
            btnExport.text = getString(R.string.buy_now)
            btnExport.setIconResource(R.drawable.ic_premium_stroke)

            when {
                // Project derived from a premium template, no extra premium assets
                isFromPremiumTemplate && !hasPremium -> {
                    seeMore.text = "This project uses a premium template"
                    // Update the card title + description
                    premiumTitle.text =
                        "Premium Template"
                    premiumSubTitle.text =
                        "This design is based on a premium template. Upgrade to export or share it."
                }

                // Premium assets inside canvas (with or without premium template origin)
                hasPremium -> {
                    seeMore.paintFlags = seeMore.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    seeMore.addPressEffect { openPremiumAssetDetailSheet() }
                    premiumTitle.text =
                        "Premium Assets Detected"
                    premiumSubTitle.text =
                        "This design contains premium fonts, stickers, or images. Upgrade to export without restrictions."
                }

                // Both — premium template AND premium assets inside it
                isFromPremiumTemplate && hasPremium -> {
                    seeMore.paintFlags = seeMore.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    seeMore.addPressEffect { openPremiumAssetDetailSheet() }
                    premiumTitle.text =
                        "Premium Template & Assets"
                    premiumSubTitle.text =
                        "This design uses a premium template and contains premium assets. Upgrade to unlock export."
                }
            }
        } else {
            btnExport.text = getString(R.string.export)
            btnExport.setIconResource(R.drawable.ic_export)
        }
    }

    private fun openPremiumAssetDetailSheet() {
        PremiumAssetsSheet.newInstance().show(parentFragmentManager, "premium_assets_sheet")
    }

    private fun renderExportResult(result: ExportResult) =
        with(binding) {

            resolutionValue.text = result.resolution
            qualityValue.text = result.quality
            formatValue.text = "${result.format} • .${result.format.lowercase()}"

            tvExportSummaryDetails.text =
                "${result.resolution} • ${result.quality} • ${result.format}"
            resolution.text = result.resolution
            format.text = result.format
        }

    private fun updateExportOptionsUI(options: ExportOptions) = with(binding) {
        resolutionValue.text = "${options.resolution.name} • ${options.resolution.label}"
        qualityValue.text = "${options.quality.label} • ${options.quality.quality}%"
        formatValue.text = "${options.format.name} • .${options.format.name.lowercase()}"

        // hide premium badges if subscribed
        val isSubscribed = subscriptionViewModel.isSubscribed.value
        isPremiumFormat.isVisible = options.format.isPremium && !isSubscribed
        isPremiumResolution.isVisible = options.resolution.isPremium && !isSubscribed

        tvExportSummaryDetails.text =
            "${options.resolution.name} • ${options.quality.label} • ${options.format.name}"
        resolution.text = options.resolution.name
        format.text = options.format.name

        if (isPremiumLocked()) {
            btnExport.text = getString(R.string.buy_now)
            btnExport.setIconResource(R.drawable.ic_premium_stroke)
        } else {
            btnExport.text = getString(R.string.export)
            btnExport.setIconResource(R.drawable.ic_export)
        }
    }

    private fun formatFileSize(sizeMB: Double): String {
        return if (sizeMB < 1.0) {
            val sizeKB = sizeMB * 1024.0
            "%.0f KB".format(sizeKB)
        } else {
            "%.1f MB".format(sizeMB)
        }
    }

    private fun renderPreview() {
        val canvas = viewModel.canvasView.value ?: return
        val options = viewModel.exportOptions.value ?: return
        lifecycleScope.launch(Dispatchers.Default) {
            val bitmap = canvas.generatePreviewBitmap()

            val sizeMB = getDisplayFileSizeMB(options, canvas)

            withContext(Dispatchers.Main) {
                val b = _binding ?: return@withContext
                b.previewImage.setImageBitmap(bitmap)
                b.exportPreviewProgress.visibility = View.GONE
                b.tvExportSize.text = formatFileSize(sizeMB)
                b.fileSize.text = formatFileSize(sizeMB)
            }
        }
    }

    private fun startExport() = with(binding) {
        if (isPremiumLocked()) {
            findNavController().navigate(R.id.subscriptionsFragment)
            return@with
        }

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
                    requireContext(), permission
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
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Render full canvas
                val (bitmap, json) = canvasView.exportCanvas(
                    options, jsonOutputPath = exportResult?.jsonPath!!
                ) { percent, stage ->
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                        val mapped = (percent * 0.7).toInt()
                        updateProgress(mapped, stage)
                    }
                }

                // 2. Generate lightweight preview + save thumbnail
                val previewBitmap =
                    bitmap.scale(800, (bitmap.height * (800f / bitmap.width)).toInt())

                // Estimate file size (using preview only for quick responsiveness)
                val sizeMB = if (options.format.name.equals("PDF", true)) {
                    (bitmap.width * bitmap.height * 4) / (1024.0 * 1024.0)
                } else {
                    estimateBitmapSize(
                        bitmap, options.format.format, options.quality.quality
                    ) / (1024.0 * 1024.0)
                }

                withContext(Dispatchers.Main) {
                    updateProgress(50, "Preview ready…")
                }

                // 3. Save full export (Image or PDF)
                updateProgressSafe(75, "Saving file…")
                val savedUri = saveImageOrPdf(bitmap, options)
                updateProgressSafe(85, "Finalizing…")
                val pdfPath = if (options.format.name.equals("PDF", true)) {
                    savedUri?.let {
                        ImageProcessor.copyPdfUriToTempFile(
                            requireContext(), it
                        )?.absolutePath
                    }
                } else {
                    null
                }

                val imagePath = if (options.format.name.equals("PDF", true)) {
                    ImageProcessor.bitmapToFilePath(requireActivity(), previewBitmap)
                } else {
                    savedUri?.let {
                        ImageProcessor.copyUriToTempFile(
                            requireContext(), it
                        )?.absolutePath
                    }
                } ?: throw IllegalStateException("Failed to save file")

                updateProgressSafe(95, "Just a moment…")

                // Wrap the plain-JSON file into our .urdc container.
                //  DEBUG  -> stays plain JSON (encrypted=false). Designer/template pipeline
                //            (zip share) keeps working untouched.
                //  RELEASE-> AES-GCM encrypted .urdc with an embedded preview thumbnail.
                val jsonPath = if (BuildConfig.DEBUG) {
                    // Authoring build: keep readable JSON exactly as before.
                    json.absolutePath
                } else {
                    val urdcFile = File(
                        json.parentFile,
                        json.nameWithoutExtension + "." + ProjectCodec.FILE_EXTENSION
                    )
                    ProjectCodec.wrapJsonFile(
                        plainJsonFile = json,
                        target = urdcFile,
                        thumbnail = previewBitmap
                    )
                    // Remove the redundant plain JSON so the structure can't leak.
                    if (urdcFile.exists() && urdcFile.absolutePath != json.absolutePath) {
                        json.delete()
                    }
                    urdcFile.absolutePath
                }

                // 4. Final file size (real saved file)
                val imageOrPdfSizeMB = if (options.format.name.equals("PDF", true)) {
                    pdfPath?.let { File(it).length().toDouble() / (1024.0 * 1024.0) } ?: sizeMB
                } else {
                    File(imagePath).takeIf { it.exists() }?.length()?.toDouble()
                        ?.div(1024.0 * 1024.0) ?: sizeMB
                }
                val jsonSizeMB = File(jsonPath).takeIf { it.exists() }?.length()?.toDouble()
                    ?.div(1024.0 * 1024.0) ?: 0.0
                val fileSizeMB = imageOrPdfSizeMB + jsonSizeMB

                val exportDate =
                    SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())

                Log.d(TAG, "exportCanvasInternal: ${exportResult?.fileName} ")
                val fileBaseName = "project_${System.currentTimeMillis()}"
                val fileName = "$fileBaseName"
                val result = exportResult?.apply {
                    this.imagePath = imagePath
                    this.jsonPath = jsonPath
                    this.pdfPath = pdfPath
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
                    pdfPath = pdfPath,
                    fileSizeMB = fileSizeMB,
                    resolution = options.resolution.label,
                    format = options.format.name,
                    quality = options.quality.label,
                    canvasSize = viewModel.canvasSize.value!!,
                    exportDate = exportDate,
                    updatedDate = exportDate,
                    isFromPremiumTemplate = viewModel.exportResult.value?.isFromPremiumTemplate == true,
                    sourceTemplateId = viewModel.exportResult.value?.sourceTemplateId,
                ).also { exportResult = it }

                withContext(Dispatchers.Main) {
                    viewModel.setExportResult(result)
                    mainViewModel.insertExportResult(result)
                }

                // 7. Update UI on complete
                withContext(Dispatchers.Main) {
                    val b = _binding ?: return@withContext
                    updateProgress(100, "Export complete")
                    b.exportProgress.postDelayed({
                        _binding?.exportProgress?.visibility = View.GONE
                    }, 1000)

                    stopRotationAnimation(b.view4)
                    stopIconRotation()
                    b.btnExport.isEnabled = true
                    b.btnExport.alpha = 1.0f
                    b.btnExport.text = "Export"

                    val performFinishNavigation = {
                        view?.post {
                            findNavController().navigate(R.id.finishExportFragment)
                        }
                    }

                    val activity = activity
                    if (activity != null) {
                        WebsCareAds.showInterstitial(activity, BuildConfig.AD_INTERSTITIAL_EXPORT) {
                            performFinishNavigation()
                        }
                    } else {
                        performFinishNavigation()
                    }
                }
            } catch (e: Exception) {
                Log.e("ExportFragment", "Export failed", e)
                withContext(Dispatchers.Main) {
                    val b = _binding ?: return@withContext
                    stopRotationAnimation(b.view4)
                    stopIconRotation()
                    b.exportProgress.visibility = View.GONE
                    b.btnExport.isEnabled = true
                    b.btnExport.alpha = 1.0f
                    b.btnExport.text = "Export"
                    showTopBanner("Export failed: ${e.message}")
                }
            }
        }
    }

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

        banner.animate().alpha(1f).translationY(0f).setDuration(300).withEndAction {
            banner.postDelayed({
                banner.animate().alpha(0f).translationY(-banner.height.toFloat()).setDuration(300)
                    .withEndAction { banner.visibility = View.GONE }.start()
            }, 2000)
        }.start()
    }

    private fun startRotationAnimation(view: View) {
        view.animate().rotationBy(360f).setDuration(1000).setInterpolator(null).setListener(null)
            .withEndAction {
                // Loop the rotation
                if (view.isVisible) {
                    startRotationAnimation(view)
                }
            }.start()
    }

    private fun stopRotationAnimation(view: View) {
        view.animate().cancel()
        view.rotation = 0f
    }

    private fun getExportedImageSizeMBEstimate(
        options: ExportOptions, originalCanvasWidth: Float, originalCanvasHeight: Float
    ): Double {
        val scaleFactor = options.resolution.scaleFactor

        val targetWidth = (originalCanvasWidth * scaleFactor).toInt()
        val targetHeight = (originalCanvasHeight * scaleFactor).toInt()
        val baseRawMB = (targetWidth.toLong() * targetHeight.toLong() * 4) / (1024.0 * 1024.0)
        val qualityPercentage = options.quality.quality / 100.0

        return when {
            options.format.name.equals("PDF", true) -> {
                baseRawMB * 0.15 * (1 + (qualityPercentage - 0.5))
            }

            options.format.format == Bitmap.CompressFormat.PNG -> {
                baseRawMB * 0.25
            }

            options.format.format == Bitmap.CompressFormat.JPEG || options.format.format == Bitmap.CompressFormat.WEBP -> {
                val baseCompressionFactor =
                    if (options.format.format == Bitmap.CompressFormat.JPEG) 0.04 else 0.03
                baseRawMB * baseCompressionFactor * qualityPercentage
            }

            else -> 0.0
        }
    }

    private fun estimateBitmapSize(
        bitmap: Bitmap, format: Bitmap.CompressFormat?, quality: Int
    ): Long {
        val stream = ByteArrayOutputStream()
        bitmap.compress(format!!, quality, stream)
        return stream.size().toLong()
    }

    private fun getDisplayFileSizeMB(
        options: ExportOptions, canvas: CanvasView
    ): Double {
        return getExportedImageSizeMBEstimate(
            options, canvas.canvasWidth.toFloat(), canvas.canvasHeight.toFloat()
        )
    }


    private suspend fun saveImageOrPdf(
        bitmap: Bitmap, options: ExportOptions
    ): Uri? = withContext(Dispatchers.IO) {

        val resolver = requireContext().contentResolver

        try {

            // =========================
            // PDF SAVE
            // =========================
            if (options.format.name.equals("PDF", ignoreCase = true)) {

                val filename = "design_${System.currentTimeMillis()}.pdf"

                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Files.getContentUri("external")
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, filename)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Files.FileColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_DOCUMENTS + "/UrduCanvas"
                        )
                        put(MediaStore.Files.FileColumns.IS_PENDING, 1)
                    }
                }

                val uri = resolver.insert(collection, contentValues) ?: return@withContext null

                resolver.openOutputStream(uri)?.use { stream ->

                    val pdfDocument = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        bitmap.width, bitmap.height, 1
                    ).create()

                    val page = pdfDocument.startPage(pageInfo)
                    val paint = Paint().apply {
                        isAntiAlias = true
                        isFilterBitmap = true
                        isDither = true
                    }
                    page.canvas.drawBitmap(bitmap, 0f, 0f, paint)
                    pdfDocument.finishPage(page)

                    pdfDocument.writeTo(stream)
                    pdfDocument.close()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Files.FileColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                return@withContext uri
            }

            // =========================
            // IMAGE SAVE
            // =========================

            val compressFormat = options.format.format ?: Bitmap.CompressFormat.PNG

            val formatExt = when (compressFormat) {
                Bitmap.CompressFormat.PNG -> "png"
                Bitmap.CompressFormat.JPEG -> "jpg"
                Bitmap.CompressFormat.WEBP -> "webp"
                else -> "png"
            }

            val mimeType = when (compressFormat) {
                Bitmap.CompressFormat.PNG -> "image/png"
                Bitmap.CompressFormat.JPEG -> "image/jpeg"
                Bitmap.CompressFormat.WEBP -> "image/webp"
                else -> "image/png"
            }

            val filename = "design_${System.currentTimeMillis()}.$formatExt"

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/UrduCanvas"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(collection, contentValues) ?: return@withContext null

            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(
                    compressFormat, options.quality.quality, stream
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            return@withContext uri

        } catch (e: Exception) {
            Log.e("ExportFragment", "Save failed", e)
            return@withContext null
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

    private fun updateProgress(progress: Int, text: String) {
        if (_binding == null || !isAdded) return

        binding.progressBar.progress = progress
        binding.tvProgressPercent.text = text
    }

    private fun isPremiumLocked(): Boolean {
        val isSubscribed = subscriptionViewModel.isSubscribed.value
        if (isSubscribed) return false

        val hasPremiumAsset = viewModel.hasPremiumAsset.value == true
        val options = viewModel.exportOptions.value
        val hasPremiumOption =
            options?.format?.isPremium == true || options?.resolution?.isPremium == true

        // NEW: also check if the project came from a premium template
        val isFromPremiumTemplate = viewModel.exportResult.value?.isFromPremiumTemplate == true

        return hasPremiumAsset || hasPremiumOption || isFromPremiumTemplate
    }

    fun isBlueStacks(): Boolean {
        return (Build.MANUFACTURER.lowercase().contains("bluestacks") || Build.BRAND.lowercase()
            .contains("bluestacks") || Build.DEVICE.lowercase().contains("bluestacks"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}