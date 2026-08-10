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

        binding.btnToggleHeaderRow.setCardBackgroundColor(if (data.hasHeader) appColor else contrast)
        binding.tvHeaderRowText.setTextColor(if (data.hasHeader) white else black)

        binding.btnToggleFooterRow.setCardBackgroundColor(if (data.hasFooter) appColor else contrast)
        binding.tvFooterRowText.setTextColor(if (data.hasFooter) white else black)

        binding.btnToggleHeaderCol.setCardBackgroundColor(if (data.hasHeaderCol) appColor else contrast)
        binding.tvHeaderColText.setTextColor(if (data.hasHeaderCol) white else black)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = TableGridFragment()
    }
}
