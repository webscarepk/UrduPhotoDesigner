package com.example.urduphotodesigner.ui.navigation.templates

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.UnitType
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.example.urduphotodesigner.common.canvas.sealed.HomeRow
import com.example.urduphotodesigner.common.canvas.sealed.TemplateDownloadState
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.data.model.toExportResultFinal
import com.example.urduphotodesigner.databinding.DialogLoadingProgressBinding
import com.example.urduphotodesigner.databinding.FragmentTemplatesBinding
import com.example.urduphotodesigner.ui.creation.CanvasSizeAdapter
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class TemplatesFragment : Fragment() {
    private var _binding: FragmentTemplatesBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()

    private lateinit var categoryAdapter: TemplateCategoriesAdapter
    private lateinit var adapter: CanvasSizeAdapter
    private var downloadingTemplate: TemplateEntity? = null
    private var bundle: Bundle = Bundle()
    private var loadingDialog: AlertDialog? = null
    private var dialogBinding: DialogLoadingProgressBinding? = null

    private val sizeList = listOf(
        CanvasSize("Instagram Story", 1080f, 1920f),
        CanvasSize("Instagram Post", 1080f, 1080f),
        CanvasSize("YouTube Thumbnail", 1280f, 720f),
        CanvasSize("Facebook Cover", 820f, 312f),
        CanvasSize("YouTube Channel Art", 2560f, 1440f),
        CanvasSize("A4", 2480f, 3508f),               // 210mm × 297mm
        CanvasSize("Letter", 2550f, 3300f),          // 8.5in × 11in
        CanvasSize("Poster", 3600f, 5400f),          // 12in × 18in
        CanvasSize("Business Card", 1050f, 600f), // 3.5in × 2in
        CanvasSize("Billboard", 1920f, 1080f),
        CanvasSize("Vertical Banner", 1080f, 1920f),
        CanvasSize("Horizontal Banner", 1920f, 600f),
        CanvasSize("Flyer", 2550f, 3300f),
        CanvasSize("Resume", 2480f, 3508f),
        CanvasSize("Invitation", 1500f, 2100f)   // 5in × 7in
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTemplatesBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        observeTemplateCategories()
    }

    private fun showLoadingDialog() {
        dialogBinding = DialogLoadingProgressBinding.inflate(LayoutInflater.from(requireActivity()))

        loadingDialog = AlertDialog.Builder(requireActivity())
            .setView(dialogBinding!!.root)
            .setCancelable(false)
            .create()

        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog?.show()
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
        dialogBinding = null
    }

    private fun setEvents() {
        adapter = CanvasSizeAdapter(sizeList, onClick = { selected ->
            adapter.selectedSizeName = selected.name
        }, false)
        binding.sizesRV.adapter = adapter

        categoryAdapter = TemplateCategoriesAdapter(
            onSeeAll = { category ->
                val args = Bundle().apply { putString("TAB_NAME", category) }
                findNavController().navigate(R.id.templatesListFragment, args)
            },
            onTemplateClick = { template, bool ->
                if (bool) {
                    val exportResult = template.toExportResultFinal()
                    lifecycleScope.launch {
                        withContext(Dispatchers.Default) {
                            viewModel.loadTemplateFromJsonFile(exportResult, requireContext())
                            bundle = Bundle().apply {
                                putSerializable("canvas_size", exportResult.canvasSize)
                                putSerializable("unit_type", UnitType.PIXELS)
                            }
                        }
                    }
                    return@TemplateCategoriesAdapter
                }
                downloadingTemplate = template
                mainViewModel.downloadTemplate(template)
            }
        )
        binding.categoriesRV.adapter = categoryAdapter

        binding.back.addPressEffect { findNavController().navigateUp() }
    }

    private fun observeTemplateCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.localTemplates.collect { templates ->
                val categories = templates.groupBy { it.category ?: "Others" }
                val rows = categories.map { (cat, list) ->
                    HomeRow.CategoryRow(cat, list.take(10)) // preview max 10 items
                }
                categoryAdapter.submitList(rows)
            }
        }

        viewModel.isLoadingTemplate.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading == true){
                showLoadingDialog()
            }else if (isLoading == false){
                dismissLoadingDialog()
                findNavController().navigate(R.id.editorFragment, bundle)
            }
        }

        // download state
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.templateDownloadState.collect { state ->
                when (state) {
                    is TemplateDownloadState.Progress -> {
                        downloadingTemplate = state.template
                    }
                    is TemplateDownloadState.SuccessWithTemplate -> {
                        downloadingTemplate = state.template
                        mainViewModel.clearTemplateDownloadState()
                        val exportResult = downloadingTemplate!!.toExportResultFinal()
                        lifecycleScope.launch {
                            withContext(Dispatchers.Default) {
                                viewModel.loadTemplateFromJsonFile(exportResult, requireContext())
                                bundle = Bundle().apply {
                                    putSerializable("canvas_size", exportResult.canvasSize)
                                    putSerializable("unit_type", UnitType.PIXELS)
                                }
                            }
                        }
                    }
                    is TemplateDownloadState.Success -> {
                        mainViewModel.clearTemplateDownloadState()
                    }
                    is TemplateDownloadState.Error -> {
                        downloadingTemplate = null
                    }
                    null -> Unit
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}