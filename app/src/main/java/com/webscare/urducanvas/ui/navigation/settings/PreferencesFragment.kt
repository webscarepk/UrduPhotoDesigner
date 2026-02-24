package com.webscare.urducanvas.ui.navigation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.common.canvas.enums.ExportViewType
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentPreferencesBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PreferencesFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentPreferencesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPreferencesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        initObservers()
    }

    private fun initObservers() {

        viewModel.fetchExportOptionsFromDataStore()

        viewModel.exportOptions.observe(viewLifecycleOwner) { opts ->
            binding.resolution.text = opts.resolution.name
            binding.quality.text = opts.quality.label
            binding.format.text = opts.format.name

            binding.resolutionList.adapter?.notifyDataSetChanged()
            binding.qualityList.adapter?.notifyDataSetChanged()
            binding.formatList.adapter?.notifyDataSetChanged()
        }
    }

    private fun setupRecycler(rv: RecyclerView, type: ExportViewType) {
        val items = when (type) {
            ExportViewType.RESOLUTION -> viewModel.availableResolutions
            ExportViewType.QUALITY -> viewModel.qualityOptions
            ExportViewType.FORMAT -> viewModel.formatOptions
        }

        rv.adapter =
            _root_ide_package_.com.webscare.urducanvas.ui.editor.export.ExportOptionAdapter(
                items,
                type,
                displayMode = false   // ✅ compact mode
            ) { selected ->
                when (selected) {
                    is com.webscare.urducanvas.common.canvas.model.ExportResolution -> viewModel.updateExportOptionsAndSave(
                        viewModel.exportOptions.value!!.copy(resolution = selected)
                    )

                    is com.webscare.urducanvas.common.canvas.model.ExportQuality -> viewModel.updateExportOptionsAndSave(
                        viewModel.exportOptions.value!!.copy(quality = selected)
                    )

                    is com.webscare.urducanvas.common.canvas.model.ExportFormat -> viewModel.updateExportOptionsAndSave(
                        viewModel.exportOptions.value!!.copy(format = selected)
                    )
                }
                rv.visibility = View.GONE
            }
    }

    private fun toggle(rv: RecyclerView) {
        rv.visibility = if (rv.isVisible) View.GONE else View.VISIBLE
    }

    private fun setEvents() {
        setupRecycler(binding.resolutionList, ExportViewType.RESOLUTION)
        setupRecycler(binding.qualityList, ExportViewType.QUALITY)
        setupRecycler(binding.formatList, ExportViewType.FORMAT)

        // expand/collapse on click
        binding.resolution.addPressEffect { toggle(binding.resolutionList) }
        binding.quality.addPressEffect { toggle(binding.qualityList) }
        binding.format.addPressEffect { toggle(binding.formatList) }

        binding.back.addPressEffect {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}