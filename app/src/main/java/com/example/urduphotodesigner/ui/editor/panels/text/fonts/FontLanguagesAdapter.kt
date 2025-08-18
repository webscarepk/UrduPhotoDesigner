package com.example.urduphotodesigner.ui.editor.panels.text.fonts

import android.content.res.ColorStateList
import android.view.LayoutInflater
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
            val lang = fonts[adapterPosition].name
            // mark selection locally
            val updated = fonts[adapterPosition].categories.map {
                it.copy(isSelected = it.name.equals(cat, true))
            }
            fonts[adapterPosition] =
                fonts[adapterPosition].copy(categories = updated)
            notifyItemChanged(adapterPosition)

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
                val toggled = row.copy(is_selected = !row.is_selected)
                fonts[pos] = toggled
                notifyItemChanged(pos)

                if (toggled.is_selected) onLanguageExpanded(toggled.name)
            }
        }

        fun bind(font: FontLanguages) {

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

            binding.tabTitle.text = font.name
        }
    }
}
