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
import com.example.urduphotodesigner.databinding.LayoutTabsItemBinding

class FontLanguagesAdapter(
    private val onLanguageExpanded: (language: String) -> Unit,
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
            LayoutTabsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FontViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FontViewHolder, position: Int) {
        holder.bind(fonts[position])
    }

    override fun getItemCount(): Int = fonts.size

    inner class FontViewHolder(private val binding: LayoutTabsItemBinding) :
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
                val row = fonts[pos].name

                onLanguageExpanded(row)
            }
        }

        fun bind(font: FontLanguages) {
            binding.tabTitle.text = font.name

            // show categories only when selected/expanded
            binding.rvCategories.visibility = if (font.is_selected) View.VISIBLE else View.GONE
            catAdapter.submit(font.categories)
            binding.root.alpha = if (font.is_selected) 1f else 0.8f

            // optional tint
//            val color = if (font.is_selected) R.color.selection else android.R.color.transparent
//            binding.root.backgroundTintList = ColorStateList.valueOf(
//                ContextCompat.getColor(binding.root.context, color)
//            )
        }
    }
}
