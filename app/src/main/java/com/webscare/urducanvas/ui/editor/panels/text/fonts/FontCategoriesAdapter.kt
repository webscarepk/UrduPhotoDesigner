package com.webscare.urducanvas.ui.editor.panels.text.fonts

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.FontCategory
import com.example.urduphotodesigner.databinding.LayoutFontCategoryBinding
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import java.util.Locale

class FontCategoriesAdapter(
    private val onCategoryClick: (category: String) -> Unit
) : RecyclerView.Adapter<FontCategoriesAdapter.VH>() {

    private val items = mutableListOf<com.webscare.urducanvas.data.model.FontCategory>()

    fun submit(newItems: List<com.webscare.urducanvas.data.model.FontCategory>) {
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
        fun bind(row: com.webscare.urducanvas.data.model.FontCategory) {
            val displayName = row.name.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
            binding.tabTitle.text = displayName
            if (row.isSelected) {
                binding.tabTitle.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.black)
                )
            } else {
                binding.tabTitle.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.gray)
                )
            }
            binding.tabTitle.addPressEffect { onCategoryClick(row.name) }
        }
    }
}
