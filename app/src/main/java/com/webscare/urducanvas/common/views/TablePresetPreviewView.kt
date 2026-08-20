package com.webscare.urducanvas.common.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.webscare.urducanvas.common.canvas.enums.TableBorderMode
import com.webscare.urducanvas.data.repository.TablePresetStyle

class TablePresetPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var preset: TablePresetStyle? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val clipPath = Path()
    private val viewRect = RectF()

    fun setPreset(style: TablePresetStyle) {
        if (this.preset != style) {
            this.preset = style
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentPreset = preset ?: return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val density = resources.displayMetrics.density
        val strokeW = (currentPreset.borderWidth * density * 0.75f).coerceIn(1f, 3f * density)
        borderPaint.strokeWidth = strokeW
        borderPaint.color = currentPreset.borderColor

        val halfStroke = strokeW / 2f
        viewRect.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke)

        val radius = if (currentPreset.cornerRadius > 0f) {
            (currentPreset.cornerRadius * density * 0.4f).coerceIn(0f, 8f * density)
        } else {
            0f
        }

        val saveCount = canvas.save()
        if (radius > 0f) {
            clipPath.reset()
            clipPath.addRoundRect(viewRect, radius, radius, Path.Direction.CW)
            canvas.clipPath(clipPath)
        }

        val hasHeader = currentPreset.hasHeader
        val hasFooter = currentPreset.hasFooter
        val numBodyRows = 3
        val totalRows = (if (hasHeader) 1 else 0) + numBodyRows + (if (hasFooter) 1 else 0)
        val rowHeight = (viewRect.height()) / totalRows.toFloat()
        val numCols = 3
        val colWidth = (viewRect.width()) / numCols.toFloat()

        var currentRowTop = viewRect.top

        // 1. Draw Header
        if (hasHeader) {
            fillPaint.color = currentPreset.headerBgColor
            canvas.drawRect(viewRect.left, currentRowTop, viewRect.right, currentRowTop + rowHeight, fillPaint)
            currentRowTop += rowHeight
        }

        // 2. Draw Body Rows (alternating colors)
        for (i in 0 until numBodyRows) {
            val isEven = (i % 2 == 0)
            fillPaint.color = if (isEven) currentPreset.row1BgColor else currentPreset.row2BgColor
            canvas.drawRect(viewRect.left, currentRowTop, viewRect.right, currentRowTop + rowHeight, fillPaint)
            currentRowTop += rowHeight
        }

        // 3. Draw Footer
        if (hasFooter) {
            fillPaint.color = currentPreset.footerBgColor ?: currentPreset.headerBgColor
            canvas.drawRect(viewRect.left, currentRowTop, viewRect.right, currentRowTop + rowHeight, fillPaint)
        }

        // 4. Draw Borders based on borderMode
        when (currentPreset.borderMode) {
            TableBorderMode.ALL -> {
                // Horizontal lines
                for (r in 0..totalRows) {
                    val y = (viewRect.top + r * rowHeight).coerceAtMost(viewRect.bottom)
                    canvas.drawLine(viewRect.left, y, viewRect.right, y, borderPaint)
                }
                // Vertical lines
                for (c in 0..numCols) {
                    val x = (viewRect.left + c * colWidth).coerceAtMost(viewRect.right)
                    canvas.drawLine(x, viewRect.top, x, viewRect.bottom, borderPaint)
                }
                // Outer rect/roundRect
                if (radius > 0f) {
                    canvas.drawRoundRect(viewRect, radius, radius, borderPaint)
                } else {
                    canvas.drawRect(viewRect, borderPaint)
                }
            }
            TableBorderMode.OUTER -> {
                if (radius > 0f) {
                    canvas.drawRoundRect(viewRect, radius, radius, borderPaint)
                } else {
                    canvas.drawRect(viewRect, borderPaint)
                }
            }
            TableBorderMode.INNER -> {
                for (r in 1 until totalRows) {
                    val y = viewRect.top + r * rowHeight
                    canvas.drawLine(viewRect.left, y, viewRect.right, y, borderPaint)
                }
                for (c in 1 until numCols) {
                    val x = viewRect.left + c * colWidth
                    canvas.drawLine(x, viewRect.top, x, viewRect.bottom, borderPaint)
                }
            }
            TableBorderMode.HORIZONTAL -> {
                for (r in 0..totalRows) {
                    val y = (viewRect.top + r * rowHeight).coerceAtMost(viewRect.bottom)
                    canvas.drawLine(viewRect.left, y, viewRect.right, y, borderPaint)
                }
            }
            TableBorderMode.VERTICAL -> {
                for (c in 0..numCols) {
                    val x = (viewRect.left + c * colWidth).coerceAtMost(viewRect.right)
                    canvas.drawLine(x, viewRect.top, x, viewRect.bottom, borderPaint)
                }
            }
            TableBorderMode.NONE -> {
                // No borders
            }
        }

        canvas.restoreToCount(saveCount)
    }
}
