package com.webscare.urducanvas.ui.editor.panels.table

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.repository.TablePresetStyle
import com.webscare.urducanvas.databinding.ItemTablePresetBinding

class TablePresetsGridAdapter(
    private val onPresetSelected: (TablePresetStyle) -> Unit
) : RecyclerView.Adapter<TablePresetsGridAdapter.VH>() {

    private val items = mutableListOf<TablePresetStyle>()
    private var selectedPresetId: String? = null

    fun submitList(newList: List<TablePresetStyle>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTablePresetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class VH(private val binding: ItemTablePresetBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(preset: TablePresetStyle) {
            binding.headerPreview.setBackgroundColor(preset.headerBgColor)
            binding.row1Preview.setBackgroundColor(preset.row1BgColor)
            binding.row2Preview.setBackgroundColor(preset.row2BgColor)
            binding.previewFrame.setBackgroundColor(preset.borderColor)

            val isSelected = preset.id == selectedPresetId
            binding.cardPreset.strokeWidth = if (isSelected) 4 else 0

            binding.cardPreset.addPressEffect {
                selectedPresetId = preset.id
                notifyDataSetChanged()
                onPresetSelected(preset)
            }
        }
    }
}
