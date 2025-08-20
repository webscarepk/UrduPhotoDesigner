package com.example.urduphotodesigner.ui.navigation.files

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import com.example.urduphotodesigner.data.model.ExportResult
import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.databinding.FragmentFilesListBinding
import com.example.urduphotodesigner.databinding.LayoutFilesPopupBinding
import com.example.urduphotodesigner.ui.navigation.saved.SavedListFragment
import com.example.urduphotodesigner.viewmodels.FiltersViewModel
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.logging.Filter
import androidx.core.graphics.drawable.toDrawable

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
            onItemLongClick = { item ->
//                if (!adapter.multiSelectMode) {
//                    adapter.toggleMultiSelectMode(true)
//                    adapter.selectItem(item)
//                }
            },
            onOptionsClick = { item, anchorView ->
                showFilePopup(anchorView, item)
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
        val popupBinding = LayoutFilesPopupBinding.inflate(LayoutInflater.from(context))
        val popupWindow = PopupWindow(
            popupBinding.root,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.elevation = 10f
        popupWindow.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        popupWindow.isOutsideTouchable = true
        popupWindow.animationStyle = R.style.PopupFadeAnimation

        popupBinding.actionExport.setOnClickListener {
            popupWindow.dismiss()
        }

        popupBinding.actionSelect.setOnClickListener {
            popupWindow.dismiss()
        }

        popupBinding.actionDuplicate.setOnClickListener {
            popupWindow.dismiss()
            when (item) {
                is ExportResult -> lifecycleScope.launch { viewModel.insertExportResult(item.copy()) }
                is ImageEntity -> lifecycleScope.launch { viewModel.insertImage(item.copy()) }
                is FontEntity -> lifecycleScope.launch { viewModel.insertFont(item.copy()) }
            }
        }

        popupBinding.actionRename.setOnClickListener {
            popupWindow.dismiss()

            // Show rename dialog
            val editText = EditText(requireContext()).apply {
                when (item) {
                    is ExportResult -> setText(item.fileName)
                    is ImageEntity -> setText(item.file_name)
                    is FontEntity -> setText(item.font_name)
                }
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Rename")
                .setView(editText)
                .setPositiveButton("OK") { _, _ ->
                    val newName = editText.text.toString().trim()
                    if (newName.isNotEmpty()) {
                        lifecycleScope.launch {
                            when (item) {
                                is ExportResult -> viewModel.insertExportResult(item.copy(fileName = newName))
                                is ImageEntity -> viewModel.updateImage(item.copy(file_name = newName))
                                is FontEntity -> viewModel.updateFont(item.copy(font_name = newName))
                            }
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        popupBinding.actionDelete.setOnClickListener {
            popupWindow.dismiss()
            when (item) {
                is ExportResult -> viewModel.deleteExportResult(item)
                is ImageEntity -> viewModel.deleteImage(item)
                is FontEntity -> viewModel.deleteFont(item)
            }
        }

        popupWindow.showAsDropDown(anchorView, 0, -anchorView.height)
    }


    private fun initObservers() {
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