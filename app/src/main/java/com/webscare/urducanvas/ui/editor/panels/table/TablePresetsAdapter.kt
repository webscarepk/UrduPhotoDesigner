package com.webscare.urducanvas.ui.editor.panels.table

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.TablePreset
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.repository.TablePresetStyle
import com.webscare.urducanvas.databinding.ItemTablePresetBinding
import androidx.core.graphics.toColorInt

class TablePresetsAdapter(
    private val presets: List<TablePreset>,
    private val onPresetSelected: (TablePreset) -> Unit
) : RecyclerView.Adapter<TablePresetsAdapter.PresetViewHolder>() {

    private var selectedPreset: TablePreset? = null

    inner class PresetViewHolder(val binding: ItemTablePresetBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetViewHolder {
        val binding = ItemTablePresetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PresetViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PresetViewHolder, position: Int) {
        val item = presets[position]

        val context = holder.itemView.context
        val isSelected = selectedPreset == item
        val strokePx = (1.5f * context.resources.displayMetrics.density + 0.5f).toInt()
        holder.binding.cardPreset.strokeColor = ContextCompat.getColor(context, R.color.appColor)
        holder.binding.cardPreset.strokeWidth = if (isSelected) strokePx else 0

        val presetStyle = when (item) {
            TablePreset.PLAIN -> TablePresetStyle("plain", "Plain", "General", Color.WHITE, Color.BLACK, Color.WHITE, Color.WHITE, Color.BLACK, "#CCCCCC".toColorInt(), 1f, com.webscare.urducanvas.common.canvas.enums.TableBorderMode.ALL)
            TablePreset.BORDERED -> TablePresetStyle("bordered", "Bordered", "General", "#EFEFEF".toColorInt(), Color.BLACK, Color.WHITE, Color.WHITE, Color.BLACK, "#333333".toColorInt(), 2f, com.webscare.urducanvas.common.canvas.enums.TableBorderMode.ALL)
            TablePreset.STRIPED -> TablePresetStyle("striped", "Striped", "General", "#E4F3E9".toColorInt(), Color.BLACK, Color.WHITE, "#EFEFEF".toColorInt(), Color.BLACK, "#CCCCCC".toColorInt(), 1f, com.webscare.urducanvas.common.canvas.enums.TableBorderMode.ALL)
            TablePreset.HEADER_HIGHLIGHT -> TablePresetStyle("header_highlight", "Header", "General", "#005D28".toColorInt(), Color.WHITE, Color.WHITE, Color.WHITE, Color.BLACK, "#005D28".toColorInt(), 1.5f, com.webscare.urducanvas.common.canvas.enums.TableBorderMode.ALL)
            TablePreset.MINIMAL -> TablePresetStyle("minimal", "Minimal", "General", "#F8F9FA".toColorInt(), Color.BLACK, Color.WHITE, Color.WHITE, Color.BLACK, "#E0E0E0".toColorInt(), 1f, com.webscare.urducanvas.common.canvas.enums.TableBorderMode.HORIZONTAL)
            TablePreset.BOXED -> TablePresetStyle("boxed", "Boxed", "General", Color.WHITE, Color.BLACK, Color.WHITE, Color.WHITE, Color.BLACK, "#444444".toColorInt(), 2f, com.webscare.urducanvas.common.canvas.enums.TableBorderMode.OUTER)
        }
        holder.binding.tablePreviewView.setPreset(presetStyle)

        holder.itemView.addPressEffect {
            selectedPreset = item
            notifyDataSetChanged()
            onPresetSelected(item)
        }
    }

    override fun getItemCount(): Int = presets.size
}
