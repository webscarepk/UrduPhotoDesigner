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
import com.webscare.urducanvas.common.canvas.enums.TableBorderMode

class TableStyleOptionsFragment : Fragment() {

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

        val borderLabel = TextView(context).apply {
            text = "Border Mode"
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.black))
        }
        root.addView(borderLabel)

        val borderContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (6 * density).toInt(), 0, (12 * density).toInt())
        }

        val btnAllBorders = createActionButton("All") {
            viewModel.updateSelectedTableData { data ->
                data.borderMode = TableBorderMode.ALL
            }
        }
        val btnOuterOnly = createActionButton("Outer") {
            viewModel.updateSelectedTableData { data ->
                data.borderMode = TableBorderMode.OUTER
            }
        }
        val btnInnerOnly = createActionButton("Inner") {
            viewModel.updateSelectedTableData { data ->
                data.borderMode = TableBorderMode.INNER
            }
        }
        val btnNoBorders = createActionButton("None") {
            viewModel.updateSelectedTableData { data ->
                data.borderMode = TableBorderMode.NONE
            }
        }

        borderContainer.addView(btnAllBorders)
        borderContainer.addView(btnOuterOnly)
        borderContainer.addView(btnInnerOnly)
        borderContainer.addView(btnNoBorders)
        root.addView(borderContainer)

        return root
    }

    private fun createActionButton(label: String, onClick: () -> Unit): View {
        val density = resources.displayMetrics.density
        return TextView(requireContext()).apply {
            text = label
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.black))
            gravity = Gravity.CENTER
            setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
            background = ContextCompat.getDrawable(context, R.drawable.button_bg_small)
            backgroundTintList = ContextCompat.getColorStateList(context, R.color.contrast)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins((2 * density).toInt(), 0, (2 * density).toInt(), 0)
            }
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }
}
