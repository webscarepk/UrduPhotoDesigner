package com.example.urduphotodesigner.ui.navigation.files

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.utils.DialogUtils
import com.example.urduphotodesigner.common.utils.ImageProcessor
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.ExportResult
import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.databinding.FragmentFilesListBinding
import com.example.urduphotodesigner.databinding.LayoutFilesPopupBinding
import com.example.urduphotodesigner.viewmodels.FiltersViewModel
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class FilesListFragment : Fragment() {
    private var _binding: FragmentFilesListBinding? = null
    private val binding get() = _binding!!

    private var tabName: String? = null
    private lateinit var adapter: FilesAdapter
    private val viewModel: MainViewModel by activityViewModels()
    private val filtersViewModel: FiltersViewModel by activityViewModels()

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

    }

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
            })
        binding.filesRV.adapter = adapter
        binding.filesRV.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun openItem(item: Any) {
        when (item) {
            is ExportResult -> {
                // open project
                Toast.makeText(
                    requireContext(),
                    "Opening project: ${item.fileName}",
                    Toast.LENGTH_SHORT
                ).show()
                // navigate to editor with this project
                // navController.navigate(R.id.editorFragment, bundleOf("projectId" to item.id))
            }

            is FontEntity -> {
                // open font preview
                Toast.makeText(
                    requireContext(),
                    "Preview font: ${item.font_name}",
                    Toast.LENGTH_SHORT
                ).show()
                // maybe launch a FontPreviewDialog
            }

            is ImageEntity -> {
                // open image viewer
                Toast.makeText(
                    requireContext(),
                    "Opening image: ${item.file_name}",
                    Toast.LENGTH_SHORT
                ).show()
                // show full screen or detail fragment
            }
        }
    }

    private fun showFilePopup(anchorView: View, item: Any) {
        val popupBinding = LayoutFilesPopupBinding.inflate(LayoutInflater.from(requireActivity()))
        val popupWindow = PopupWindow(
            popupBinding.root,
            (150 * requireActivity().resources.displayMetrics.density).toInt(), // fixed width ~200dp
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.elevation = 20f
        popupWindow.isOutsideTouchable = true
        popupWindow.animationStyle = R.style.PopupFadeAnimation

        // ---- placement logic ----
        anchorView.post {
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels
            val margin = (20 * resources.displayMetrics.density).toInt()

            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val anchorLeft = location[0]
            val anchorTop = location[1]
            val anchorBottom = anchorTop + anchorView.height

            // Measure popup
            popupBinding.root.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val popupHeight = popupBinding.root.measuredHeight
            val popupWidth = popupBinding.root.measuredWidth

            val spaceBelow = screenHeight - anchorBottom
            val spaceAbove = anchorTop

            // vertical placement
            val y = if (spaceBelow >= popupHeight + margin) {
                anchorBottom
            } else if (spaceAbove >= popupHeight + margin) {
                anchorTop - popupHeight
            } else {
                anchorBottom
            }

            // horizontal placement (keep 20dp margin from right)
            var x = anchorLeft
            if (x + popupWidth > screenWidth - margin) {
                x = screenWidth - margin - popupWidth
            }
            if (x < margin) x = margin // also protect left

            popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y)
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
                            Toast.makeText(
                                requireContext(),
                                "Could not load image",
                                Toast.LENGTH_SHORT
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
                            Toast.makeText(
                                requireContext(),
                                "Could not load image",
                                Toast.LENGTH_SHORT
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
                            Toast.makeText(
                                requireContext(),
                                "Could not load image",
                                Toast.LENGTH_SHORT
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
                Environment.DIRECTORY_PICTURES + "/UrduDesigner"
            )
        }

        val resolver = requireContext().contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { stream ->
                bitmap.compress(format, 100, stream)
            }
            Toast.makeText(requireContext(), "Exported to Gallery", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(requireContext(), "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initObservers() {
        lifecycleScope.launch {
            filtersViewModel.isGrid.collect { isGrid ->
                if (isGrid) {
                    binding.filesRV.layoutManager = GridLayoutManager(requireContext(), 3)
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
                    }.collect { list -> adapter.updateList(list) }
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
                    }.collect { list -> adapter.updateList(list) }
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
                    }.collect { list -> adapter.updateList(list) }
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
                    }.collect { list -> adapter.updateList(list) }
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
                    }.collect { list -> adapter.updateList(list) }
                }
            }

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
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