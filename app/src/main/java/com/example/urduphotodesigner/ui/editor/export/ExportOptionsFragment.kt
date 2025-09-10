package com.example.urduphotodesigner.ui.editor.export

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.ExportViewType
import com.example.urduphotodesigner.common.canvas.model.ExportFormat
import com.example.urduphotodesigner.common.canvas.model.ExportQuality
import com.example.urduphotodesigner.common.canvas.model.ExportResolution
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.FragmentExportOptionsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ExportOptionsFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentExportOptionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewType: ExportViewType
    private val viewModel: CanvasViewModel by activityViewModels()

    private lateinit var items: List<Any>
    private lateinit var adapter: ExportOptionAdapter<Any>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewType = ExportViewType.valueOf(requireArguments().getString(ARG_VIEW_TYPE)!!)
    }

    override fun onStart() {
        super.onStart()
        dialog?.let {
            val bottomSheet = it.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.layoutParams?.height = (resources.displayMetrics.heightPixels * 0.5).toInt()
            bottomSheet?.requestLayout()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        initObservers()
    }

    private fun setEvents() {

        items = when (viewType) {
            ExportViewType.RESOLUTION -> viewModel.availableResolutions
            ExportViewType.QUALITY -> viewModel.qualityOptions
            ExportViewType.FORMAT -> viewModel.formatOptions
        }

        binding.title.text = when (viewType) {
            ExportViewType.RESOLUTION -> "Choose Resolution"
            ExportViewType.QUALITY -> "Choose Quality"
            ExportViewType.FORMAT -> "Choose Format"
        }

        adapter = ExportOptionAdapter(
            items,
            viewType,
            true
        ) { selected ->
            when (selected) {
                is ExportResolution -> viewModel.updateExportOptionsInMemory(
                    viewModel.exportOptions.value!!.copy(resolution = selected)
                )
                is ExportQuality -> viewModel.updateExportOptionsInMemory(
                    viewModel.exportOptions.value!!.copy(quality = selected)
                )
                is ExportFormat -> viewModel.updateExportOptionsInMemory(
                    viewModel.exportOptions.value!!.copy(format = selected)
                )
            }
        }

        binding.options.adapter = adapter

        binding.back.addPressEffect { dismiss() }
    }

    private fun initObservers() {
        viewModel.exportOptions.observe(viewLifecycleOwner) { options ->
            val items = when (viewType) {
                ExportViewType.RESOLUTION -> viewModel.availableResolutions
                ExportViewType.QUALITY -> viewModel.qualityOptions
                ExportViewType.FORMAT -> viewModel.formatOptions
            }
            adapter.updateList(items)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_VIEW_TYPE = "arg_view_type"

        fun newInstance(viewType: ExportViewType): ExportOptionsFragment {
            val fragment = ExportOptionsFragment()
            val args = Bundle()
            args.putString(ARG_VIEW_TYPE, viewType.name)
            fragment.arguments = args
            return fragment
        }
    }

}
