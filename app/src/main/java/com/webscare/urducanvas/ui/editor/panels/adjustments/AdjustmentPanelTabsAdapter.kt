package com.webscare.urducanvas.ui.editor.panels.adjustments

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.AdjustmentPanelTabs
import com.webscare.urducanvas.databinding.LayoutTabsItemBinding

class AdjustmentPanelTabsAdapter(
    private val onFontSelected: (AdjustmentPanelTabs) -> Unit,
    private val isEnabled: Boolean = true
) : RecyclerView.Adapter<AdjustmentPanelTabsAdapter.FontViewHolder>() {

    private val fonts = mutableListOf<AdjustmentPanelTabs>()

    fun submitList(newList: List<AdjustmentPanelTabs>) {
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
        fun bind(font: AdjustmentPanelTabs) {
            val context = binding.root.context
            val appColor = ContextCompat.getColor(context, R.color.appColor)
            val selectedBgColor = ContextCompat.getColor(context, R.color.contrast)

            // Clear compound drawable tint so ic_done_small_filled and ic_done_small_stroke render their native colors (white check / gray stroke)
            TextViewCompat.setCompoundDrawableTintList(binding.tabTitle, null)

            if (isEnabled) {
                val iconRes = if (font.is_enabled) R.drawable.ic_done_small_filled else R.drawable.ic_done_small_stroke
                binding.tabTitle.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
            } else {
                binding.tabTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }

            if (font.is_selected) {
                binding.tabRoot.setBackgroundColor(selectedBgColor)
                binding.tabTitle.setTextColor(appColor)
                binding.selectedIndicator.isVisible = true
            } else {
                binding.tabRoot.setBackgroundColor(Color.TRANSPARENT)
                binding.tabTitle.setTextColor(ContextCompat.getColor(context, R.color.black))
                binding.selectedIndicator.isVisible = false
            }

            binding.tabTitle.text = font.tab_name

            binding.root.addPressEffect {
                onFontSelected.invoke(font)
            }
        }
    }
}
