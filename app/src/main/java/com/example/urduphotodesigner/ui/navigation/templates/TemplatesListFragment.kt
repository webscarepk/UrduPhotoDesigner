package com.example.urduphotodesigner.ui.navigation.templates

import android.app.AlertDialog
import android.graphics.Canvas
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.UnitType
import com.example.urduphotodesigner.common.canvas.sealed.TemplateDownloadState
import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.data.model.toExportResultFinal
import com.example.urduphotodesigner.databinding.DialogLoadingProgressBinding
import com.example.urduphotodesigner.databinding.FragmentTemplatesListBinding
import com.example.urduphotodesigner.ui.navigation.saved.SavedListFragment
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class TemplatesListFragment : Fragment() {
    private var _binding: FragmentTemplatesListBinding?= null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()
    private lateinit var adapter: TemplatesAdapter
    private var currentCategory: String? = null
    private var downloadingTemplate: TemplateEntity? = null

    private var bundle: Bundle = Bundle()
    private var loadingDialog: AlertDialog? = null
    private var dialogBinding: DialogLoadingProgressBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentCategory  = arguments?.getString("TAB_NAME")
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
        setupRecycler()
        observeData()
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
            // start download like fonts flow
            downloadingTemplate = template
            val newList = adapter.currentList.map {
                if (it.id == template.id) it.copy(is_downloading = true) else it
            }
            adapter.submitList(newList)
            mainViewModel.downloadTemplate(template)
        }
        binding.templatesRV.adapter = adapter
    }

    private fun observeData() {
        // list
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.localTemplates.collect { all ->
                val filtered = if (currentCategory.equals("All", true)) {
                    all
                } else {
                    all.filter { it.category.equals(currentCategory, true) }
                }
                adapter.submitList(filtered)
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