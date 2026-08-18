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
            val collapsedSize = (44 * density + 0.5f).toInt()
            val marginEndPx = (6 * density + 0.5f).toInt()
            val marginBottomPx = (6 * density * slideOffset + 0.5f).toInt()

            val columnWidth = if (rvWidth > 0) {
                val usableWidth = rvWidth - rvPadding
                val spanCount = 4
                val totalGaps = (spanCount - 1) * marginEndPx
                ((usableWidth - totalGaps) / spanCount).coerceAtLeast(collapsedSize)
            } else collapsedSize

            val currentSize = (collapsedSize + (columnWidth - collapsedSize) * slideOffset).toInt()
            val finalSize = currentSize.coerceAtLeast(1)

            val lp = cardRoot.layoutParams as? ViewGroup.MarginLayoutParams
            if (lp != null) {
                if (lp.width != finalSize || lp.height != finalSize || lp.rightMargin != marginEndPx || lp.bottomMargin != marginBottomPx) {
                    lp.width = finalSize
                    lp.height = finalSize
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
