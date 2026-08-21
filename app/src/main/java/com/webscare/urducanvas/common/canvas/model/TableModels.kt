package com.webscare.urducanvas.common.canvas.model

import com.google.gson.annotations.SerializedName
import com.webscare.urducanvas.common.canvas.enums.TableBorderMode
import com.webscare.urducanvas.common.canvas.enums.TableScope
import com.webscare.urducanvas.common.canvas.enums.TextAlignment
import com.webscare.urducanvas.common.canvas.enums.VAlign

data class TableTextStyle(
    @SerializedName("bgColor") var bgColor: Int? = null,
    @SerializedName("bgGradient") var bgGradient: GradientItem? = null,
    @SerializedName("textColor") var textColor: Int? = null,
    @SerializedName("textGradient") var textGradient: GradientItem? = null,
    @SerializedName("textSize") var textSize: Float? = null,
    @SerializedName("fontId") var fontId: String? = null,
    @SerializedName("isBold") var isBold: Boolean? = null,
    @SerializedName("isItalic") var isItalic: Boolean? = null,
    @SerializedName("isUnderline") var isUnderline: Boolean? = null,
    @SerializedName("hAlign") var hAlign: TextAlignment? = null,
    @SerializedName("vAlign") var vAlign: VAlign? = null,
    @SerializedName("lineSpacing") var lineSpacing: Float? = null,
    @SerializedName("letterSpacing") var letterSpacing: Float? = null
) {
    fun deepCopy(): TableTextStyle = copy(
        bgGradient = bgGradient?.copy(),
        textGradient = textGradient?.copy()
    )
}

data class TableCell(
    @SerializedName("text") var text: String = "",
    @SerializedName("override") var override: TableTextStyle? = null
) {
    fun deepCopy(): TableCell = TableCell(
        text = text,
        override = override?.deepCopy()
    )
}

