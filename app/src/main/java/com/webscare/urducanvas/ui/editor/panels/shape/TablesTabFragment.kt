package com.webscare.urducanvas.ui.editor.panels.shape

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel

import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.viewmodels.MainViewModel

class TablesTabFragment : Fragment() {

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private var selectedRows = 3
    private var selectedCols = 3

    private lateinit var tvGridReadout: TextView
    private lateinit var tvGridSubtitle: TextView
    private lateinit var btnInsertTable: ImageView
    private lateinit var gridContainer: GridLayout

    private val maxRows = 10
    private val maxCols = 10
    private val cellViews = Array(maxRows) { Array<View?>(maxCols) { null } }

    private var currentPanelOffset = 0f
    private var expandedCellHeightPx = 0f
    private var collapsedCellHeightPx = 0f
    private var cellWidthPx = 0f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tables_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvGridReadout = view.findViewById(R.id.tvGridReadout)
        tvGridSubtitle = view.findViewById(R.id.tvGridSubtitle)
        btnInsertTable = view.findViewById(R.id.btnInsertTable)
        gridContainer = view.findViewById(R.id.gridContainer)

        setupGrid()

        btnInsertTable.addPressEffect {
            val act = activity ?: return@addPressEffect
            viewModel.addTableElement(selectedRows, selectedCols, act)
            mainViewModel.collapsePanel()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGrid() {
        gridContainer.removeAllViews()
        gridContainer.rowCount = maxRows
        gridContainer.columnCount = maxCols

        val density = resources.displayMetrics.density

        gridContainer.post {
            val gridW = gridContainer.width
            if (gridW <= 0) return@post

            val marginPx = (1.2f * density).toInt().coerceAtLeast(1)
            val totalSpacingW = (maxCols * 2) * marginPx
            cellWidthPx = (gridW - totalSpacingW) / maxCols.toFloat()
            expandedCellHeightPx = cellWidthPx

            val parentView = view as? ViewGroup
            val parentH = parentView?.height ?: 0
            val headerH = (54 * density).toInt()
            val availableGridH = parentH - headerH - (8 * density).toInt()
            val totalSpacingH = (maxRows * 2) * marginPx
            collapsedCellHeightPx = if (availableGridH > 0) {
                ((availableGridH - totalSpacingH) / maxRows.toFloat()).coerceIn(6f * density, expandedCellHeightPx)
            } else {
                11f * density
            }

            gridContainer.removeAllViews()

            for (r in 0 until maxRows) {
                for (c in 0 until maxCols) {
                    val cell = View(requireContext())
                    val currentH = (collapsedCellHeightPx + (expandedCellHeightPx - collapsedCellHeightPx) * currentPanelOffset).toInt()

                    val params = GridLayout.LayoutParams().apply {
                        width = cellWidthPx.toInt()
                        height = currentH
                        rowSpec = GridLayout.spec(r)
                        columnSpec = GridLayout.spec(c)
                        setMargins(marginPx, marginPx, marginPx, marginPx)
                    }
                    cell.layoutParams = params
                    cellViews[r][c] = cell
                    gridContainer.addView(cell)
                }
            }

            updateSelection(selectedRows, selectedCols)
        }

        gridContainer.setOnTouchListener { v, event ->
            v.parent?.requestDisallowInterceptTouchEvent(true)

            val gridW = v.width.toFloat()
            val gridH = v.height.toFloat()

            if (gridW > 0f && gridH > 0f) {
                val clampX = event.x.coerceIn(0f, gridW - 1f)
                val clampY = event.y.coerceIn(0f, gridH - 1f)

                val col = ((clampX / gridW) * maxCols).toInt().coerceIn(0, maxCols - 1)
                val row = ((clampY / gridH) * maxRows).toInt().coerceIn(0, maxRows - 1)

                selectedRows = row + 1
                selectedCols = col + 1
                updateSelection(selectedRows, selectedCols)

                if (event.action == MotionEvent.ACTION_UP) {
                    v.performClick()
                }
            }
            true
        }
    }

    private fun updateCellHeights(offset: Float) {
        if (cellWidthPx <= 0f) return
        val clampedOffset = offset.coerceIn(0f, 1f)
        val targetH = (collapsedCellHeightPx + (expandedCellHeightPx - collapsedCellHeightPx) * clampedOffset).toInt()

        for (r in 0 until maxRows) {
            for (c in 0 until maxCols) {
                val cell = cellViews[r][c] ?: continue
                val lp = cell.layoutParams as? GridLayout.LayoutParams ?: continue
                if (lp.height != targetH) {
                    lp.height = targetH
                    cell.layoutParams = lp
                }
            }
        }
        gridContainer.requestLayout()
    }

    private fun updateSelection(rows: Int, cols: Int) {
        tvGridReadout.text = "$rows × $cols"

        val context = context ?: return
        val appColor = ContextCompat.getColor(context, R.color.appColor)
        val contrastColor = ContextCompat.getColor(context, R.color.contrast)
        val lightGray = ContextCompat.getColor(context, R.color.light_gray)

        val density = resources.displayMetrics.density
        val cornerRadius = 4f * density

        for (r in 0 until maxRows) {
            for (c in 0 until maxCols) {
                val cell = cellViews[r][c] ?: continue
                val isSelected = r < rows && c < cols

                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setCornerRadius(cornerRadius)
                    if (isSelected) {
                        setColor(contrastColor)
                        setStroke((1f * density).toInt(), appColor)
                    } else {
                        setColor(Color.WHITE)
                        setStroke((1f * density).toInt(), lightGray)
                    }
                }
                cell.background = bg
            }
        }
    }

    fun onPanelSlide(offset: Float) {
        currentPanelOffset = offset
        updateCellHeights(offset)
    }
}
