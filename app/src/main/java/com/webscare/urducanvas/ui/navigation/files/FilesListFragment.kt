package com.webscare.urducanvas.ui.navigation.files

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.common.utils.DialogUtils
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.ImageProcessor.downsampleIfNeeded
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.ExportResult
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.databinding.DialogLoadingProgressBinding
import com.webscare.urducanvas.databinding.FragmentFilesListBinding
import com.webscare.urducanvas.databinding.LayoutFilesPopupBinding
import com.webscare.urducanvas.viewmodels.FiltersViewModel
import com.webscare.urducanvas.viewmodels.MainViewModel
import android.content.Intent
import androidx.core.content.FileProvider
import com.webscare.urducanvas.MainActivity
import com.webscare.urducanvas.common.canvas.io.ProjectCodec
import com.webscare.urducanvas.common.utils.SpringEdgeEffectFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class FilesListFragment : Fragment() {
    private var _binding: FragmentFilesListBinding? = null
    private val binding get() = _binding

    private var tabName: String? = null
    private lateinit var adapter: FilesAdapter
    private val viewModel: MainViewModel by activityViewModels()
    private val canvasViewModel: CanvasViewModel by activityViewModels()
    private val filtersViewModel: FiltersViewModel by activityViewModels()
    private var bundle: Bundle = Bundle()
    private var loadingDialog: Dialog? = null
    private var dialogBinding: DialogLoadingProgressBinding? = null

    // ─── File pickers ─────────────────────────────────────────────────────────

    /** Projects tab: pick one or more .urdc / .json project files. */
    private val importProjectLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    uris.forEach { importProjectFile(it) }
                }
            }
        }

    /**
     * Non-project tabs: accept any mix of file types.
     * Auto-detected by extension in [handlePickedFile].
     */
    private val pickFiles =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                handlePickedFiles(uris)
            }
        }

    private var rotationAnimator: ObjectAnimator? = null
    val navOptions = NavOptions.Builder().setLaunchSingleTop(true).build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabName = arguments?.getString("TAB_NAME")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesListBinding.inflate(layoutInflater, container, false)
        return _binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setEvents()
        initObservers()
        (activity as? MainActivity)?.bindScrollToNav(_binding!!.filesRV)
        _binding!!.filesRV.edgeEffectFactory = SpringEdgeEffectFactory()
    }

    // ─── Public API called by FilesFragment ───────────────────────────────────

    /**
     * Triggered by the parent toolbar "Import …" button.
     * Routes to the correct system picker based on the active tab.
     */
    fun triggerImport() {
        if (tabName.equals("Projects", true)) {
            importProjectLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        } else {
            pickFiles.launch(arrayOf("*/*"))
        }
    }

    /**
     * Triggered by the parent toolbar "Delete All" button.
     * Shows a confirmation dialog then deletes every selected item.
     */
    fun triggerDeleteSelected() {
        val selectedItems = adapter.getSelectedItems()
        if (selectedItems.isEmpty()) return

        DialogUtils.showDeleteDialog(
            requireActivity(),
            getString(R.string.delete_permanently),
            getString(R.string.your_asset_will_be_permanently_deleted)
        ) {
            lifecycleScope.launch {
                selectedItems.forEach { item ->
                    when (item) {
                        is ExportResult -> viewModel.deleteExportResult(item)
                        is ImageEntity  -> viewModel.deleteImage(item)
                        is FontEntity   -> viewModel.deleteFont(item)
                    }
                }
                adapter.clearSelection()
                // Notify parent so it swaps the toolbar button back to Import
                (parentFragment as? FilesFragment)?.onSelectionModeChanged(false)
            }
        }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setEvents() {
        adapter = FilesAdapter(
            items           = emptyList(),
            isGrid          = false,
            onItemClick     = { item -> openItem(item) },
            onItemLongClick = {},
            onOptionsClick  = { item, anchorView -> showFilePopup(anchorView, item) },
            onRename        = { item, newName ->
                if (newName.isNotEmpty()) {
                    lifecycleScope.launch {
                        when (item) {
                            is ExportResult -> viewModel.insertExportResult(item.copy(fileName = newName))
                            is ImageEntity  -> viewModel.updateImage(item.copy(file_name = newName))
                            is FontEntity   -> viewModel.updateFont(item.copy(font_name = newName))
                        }
                    }
                }
            },
            onSelectionChanged = { active ->
                (parentFragment as? FilesFragment)?.onSelectionModeChanged(active)
            }
        )

        _binding!!.filesRV.adapter = adapter
        _binding!!.filesRV.layoutManager = com.webscare.urducanvas.common.views.SafeLinearLayoutManager(requireContext())


    }

    // ─── Multi-file import ────────────────────────────────────────────────────

    /**
     * Dispatches each picked URI to [handlePickedFile] on IO, then
     * shows a single summary Snackbar on the main thread.
     */
    private fun handlePickedFiles(uris: List<Uri>) {
        lifecycleScope.launch(Dispatchers.IO) {
            var imported = 0
            var skipped  = 0
            uris.forEach { uri ->
                if (handlePickedFile(uri)) imported++ else skipped++
            }
            withContext(Dispatchers.Main) {
                val root = _binding?.root ?: return@withContext
                val msg = when {
                    imported > 0 && skipped == 0 ->
                        if (imported == 1) "File imported successfully"
                        else "$imported files imported successfully"
                    imported > 0 ->
                        "$imported imported, $skipped skipped (unsupported type)"
                    else -> "No supported files found"
                }
                Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Auto-detects the file category from its extension and stores it in Room.
     *  • .ttf / .otf          → Font
     *  • .jpg / .jpeg / .webp → Background
     *  • .png                 → Sticker
     *  • .urdc / .json        → Project
     *
     * @return true on success, false if the type is unsupported or an error occurred.
     */
    private suspend fun handlePickedFile(uri: Uri): Boolean {
        val name = getFileName(uri)
        val ext  = name.substringAfterLast('.', "").lowercase()

        return when (ext) {

            // ── Fonts ────────────────────────────────────────────────────────
            "ttf", "otf" -> try {
                val fontFile   = copyToTemp(uri, ".$ext")
                val exportDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                viewModel.insertFont(FontEntity(
                    id             = System.currentTimeMillis().toInt(),
                    file_name      = fontFile.name,
                    font_name      = fontFile.nameWithoutExtension,
                    font_category  = "Imported",
                    font_language  = "Imported",
                    file_url       = "",
                    file_size      = fontFile.length().toString(),
                    font_image     = null,
                    image_url      = "",
                    alt_text       = "Font sample image",
                    user_id        = 0,
                    created_at     = exportDate,
                    updated_at     = exportDate,
                    is_selected    = false,
                    is_downloaded  = true,
                    is_downloading = false,
                    file_path      = fontFile.absolutePath
                ))
                true
            } catch (e: Exception) {
                Log.e("FilesListFragment", "Font import failed: $name", e); false
            }

            // ── Backgrounds (JPEG / WEBP) ─────────────────────────────────
            "jpg", "jpeg", "webp" -> try {
                val filePath  = ImageProcessor.copyUriToTempFile(requireActivity(), uri)?.absolutePath ?: return false
                val rawBitmap = ImageProcessor.filePathToBitmap(filePath) ?: return false
                val bitmap    = downsampleIfNeeded(rawBitmap, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
                val fmt       = if (ext == "webp") Bitmap.CompressFormat.WEBP else Bitmap.CompressFormat.JPEG
                val outFile   = File(filePath)
                outFile.outputStream().use { bitmap.compress(fmt, 100, it) }
                val exportDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                viewModel.insertImage(ImageEntity(
                    id              = System.currentTimeMillis().toInt(),
                    file_name       = outFile.name,
                    file_url        = "",
                    file_size       = outFile.length().toString(),
                    alt_text        = "",
                    category        = "Backgrounds Imported",
                    parent_category = "Images",
                    user_id         = 0,
                    is_selected     = false,
                    bitmapData      = filePath,
                    created_at      = exportDate
                ))
                true
            } catch (e: Exception) {
                Log.e("FilesListFragment", "Background import failed: $name", e); false
            }

            // ── Stickers (PNG) ────────────────────────────────────────────
            "png" -> try {
                val filePath  = ImageProcessor.copyUriToTempFile(requireActivity(), uri)?.absolutePath ?: return false
                val rawBitmap = ImageProcessor.filePathToBitmap(filePath) ?: return false
                val bitmap    = downsampleIfNeeded(rawBitmap, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
                val outFile   = File(filePath)
                outFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val exportDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                viewModel.insertImage(ImageEntity(
                    id              = System.currentTimeMillis().toInt(),
                    file_name       = outFile.name,
                    file_url        = "",
                    file_size       = outFile.length().toString(),
                    alt_text        = "",
                    category        = "Images Imported",
                    parent_category = "Images",
                    user_id         = 0,
                    is_selected     = false,
                    bitmapData      = filePath,
                    created_at      = exportDate
                ))
                true
            } catch (e: Exception) {
                Log.e("FilesListFragment", "Sticker import failed: $name", e); false
            }

            // ── Projects (.urdc / .json) ──────────────────────────────────
            ProjectCodec.FILE_EXTENSION, "json" -> {
                importProjectFile(uri); true
            }

            else -> {
                Log.w("FilesListFragment", "Unsupported extension: $ext ($name)"); false
            }
        }
    }

    // ─── Project import ───────────────────────────────────────────────────────

    private suspend fun importProjectFile(uri: Uri) {
        try {
            val displayName = getFileName(uri).ifBlank { "imported_${System.currentTimeMillis()}" }
            val baseName    = displayName.substringBeforeLast('.')

            // 1. Copy to permanent app storage.
            val destJson = ImageProcessor.newExportJsonFile(
                requireActivity(), "$baseName.${ProjectCodec.FILE_EXTENSION}"
            )
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                destJson.outputStream().use { input.copyTo(it) }
            } ?: run {
                withContext(Dispatchers.Main) {
                    _binding?.root?.let { Snackbar.make(it, "Could not read file", Snackbar.LENGTH_SHORT).show() }
                }
                return
            }

            // 2. Validate.
            val isUrdc      = ProjectCodec.isUrdcFile(destJson)
            val firstByte   = if (!isUrdc) destJson.inputStream().use { it.read() } else -1
            val isPlainJson = !isUrdc && (firstByte == '['.code || firstByte == '{'.code)
            if (!isUrdc && !isPlainJson) {
                destJson.delete()
                withContext(Dispatchers.Main) {
                    _binding?.root?.let { Snackbar.make(it, "Not a valid Urdu Canvas project file", Snackbar.LENGTH_SHORT).show() }
                }
                return
            }

            // 3. Extract thumbnail.
            val thumb      = ProjectCodec.readThumbnail(destJson)
            val thumbPath  = thumb?.let {
                val f = File(destJson.parentFile, "$baseName.jpg")
                f.outputStream().use { out -> it.compress(Bitmap.CompressFormat.JPEG, 85, out) }
                f.absolutePath
            }

            // 4. Insert into Room.
            val now = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            viewModel.insertExportResult(ExportResult(
                id            = 0,
                imagePath     = thumbPath ?: "",
                jsonPath      = destJson.absolutePath,
                fileName      = baseName,
                fileSizeMB    = destJson.length() / (1024.0 * 1024.0),
                resolution    = "",
                format        = if (isUrdc) "URDC" else "JSON",
                quality       = "",
                canvasSize    = canvasViewModel.canvasSize.value
                    ?: CanvasSize(id = 0, "Imported", 1080f, 1080f),
                exportDate    = now,
                updatedDate   = now,
                thumbnailPath = thumbPath
            ))
        } catch (e: Exception) {
            Log.e("FilesListFragment", "importProjectFile failed", e)
            withContext(Dispatchers.Main) {
                _binding?.root?.let { Snackbar.make(it, "Import failed: ${e.message}", Snackbar.LENGTH_SHORT).show() }
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun getFileName(uri: Uri): String {
        var name: String? = null
        requireContext().contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst())
                name = it.getString(it.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
        }
        return name ?: uri.lastPathSegment ?: "file"
    }

    private fun copyToTemp(uri: Uri, dotExt: String): File {
        val temp = File.createTempFile("imported_${System.currentTimeMillis()}", dotExt, requireContext().cacheDir)
        requireContext().contentResolver.openInputStream(uri).use { input ->
            temp.outputStream().use { out -> input?.copyTo(out) }
        }
        return temp
    }

    private fun createFontSampleBitmap(typeface: Typeface): Bitmap {
        val paint = Paint().apply {
            this.typeface = typeface
            textSize = 100f
            color = ContextCompat.getColor(requireContext(), R.color.appColor)
            textAlign = Paint.Align.LEFT
        }
        val bitmap = createBitmap(paint.measureText("Ab").toInt(), (paint.descent() - paint.ascent()).toInt())
        Canvas(bitmap).drawText("Ab", 0f, -paint.ascent(), paint)
        return bitmap
    }

    // ─── Open item ────────────────────────────────────────────────────────────

    private fun openItem(item: Any) {
        when (item) {
            is ExportResult -> {
                canvasViewModel.loadTemplateFromJsonFile(item, requireContext(), titleHint = "Loading Project") { success ->
                    if (success && isAdded) {
                        findNavController().navigate(R.id.editorFragment, bundle, navOptions)
                    }
                }
            }
            is FontEntity -> {
                canvasViewModel.setCanvasSize(CanvasSize(id = 0, "", 2000f, 2000f))
                canvasViewModel.addTextWithFont(requireActivity().getString(R.string.dummyText), item, requireActivity())
                view?.post { findNavController().navigate(R.id.editorFragment, bundle, navOptions) }
            }
            is ImageEntity -> {
                val rawBitmap = BitmapFactory.decodeFile(item.bitmapData)
                rawBitmap?.let { bmp ->
                    val canvasW = canvasViewModel.canvasSize.value?.width ?: bmp.width.toFloat()
                    val canvasH = canvasViewModel.canvasSize.value?.height ?: bmp.height.toFloat()
                    val bitmap  = downsampleIfNeeded(bmp,
                        (canvasW * 2).toInt().coerceIn(1024, MAX_IMAGE_DIMENSION),
                        (canvasH * 2).toInt().coerceIn(1024, MAX_IMAGE_DIMENSION))
                    canvasViewModel.clearCanvas()
                    canvasViewModel.setCanvasSize(CanvasSize(id = 0, "From Image", bitmap.width.toFloat(), bitmap.height.toFloat()))
                    canvasViewModel.setCanvasBackgroundImage(bitmap, requireActivity())
                    view?.post { findNavController().navigate(R.id.editorFragment, bundle, navOptions) }
                }
            }
        }
    }

    // ─── File popup ───────────────────────────────────────────────────────────

    private fun showFilePopup(anchorView: View, item: Any) {
        val popupBinding = LayoutFilesPopupBinding.inflate(LayoutInflater.from(requireActivity()))
        val popupWindow  = PopupWindow(
            popupBinding.root,
            (180 * requireActivity().resources.displayMetrics.density).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 2f
            isOutsideTouchable = true
        }

        anchorView.post {
            val screenHeight = resources.displayMetrics.heightPixels
            val loc          = IntArray(2).also { anchorView.getLocationOnScreen(it) }
            val anchorTop    = loc[1]
            val anchorBottom = anchorTop + anchorView.height
            popupBinding.root.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val popupHeight = popupBinding.root.measuredHeight
            if (screenHeight - anchorBottom >= popupHeight) {
                popupWindow.showAsDropDown(anchorView)
            } else if (anchorTop >= popupHeight) {
                popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, loc[0], anchorTop - popupHeight)
            } else {
                popupWindow.showAsDropDown(anchorView)
            }
        }

        popupBinding.actionExport.addPressEffect {
            popupWindow.dismiss()
            when (item) {
                is ExportResult -> lifecycleScope.launch {
                    BitmapFactory.decodeFile(item.imagePath)?.let { exportToGallery(it, item.fileName, Bitmap.CompressFormat.PNG) }
                        ?: Snackbar.make(requireView(), "Could not load image", Snackbar.LENGTH_SHORT).show()
                }
                is ImageEntity -> lifecycleScope.launch {
                    BitmapFactory.decodeFile(item.bitmapData)?.let { exportToGallery(it, item.file_name, Bitmap.CompressFormat.PNG) }
                        ?: Snackbar.make(requireView(), "Could not load image", Snackbar.LENGTH_SHORT).show()
                }
                is FontEntity -> lifecycleScope.launch {
                    BitmapFactory.decodeFile(item.font_image)?.let { exportToGallery(it, item.font_name!!, Bitmap.CompressFormat.PNG) }
                        ?: Snackbar.make(requireView(), "Could not load image", Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        popupBinding.actionShare.addPressEffect { popupWindow.dismiss(); shareItem(item) }

        popupBinding.actionSelect.addPressEffect {
            popupWindow.dismiss()
            adapter.toggleMultiSelectMode(true)
            (parentFragment as? FilesFragment)?.onSelectionModeChanged(true)
        }

        popupBinding.actionDuplicate.addPressEffect {
            popupWindow.dismiss()
            lifecycleScope.launch(Dispatchers.IO) {
                when (item) {
                    is ExportResult -> {
                        val newImage = ImageProcessor.newExportImageFile(requireActivity(), File(item.imagePath).name)
                        val newJson  = ImageProcessor.newExportJsonFile(requireActivity(), File(item.jsonPath).name)
                        ImageProcessor.copyFile(File(item.imagePath), newImage)
                        ImageProcessor.copyFile(File(item.jsonPath),  newJson)
                        viewModel.insertExportResult(item.copy(
                            id = 0, imagePath = newImage.absolutePath, jsonPath = newJson.absolutePath,
                            fileName = "${item.fileName}_copy", updatedDate = System.currentTimeMillis().toString()
                        ))
                    }
                    is ImageEntity -> {
                        val src  = File(item.bitmapData ?: item.file_url)
                        val dest = ImageProcessor.newImageFile(requireContext(), src.name)
                        if (src.exists()) ImageProcessor.copyFile(src, dest)
                        viewModel.insertImage(item.copy(
                            id = 0, file_name = "${item.file_name}_copy",
                            bitmapData = dest.absolutePath, created_at = System.currentTimeMillis().toString()
                        ))
                    }
                    is FontEntity -> {
                        val srcFont    = File(item.file_path ?: item.file_url)
                        val destFont   = ImageProcessor.newFontFile(requireContext(), srcFont.name)
                        if (srcFont.exists()) ImageProcessor.copyFile(srcFont, destFont)
                        val srcPrev    = File(item.font_image)
                        val destPrev   = ImageProcessor.newFontPreviewFile(requireContext(), srcPrev.name)
                        if (srcPrev.exists()) ImageProcessor.copyFile(srcPrev, destPrev)
                        viewModel.insertFont(item.copy(
                            id = 0, font_name = "${item.font_name}_copy",
                            font_image = destPrev.absolutePath, file_path = destFont.absolutePath,
                            created_at = System.currentTimeMillis().toString()
                        ))
                    }
                }
            }
        }

        popupBinding.actionRename.addPressEffect {
            popupWindow.dismiss()
            adapter.startEditing(when (item) {
                is ImageEntity  -> item.id.toLong()
                is FontEntity   -> item.id.toLong()
                is ExportResult -> item.id
                else            -> 0
            })
        }

        popupBinding.actionDelete.addPressEffect {
            popupWindow.dismiss()
            val (title, subtitle) = when (item) {
                is ExportResult -> getString(R.string.delete_project) to getString(R.string.your_asset_will_be_permanently_deleted)
                is ImageEntity  -> getString(R.string.delete_image)   to getString(R.string.your_asset_will_be_permanently_deleted)
                is FontEntity   -> getString(R.string.delete_font)    to getString(R.string.your_asset_will_be_permanently_deleted)
                else            -> getString(R.string.delete)         to getString(R.string.your_asset_will_be_permanently_deleted)
            }
            DialogUtils.showDeleteDialog(requireActivity(), title, subtitle) {
                when (item) {
                    is ExportResult -> viewModel.deleteExportResult(item)
                    is ImageEntity  -> viewModel.deleteImage(item)
                    is FontEntity   -> viewModel.deleteFont(item)
                }
            }
        }
    }

    // ─── Export / Share ───────────────────────────────────────────────────────

    private fun exportToGallery(bitmap: Bitmap, fileName: String, format: Bitmap.CompressFormat) {
        val ext      = when (format) { Bitmap.CompressFormat.PNG -> "png"; Bitmap.CompressFormat.JPEG -> "jpg"; else -> "webp" }
        val mimeType = when (format) { Bitmap.CompressFormat.PNG -> "image/png"; Bitmap.CompressFormat.JPEG -> "image/jpeg"; else -> "image/webp" }

        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${fileName}_${System.currentTimeMillis()}.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/${getString(R.string.app_name)}")
        }
        val resolver = requireContext().contentResolver
        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)?.let { uri ->
            resolver.openOutputStream(uri)?.use { bitmap.compress(format, 100, it) }
            Snackbar.make(requireView(), "Exported to Gallery", Snackbar.LENGTH_SHORT).show()
        } ?: Snackbar.make(requireView(), "Export failed", Snackbar.LENGTH_SHORT).show()
    }

    private fun shareItem(item: Any) {
        val authority = "${requireContext().packageName}.fileprovider"
        fun share(file: File, mime: String) {
            if (!file.exists()) { Snackbar.make(requireView(), "File not found", Snackbar.LENGTH_SHORT).show(); return }
            val uri = runCatching { FileProvider.getUriForFile(requireContext(), authority, file) }.getOrElse {
                Snackbar.make(requireView(), "Cannot share this file", Snackbar.LENGTH_SHORT).show(); return
            }
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = mime; putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share"
            ))
        }
        when (item) {
            is ExportResult -> share(File(item.jsonPath), "application/octet-stream")
            is ImageEntity  -> {
                val file = File(item.bitmapData?.takeIf { it.isNotBlank() } ?: item.file_url)
                share(file, when (file.extension.lowercase()) {
                    "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"
                    "webp" -> "image/webp"; "svg" -> "image/svg+xml"; else -> "image/*"
                })
            }
            is FontEntity -> {
                val path = item.file_path?.takeIf { it.isNotBlank() } ?: return
                share(File(path), if (File(path).extension.lowercase() == "otf") "font/otf" else "font/ttf")
            }
        }
    }

    // ─── Observers ────────────────────────────────────────────────────────────

    private fun initObservers() {
        canvasViewModel.loadingStage.observe(viewLifecycleOwner) { (message, percent) ->
            dialogBinding?.apply {
                progressBar.progress = percent
                subtitle.text = "$message... $percent%"
                tvProgressPercent.text = "$percent% complete"
            }
        }

        canvasViewModel.isLoadingTemplate.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading == true) {
                showLoadingDialog()
            } else if (isLoading == false) {
                dismissLoadingDialog()
                if (canvasViewModel.canvasSize.value != null) {
                    lifecycleScope.launch {
                        delay(500)
                        if (findNavController().currentDestination?.id != R.id.editorFragment) {
                            view?.post { findNavController().navigate(R.id.editorFragment, bundle, navOptions) }
                        }
                    }
                } else {
                    android.widget.Toast.makeText(context, "Failed to load project", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                filtersViewModel.isGrid.collect { isGrid ->
                    val b = _binding ?: return@collect
                    b.filesRV.layoutManager = if (isGrid)
                        com.webscare.urducanvas.common.views.SafeGridLayoutManager(requireContext(), 2)
                    else
                        com.webscare.urducanvas.common.views.SafeLinearLayoutManager(requireContext())
                    adapter.toggleViewType(isGrid)
                }
            }
        }

        when (tabName) {
            "All" -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        combine(
                            viewModel.localFonts,
                            viewModel.localImages,
                            viewModel.exportResults.asFlow(),
                            filtersViewModel.searchQuery
                        ) { fonts, images, results, query ->
                            val q = query.trim().lowercase()
                            fonts.filter {
                                it.font_category == "Imported" && (q.isEmpty() || it.font_name!!.lowercase().contains(q))
                            } + images.filter {
                                it.category == "Images Imported" && (q.isEmpty() || it.file_name.lowercase().contains(q))
                            } + results.filter { q.isEmpty() || it.fileName.lowercase().contains(q) }
                        }.collect { list ->
                            val b = _binding ?: return@collect
                            adapter.updateList(list)
                            b.noImagesText.text = requireActivity().getString(R.string.no_assets_available)
                            b.noEmojis.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }
            }

            "Projects" -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        combine(viewModel.exportResults.asFlow(), filtersViewModel.searchQuery) { results, query ->
                            val q = query.trim().lowercase()
                            results.filter { q.isEmpty() || it.fileName.lowercase().contains(q) }
                        }.collect { list ->
                            val b = _binding ?: return@collect
                            adapter.updateList(list)
                            b.noImagesText.text = requireActivity().getString(R.string.no_projects_available)
                            b.noEmojis.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }
            }

            "Fonts" -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        combine(viewModel.localFonts, filtersViewModel.searchQuery) { fonts, query ->
                            val q = query.trim().lowercase()
                            fonts.filter {
                                it.font_category == "Imported" && (q.isEmpty() || it.font_name!!.lowercase().contains(q))
                            }
                        }.collect { list ->
                            val b = _binding ?: return@collect
                            adapter.updateList(list)
                            if (list.isEmpty()) {
                                b.noEmojis.visibility = View.VISIBLE
                                val fullText      = "No imported fonts.\nBrowse in-app fonts."
                                val clickablePart = "in-app fonts"
                                val spannable     = android.text.SpannableString(fullText)
                                val start         = fullText.indexOf(clickablePart)
                                spannable.setSpan(object : android.text.style.ClickableSpan() {
                                    override fun onClick(widget: View) {
                                        findNavController().navigate(R.id.popularFontsFragment)
                                    }
                                    override fun updateDrawState(ds: android.text.TextPaint) {
                                        super.updateDrawState(ds)
                                        ds.isUnderlineText = true
                                        ds.typeface = ResourcesCompat.getFont(requireContext(), R.font.medium)
                                        ds.color    = requireContext().getColor(R.color.appColor)
                                    }
                                }, start, start + clickablePart.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                b.noImagesText.text = spannable
                                b.noImagesText.movementMethod = android.text.method.LinkMovementMethod.getInstance()
                            } else {
                                b.noEmojis.visibility = View.GONE
                            }
                        }
                    }
                }
            }

            "Stickers" -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        combine(viewModel.localImages, filtersViewModel.searchQuery) { images, query ->
                            val q = query.trim().lowercase()
                            images.filter {
                                it.category == "Images Imported" && (q.isEmpty() || it.file_name.lowercase().contains(q))
                            }
                        }.collect { list ->
                            val b = _binding ?: return@collect
                            adapter.updateList(list)
                            b.noImagesText.text = requireActivity().getString(R.string.no_stickers_available)
                            b.noEmojis.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }
            }

            "Backgrounds" -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        combine(viewModel.localImages, filtersViewModel.searchQuery) { images, query ->
                            val q = query.trim().lowercase()
                            images.filter {
                                it.category == "Backgrounds Imported" && (q.isEmpty() || it.file_name.lowercase().contains(q))
                            }
                        }.collect { list ->
                            val b = _binding ?: return@collect
                            adapter.updateList(list)
                            b.noImagesText.text = requireActivity().getString(R.string.no_backgrounds_available)
                            b.noEmojis.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }
            }
        }
    }

    // ─── Loading dialog ───────────────────────────────────────────────────────

    private fun showLoadingDialog() {
        if (loadingDialog?.isShowing == true) return
        dialogBinding = DialogLoadingProgressBinding.inflate(LayoutInflater.from(requireActivity()))
        loadingDialog = Dialog(requireContext()).apply {
            setContentView(dialogBinding!!.root)
            setCancelable(true)
            setOnCancelListener { canvasViewModel.clearLoading() }
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            val params = window?.attributes
            params?.width  = (resources.displayMetrics.widthPixels * 0.8).toInt()
            params?.height = ViewGroup.LayoutParams.WRAP_CONTENT
            window?.attributes = params
            window?.setGravity(Gravity.CENTER)
            show()
        }
        dialogBinding?.title?.text = "Loading Template"
        startIconRotation()
    }

    private fun startIconRotation() {
        dialogBinding?.view4?.let { icon ->
            rotationAnimator = ObjectAnimator.ofFloat(icon, View.ROTATION, 0f, 360f).apply {
                duration     = 1000L
                repeatCount  = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    private fun stopIconRotation() { rotationAnimator?.cancel(); rotationAnimator = null }

    private fun dismissLoadingDialog() {
        stopIconRotation()
        loadingDialog?.dismiss()
        loadingDialog = null
        dialogBinding = null
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        if (findNavController().currentDestination?.id!! != R.id.editorFragment) {
            canvasViewModel.clearCanvas()
        }
    }

    companion object {
        private const val MAX_IMAGE_DIMENSION = 4899

        fun newInstance(tabName: String): FilesListFragment {
            return FilesListFragment().apply {
                arguments = Bundle().apply { putString("TAB_NAME", tabName) }
            }
        }
    }
}