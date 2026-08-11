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
        holder.binding.cardPreset.strokeColor = context.getColor(R.color.appColor)
        holder.binding.cardPreset.strokeWidth = if (isSelected) strokePx else 0

        // Custom visual thumbnail styling for each preset
        when (item) {
            TablePreset.PLAIN -> {
                holder.binding.headerPreview.setBackgroundColor(Color.WHITE)
                holder.binding.row1Preview.setBackgroundColor(Color.WHITE)
                holder.binding.row2Preview.setBackgroundColor(Color.WHITE)
                setFrameBorder(holder.binding.previewFrame, "#CCCCCC".toColorInt(), 2)
            }
            TablePreset.BORDERED -> {
                holder.binding.headerPreview.setBackgroundColor("#EFEFEF".toColorInt())
                holder.binding.row1Preview.setBackgroundColor(Color.WHITE)
                holder.binding.row2Preview.setBackgroundColor(Color.WHITE)
                setFrameBorder(holder.binding.previewFrame, "#333333".toColorInt(), 4)
            }
            TablePreset.STRIPED -> {
                holder.binding.headerPreview.setBackgroundColor("#E4F3E9".toColorInt())
                holder.binding.row1Preview.setBackgroundColor(Color.WHITE)
                holder.binding.row2Preview.setBackgroundColor("#EFEFEF".toColorInt())
                setFrameBorder(holder.binding.previewFrame, "#CCCCCC".toColorInt(), 2)
            }
            TablePreset.HEADER_HIGHLIGHT -> {
                holder.binding.headerPreview.setBackgroundColor("#005D28".toColorInt())
                holder.binding.row1Preview.setBackgroundColor(Color.WHITE)
                holder.binding.row2Preview.setBackgroundColor(Color.WHITE)
                setFrameBorder(holder.binding.previewFrame, "#005D28".toColorInt(), 3)
            }
            TablePreset.MINIMAL -> {
                holder.binding.headerPreview.setBackgroundColor("#F8F9FA".toColorInt())
                holder.binding.row1Preview.setBackgroundColor(Color.WHITE)
                holder.binding.row2Preview.setBackgroundColor(Color.WHITE)
                setFrameBorder(holder.binding.previewFrame, "#E0E0E0".toColorInt(), 1)
            }
            TablePreset.BOXED -> {
                holder.binding.headerPreview.setBackgroundColor(Color.WHITE)
                holder.binding.row1Preview.setBackgroundColor(Color.WHITE)
                holder.binding.row2Preview.setBackgroundColor(Color.WHITE)
                setFrameBorder(holder.binding.previewFrame, "#444444".toColorInt(), 4)
            }
        }

        holder.itemView.addPressEffect {
            selectedPreset = item
            notifyDataSetChanged()
            onPresetSelected(item)
        }
    }

    private fun setFrameBorder(view: View, borderColor: Int, borderWidthDp: Int) {
        val density = view.resources.displayMetrics.density
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setCornerRadius(4f * density)
            setStroke((borderWidthDp * density).toInt(), borderColor)
        }
        view.background = drawable
    }

    override fun getItemCount(): Int = presets.size
}
