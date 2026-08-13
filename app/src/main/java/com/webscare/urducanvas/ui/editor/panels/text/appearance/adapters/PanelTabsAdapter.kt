package com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.databinding.LayoutTabsItemBinding

class PanelTabsAdapter(
    private val onFontSelected: (PanelTabs) -> Unit
) : RecyclerView.Adapter<PanelTabsAdapter.FontViewHolder>() {

    private val fonts = mutableListOf<PanelTabs>()

    fun submitList(newList: List<PanelTabs>) {
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
        fun bind(font: PanelTabs) {
            val context = binding.root.context
            val appColor = ContextCompat.getColor(context, R.color.appColor)
            val selectedBgColor = ContextCompat.getColor(context, R.color.contrast)

            // Clear compound drawable tint so ic_done_small_filled and ic_done_small_stroke render their native vector drawables
            TextViewCompat.setCompoundDrawableTintList(binding.tabTitle, null)

            if (font.id == -100 || font.tab_name.startsWith("+")) {
                binding.tabRoot.setBackgroundResource(R.drawable.bg_add_style_outline_btn)
                val density = context.resources.displayMetrics.density
                val lp = binding.tabRoot.layoutParams as? ViewGroup.MarginLayoutParams
                if (lp != null) {
                    lp.setMargins(
                        (6 * density).toInt(),
                        (4 * density).toInt(),
                        (6 * density).toInt(),
                        (8 * density).toInt()
                    )
                    binding.tabRoot.layoutParams = lp
                }
                binding.tabTitle.setPadding(
                    (8 * density).toInt(),
                    (6 * density).toInt(),
                    (8 * density).toInt(),
                    (6 * density).toInt()
                )
                binding.tabTitle.setTextColor(appColor)
                binding.tabTitle.typeface = android.graphics.Typeface.DEFAULT_BOLD
                binding.selectedIndicator.isVisible = false
            } else {
                val density = context.resources.displayMetrics.density
                val lp = binding.tabRoot.layoutParams as? ViewGroup.MarginLayoutParams
                if (lp != null) {
                    lp.setMargins(0, 0, 0, 0)
                    binding.tabRoot.layoutParams = lp
                }
                binding.tabTitle.setPadding(
                    (12 * density).toInt(),
                    (4 * density).toInt(),
                    (8 * density).toInt(),
                    (4 * density).toInt()
                )
                binding.tabTitle.typeface = android.graphics.Typeface.DEFAULT

                if (font.is_selected) {
                    binding.tabRoot.setBackgroundColor(selectedBgColor)
                    binding.tabTitle.setTextColor(appColor)
                    binding.selectedIndicator.isVisible = true
                } else {
                    binding.tabRoot.setBackgroundColor(Color.TRANSPARENT)
                    binding.tabTitle.setTextColor(ContextCompat.getColor(context, R.color.black))
                    binding.selectedIndicator.isVisible = false
                }
            }

            binding.tabTitle.text = font.tab_name

            binding.root.addPressEffect {
                onFontSelected.invoke(font)
            }
        }
    }
}
