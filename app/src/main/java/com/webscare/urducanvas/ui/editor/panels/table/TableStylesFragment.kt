package com.webscare.urducanvas.ui.editor.panels.table

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.TablePreset
import com.webscare.urducanvas.databinding.LayoutTableStylesOptionsBinding

class TableStylesFragment : Fragment() {

    private var _binding: LayoutTableStylesOptionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = LayoutTableStylesOptionsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = TablePresetsAdapter(TablePreset.values().toList()) { preset ->
            viewModel.applyTablePreset(preset)
        }
        binding.rvPresets.adapter = adapter
    }

    override fun onDestroyView() {
        _binding?.rvPresets?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = TableStylesFragment()
    }
}
