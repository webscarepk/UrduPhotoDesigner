package com.webscare.urducanvas.common.canvas.cache

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import com.webscare.urducanvas.common.canvas.enums.TextAlignment
import com.webscare.urducanvas.common.canvas.enums.VAlign
import com.webscare.urducanvas.common.canvas.model.TableData
import com.webscare.urducanvas.common.canvas.model.TableTextStyle

data class CellLayout(
    val rect: RectF,
    val style: TableTextStyle,
    val lines: List<String>,
    val paint: TextPaint
)

class TableLayoutCache(
    val width: Float,
    val height: Float,
    val rows: Int,
    val cols: Int,
    val rowHeightsPx: List<Float>,
    val colWidthsPx: List<Float>,
    val cellLayouts: List<List<CellLayout>>
) {
    companion object {
        fun build(
            data: TableData,
            totalW: Float,
            totalH: Float,
            fontLookup: (String?) -> Typeface?
        ): TableLayoutCache {
            val rCount = data.rows.coerceAtLeast(1)
            val cCount = data.cols.coerceAtLeast(1)
            val cellW = totalW / cCount
            val cellH = totalH / rCount

            val rowHeights = List(rCount) { cellH }
            val colWidths = List(cCount) { cellW }

            val left0 = -totalW / 2f
            val top0 = -totalH / 2f

            val layouts = List(rCount) { r ->
                List(cCount) { c ->
                    val cellRect = RectF(
                        left0 + c * cellW,
                        top0 + r * cellH,
                        left0 + (c + 1) * cellW,
                        top0 + (r + 1) * cellH
                    )
                    val cellObj = if (r < data.cells.size && c < data.cells[r].size) data.cells[r][c] else null
                    val mergedStyle = mergeStyle(data, r, c, cellObj?.override)
                    val text = cellObj?.text ?: ""
                    val tf = fontLookup(mergedStyle.fontId)
                    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = mergedStyle.textColor ?: android.graphics.Color.BLACK
                        textSize = mergedStyle.textSize ?: 16f
                        if (tf != null) typeface = tf
                    }
                    val lines = if (text.isNotBlank()) text.split("\n") else emptyList()
                    CellLayout(cellRect, mergedStyle, lines, paint)
                }
            }

            return TableLayoutCache(totalW, totalH, rCount, cCount, rowHeights, colWidths, layouts)
        }

        private fun mergeStyle(data: TableData, r: Int, c: Int, override: TableTextStyle?): TableTextStyle {
            val res = TableTextStyle()
            fun applyStyle(s: TableTextStyle?) {
                if (s == null) return
                s.bgColor?.let { res.bgColor = it }
                s.bgGradient?.let { res.bgGradient = it }
                s.textColor?.let { res.textColor = it }
                s.textGradient?.let { res.textGradient = it }
                s.textSize?.let { res.textSize = it }
                s.fontId?.let { res.fontId = it }
                s.isBold?.let { res.isBold = it }
                s.isItalic?.let { res.isItalic = it }
                s.isUnderline?.let { res.isUnderline = it }
                s.hAlign?.let { res.hAlign = it }
                s.vAlign?.let { res.vAlign = it }
                s.lineSpacing?.let { res.lineSpacing = it }
            }
            applyStyle(data.base)
            if (r == 0) applyStyle(data.headerStyle)
            if (r == data.rows - 1 && data.rows > 1) applyStyle(data.footerStyle)
            if (c == 0) applyStyle(data.headerColStyle)
            applyStyle(data.rowStyles[r])
            applyStyle(data.colStyles[c])
            applyStyle(override)
            return res
        }
    }
}
