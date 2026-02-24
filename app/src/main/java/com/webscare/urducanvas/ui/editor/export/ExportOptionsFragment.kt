package com.webscare.urducanvas.ui.editor.export

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.common.canvas.enums.ExportViewType
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentExportOptionsBinding

class ExportOptionsFragment : com.google.android.material.bottomsheet.BottomSheetDialogFragment() {

    private var _binding: FragmentExportOptionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewType: ExportViewType
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()

    private lateinit var items: List<Any>
    private lateinit var adapter: ExportOptionAdapter<Any>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewType = ExportViewType.valueOf(requireArguments().getString(ARG_VIEW_TYPE)!!)
    }

    override fun onStart() {
        super.onStart()
        dialog?.let {
            val bottomSheet =
                it.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.layoutParams?.height =
                (resources.displayMetrics.heightPixels * 0.8).toInt()
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
                is com.webscare.urducanvas.common.canvas.model.ExportResolution -> viewModel.updateExportOptionsInMemory(
                    viewModel.exportOptions.value!!.copy(resolution = selected)
                )

                is com.webscare.urducanvas.common.canvas.model.ExportQuality -> viewModel.updateExportOptionsInMemory(
                    viewModel.exportOptions.value!!.copy(quality = selected)
                )

                is com.webscare.urducanvas.common.canvas.model.ExportFormat -> viewModel.updateExportOptionsInMemory(
                    viewModel.exportOptions.value!!.copy(format = selected)
                )
            }
            binding.options.postDelayed({
                if (isAdded && view != null) {
                    dismissAllowingStateLoss()
                }
            }, 500)
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
