package com.example.urduphotodesigner.ui.editor.export

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.ExportViewType
import com.example.urduphotodesigner.common.canvas.model.ExportOptions
import com.example.urduphotodesigner.common.canvas.model.ExportResult
import com.example.urduphotodesigner.common.views.CanvasView
import com.example.urduphotodesigner.databinding.FragmentExportBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class ExportFragment : Fragment() {
    private var _binding: FragmentExportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    private lateinit var canvasView: CanvasView
    private var isFirstRender = true
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
        initObservers()
        setEvents()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun View.addPressEffect() {
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100).start()
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    false
                }
                else -> false
            }
        }
    }

    private fun setEvents() = with(binding) {
        binding.btnExport.addPressEffect()
        stopIconRotation()
        btnExport.setOnClickListener { startExport() }
        btnReset.setOnClickListener {
            viewModel.resetExportOptions()
            showTopBanner("Settings reset to defaults")
        }

        resolutionButton.setOnClickListener {
            ExportOptionsFragment.newInstance(ExportViewType.RESOLUTION)
                .show(parentFragmentManager, "resolution_sheet")
        }

        qualityButton.setOnClickListener {
            ExportOptionsFragment.newInstance(ExportViewType.QUALITY)
                .show(parentFragmentManager, "quality_sheet")
        }

        formatButton.setOnClickListener {
            ExportOptionsFragment.newInstance(ExportViewType.FORMAT)
                .show(parentFragmentManager, "format_sheet")
        }
    }

    private fun initObservers() {
        viewModel.canvasView.observe(viewLifecycleOwner) { canvas ->
            lifecycleScope.launch(Dispatchers.Main) {
                canvasView = canvas
                renderPreview()
            }
        }

        viewModel.exportOptions.observe(viewLifecycleOwner) { options ->
            if (!isAdded) return@observe
            lifecycleScope.launch(Dispatchers.Main) {
                updateExportOptionsUI(options)
                renderPreview()
            }

        }
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

        if (isFirstRender) {
            // First time: hide preview, show progress
            binding.previewImage.visibility = View.INVISIBLE
            binding.exportPreviewProgress.visibility = View.VISIBLE
        } else {
            // From second time onward: show current image while updating in background
            binding.previewImage.visibility = View.VISIBLE
            binding.exportPreviewProgress.visibility = View.GONE
        }

        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                canvas.exportCanvas(options)
            }

            withContext(Dispatchers.Main) {
                val (bitmap, _) = result
                val sizeMB = estimateBitmapSize(
                    bitmap,
                    options.format.format,
                    options.quality.quality
                ) / (1024.0 * 1024.0)

                // Update new preview image
                binding.previewImage.setImageBitmap(bitmap)
                binding.tvExportSize.text = "%.1f MB".format(sizeMB)
                binding.fileSize.text = "%.1f MB".format(sizeMB)

                // If first render, show preview now and hide progress
                if (isFirstRender) {
                    binding.previewImage.visibility = View.VISIBLE
                    binding.exportPreviewProgress.visibility = View.GONE
                    isFirstRender = false
                }
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

        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                canvasView.exportCanvas(options) { percent, stage ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        updateProgress(percent, stage)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                val (bitmap, json) = result

                updateProgress(50, "Rendering image...")
                binding.previewImage.setImageBitmap(bitmap)

                updateProgress(70, "Saving image...")
                val imagePath = saveImage(bitmap, options)

                updateProgress(85, "Saving JSON...")
                val jsonPath = saveJson(json)

                val fileSizeMB = estimateBitmapSize(bitmap, options.format.format, options.quality.quality) / (1024.0 * 1024.0)
                val exportDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(
                    Date()
                )

                viewModel.setExportResult(
                    ExportResult(
                        bitmap = bitmap,
                        imagePath = imagePath!!,
                        jsonPath = jsonPath,
                        fileName = imagePath.substringAfterLast("/") ?: "design",
                        fileSizeMB = fileSizeMB,
                        resolution = options.resolution.label,
                        format = options.format.name,
                        quality = options.quality.label,
                        exportDate = exportDate
                    )
                )

                updateProgress(100, "Export complete")

                binding.exportProgress.postDelayed({
                    binding.exportProgress.visibility = View.GONE
                }, 1000)

                stopRotationAnimation(binding.view4)
                stopIconRotation()
                binding.btnExport.isEnabled = true
                binding.btnExport.alpha = 1.0f
                binding.btnExport.text = "Export"

                findNavController().navigate(R.id.finishExportFragment)
            }
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
        format: Bitmap.CompressFormat,
        quality: Int
    ): Long {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(format, quality, stream)
        return stream.size().toLong()
    }

    private fun saveJson(json: String): String {
        val filename = "canvas_data_${System.currentTimeMillis()}.json"
        val file = File(requireContext().filesDir, filename)
        file.writeText(json)
        showTopBanner("Design JSON saved")
        return file.absolutePath
    }

    private fun saveImage(bitmap: Bitmap, options: ExportOptions): String? {
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
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        val uri = requireContext().contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        )

        uri?.let {
            requireContext().contentResolver.openOutputStream(it)?.use { stream ->
                bitmap.compress(options.format.format, options.quality.quality, stream)
                showTopBanner("Image saved to gallery")
            }

            return uri.toString() // Or return filename for simplicity
        }

        return null
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