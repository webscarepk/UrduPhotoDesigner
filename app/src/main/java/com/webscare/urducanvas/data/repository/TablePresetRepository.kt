package com.webscare.urducanvas.data.repository

import android.graphics.Color
import com.webscare.urducanvas.common.canvas.enums.TableBorderMode
import com.webscare.urducanvas.common.canvas.model.TableData
import com.webscare.urducanvas.common.canvas.model.TableTextStyle

data class TablePresetStyle(
    val id: String,
    val name: String,
    val category: String,
    val headerBgColor: Int,
    val headerTextColor: Int,
    val row1BgColor: Int,
    val row2BgColor: Int,
    val bodyTextColor: Int,
    val borderColor: Int,
    val borderWidth: Float = 1f,
    val borderMode: TableBorderMode = TableBorderMode.ALL
)

object TablePresetRepository {

    val categories = listOf(
        "Minimal", "Modern", "Professional", "Pastel", "Dark",
        "Corporate", "Vibrant", "Gradient", "Accent", "Warm"
    )

    private val presetList: List<TablePresetStyle> by lazy {
        val list = mutableListOf<TablePresetStyle>()

        // 1. Minimal (10 presets)
        val minimalColors = listOf(
            Triple("#FFFFFF", "#F9FAFB", "#E5E7EB"),
            Triple("#FFFFFF", "#F3F4F6", "#D1D5DB"),
            Triple("#FFFFFF", "#F8FAFC", "#E2E8F0"),
            Triple("#FFFFFF", "#FAF5FF", "#E9D5FF"),
            Triple("#FFFFFF", "#F0FDF4", "#BBF7D0"),
            Triple("#FFFFFF", "#EFF6FF", "#BFDBFE"),
            Triple("#FFFFFF", "#FFF7ED", "#FED7AA"),
            Triple("#FFFFFF", "#FEF2F2", "#FCA5A5"),
            Triple("#FFFFFF", "#F5F3FF", "#DDD6FE"),
            Triple("#FFFFFF", "#ECFEFF", "#A5F3FC")
        )
        minimalColors.forEachIndexed { i, (c1, c2, b) ->
            list.add(TablePresetStyle("min_$i", "Minimal ${i+1}", "Minimal", Color.parseColor(c1), Color.parseColor("#1F2937"), Color.parseColor(c1), Color.parseColor(c2), Color.parseColor("#374151"), Color.parseColor(b), 1f))
        }

        // 2. Modern (10 presets)
        val modernColors = listOf(
            Triple("#005D28", "#E4F3E9", "#10B981"),
            Triple("#1E40AF", "#EFF6FF", "#3B82F6"),
            Triple("#6B21A8", "#F5F3FF", "#8B5CF6"),
            Triple("#991B1B", "#FEF2F2", "#EF4444"),
            Triple("#9A3412", "#FFF7ED", "#F97316"),
            Triple("#065F46", "#ECFDF5", "#059669"),
            Triple("#1E293B", "#F8FAFC", "#64748B"),
            Triple("#831843", "#FDF2F8", "#EC4899"),
            Triple("#3730A3", "#EEF2FF", "#6366F1"),
            Triple("#064E3B", "#F0FDF4", "#34D399")
        )
        modernColors.forEachIndexed { i, (hBg, r2, brd) ->
            list.add(TablePresetStyle("mod_$i", "Modern ${i+1}", "Modern", Color.parseColor(hBg), Color.WHITE, Color.WHITE, Color.parseColor(r2), Color.parseColor("#1F2937"), Color.parseColor(brd), 1.5f))
        }

        // 3. Professional (10 presets)
        val profColors = listOf(
            Triple("#1E3A8A", "#F0F9FF", "#93C5FD"),
            Triple("#0F766E", "#F0FDFA", "#99F6E4"),
            Triple("#374151", "#F9FAFB", "#D1D5DB"),
            Triple("#15803D", "#F0FDF4", "#86EFAC"),
            Triple("#B45309", "#FFFBEB", "#FDE68A"),
            Triple("#6D28D9", "#F5F3FF", "#C4B5FD"),
            Triple("#BE185D", "#FDF2F8", "#F9A8D4"),
            Triple("#4338CA", "#EEF2FF", "#A5B4FC"),
            Triple("#0369A1", "#F0F9FF", "#7DD3FC"),
            Triple("#047857", "#ECFDF5", "#6EE7B7")
        )
        profColors.forEachIndexed { i, (hBg, r2, brd) ->
            list.add(TablePresetStyle("prof_$i", "Pro ${i+1}", "Professional", Color.parseColor(hBg), Color.WHITE, Color.WHITE, Color.parseColor(r2), Color.parseColor("#111827"), Color.parseColor(brd), 1f))
        }

        // 4. Pastel (10 presets)
        val pastelColors = listOf(
            Triple("#FCE7F3", "#FDF2F8", "#F472B6"),
            Triple("#E0E7FF", "#EEF2FF", "#818CF8"),
            Triple("#D1FAE5", "#ECFDF5", "#34D399"),
            Triple("#FEF3C7", "#FFFBEB", "#FBBF24"),
            Triple("#E0F2FE", "#F0F9FF", "#38BDF8"),
            Triple("#F3E8FF", "#F5F3FF", "#A855F7"),
            Triple("#FFEDD5", "#FFF7ED", "#FB923C"),
            Triple("#FFE4E6", "#FFF1F2", "#FB7185"),
            Triple("#CCFBF1", "#F0FDFA", "#2DD4BF"),
            Triple("#E2E8F0", "#F8FAFC", "#94A3B8")
        )
        pastelColors.forEachIndexed { i, (hBg, r2, brd) ->
            list.add(TablePresetStyle("pas_$i", "Pastel ${i+1}", "Pastel", Color.parseColor(hBg), Color.parseColor("#1E293B"), Color.WHITE, Color.parseColor(r2), Color.parseColor("#334155"), Color.parseColor(brd), 1f))
        }

        // 5. Dark (10 presets)
        val darkColors = listOf(
            Triple("#18181B", "#27272A", "#3F3F46"),
            Triple("#0F172A", "#1E293B", "#334155"),
            Triple("#111827", "#1F2937", "#374151"),
            Triple("#1E1B4B", "#312E81", "#4338CA"),
            Triple("#064E3B", "#065F46", "#047857"),
            Triple("#4C1D95", "#5B21B6", "#6D28D9"),
            Triple("#701A75", "#86198F", "#A21CAF"),
            Triple("#831843", "#9D174D", "#BE185D"),
            Triple("#7C2D12", "#9A3412", "#C2410C"),
            Triple("#14532D", "#166534", "#15803D")
        )
        darkColors.forEachIndexed { i, (hBg, r2, brd) ->
            list.add(TablePresetStyle("dark_$i", "Dark ${i+1}", "Dark", Color.parseColor(hBg), Color.WHITE, Color.parseColor("#27272A"), Color.parseColor(r2), Color.WHITE, Color.parseColor(brd), 1f))
        }

        // 6. Corporate (10 presets)
        val corpColors = listOf(
            Triple("#1E3A8A", "#E0F2FE", "#0284C7"),
            Triple("#065F46", "#D1FAE5", "#059669"),
            Triple("#312E81", "#E0E7FF", "#4F46E5"),
            Triple("#831843", "#FCE7F3", "#DB2777"),
            Triple("#78350F", "#FEF3C7", "#D97706"),
            Triple("#1F2937", "#E5E7EB", "#4B5563"),
            Triple("#4C1D95", "#EDE9FE", "#7C3AED"),
            Triple("#134E4A", "#CCFBF1", "#0D9488"),
            Triple("#701A75", "#F5D0FE", "#C026D3"),
            Triple("#881337", "#FFE4E6", "#E11D48")
        )
        corpColors.forEachIndexed { i, (hBg, r2, brd) ->
            list.add(TablePresetStyle("corp_$i", "Corporate ${i+1}", "Corporate", Color.parseColor(hBg), Color.WHITE, Color.WHITE, Color.parseColor(r2), Color.parseColor("#1F2937"), Color.parseColor(brd), 1.5f))
        }

        // 7. Vibrant (10 presets)
        val vibColors = listOf(
            Triple("#EF4444", "#FEF2F2", "#FCA5A5"),
            Triple("#F97316", "#FFF7ED", "#FDBA74"),
            Triple("#F59E0B", "#FFFBEB", "#FDE68A"),
            Triple("#10B981", "#ECFDF5", "#6EE7B7"),
            Triple("#06B6D4", "#ECFEFF", "#67E8F9"),
            Triple("#3B82F6", "#EFF6FF", "#93C5FD"),
            Triple("#6366F1", "#EEF2FF", "#A5B4FC"),
            Triple("#8B5CF6", "#F5F3FF", "#C4B5FD"),
            Triple("#EC4899", "#FDF2F8", "#F9A8D4"),
            Triple("#14B8A6", "#F0FDFA", "#5EEAD4")
        )
        vibColors.forEachIndexed { i, (hBg, r2, brd) ->
            list.add(TablePresetStyle("vib_$i", "Vibrant ${i+1}", "Vibrant", Color.parseColor(hBg), Color.WHITE, Color.WHITE, Color.parseColor(r2), Color.parseColor("#111827"), Color.parseColor(brd), 1f))
        }

        // 8. Gradient (10 presets)
        val gradColors = listOf(
            Triple("#4F46E5", "#EEF2FF", "#6366F1"),
            Triple("#059669", "#ECFDF5", "#10B981"),
            Triple("#D97706", "#FFFBEB", "#F59E0B"),
            Triple("#DC2626", "#FEF2F2", "#EF4444"),
            Triple("#2563EB", "#EFF6FF", "#3B82F6"),
            Triple("#7C3AED", "#F5F3FF", "#8B5CF6"),
            Triple("#DB2777", "#FDF2F8", "#EC4899"),
            Triple("#0891B2", "#ECFEFF", "#06B6D4"),
            Triple("#0D9488", "#F0FDFA", "#14B8A6"),
            Triple("#4B5563", "#F9FAFB", "#6B7280")
        )
        gradColors.forEachIndexed { i, (hBg, r2, brd) ->
            list.add(TablePresetStyle("grad_$i", "Gradient ${i+1}", "Gradient", Color.parseColor(hBg), Color.WHITE, Color.WHITE, Color.parseColor(r2), Color.parseColor("#1E293B"), Color.parseColor(brd), 1.5f))
        }

        // 9. Accent (10 presets)
        val accColors = listOf(
            Triple("#005D28", "#E4F3E9", "#005D28"),
            Triple("#0284C7", "#E0F2FE", "#0284C7"),
            Triple("#7C3AED", "#EDE9FE", "#7C3AED"),
            Triple("#E11D48", "#FFE4E6", "#E11D48"),
            Triple("#D97706", "#FEF3C7", "#D97706"),
            Triple("#059669", "#D1FAE5", "#059669"),
            Triple("#4338CA", "#EEF2FF", "#4338CA"),
            Triple("#C026D3", "#F5D0FE", "#C026D3"),
            Triple("#0D9488", "#CCFBF1", "#0D9488"),
            Triple("#475569", "#F1F5F9", "#475569")
        )
        accColors.forEachIndexed { i, (hBg, r2, brd) ->
            list.add(TablePresetStyle("acc_$i", "Accent ${i+1}", "Accent", Color.parseColor(hBg), Color.WHITE, Color.WHITE, Color.parseColor(r2), Color.parseColor("#0F172A"), Color.parseColor(brd), 2f))
        }

        // 10. Warm (10 presets)
        val warmColors = listOf(
            Triple("#78350F", "#FEF3C7", "#D97706"),
            Triple("#9A3412", "#FFEDD5", "#F97316"),
            Triple("#991B1B", "#FEE2E2", "#EF4444"),
            Triple("#854D0E", "#FEF08A", "#EAB308"),
            Triple("#713F12", "#FEF9C3", "#CA8A04"),
            Triple("#881337", "#FFE4E6", "#F43F5E"),
            Triple("#701A75", "#F5D0FE", "#E879F9"),
            Triple("#581C87", "#F3E8FF", "#A855F7"),
            Triple("#3730A3", "#E0E7FF", "#6366F1"),
            Triple("#1E3A8A", "#DBEAFE", "#3B82F6")
        )
        warmColors.forEachIndexed { i, (hBg, r2, brd) ->
            list.add(TablePresetStyle("warm_$i", "Warm ${i+1}", "Warm", Color.parseColor(hBg), Color.WHITE, Color.WHITE, Color.parseColor(r2), Color.parseColor("#1F2937"), Color.parseColor(brd), 1.5f))
        }

        list
    }

