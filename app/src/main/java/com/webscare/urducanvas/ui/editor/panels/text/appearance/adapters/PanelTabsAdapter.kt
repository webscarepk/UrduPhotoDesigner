package com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.databinding.LayoutTabsItemBinding
import com.webscare.urducanvas.common.utils.Utils.addPressEffect

class PanelTabsAdapter(
    private val onFontSelected: (com.webscare.urducanvas.data.model.PanelTabs) -> Unit
) : RecyclerView.Adapter<PanelTabsAdapter.FontViewHolder>() {

    private val fonts = mutableListOf<com.webscare.urducanvas.data.model.PanelTabs>()

    fun submitList(newList: List<com.webscare.urducanvas.data.model.PanelTabs>) {
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
        fun bind(font: com.webscare.urducanvas.data.model.PanelTabs) {

            if (font.is_selected) {
                binding.tabTitle.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        binding.root.context,
                        R.color.selection
                    )
                )
            } else {
                binding.tabTitle.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        binding.root.context,
                        android.R.color.transparent
                    )
                )
            }

            binding.tabTitle.text = font.tab_name

            binding.root.addPressEffect {
                onFontSelected.invoke(font)
            }
        }
    }
}
