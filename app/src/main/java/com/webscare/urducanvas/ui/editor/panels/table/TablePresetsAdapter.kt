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
        holder.binding.tvPresetTitle.text = item.title

        val context = holder.itemView.context
        val isSelected = selectedPreset == item
        val appColor = ContextCompat.getColor(context, R.color.appColor)

        holder.binding.cardPreset.strokeColor = appColor
        holder.binding.cardPreset.strokeWidth = if (isSelected) 4 else 0

        // Custom visual thumbnail styling for each preset
        when (item) {
            TablePreset.PLAIN -> {
                holder.binding.headerPreview.setBackgroundColor(Color.WHITE)
                holder.binding.row1Preview.setBackgroundColor(Color.WHITE)
                holder.binding.row2Preview.setBackgroundColor(Color.WHITE)
                setFrameBorder(holder.binding.previewFrame, Color.parseColor("#CCCCCC"), 2)
            }
            TablePreset.BORDERED -> {
                holder.binding.headerPreview.setBackgroundColor(Color.parseColor("#EFEFEF"))
                holder.binding.row1Preview.setBackgroundColor(Color.WHITE)
                holder.binding.row2Preview.setBackgroundColor(Color.WHITE)
                setFrameBorder(holder.binding.previewFrame, Color.parseColor("#333333"), 4)
            }
            TablePreset.STRIPED -> {
                holder.binding.headerPreview.setBackgroundColor(Color.parseColor("#E4F3E9"))
                holder.binding.row1Preview.setBackgroundColor(Color.WHITE)
                holder.binding.row2Preview.setBackgroundColor(Color.parseColor("#EFEFEF"))
                setFrameBorder(holder.binding.previewFrame, Color.parseColor("#CCCCCC"), 2)
            }
            TablePreset.HEADER_HIGHLIGHT -> {
                holder.binding.headerPreview.setBackgroundColor(Color.parseColor("#005D28"))
                holder.binding.row1Preview.setBackgroundColor(Color.WHITE)
                holder.binding.row2Preview.setBackgroundColor(Color.WHITE)
                setFrameBorder(holder.binding.previewFrame, Color.parseColor("#005D28"), 3)
            }
            TablePreset.MINIMAL -> {
                holder.binding.headerPreview.setBackgroundColor(Color.parseColor("#F8F9FA"))
                holder.binding.row1Preview.setBackgroundColor(Color.WHITE)
                holder.binding.row2Preview.setBackgroundColor(Color.WHITE)
                setFrameBorder(holder.binding.previewFrame, Color.parseColor("#E0E0E0"), 1)
            }
            TablePreset.BOXED -> {
                holder.binding.headerPreview.setBackgroundColor(Color.WHITE)
                holder.binding.row1Preview.setBackgroundColor(Color.WHITE)
                holder.binding.row2Preview.setBackgroundColor(Color.WHITE)
                setFrameBorder(holder.binding.previewFrame, Color.parseColor("#444444"), 4)
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
