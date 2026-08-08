package com.webscare.urducanvas.ui.editor.panels.table

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel

class TableLayoutOptionsFragment : Fragment() {

    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val density = resources.displayMetrics.density

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Section 1: Rows
        val rowLabel = TextView(context).apply {
            text = "Rows"
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.black))
        }
        root.addView(rowLabel)

        val rowContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (6 * density).toInt(), 0, (12 * density).toInt())
        }

        val btnAddRowAbove = createActionButton("+ Row Above") {
            viewModel.updateSelectedTableData { data ->
                if (data.rows < 15) {
                    data.rows += 1
                }
            }
        }
        val btnAddRowBelow = createActionButton("+ Row Below") {
            viewModel.updateSelectedTableData { data ->
                if (data.rows < 15) {
                    data.rows += 1
                }
            }
        }
        val btnDeleteRow = createActionButton("- Delete Row") {
            viewModel.updateSelectedTableData { data ->
                if (data.rows > 1) {
                    data.rows -= 1
                }
            }
        }

        rowContainer.addView(btnAddRowAbove)
        rowContainer.addView(btnAddRowBelow)
        rowContainer.addView(btnDeleteRow)
        root.addView(rowContainer)

        // Section 2: Columns
        val colLabel = TextView(context).apply {
            text = "Columns"
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.black))
        }
        root.addView(colLabel)

        val colContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (6 * density).toInt(), 0, (12 * density).toInt())
        }

        val btnAddColLeft = createActionButton("+ Col Left") {
            viewModel.updateSelectedTableData { data ->
                if (data.cols < 15) {
                    data.cols += 1
                }
            }
        }
        val btnAddColRight = createActionButton("+ Col Right") {
            viewModel.updateSelectedTableData { data ->
                if (data.cols < 15) {
                    data.cols += 1
                }
            }
        }
        val btnDeleteCol = createActionButton("- Delete Col") {
            viewModel.updateSelectedTableData { data ->
                if (data.cols > 1) {
                    data.cols -= 1
                }
            }
        }

        colContainer.addView(btnAddColLeft)
        colContainer.addView(btnAddColRight)
        colContainer.addView(btnDeleteCol)
        root.addView(colContainer)

        return root
    }

    private fun createActionButton(label: String, onClick: () -> Unit): View {
        val density = resources.displayMetrics.density
        return TextView(requireContext()).apply {
            text = label
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.black))
            gravity = Gravity.CENTER
            setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
            background = ContextCompat.getDrawable(context, R.drawable.button_bg_small)
            backgroundTintList = ContextCompat.getColorStateList(context, R.color.contrast)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins((3 * density).toInt(), 0, (3 * density).toInt(), 0)
            }
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }
}
