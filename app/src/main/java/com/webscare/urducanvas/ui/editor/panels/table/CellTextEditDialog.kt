package com.webscare.urducanvas.ui.editor.panels.table

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.model.TableData
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.DialogCanvasCellEditBinding

class CellTextEditDialog : DialogFragment() {

    private var _binding: DialogCanvasCellEditBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    private var currentRow = 0
    private var currentCol = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar_MinWidth)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0f)
            setGravity(android.view.Gravity.BOTTOM)
            setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogCanvasCellEditBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentRow = viewModel.selectedTableRow.value
        currentCol = viewModel.selectedTableCol.value

        loadCellData(currentRow, currentCol)

        binding.btnDone.addPressEffect {
            saveCurrentCellText()
            dismiss()
        }

        binding.btnNextCell.addPressEffect {
            saveCurrentCellText()
            advanceCell(1)
        }

        binding.btnPrevCell.addPressEffect {
            saveCurrentCellText()
            advanceCell(-1)
        }
    }

    private fun getSelectedTableData(): TableData? {
        return viewModel.selectedElements.value?.firstOrNull { it.type == ElementType.TABLE }?.tableData
    }

    private fun loadCellData(row: Int, col: Int) {
        val tableData = getSelectedTableData() ?: return
        if (row !in 0 until tableData.rows || col !in 0 until tableData.cols) return

        currentRow = row
        currentCol = col
        viewModel.setTableScope(com.webscare.urducanvas.common.canvas.enums.TableScope.CELL, row, col)

        binding.tvCellPosition.text = "row ${row + 1} · col ${col + 1}"

        // Header col text reference
        val headerText = tableData.cells.getOrNull(0)?.getOrNull(col)?.text.orEmpty()
        binding.tvHeaderName.text = if (headerText.isNotEmpty()) "\"$headerText\"" else ""

        val currentText = tableData.cells.getOrNull(row)?.getOrNull(col)?.text.orEmpty()
        binding.editCellInput.setText(currentText)
        binding.editCellInput.setSelection(currentText.length)
    }

    private fun saveCurrentCellText() {
        val text = binding.editCellInput.text?.toString().orEmpty()
        viewModel.setTableCellText(currentRow, currentCol, text)
    }

    private fun advanceCell(delta: Int) {
        val tableData = getSelectedTableData() ?: return
        val totalCells = tableData.rows * tableData.cols
        if (totalCells <= 0) return

        val currentIndex = currentRow * tableData.cols + currentCol
        val nextIndex = (currentIndex + delta).coerceIn(0, totalCells - 1)

        val nextRow = nextIndex / tableData.cols
        val nextCol = nextIndex % tableData.cols

        loadCellData(nextRow, nextCol)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = CellTextEditDialog()
    }
}
