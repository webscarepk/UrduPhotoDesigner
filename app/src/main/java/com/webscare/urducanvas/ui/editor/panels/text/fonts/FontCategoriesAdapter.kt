package com.webscare.urducanvas.ui.editor.panels.text.fonts

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.FontCategory
import com.webscare.urducanvas.databinding.LayoutFontCategoryBinding
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import java.util.Locale

class FontCategoriesAdapter(
    private val onCategoryClick: (category: String) -> Unit
) : RecyclerView.Adapter<FontCategoriesAdapter.VH>() {

    private val items = mutableListOf<com.webscare.urducanvas.data.model.FontCategory>()

    private var selectedCategory: String? = null

    fun submit(newItems: List<com.webscare.urducanvas.data.model.FontCategory>) {
        items.clear(); items.addAll(newItems); notifyDataSetChanged()
    }

    fun setSelectedCategory(category: String?) {
        selectedCategory = category
        notifyDataSetChanged()
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

            // Selected = green/appColor, unselected = gray
            val isSelected = row.name.equals(selectedCategory, ignoreCase = true) ||
                    (selectedCategory == null && row.name.equals("All", ignoreCase = true))
            binding.tabTitle.setTextColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if (isSelected) R.color.appColor else R.color.gray
                )
            )

            binding.tabTitle.addPressEffect {
                selectedCategory = row.name
                notifyDataSetChanged()
                onCategoryClick(row.name)
            }
        }
    }
}