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

        binding.title.text = when (viewType) {
            ExportViewType.RESOLUTION -> "Choose Resolution"
            ExportViewType.QUALITY -> "Choose Quality"
            ExportViewType.FORMAT -> "Choose Format"
        }

        val items = when (viewType) {
            ExportViewType.RESOLUTION -> viewModel.availableResolutions
            ExportViewType.QUALITY -> viewModel.qualityOptions
            ExportViewType.FORMAT -> viewModel.formatOptions
        }

        val adapter = ExportOptionAdapter(
            items,
            viewType
        ) { selected ->
            when (selected) {
                is ExportResolution -> viewModel.updateExportOptions(
                    viewModel.exportOptions.value!!.copy(resolution = selected)
                )

                is ExportQuality -> viewModel.updateExportOptions(
                    viewModel.exportOptions.value!!.copy(quality = selected)
                )

                is ExportFormat -> viewModel.updateExportOptions(
                    viewModel.exportOptions.value!!.copy(format = selected)
                )
            }
        }

        binding.options.adapter = adapter

        // Observer for selection update
        viewModel.exportOptions.observe(viewLifecycleOwner) { options ->
            items.forEach {
                when (it) {
                    is ExportResolution -> it.isSelected = (it == options.resolution)
                    is ExportQuality -> it.isSelected = (it == options.quality)
                    is ExportFormat -> it.isSelected = (it == options.format)
                }
            }
            adapter.notifyDataSetChanged()
        }

        binding.back.addPressEffect { dismiss() }
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
