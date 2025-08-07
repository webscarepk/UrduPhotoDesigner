package com.example.urduphotodesigner.ui.editor.panels.text.fonts

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.FontCategory
import com.example.urduphotodesigner.databinding.LayoutTabsItemBinding

class FontCategoryAdapter(
    private val onFontSelected: (FontCategory) -> Unit
) : RecyclerView.Adapter<FontCategoryAdapter.FontViewHolder>() {

    private val fonts = mutableListOf<FontCategory>()

    fun submitList(newList: List<FontCategory>) {
        fonts.clear()
        fonts.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FontViewHolder {
        val binding =
            LayoutTabsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FontViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FontViewHolder, position: Int) {
        holder.bind(fonts[position])
    }

    override fun getItemCount(): Int = fonts.size

    inner class FontViewHolder(private val binding: LayoutTabsItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(font: FontCategory) {

            if (font.is_selected) {
                binding.root.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        binding.root.context,
                        R.color.selection
                    )
                )
            } else {
                binding.root.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        binding.root.context,
                        android.R.color.transparent
                    )
                )
            }

            binding.tabTitle.text = font.font_category

            binding.root.addPressEffect {
                onFontSelected.invoke(font)
            }
        }
    }
}
