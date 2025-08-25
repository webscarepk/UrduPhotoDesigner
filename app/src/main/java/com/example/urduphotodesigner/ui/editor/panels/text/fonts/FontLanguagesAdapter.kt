package com.example.urduphotodesigner.ui.editor.panels.text.fonts

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.FontLanguages
import com.example.urduphotodesigner.databinding.LayoutTabsFontLanguagesItemBinding

class FontLanguagesAdapter(
    private val onLanguageExpanded: (language: String, collapse: Boolean) -> Unit,
    private val onCategorySelected: (language: String, category: String) -> Unit
) : RecyclerView.Adapter<FontLanguagesAdapter.FontViewHolder>() {

    private val fonts = mutableListOf<FontLanguages>()
    private val viewPool = RecyclerView.RecycledViewPool()

    fun submitList(newList: List<FontLanguages>) {
        fonts.clear()
        fonts.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FontViewHolder {
        val binding =
            LayoutTabsFontLanguagesItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FontViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FontViewHolder, position: Int) {
        holder.bind(fonts[position])
    }

    override fun getItemCount(): Int = fonts.size

    inner class FontViewHolder(private val binding: LayoutTabsFontLanguagesItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val catAdapter = FontCategoriesAdapter { cat ->
            val pos = adapterPosition
            if (pos == RecyclerView.NO_POSITION) return@FontCategoriesAdapter
            val lang = fonts[pos].name

            onCategorySelected(lang, cat)
        }

        init {
            binding.rvCategories.apply {
                adapter = catAdapter
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
                setRecycledViewPool(viewPool)
                itemAnimator = null
            }

            binding.tabTitle.addPressEffect {
                val pos = adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@addPressEffect
                val row = fonts[pos]
                val collapse = row.is_selected
                onLanguageExpanded(row.name, collapse)
            }
        }

        fun bind(font: FontLanguages) {
            binding.tabTitle.text = font.name
            if (font.name == "All" || font.name == "Imported") {
                binding.tabTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            } else {
                val arrowRes = if (font.is_selected) R.drawable.ic_dropup else R.drawable.ic_dropdown
                binding.tabTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, arrowRes, 0)
            }
            binding.rvCategories.visibility =
                if (font.is_selected && font.name != "All" && font.name != "Imported") View.VISIBLE else View.GONE
            catAdapter.submit(font.categories)
            // optional tint
            val color = if (font.is_selected) R.color.selection else android.R.color.transparent
            binding.tabTitle.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(binding.root.context, color)
            )
        }
    }
}
