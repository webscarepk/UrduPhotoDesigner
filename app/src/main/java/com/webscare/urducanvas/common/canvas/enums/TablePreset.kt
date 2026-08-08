package com.webscare.urducanvas.common.canvas.enums

import android.graphics.Color
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.model.TableData
import com.webscare.urducanvas.common.canvas.model.TableTextStyle

enum class TablePreset(
    val title: String,
    val iconResId: Int
) {
    PLAIN("Plain", R.drawable.ic_grid),
    BORDERED("Bordered", R.drawable.ic_grid),
    STRIPED("Striped", R.drawable.ic_grid),
    HEADER_HIGHLIGHT("Header", R.drawable.ic_grid),
    MINIMAL("Minimal", R.drawable.ic_grid),
    BOXED("Boxed", R.drawable.ic_grid);

    /**
     * Merges preset style properties onto existing TableData.
     * Text content, row/col counts, and cell data remain untouched.
     */
    fun applyTo(data: TableData) {
        when (this) {
            PLAIN -> {
                data.borderMode = TableBorderMode.ALL
                data.borderWidth = 1.5f
                data.borderColor = Color.parseColor("#CCCCCC")
                data.hasHeader = false
            }
            BORDERED -> {
                data.borderMode = TableBorderMode.ALL
                data.borderWidth = 2.5f
                data.borderColor = Color.parseColor("#333333")
            }
            STRIPED -> {
                data.borderMode = TableBorderMode.ALL
                data.borderWidth = 1f
                data.borderColor = Color.parseColor("#E0E0E0")
                data.hasHeader = true
                data.headerStyle = TableTextStyle(
                    isBold = true,
                    bgColor = Color.parseColor("#E4F3E9")
                )
                // Alternating row background fills
                for (r in 0 until data.rows) {
                    if (r > 0) {
                        val rowBg = if (r % 2 == 1) Color.WHITE else Color.parseColor("#F4F6F8")
                        data.rowStyles.getOrPut(r) { TableTextStyle() }.bgColor = rowBg
                    }
                }
            }
            HEADER_HIGHLIGHT -> {
                data.borderMode = TableBorderMode.ALL
                data.borderWidth = 1.5f
                data.borderColor = Color.parseColor("#005D28")
                data.hasHeader = true
                data.headerStyle = TableTextStyle(
                    isBold = true,
                    textColor = Color.WHITE,
                    bgColor = Color.parseColor("#005D28")
                )
            }
            MINIMAL -> {
                data.borderMode = TableBorderMode.HORIZONTAL
                data.borderWidth = 1f
                data.borderColor = Color.parseColor("#E0E0E0")
                data.hasHeader = true
                data.headerStyle = TableTextStyle(
                    isBold = true,
                    bgColor = Color.parseColor("#F8F9FA")
                )
            }
            BOXED -> {
                data.borderMode = TableBorderMode.OUTER
                data.borderWidth = 2f
                data.borderColor = Color.parseColor("#444444")
            }
        }
    }
}
