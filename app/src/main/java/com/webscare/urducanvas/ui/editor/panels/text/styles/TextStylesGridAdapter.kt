package com.webscare.urducanvas.ui.editor.panels.text.styles

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.TextStylePreset

class TextStylesGridAdapter(
    private var presets: List<TextStylePreset>,
    private val onPresetClick: (TextStylePreset) -> Unit
) : RecyclerView.Adapter<TextStylesGridAdapter.PresetViewHolder>() {

    var attachedRecyclerView: RecyclerView? = null
        private set

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        if (attachedRecyclerView == recyclerView) {
            attachedRecyclerView = null
        }
    }

    var selectedPresetId: String? = null
        set(value) {
            val old = field
            field = value
            if (old != value) {
                notifyDataSetChanged()
            }
        }

    var currentTypeface: Typeface? = null
        private set
    var currentFontKey: String? = null
        private set

    fun updateTypeface(typeface: Typeface?, fontKey: String?) {
        if (currentTypeface != typeface || currentFontKey != fontKey) {
            currentTypeface = typeface
            currentFontKey = fontKey
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_text_style_preset_item, parent, false)
        return PresetViewHolder(view)
    }

    override fun onBindViewHolder(holder: PresetViewHolder, position: Int) {
        val preset = presets[position]
        holder.titleTxt.visibility = View.GONE
        holder.previewImg.visibility = View.VISIBLE

        val isSelected = preset.id == selectedPresetId
        val cardView = holder.itemView as? MaterialCardView
        if (cardView != null) {
            val strokePx = (1.5f * cardView.context.resources.displayMetrics.density + 0.5f).toInt()
            cardView.strokeWidth = if (isSelected) strokePx else 0
            cardView.strokeColor = ContextCompat.getColor(cardView.context, R.color.appColor)
        }

        val bmp = TextStyleThumbnailRenderer.getCachedOrGenerateThumbnail(
            holder.itemView.context,
            preset,
            currentTypeface,
            currentFontKey
        )
        holder.previewImg.setImageBitmap(bmp)

        holder.updateSize(attachedRecyclerView)

        holder.itemView.addPressEffect {
            selectedPresetId = preset.id
            onPresetClick(preset)
        }
    }

    override fun getItemCount(): Int = presets.size

    class PresetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val previewImg: ImageView = view.findViewById(R.id.presetPreviewImage)
        val titleTxt: TextView = view.findViewById(R.id.presetTitleText)

        fun updateSize(attachedRecyclerView: RecyclerView?) {
            val cardRoot = itemView as? MaterialCardView ?: return
            val context = cardRoot.context
            val density = context.resources.displayMetrics.density
            val recyclerView = (cardRoot.parent as? RecyclerView) ?: attachedRecyclerView

            val marginEndPx = (6 * density).toInt()
            val marginBottomPx = (6 * density).toInt()

            val lm = recyclerView?.layoutManager as? GridLayoutManager
            val spanCountCollapsed = lm?.spanCount?.coerceAtLeast(1) ?: 3

            val rvHeight = recyclerView?.height ?: 0
            val rvPaddingY = (recyclerView?.paddingTop ?: 0) + (recyclerView?.paddingBottom ?: 0)
            val availHeight = rvHeight - rvPaddingY

            val computedCollapsedHeight = if (availHeight > 0) {
                ((availHeight - (spanCountCollapsed * marginBottomPx)) / spanCountCollapsed).coerceAtLeast((24 * density).toInt())
            } else {
                (70 * density).toInt()
            }

            val finalSize = computedCollapsedHeight.coerceAtLeast(1)

            val lp = cardRoot.layoutParams as? ViewGroup.MarginLayoutParams
            if (lp != null) {
                if (lp.width != finalSize || lp.height != finalSize || lp.leftMargin != 0 || lp.topMargin != 0 || lp.rightMargin != marginEndPx || lp.bottomMargin != marginBottomPx) {
                    lp.width = finalSize
                    lp.height = finalSize
                    lp.leftMargin = 0
                    lp.topMargin = 0
                    lp.rightMargin = marginEndPx
                    lp.bottomMargin = marginBottomPx
                    cardRoot.layoutParams = lp
                }
            }
        }
    }
}