    fun getPresetsByCategory(cat: String): List<TablePresetStyle> {
        return presetList.filter { it.category.equals(cat, ignoreCase = true) }
    }

    fun applyPresetToTable(preset: TablePresetStyle, tableData: TableData) {
        tableData.headerStyle.bgColor = preset.headerBgColor
        tableData.headerStyle.textColor = preset.headerTextColor
        tableData.base.bgColor = preset.row1BgColor
        tableData.base.textColor = preset.bodyTextColor
        tableData.borderColor = preset.borderColor
        tableData.borderWidth = preset.borderWidth
        tableData.borderMode = preset.borderMode

        // Clear rowStyles to apply clean alternating pattern for actual table rows
        tableData.rowStyles.clear()

        val startRow = if (tableData.hasHeader) 1 else 0
        for (r in startRow until tableData.rows) {
            val bodyRowIndex = r - startRow
            val rowStyle = tableData.rowStyles.getOrPut(r) { TableTextStyle() }
            if (bodyRowIndex % 2 == 1) {
                rowStyle.bgColor = preset.row2BgColor
                rowStyle.textColor = preset.bodyTextColor
            } else {
                rowStyle.bgColor = preset.row1BgColor
                rowStyle.textColor = preset.bodyTextColor
            }
        }

        // Clear individual cell background overrides so preset colors show cleanly
        tableData.cells.forEach { row ->
            row.forEach { cell ->
                cell.override?.bgColor = null
                cell.override?.textColor = null
            }
        }
    }
}
