package com.webscare.urducanvas.ui.editor.panels.draw.brush

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
import com.webscare.urducanvas.common.canvas.enums.BrushStyle
import com.webscare.urducanvas.common.utils.Utils.addPressEffect

class BrushStyleAdapter(
    private val styles: List<BrushStyle>,
    private val onStyleSelected: (BrushStyle) -> Unit
) : RecyclerView.Adapter<BrushStyleAdapter.StyleViewHolder>() {

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

    var selectedStyle: BrushStyle = BrushStyle.ROUND_BRUSH
        set(value) {
            val old = field
            field = value
            if (old != value) {
                notifyDataSetChanged()
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StyleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_brush_style_item, parent, false)
        return StyleViewHolder(view)
    }

    override fun onBindViewHolder(holder: StyleViewHolder, position: Int) {
        val style = styles[position]
        holder.titleTxt.text = style.displayName
        holder.titleTxt.visibility = View.VISIBLE

        val isSelected = style == selectedStyle ||
                (selectedStyle == BrushStyle.BRUSH && style == BrushStyle.ROUND_BRUSH) ||
                (selectedStyle == BrushStyle.PEN && style == BrushStyle.INK_PEN)

        val cardView = holder.itemView as? MaterialCardView
        if (cardView != null) {
            val strokePx = (1.5f * cardView.context.resources.displayMetrics.density + 0.5f).toInt()
            cardView.strokeWidth = if (isSelected) strokePx else 0
            cardView.strokeColor = ContextCompat.getColor(cardView.context, R.color.appColor)
        }

        holder.selectionBadge.visibility = if (isSelected) View.VISIBLE else View.GONE

        val bmp = BrushStyleThumbnailRenderer.getCachedOrGenerateThumbnail(
            holder.itemView.context,
            style
        )
        holder.previewImg.setImageBitmap(bmp)

        holder.updateSize(attachedRecyclerView)

        holder.itemView.addPressEffect {
            selectedStyle = style
            onStyleSelected(style)
        }
    }

    override fun getItemCount(): Int = styles.size

    class StyleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val previewImg: ImageView = view.findViewById(R.id.brushPreviewImage)
        val titleTxt: TextView = view.findViewById(R.id.brushTitleText)
        val selectionBadge: ImageView = view.findViewById(R.id.selectionBadge)

        fun updateSize(attachedRecyclerView: RecyclerView?) {
            val cardRoot = itemView as? MaterialCardView ?: return
            val context = cardRoot.context
            val density = context.resources.displayMetrics.density
            val recyclerView = (cardRoot.parent as? RecyclerView) ?: attachedRecyclerView

            val marginEndPx = (6 * density).toInt()
            val marginBottomPx = (6 * density).toInt()

            val lm = recyclerView?.layoutManager as? GridLayoutManager
            val spanCountCollapsed = lm?.spanCount?.coerceAtLeast(1) ?: 2

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