data class TableData(
    @SerializedName("rows") var rows: Int = 3,
    @SerializedName("cols") var cols: Int = 3,
    @SerializedName("borderWidth") var borderWidth: Float = 2f,
    @SerializedName("borderColor") var borderColor: Int = android.graphics.Color.BLACK,
    @SerializedName("borderGradient") var borderGradient: GradientItem? = null,
    @SerializedName("cornerRadius") var cornerRadius: Float = 0f,
    @SerializedName("borderMode") var borderMode: TableBorderMode = TableBorderMode.ALL,
    @SerializedName("paddingH") var paddingH: Float = 8f,
    @SerializedName("paddingV") var paddingV: Float = 8f,
    @SerializedName("hasHeader") var hasHeader: Boolean = true,
    @SerializedName("hasFooter") var hasFooter: Boolean = false,
    @SerializedName("hasHeaderCol") var hasHeaderCol: Boolean = false,
    @SerializedName("isRTL") var isRTL: Boolean = true,
    @SerializedName("contentWrap") var contentWrap: Boolean = false,
    @SerializedName("base") var base: TableTextStyle = TableTextStyle(),
    @SerializedName("headerStyle") var headerStyle: TableTextStyle = TableTextStyle(),
    @SerializedName("footerStyle") var footerStyle: TableTextStyle = TableTextStyle(),
    @SerializedName("headerColStyle") var headerColStyle: TableTextStyle = TableTextStyle(),
    @SerializedName("rowStyles") var rowStyles: MutableMap<Int, TableTextStyle> = mutableMapOf(),
    @SerializedName("colStyles") var colStyles: MutableMap<Int, TableTextStyle> = mutableMapOf(),
    @SerializedName("colWidthRatios") var colWidthRatios: MutableList<Float>? = null,
    @SerializedName("rowHeightRatios") var rowHeightRatios: MutableList<Float>? = null,
    @SerializedName("cells") var cells: MutableList<MutableList<TableCell>> = mutableListOf(),
    @SerializedName("selectedCells") var selectedCells: MutableSet<Pair<Int, Int>> = mutableSetOf()
) {
    fun deepCopy(): TableData {
        return TableData(
            rows = rows,
            cols = cols,
            borderWidth = borderWidth,
            borderColor = borderColor,
            borderGradient = borderGradient?.copy(),
            cornerRadius = cornerRadius,
            borderMode = borderMode,
            paddingH = paddingH,
            paddingV = paddingV,
            hasHeader = hasHeader,
            hasFooter = hasFooter,
            hasHeaderCol = hasHeaderCol,
            isRTL = isRTL,
            contentWrap = contentWrap,
            base = base.deepCopy(),
            headerStyle = headerStyle.deepCopy(),
            footerStyle = footerStyle.deepCopy(),
            headerColStyle = headerColStyle.deepCopy(),
            rowStyles = rowStyles.mapValues { it.value.deepCopy() }.toMutableMap(),
            colStyles = colStyles.mapValues { it.value.deepCopy() }.toMutableMap(),
            colWidthRatios = colWidthRatios?.toMutableList(),
            rowHeightRatios = rowHeightRatios?.toMutableList(),
            cells = cells.map { row -> row.map { cell -> cell.deepCopy() }.toMutableList() }.toMutableList(),
            selectedCells = selectedCells.toMutableSet()
        )
    }

    fun allFontIds(): List<String> {
        val list = mutableListOf<String>()
        base.fontId?.let { list.add(it) }
        headerStyle.fontId?.let { list.add(it) }
        footerStyle.fontId?.let { list.add(it) }
        headerColStyle.fontId?.let { list.add(it) }
        rowStyles.values.forEach { s -> s.fontId?.let { list.add(it) } }
        colStyles.values.forEach { s -> s.fontId?.let { list.add(it) } }
        cells.forEach { row ->
            row.forEach { cell ->
                cell.override?.fontId?.let { list.add(it) }
            }
        }
        return list.distinct()
    }

    fun applyFontToScope(fontId: String, scope: TableScope, row: Int = 0, col: Int = 0) {
        when (scope) {
            TableScope.WHOLE_TABLE -> base.fontId = fontId
            TableScope.HEADER_ROW -> headerStyle.fontId = fontId
            TableScope.FOOTER_ROW -> footerStyle.fontId = fontId
            TableScope.HEADER_COL -> headerColStyle.fontId = fontId
            TableScope.ROW -> rowStyles.getOrPut(row) { TableTextStyle() }.fontId = fontId
            TableScope.COLUMN -> colStyles.getOrPut(col) { TableTextStyle() }.fontId = fontId
            TableScope.CELL -> {
                if (row in 0 until rows && col in 0 until cols) {
                    val cell = cells[row][col]
                    val cellOverride = cell.override ?: TableTextStyle().also { cell.override = it }
                    cellOverride.fontId = fontId
                }
            }
        }
    }

    companion object {
        fun createDefault(r: Int = 3, c: Int = 3, withHeader: Boolean = true): TableData {
            val validR = r.coerceAtLeast(1)
            val validC = c.coerceAtLeast(1)
            val data = TableData(rows = validR, cols = validC, hasHeader = withHeader, paddingH = 10f, paddingV = 8f)
            data.base = TableTextStyle(hAlign = TextAlignment.RIGHT, vAlign = VAlign.MIDDLE)
            data.cells = MutableList(validR) { MutableList(validC) { TableCell() } }
            if (withHeader && validR > 0) {
                data.headerStyle = TableTextStyle(
                    isBold = true,
                    hAlign = TextAlignment.RIGHT,
                    vAlign = VAlign.MIDDLE,
                    bgColor = android.graphics.Color.parseColor("#E4F3E9")
                )
                val urduDigits = listOf("۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹", "۱۰")
                for (colIdx in 0 until validC) {
                    val num = if (colIdx < urduDigits.size) urduDigits[colIdx] else "${colIdx + 1}"
                    data.cells[0][colIdx].text = "عنوان $num"
                }
            }
            return data
        }
    }
}
