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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.common.utils.DialogUtils
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.ExportResult
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.databinding.DialogLoadingProgressBinding
import com.webscare.urducanvas.databinding.FragmentFilesListBinding
import com.webscare.urducanvas.databinding.LayoutFilesPopupBinding
import com.webscare.urducanvas.viewmodels.FiltersViewModel
import com.webscare.urducanvas.viewmodels.MainViewModel
import com.google.android.material.snackbar.Snackbar
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
import androidx.core.graphics.createBitmap
import com.webscare.urducanvas.common.utils.Utils.addPressEffect

@AndroidEntryPoint
class FilesListFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentFilesListBinding? = null
    private val binding get() = _binding!!

    private var tabName: String? = null
    private lateinit var adapter: FilesAdapter
    private val viewModel: MainViewModel by activityViewModels()
    private val canvasViewModel: CanvasViewModel by activityViewModels()
    private val filtersViewModel: FiltersViewModel by activityViewModels()
    private var bundle: Bundle = Bundle()
    private var loadingDialog: Dialog? = null
    private var dialogBinding: DialogLoadingProgressBinding? = null
    private val pickFiles =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                handlePickedFiles(uris)
            }
        }

    private var rotationAnimator: ObjectAnimator? = null
    val navOptions = NavOptions.Builder()
        .setLaunchSingleTop(true)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabName = arguments?.getString("TAB_NAME")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesListBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        initObservers()
        if (tabName.equals("All", true) || tabName.equals("Projects", true)) {
            binding.addMore.visibility = View.GONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setEvents() {
        adapter = FilesAdapter(
            emptyList(), isGrid = false,
            onItemClick = { item ->
                openItem(item)
            },
            onItemLongClick = {},
            onOptionsClick = { item, anchorView ->
                showFilePopup(anchorView, item)
            }, onRename = { item, newName ->
                if (newName.isNotEmpty()) {
                    lifecycleScope.launch {
                        when (item) {
                            is ExportResult -> viewModel.insertExportResult(item.copy(fileName = newName))
                            is ImageEntity -> viewModel.updateImage(item.copy(file_name = newName))
                            is FontEntity -> viewModel.updateFont(item.copy(font_name = newName))
                        }
                    }
                }
            }, onSelectionChanged = { active ->
                binding.deleteAll.visibility = if (active) View.VISIBLE else View.GONE
                binding.addMore.visibility = if (active) View.GONE else View.VISIBLE
                if (tabName.equals("All", true) || tabName.equals("Projects", true)) {
                    binding.addMore.visibility = View.GONE
                }
            })
        binding.filesRV.adapter = adapter
        binding.filesRV.layoutManager = LinearLayoutManager(requireContext())

        binding.filesRV.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                if (adapter.isEditing()) {
                    val imm = requireContext()
                        .getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                            as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    requireActivity().currentFocus?.clearFocus()
                    adapter.stopEditing()
                }
            }

            false
        }

        binding.addMore.addPressEffect {
            pickFiles.launch(arrayOf("*/*"))  // allow all file types
        }

        binding.deleteAll.addPressEffect {
            val selectedItems = adapter.getSelectedItems() // we’ll add this helper in adapter
            if (selectedItems.isNotEmpty()) {
                DialogUtils.showDeleteDialog(
                    requireActivity(),
                    getString(R.string.delete_permanently),
                    getString(R.string.your_asset_will_be_permanently_deleted)
                ) {
                    lifecycleScope.launch {
                        selectedItems.forEach { item ->
                            when (item) {
                                is ExportResult -> viewModel.deleteExportResult(item)
                                is ImageEntity -> viewModel.deleteImage(item)
                                is FontEntity -> viewModel.deleteFont(item)
                            }
                        }
                        adapter.clearSelection()
                    }
                }
            }
        }
    }

    private fun handlePickedFiles(uris: List<Uri>) {
        lifecycleScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                handlePickedFile(uri)
            }
        }
    }

    private fun handlePickedFile(uri: Uri) {
        val name = getFileName(uri)
        val ext = name.substringAfterLast('.', "").lowercase()

        when (ext) {
            "ttf", "otf" -> {   // FONT IMPORT
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val fontFile = copyToTemp(uri, ".$ext")
                        val exportDate =
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                        val fontEntity =
                            _root_ide_package_.com.webscare.urducanvas.data.model.FontEntity(
                                id = System.currentTimeMillis().toInt(),
                                file_name = fontFile.name,
                                font_name = fontFile.nameWithoutExtension,
                                font_category = "Imported",
                                font_language = "Imported",
                                file_url = "",
                                file_size = fontFile.length().toString(),
                                font_image = null,
                                image_url = "",
                                alt_text = "Font sample image",
                                user_id = 0,
                                created_at = exportDate,
                                updated_at = exportDate,
                                is_selected = false,
                                is_downloaded = true,
                                is_downloading = false,
                                file_path = fontFile.absolutePath
                            )
                        viewModel.insertFont(fontEntity)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Snackbar.make(
                                requireView(),   // or binding.root
                                "Font import failed",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }

            "jpg", "jpeg" -> {   // BACKGROUND IMPORT
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val filePath =
                            ImageProcessor.copyUriToTempFile(requireActivity(), uri)?.absolutePath
                        val exportDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
                            Date()
                        )

                        val entity =
                            _root_ide_package_.com.webscare.urducanvas.data.model.ImageEntity(
                                id = System.currentTimeMillis().toInt(),
                                file_name = File(filePath!!).name,
                                file_url = "",
                                file_size = File(filePath).length().toString(),
                                alt_text = "",
                                category = "Backgrounds Imported",
                                user_id = 0,
                                is_selected = false,
                                bitmapData = filePath,
                                created_at = exportDate
                            )
                        viewModel.insertImage(entity)
                    } catch (e: Exception) {
                        Log.e("BackgroundPicker", "Failed", e)
                    }
                }
            }

            "png" -> {   // STICKER IMPORT
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val filePath =
                            ImageProcessor.copyUriToTempFile(requireActivity(), uri)?.absolutePath
                        val exportDate =
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                        val entity =
                            _root_ide_package_.com.webscare.urducanvas.data.model.ImageEntity(
                                id = System.currentTimeMillis().toInt(),
                                file_name = File(filePath!!).name,
                                file_url = "",
                                file_size = File(filePath).length().toString(),
                                alt_text = "",
                                category = "Images Imported",
                                user_id = 0,
                                is_selected = false,
                                bitmapData = filePath,
                                created_at = exportDate
                            )
                        viewModel.insertImage(entity)
                    } catch (e: Exception) {
                        Log.e("StickerPicker", "Failed", e)
                    }
                }
            }

            else -> {
                Snackbar.make(requireView(), "Unsupported file type!", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var name: String? = null
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                name =
                    it.getString(it.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
            }
        }
        return name ?: uri.lastPathSegment ?: "file"
    }

    private fun copyToTemp(uri: Uri, dotExt: String): File {
        val tempFile = File.createTempFile(
            "imported_${System.currentTimeMillis()}",
            dotExt,
            requireContext().cacheDir
        )
        requireContext().contentResolver.openInputStream(uri).use { input ->
            tempFile.outputStream().use { out -> input?.copyTo(out) }
        }
        return tempFile
    }

    private fun createFontSampleBitmap(typeface: Typeface): Bitmap {
        val paint = Paint().apply {
            this.typeface = typeface
            textSize = 100f
            color = ContextCompat.getColor(requireContext(), R.color.appColor)
            textAlign = Paint.Align.LEFT
        }

        val width = paint.measureText("Ab").toInt()
        val height = (paint.descent() - paint.ascent()).toInt()
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val y = -paint.ascent()
        canvas.drawText("Ab", 0f, y, paint)
        return bitmap
    }

    private fun openItem(item: Any) {
        when (item) {
            is ExportResult -> {
                lifecycleScope.launch {
                    withContext(Dispatchers.Default) {
                        canvasViewModel.loadTemplateFromJsonFile(item, requireContext())
                    }
                }
            }

            is FontEntity -> {
                canvasViewModel.setCanvasSize(
                    CanvasSize(
                        "",
                        2000f,
                        2000f
                    )
                )
                canvasViewModel.addTextWithFont(
                    requireActivity().getString(R.string.dummyText),
                    item,
                    requireActivity()
                )
                view?.post {
                    findNavController().navigate(R.id.editorFragment, bundle, navOptions)
                }
            }

            is ImageEntity -> {
                val bitmap = BitmapFactory.decodeFile(item.bitmapData)

                bitmap?.let {
                    val widthVal = it.width.toFloat()
                    val heightVal = it.height.toFloat()

                    val canvasSize =
                        CanvasSize(
                            "From Image",
                            widthVal,
                            heightVal
                        )

                    canvasViewModel.clearCanvas()
                    canvasViewModel.setCanvasSize(canvasSize)
                    canvasViewModel.setCanvasBackgroundImage(it, requireActivity())
                    view?.post {
                        findNavController().navigate(R.id.editorFragment, bundle, navOptions)
                    }
                }
            }
        }
    }

    private fun showFilePopup(anchorView: View, item: Any) {
        val popupBinding = LayoutFilesPopupBinding.inflate(LayoutInflater.from(requireActivity()))
        val popupWindow = PopupWindow(
            popupBinding.root,
            (180 * requireActivity().resources.displayMetrics.density).toInt(), // fixed width ~200dp
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.elevation = 2f
        popupWindow.isOutsideTouchable = true
        

        anchorView.post {
            val screenHeight = resources.displayMetrics.heightPixels

            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val anchorTop = location[1]
            val anchorBottom = anchorTop + anchorView.height

            // Measure popup height
            popupBinding.root.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val popupHeight = popupBinding.root.measuredHeight

            val spaceBelow = screenHeight - anchorBottom
            val spaceAbove = anchorTop

            if (spaceBelow >= popupHeight) {
                // Enough space below → dropdown
                popupWindow.showAsDropDown(anchorView)
            } else if (spaceAbove >= popupHeight) {
                // Enough space above → show on top
                popupWindow.showAtLocation(
                    anchorView,
                    Gravity.NO_GRAVITY,
                    location[0], // x
                    anchorTop - popupHeight // y (above anchor)
                )
            } else {
                // Default fallback → force dropdown
                popupWindow.showAsDropDown(anchorView)
            }
        }

        popupBinding.actionExport.addPressEffect {
            popupWindow.dismiss()

            when (item) {
                is ExportResult -> {
                    // Export the project file as JSON/Zip or shareable format
                    lifecycleScope.launch {
                        // Example: save to external storage
                        val bitmap = BitmapFactory.decodeFile(item.imagePath)
                        if (bitmap != null) {
                            exportToGallery(bitmap, item.fileName, Bitmap.CompressFormat.PNG)
                            // or detect extension from fileName
                        } else {
                            Snackbar.make(
                                requireView(),   // or binding.root if using ViewBinding
                                "Could not load image",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                is ImageEntity -> {
                    // Save/Share image file
                    lifecycleScope.launch {
                        val bitmap = BitmapFactory.decodeFile(item.bitmapData)
                        if (bitmap != null) {
                            exportToGallery(bitmap, item.file_name, Bitmap.CompressFormat.PNG)
                            // or detect extension from fileName
                        } else {
                            Snackbar.make(
                                requireView(),   // or binding.root if using ViewBinding
                                "Could not load image",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                is FontEntity -> {
                    // Save font file (.ttf)
                    lifecycleScope.launch {
                        val bitmap = BitmapFactory.decodeFile(item.font_image)
                        if (bitmap != null) {
                            exportToGallery(bitmap, item.font_name, Bitmap.CompressFormat.PNG)
                            // or detect extension from fileName
                        } else {
                            Snackbar.make(
                                requireView(),   // or binding.root if using ViewBinding
                                "Could not load image",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

        popupBinding.actionSelect.addPressEffect {
            popupWindow.dismiss()

            when (item) {
                is ExportResult -> {
                    adapter.toggleMultiSelectMode(true)
                }

                is ImageEntity -> {
                    adapter.toggleMultiSelectMode(true)
                }

                is FontEntity -> {
                    adapter.toggleMultiSelectMode(true)
                }
            }
        }

        popupBinding.actionDuplicate.addPressEffect {
            popupWindow.dismiss()
            lifecycleScope.launch(Dispatchers.IO) {
                when (item) {
                    is ExportResult -> {
                        val srcImage = File(item.imagePath)
                        val srcJson = File(item.jsonPath)

                        // use same hierarchy as ImageProcessor
                        val newImageFile =
                            ImageProcessor.newExportImageFile(requireActivity(), srcImage.name)
                        val newJsonFile =
                            ImageProcessor.newExportJsonFile(requireActivity(), srcJson.name)

                        ImageProcessor.copyFile(srcImage, newImageFile)
                        ImageProcessor.copyFile(srcJson, newJsonFile)

                        val newExport = item.copy(
                            id = 0,
                            imagePath = newImageFile.absolutePath,
                            jsonPath = newJsonFile.absolutePath,
                            fileName = "${item.fileName}_copy",
                            updatedDate = System.currentTimeMillis().toString()
                        )
                        viewModel.insertExportResult(newExport)
                    }

                    is ImageEntity -> {
                        val srcImage = File(item.bitmapData ?: item.file_url)
                        val newImageFile =
                            ImageProcessor.newImageFile(requireContext(), srcImage.name)

                        if (srcImage.exists()) ImageProcessor.copyFile(srcImage, newImageFile)

                        val newEntity = item.copy(
                            id = 0,
                            file_name = "${item.file_name}_copy",
                            bitmapData = newImageFile.absolutePath,
                            created_at = System.currentTimeMillis().toString()
                        )
                        viewModel.insertImage(newEntity)
                    }

                    is FontEntity -> {
                        val srcFont = File(item.file_path ?: item.file_url)
                        val newFontFile = ImageProcessor.newFontFile(requireContext(), srcFont.name)

                        if (srcFont.exists()) ImageProcessor.copyFile(srcFont, newFontFile)

                        val srcPreview = File(item.font_image)
                        val newPreviewFile =
                            ImageProcessor.newFontPreviewFile(requireContext(), srcPreview.name)

                        if (srcPreview.exists()) ImageProcessor.copyFile(srcPreview, newPreviewFile)

                        val newEntity = item.copy(
                            id = 0,
                            font_name = "${item.font_name}_copy",
                            font_image = newPreviewFile.absolutePath,
                            file_path = newFontFile.absolutePath,
                            created_at = System.currentTimeMillis().toString()
                        )
                        viewModel.insertFont(newEntity)
                    }
                }
            }
        }


        popupBinding.actionRename.addPressEffect {
            popupWindow.dismiss()

            val itemId = when (item) {
                is ImageEntity -> item.id.toLong()
                is FontEntity -> item.id.toLong()
                is ExportResult -> item.id
                else -> 0
            }
            adapter.startEditing(itemId)
        }

        popupBinding.actionDelete.addPressEffect {
            popupWindow.dismiss()
            val (title, subtitle) = when (item) {
                is ExportResult -> getString(R.string.delete_project) to getString(R.string.your_asset_will_be_permanently_deleted)
                is ImageEntity -> getString(R.string.delete_image) to getString(R.string.your_asset_will_be_permanently_deleted)
                is FontEntity -> getString(R.string.delete_font) to getString(R.string.your_asset_will_be_permanently_deleted)
                else -> getString(R.string.delete) to getString(R.string.your_asset_will_be_permanently_deleted)
            }

            DialogUtils.showDeleteDialog(requireActivity(), title, subtitle) {
                when (item) {
                    is ExportResult -> viewModel.deleteExportResult(item)
                    is ImageEntity -> viewModel.deleteImage(item)
                    is FontEntity -> viewModel.deleteFont(item)
                }
            }
        }
    }

    private fun exportToGallery(bitmap: Bitmap, fileName: String, format: Bitmap.CompressFormat) {
        val ext = when (format) {
            Bitmap.CompressFormat.PNG -> "png"
            Bitmap.CompressFormat.JPEG -> "jpg"
            Bitmap.CompressFormat.WEBP -> "webp"
            else -> "png"
        }

        val mimeType = when (format) {
            Bitmap.CompressFormat.PNG -> "image/png"
            Bitmap.CompressFormat.JPEG -> "image/jpeg"
            Bitmap.CompressFormat.WEBP -> "image/webp"
            else -> "image/png"
        }

        val filename = "${fileName}_${System.currentTimeMillis()}.$ext"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/${getString(R.string.app_name)}"
            )
        }

        val resolver = requireContext().contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { stream ->
                bitmap.compress(format, 100, stream)
            }
            Snackbar.make(
                requireView(),
                "Exported to Gallery",
                Snackbar.LENGTH_SHORT
            ).show()
        } ?: run {
            Snackbar.make(
                requireView(),
                "Export failed",
                Snackbar.LENGTH_SHORT
            ).show()
        }

    }

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
                lifecycleScope.launch {
                    delay(500)
                    if (findNavController().currentDestination?.id != R.id.editorFragment) {
                        view?.post {
                            findNavController().navigate(R.id.editorFragment, bundle, navOptions)
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            filtersViewModel.isGrid.collect { isGrid ->
                if (isGrid) {
                    binding.filesRV.layoutManager = GridLayoutManager(requireContext(), 2)
                } else {
                    binding.filesRV.layoutManager = LinearLayoutManager(requireContext())
                }
                adapter.toggleViewType(isGrid)
            }
        }

        when (tabName) {
            "All" -> {
                lifecycleScope.launch {
                    combine(
                        viewModel.localFonts,
                        viewModel.localImages,
                        viewModel.exportResults.asFlow(),
                        filtersViewModel.searchQuery
                    ) { fonts, images, results, query ->
                        val q = query.trim().lowercase()

                        val filteredFonts = fonts.filter {
                            it.font_category == "Imported" &&
                                    (q.isEmpty() || it.font_name.lowercase().contains(q))
                        }

                        val filteredImages = images.filter {
                            it.category == "Images Imported" &&
                                    (q.isEmpty() || it.file_name.lowercase().contains(q))
                        }

                        val filteredProjects = results.filter {
                            q.isEmpty() || it.fileName.lowercase().contains(q)
                        }

                        filteredFonts + filteredImages + filteredProjects
                    }.collect { list ->
                        adapter.updateList(list)
                        binding.noImagesText.text = requireActivity().getString(R.string.no_assets_available)
                        binding.noEmojis.visibility =
                            if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }

            "Projects" -> {
                lifecycleScope.launch {
                    combine(
                        viewModel.exportResults.asFlow(),
                        filtersViewModel.searchQuery
                    ) { results, query ->
                        val q = query.trim().lowercase()
                        results.filter { q.isEmpty() || it.fileName.lowercase().contains(q) }
                    }.collect { list ->
                        adapter.updateList(list)
                        binding.noImagesText.text = requireActivity().getString(R.string.no_projects_available)
                        binding.noEmojis.visibility =
                            if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }

            "Fonts" -> {
                lifecycleScope.launch {
                    combine(
                        viewModel.localFonts,
                        filtersViewModel.searchQuery
                    ) { fonts, query ->
                        val q = query.trim().lowercase()
                        fonts.filter {
                            it.font_category == "Imported" &&
                                    (q.isEmpty() || it.font_name.lowercase().contains(q))
                        }
                    }.collect { list ->
                        if (list.isEmpty()) {

                            binding.noEmojis.visibility = View.VISIBLE

                            val fullText = "No imported fonts.\nBrowse in-app fonts."
                            val clickablePart = "in-app fonts"

                            val spannable = android.text.SpannableString(fullText)

                            val start = fullText.indexOf(clickablePart)
                            val end = start + clickablePart.length

                            val clickableSpan = object : android.text.style.ClickableSpan() {
                                override fun onClick(widget: View) {
                                    // Navigate to In-App Fonts screen
                                    findNavController().navigate(
                                        R.id.popularFontsFragment
                                    )
                                }

                                override fun updateDrawState(ds: android.text.TextPaint) {
                                    super.updateDrawState(ds)
                                    ds.isUnderlineText = true
                                    val typeface = ResourcesCompat.getFont(
                                        requireContext(),
                                        R.font.medium
                                    )

                                    ds.typeface = typeface
                                    ds.color = requireContext().getColor(R.color.appColor)
                                }
                            }

                            spannable.setSpan(
                                clickableSpan,
                                start,
                                end,
                                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )

                            binding.noImagesText.text = spannable
                            binding.noImagesText.movementMethod =
                                android.text.method.LinkMovementMethod.getInstance()

                        } else {
                            adapter.updateList(list)
                            binding.noEmojis.visibility = View.GONE
                        }

                    }
                }
            }

            "Stickers" -> {
                lifecycleScope.launch {
                    combine(
                        viewModel.localImages,
                        filtersViewModel.searchQuery
                    ) { images, query ->
                        val q = query.trim().lowercase()
                        images.filter {
                            it.category == "Images Imported" &&
                                    (q.isEmpty() || it.file_name.lowercase().contains(q))
                        }
                    }.collect { list ->
                        adapter.updateList(list)

                        binding.noImagesText.text = requireActivity().getString(R.string.no_stickers_available)
                        binding.noEmojis.visibility =
                            if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }

            "Backgrounds" -> {
                lifecycleScope.launch {
                    combine(
                        viewModel.localImages,
                        filtersViewModel.searchQuery
                    ) { images, query ->
                        val q = query.trim().lowercase()
                        images.filter {
                            it.category == "Backgrounds Imported" &&
                                    (q.isEmpty() || it.file_name.lowercase().contains(q))
                        }
                    }.collect { list ->
                        adapter.updateList(list)
                        binding.noImagesText.text = requireActivity().getString(R.string.no_backgrounds_available)
                        binding.noEmojis.visibility =
                            if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }

        }
    }

    private fun showLoadingDialog() {
        if (loadingDialog?.isShowing == true) return

        dialogBinding = DialogLoadingProgressBinding.inflate(LayoutInflater.from(requireActivity()))

        loadingDialog = Dialog(requireContext()).apply {
            setContentView(dialogBinding!!.root)
            setCancelable(true)
            setOnCancelListener { dialog ->
                canvasViewModel.clearLoading()
            }
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            val params = window?.attributes
            params?.width = (resources.displayMetrics.widthPixels * 0.8).toInt() // 80% width
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
                duration = 1000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    private fun stopIconRotation() {
        rotationAnimator?.cancel()
        rotationAnimator = null
    }

    private fun dismissLoadingDialog() {
        stopIconRotation()
        loadingDialog?.dismiss()
        loadingDialog = null
        dialogBinding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        if (findNavController().currentDestination?.id!! != R.id.editorFragment) {
            canvasViewModel.clearCanvas()
        }
    }

    companion object {
        fun newInstance(tabName: String): FilesListFragment {
            return FilesListFragment().apply {
                arguments = Bundle().apply {
                    putString("TAB_NAME", tabName)
                }
            }
        }
    }
}