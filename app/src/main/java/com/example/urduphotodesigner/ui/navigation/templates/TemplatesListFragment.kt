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
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.UnitType
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.example.urduphotodesigner.common.canvas.sealed.TemplateDownloadState
import com.example.urduphotodesigner.common.utils.showGlobalSuccessSnack
import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.data.model.toExportResultFinal
import com.example.urduphotodesigner.databinding.DialogLoadingProgressBinding
import com.example.urduphotodesigner.databinding.FragmentTemplatesListBinding
import com.example.urduphotodesigner.ui.creation.CanvasSizeAdapter
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class TemplatesListFragment : Fragment() {
    private var _binding: FragmentTemplatesListBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()
    private lateinit var adapter: TemplatesAdapter
    private var currentCategory: String? = null
    private var downloadingTemplate: TemplateEntity? = null

    private var bundle: Bundle = Bundle()
    private var loadingDialog: AlertDialog? = null
    private var dialogBinding: DialogLoadingProgressBinding? = null
    private lateinit var sizeAdapter: CanvasSizeAdapter

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentCategory = arguments?.getString("TAB_NAME")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTemplatesListBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        setupRecycler()
        observeData()
    }

    private fun setEvents() {
        binding.back.setOnClickListener { findNavController().navigateUp() }
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

    private fun setupRecycler() {
        binding.title.text = currentCategory

        sizeAdapter = CanvasSizeAdapter(sizeList, onClick = { selected ->
            sizeAdapter.selectedSizeName = selected.name
        }, false)
        binding.sizesRV.adapter = sizeAdapter

        adapter = TemplatesAdapter { template, isDownloaded ->
            if (isDownloaded) {
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
                return@TemplatesAdapter
            }

            downloadingTemplate = template
            adapter.updateProgress(
                template.id,
                TemplatesAdapter.ProgressUi(
                    progress = 0,
                    isDownloading = true,
                    isDownloaded = false
                )
            )
            mainViewModel.downloadTemplate(template)
        }

        binding.templatesRV.setHasFixedSize(true)
        binding.templatesRV.adapter = adapter
        binding.templatesRV.itemAnimator = null
    }

    private fun observeData() {
        // 1) List stream: keep current on-screen order when new data arrives
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.localTemplates.collect { all ->
                val filtered = if (currentCategory.equals("All", true)) {
                    all
                } else {
                    all.filter { it.category.equals(currentCategory, true) }
                }

                val current = adapter.currentList
                if (current.isEmpty()) {
                    adapter.submitList(filtered)
                } else {
                    val byId = filtered.associateBy { it.id }
                    // Preserve order for items already shown
                    val merged = buildList {
                        current.forEach { cur ->
                            byId[cur.id]?.let { add(it) }
                        }
                        // Append any new items not present before (stable add)
                        filtered.forEach { f ->
                            if (current.none { it.id == f.id }) add(f)
                        }
                    }
                    adapter.submitList(merged)
                }
            }
        }

        viewModel.isLoadingTemplate.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading == true) {
                showLoadingDialog()
            } else if (isLoading == false) {
                dismissLoadingDialog()
                findNavController().navigate(R.id.editorFragment, bundle)
            }
        }

        // 2) Download state: update only the affected row via payload; avoid submitList
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.templateDownloadState.collect { state ->
                when (state) {
                    is TemplateDownloadState.Progress -> {
                        val t = state.template
                        downloadingTemplate = t
                        adapter.updateProgress(
                            t.id,
                            TemplatesAdapter.ProgressUi(
                                progress = state.progress,
                                isDownloading = true,
                                isDownloaded = false
                            )
                        )
                    }

                    is TemplateDownloadState.SuccessWithTemplate -> {
                        val t = state.template
                        downloadingTemplate = t

                        // Immediately flip UI to "downloaded" for that row
                        adapter.updateProgress(
                            t.id,
                            TemplatesAdapter.ProgressUi(
                                progress = 100,
                                isDownloading = false,
                                isDownloaded = true
                            )
                        )

                        // Persist to DB; the list collector above will bring in the updated entity
                        mainViewModel.insertTemplate(
                            t.copy(is_downloading = false, is_downloaded = true)
                        )
                        mainViewModel.clearTemplateDownloadState()

                        showGlobalSuccessSnack("Template ready") {
                            val exportResult = t.toExportResultFinal()
                            lifecycleScope.launch {
                                withContext(Dispatchers.Default) {
                                    viewModel.loadTemplateFromJsonFile(
                                        exportResult,
                                        requireContext()
                                    )
                                    bundle = Bundle().apply {
                                        putSerializable("canvas_size", exportResult.canvasSize)
                                        putSerializable("unit_type", UnitType.PIXELS)
                                    }
                                }
                            }
                        }
                    }

                    is TemplateDownloadState.Success -> {
                        mainViewModel.clearTemplateDownloadState()
                    }

                    is TemplateDownloadState.Error -> {
                        downloadingTemplate?.let { t ->
                            adapter.updateProgress(
                                t.id,
                                TemplatesAdapter.ProgressUi(
                                    progress = 0,
                                    isDownloading = false,
                                    isDownloaded = false
                                )
                            )
                        }
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

    companion object {
        fun newInstance(tabName: String): TemplatesListFragment {
            return TemplatesListFragment().apply {
                arguments = Bundle().apply {
                    putString("TAB_NAME", tabName)
                }
            }
        }
    }
}