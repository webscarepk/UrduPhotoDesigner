package com.webscare.urducanvas.ui.editor.panels.text.fonts

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.FontLanguages
import com.webscare.urducanvas.databinding.LayoutTabsFontLanguagesItemBinding

class FontLanguagesAdapter(
    private val onLanguageExpanded: (language: String, collapse: Boolean) -> Unit,
    private val onCategorySelected: (language: String, category: String) -> Unit,
) : RecyclerView.Adapter<FontLanguagesAdapter.FontViewHolder>() {

    private val fonts = mutableListOf<FontLanguages>()
    private val viewPool = RecyclerView.RecycledViewPool()

    /** When true the left-panel behaves as a single-select radio list (expanded panel state). */
    var isExpandedMode: Boolean = false

    fun submitList(newList: List<FontLanguages>) {
        fonts.clear()
        fonts.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FontViewHolder {
        val binding = LayoutTabsFontLanguagesItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return FontViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FontViewHolder, position: Int) = holder.bind(fonts[position])

    override fun getItemCount() = fonts.size

    inner class FontViewHolder(private val binding: LayoutTabsFontLanguagesItemBinding) : RecyclerView.ViewHolder(binding.root) {

        private val catAdapter: FontCategoriesAdapter = FontCategoriesAdapter { cat ->
            val pos = adapterPosition
            if (pos == RecyclerView.NO_POSITION) return@FontCategoriesAdapter
            // setSelectedCategory called after init — no recursive reference
            (binding.rvCategories.adapter as? FontCategoriesAdapter)?.setSelectedCategory(cat)
            onCategorySelected(fonts[pos].name, cat)
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

                if (isExpandedMode) {
                    // Radio behaviour: select this one, deselect all others
                    if (row.is_selected) return@addPressEffect // already selected — no-op
                    fonts.forEachIndexed { i, lang ->
                        val shouldSelect = i == pos
                        if (lang.is_selected != shouldSelect) {
                            lang.is_selected = shouldSelect
                            notifyItemChanged(i)
                        }
                    }
                    onLanguageExpanded(row.name, false) // false = "expand/select"
                } else {
                    // Original toggle behaviour (collapsed panel)
                    // Reset category selection when language row toggles
                    if (row.is_selected) {
                        // collapsing — clear selected category
                        (binding.rvCategories.adapter as? FontCategoriesAdapter)
                            ?.setSelectedCategory(null)
                    }
                    row.is_selected = !row.is_selected
                    notifyItemChanged(pos)
                    onLanguageExpanded(row.name, !row.is_selected)
                }
            }
        }

        fun bind(font: FontLanguages) {
            binding.tabTitle.text = font.name

            // Arrow drawable — hidden for All / Imported and in expanded mode
            when {
                font.name == "All" || font.name == "Imported" || isExpandedMode -> {
                    binding.tabTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                }
                else -> {
                    val arrowRes = if (font.is_selected) R.drawable.ic_dropup else R.drawable.ic_dropdown
                    binding.tabTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, arrowRes, 0)
                }
            }

            // Category list visibility
            val showCategories = font.is_selected &&
                font.name != "All" &&
                font.name != "Imported"
            binding.rvCategories.visibility = if (showCategories) View.VISIBLE else View.GONE
            catAdapter.submit(font.categories)

            // Background tint (collapsed) / left-border accent (expanded)
            if (isExpandedMode) {
                // Remove background fill; left border is handled by the layout drawable
                binding.tabTitle.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(binding.root.context, android.R.color.transparent),
                )
                // Swap a left-border indicator drawable on the root
                binding.tabRoot.setBackgroundResource(
                    if (font.is_selected) {
                        R.drawable.button_bg_small
                    } else {
                        android.R.color.transparent
                    },
                )
            } else {
                val color = if (font.is_selected) R.color.selection else android.R.color.transparent
                binding.tabTitle.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(binding.root.context, color),
                )
                binding.tabRoot.setBackgroundResource(android.R.color.transparent)
            }
        }
    }
}
