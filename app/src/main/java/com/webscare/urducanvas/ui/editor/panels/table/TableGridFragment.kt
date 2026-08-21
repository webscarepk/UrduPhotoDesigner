package com.webscare.urducanvas.ui.editor.panels.table

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.model.TableData
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.LayoutTableStructureOptionsBinding

class TableGridFragment : Fragment() {

    private var _binding: LayoutTableStructureOptionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = LayoutTableStructureOptionsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewGrid.visibility = View.VISIBLE

        binding.btnIncRows.addPressEffect {
            viewModel.updateSelectedTableData { data ->
                if (data.rows < 15) {
                    data.rows += 1
                    data.cells.add(MutableList(data.cols) { com.webscare.urducanvas.common.canvas.model.TableCell() })
                }
                binding.tvRowsCount.text = "${data.rows}"
            }
        }

        binding.btnDecRows.addPressEffect {
            viewModel.updateSelectedTableData { data ->
                if (data.rows > 1) {
                    data.rows -= 1
                    if (data.cells.size > data.rows) {
                        data.cells.removeAt(data.cells.size - 1)
                    }
                }
                binding.tvRowsCount.text = "${data.rows}"
            }
        }

        binding.btnIncCols.addPressEffect {
            viewModel.updateSelectedTableData { data ->
                if (data.cols < 15) {
                    data.cols += 1
                    data.cells.forEach { row ->
                        row.add(com.webscare.urducanvas.common.canvas.model.TableCell())
                    }
                }
                binding.tvColsCount.text = "${data.cols}"
            }
        }

        binding.btnDecCols.addPressEffect {
            viewModel.updateSelectedTableData { data ->
                if (data.cols > 1) {
                    data.cols -= 1
                    data.cells.forEach { row ->
                        if (row.size > data.cols) {
                            row.removeAt(row.size - 1)
                        }
                    }
                }
                binding.tvColsCount.text = "${data.cols}"
            }
        }

        binding.btnToggleHeaderRow.addPressEffect {
            val data = viewModel.getSelectedTableData() ?: return@addPressEffect
            viewModel.setTableHeader(!data.hasHeader)
            viewModel.getSelectedTableData()?.let { updateChipStates(it) }
        }

        binding.btnToggleFooterRow.addPressEffect {
            val data = viewModel.getSelectedTableData() ?: return@addPressEffect
            viewModel.setTableFooter(!data.hasFooter)
            viewModel.getSelectedTableData()?.let { updateChipStates(it) }
        }

        binding.btnToggleHeaderCol.addPressEffect {
            val data = viewModel.getSelectedTableData() ?: return@addPressEffect
            viewModel.setTableHeaderCol(!data.hasHeaderCol)
            viewModel.getSelectedTableData()?.let { updateChipStates(it) }
        }

        viewModel.getSelectedTableData()?.let { data ->
            binding.tvRowsCount.text = "${data.rows}"
            binding.tvColsCount.text = "${data.cols}"
            updateChipStates(data)
        }
    }

    private fun updateChipStates(data: TableData) {
        val ctx = context ?: return
        val appColor = ContextCompat.getColor(ctx, R.color.appColor)
        val contrast = ContextCompat.getColor(ctx, R.color.contrast)
        val white = ContextCompat.getColor(ctx, R.color.white)
        val black = ContextCompat.getColor(ctx, R.color.black)

        val strokePx = (1.5f * resources.displayMetrics.density + 0.5f).toInt()
        if (data.hasHeader) {
            binding.btnToggleHeaderRow.strokeColor = appColor
            binding.btnToggleHeaderRow.strokeWidth = strokePx
            binding.btnToggleHeaderRow.setCardBackgroundColor(white)
            binding.tvHeaderRowText.setTextColor(black)
        } else {
            binding.btnToggleHeaderRow.strokeWidth = 0
            binding.btnToggleHeaderRow.setCardBackgroundColor(contrast)
            binding.tvHeaderRowText.setTextColor(black)
        }

        if (data.hasFooter) {
            binding.btnToggleFooterRow.strokeColor = appColor
            binding.btnToggleFooterRow.strokeWidth = strokePx
            binding.btnToggleFooterRow.setCardBackgroundColor(white)
            binding.tvFooterRowText.setTextColor(black)
        } else {
            binding.btnToggleFooterRow.strokeWidth = 0
            binding.btnToggleFooterRow.setCardBackgroundColor(contrast)
            binding.tvFooterRowText.setTextColor(black)
        }

        if (data.hasHeaderCol) {
            binding.btnToggleHeaderCol.strokeColor = appColor
            binding.btnToggleHeaderCol.strokeWidth = strokePx
            binding.btnToggleHeaderCol.setCardBackgroundColor(white)
            binding.tvHeaderColText.setTextColor(black)
        } else {
            binding.btnToggleHeaderCol.strokeWidth = 0
            binding.btnToggleHeaderCol.setCardBackgroundColor(contrast)
            binding.tvHeaderColText.setTextColor(black)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = TableGridFragment()
    }
}
