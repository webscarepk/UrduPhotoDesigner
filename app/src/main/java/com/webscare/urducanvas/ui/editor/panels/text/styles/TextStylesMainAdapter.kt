package com.webscare.urducanvas.ui.editor.panels.text.styles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.LabelShape
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.TextStylePreset
import com.webscare.urducanvas.databinding.LayoutTextStylePresetItemBinding

class TextStylesMainAdapter(
    private val onPresetClick: (TextStylePreset) -> Unit
) : ListAdapter<TextStylePreset, TextStylesMainAdapter.PresetViewHolder>(DiffCallback()) {

    var slideOffset: Float = 0f
    var recyclerViewWidth: Int = 0
    var recyclerViewPadding: Int = 0

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

    var isExpanded: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    var selectedPresetId: String? = null
        set(value) {
            val old = field
            field = value
            if (old != value) {
                notifyDataSetChanged()
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetViewHolder {
        val binding = LayoutTextStylePresetItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PresetViewHolder(binding, this, onPresetClick)
    }

    override fun onBindViewHolder(holder: PresetViewHolder, position: Int) {
        val preset = getItem(position)
        holder.bind(preset, selectedPresetId, slideOffset, recyclerViewWidth, recyclerViewPadding)
    }

    class PresetViewHolder(
        private val binding: LayoutTextStylePresetItemBinding,
        private val adapter: TextStylesMainAdapter,
        private val onPresetClick: (TextStylePreset) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        val cardRoot: MaterialCardView get() = binding.presetItemContainer
        val previewImg: ImageView get() = binding.presetPreviewImage
        val titleTxt: TextView get() = binding.presetTitleText

        fun bind(
            preset: TextStylePreset,
            selectedPresetId: String?,
            slideOffset: Float,
            rvWidth: Int,
            rvPadding: Int
        ) {
            titleTxt.visibility = View.GONE
            previewImg.visibility = View.VISIBLE

            val isSelected = preset.id == selectedPresetId
            val strokePx = (1.5f * cardRoot.context.resources.displayMetrics.density + 0.5f).toInt()
            cardRoot.strokeWidth = if (isSelected) strokePx else 0
            cardRoot.strokeColor = ContextCompat.getColor(cardRoot.context, R.color.appColor)

            val bmp = TextStyleThumbnailRenderer.getCachedOrGenerateThumbnail(cardRoot.context, preset)
            previewImg.setImageBitmap(bmp)

            updateSize(slideOffset, rvWidth, rvPadding)

            cardRoot.addPressEffect {
                adapter.selectedPresetId = preset.id
                onPresetClick(preset)
            }
        }

        fun updateSize(slideOffset: Float, rvWidth: Int, rvPadding: Int) {
            val context = cardRoot.context
            val density = context.resources.displayMetrics.density
            val recyclerView = (cardRoot.parent as? RecyclerView) ?: adapter.attachedRecyclerView

            val marginEndPx = (6 * density).toInt()
            val marginBottomPx = (6 * density).toInt()

            val lm = recyclerView?.layoutManager as? androidx.recyclerview.widget.GridLayoutManager
            val spanCountCollapsed = lm?.spanCount?.coerceAtLeast(1) ?: 3

            val rvHeight = recyclerView?.height ?: 0
            val rvPaddingY = (recyclerView?.paddingTop ?: 0) + (recyclerView?.paddingBottom ?: 0)
            val availHeight = rvHeight - rvPaddingY

            val computedCollapsedHeight = if (availHeight > 0) {
                ((availHeight - (spanCountCollapsed * marginBottomPx)) / spanCountCollapsed).coerceAtLeast((24 * density).toInt())
            } else {
                (70 * density).toInt()
            }

            val collapsedSize = computedCollapsedHeight

            val effectiveWidth = if (rvWidth > 0) rvWidth else (recyclerView?.width ?: 0)
            val columnWidth = if (effectiveWidth > 0) {
                val spanCountExpanded = 3 // 3 columns in expanded mode
                val totalMarginW = (spanCountExpanded - 1) * marginEndPx
                ((effectiveWidth - rvPadding - totalMarginW) / spanCountExpanded).toInt()
            } else collapsedSize

            val currentSize = (collapsedSize + (columnWidth - collapsedSize) * slideOffset).toInt()
            val finalSize = currentSize.coerceAtLeast(1)

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

    class DiffCallback : DiffUtil.ItemCallback<TextStylePreset>() {
        override fun areItemsTheSame(oldItem: TextStylePreset, newItem: TextStylePreset): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TextStylePreset, newItem: TextStylePreset): Boolean =
            oldItem == newItem
    }
}
