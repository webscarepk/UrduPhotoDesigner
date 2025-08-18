package com.example.urduphotodesigner.ui.editor.panels.text.fonts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.FontCategory
import com.example.urduphotodesigner.databinding.LayoutFontCategoryBinding

class FontCategoriesAdapter(
    private val onCategoryClick: (category: String) -> Unit
) : RecyclerView.Adapter<FontCategoriesAdapter.VH>() {

    private val items = mutableListOf<FontCategory>()

    fun submit(newItems: List<FontCategory>) {
        items.clear(); items.addAll(newItems); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding =
            LayoutFontCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class VH(private val binding: LayoutFontCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: FontCategory) {
            binding.tabTitle.text = row.name
            binding.tabTitle.alpha = if (row.isSelected) 1f else 0.85f

            binding.tabTitle.addPressEffect { onCategoryClick(row.name) }
        }
    }
}
