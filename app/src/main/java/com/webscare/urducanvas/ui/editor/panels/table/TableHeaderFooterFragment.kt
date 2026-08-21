package com.webscare.urducanvas.ui.editor.panels.table

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.LayoutTableHeaderFooterOptionsBinding

class TableHeaderFooterFragment : Fragment() {

    private var _binding: LayoutTableHeaderFooterOptionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = LayoutTableHeaderFooterOptionsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnHeaderEnable.addPressEffect {
            viewModel.setTableHeader(true)
            updatePillSelection(binding.btnHeaderEnable, binding.btnHeaderDisable)
        }

        binding.btnHeaderDisable.addPressEffect {
            viewModel.setTableHeader(false)
            updatePillSelection(binding.btnHeaderDisable, binding.btnHeaderEnable)
        }

        binding.btnFooterEnable.addPressEffect {
            viewModel.setTableFooter(true)
            updatePillSelection(binding.btnFooterEnable, binding.btnFooterDisable)
        }

        binding.btnFooterDisable.addPressEffect {
            viewModel.setTableFooter(false)
            updatePillSelection(binding.btnFooterDisable, binding.btnFooterEnable)
        }

        // Set initial state from table data
        val tableData = viewModel.getSelectedTableData()
        if (tableData != null) {
            if (tableData.hasHeader) {
                updatePillSelection(binding.btnHeaderEnable, binding.btnHeaderDisable)
            } else {
                updatePillSelection(binding.btnHeaderDisable, binding.btnHeaderEnable)
            }
            if (tableData.hasFooter) {
                updatePillSelection(binding.btnFooterEnable, binding.btnFooterDisable)
            } else {
                updatePillSelection(binding.btnFooterDisable, binding.btnFooterEnable)
            }
        }
    }

    private fun updatePillSelection(selected: TextView, unselected: TextView) {
        val context = context ?: return
        val contrastColor = ContextCompat.getColor(context, R.color.contrast)

        selected.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        selected.setTextColor(Color.BLACK)
        selected.typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.bold)

        unselected.backgroundTintList = ColorStateList.valueOf(contrastColor)
        unselected.setTextColor(Color.BLACK)
        unselected.typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.medium)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = TableHeaderFooterFragment()
    }
}
