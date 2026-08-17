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

            val bmp = getCachedOrGenerateThumbnail(cardRoot.context, preset)
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

    companion object {
        private val thumbnailCache = LruCache<String, Bitmap>(80)

        fun getCachedOrGenerateThumbnail(context: Context, preset: TextStylePreset): Bitmap {
            val key = preset.id
            thumbnailCache.get(key)?.let { return it }

            val bmp = generatePresetThumbnail(context, preset)
            thumbnailCache.put(key, bmp)
            return bmp
        }

        private fun generatePresetThumbnail(context: Context, preset: TextStylePreset): Bitmap {
            val width = 160
            val height = 160
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val text = "اردو"
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 40f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }

            val textWidth = textPaint.measureText(text)
            val fontMetrics = textPaint.fontMetrics
            val textHeight = fontMetrics.bottom - fontMetrics.top

            val cx = width / 2f
            val cy = height / 2f + (textHeight / 4f)

            // Draw Label Background if present
            if (preset.hasLabel) {
                val padX = 18f
                val padY = 10f
                val rect = RectF(
                    cx - textWidth / 2f - padX,
                    height / 2f - textHeight / 2f - padY,
                    cx + textWidth / 2f + padX,
                    height / 2f + textHeight / 2f + padY
                )

                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                }

                if (preset.labelGradient != null) {
                    val colors = preset.labelGradient.colors.toIntArray()
                    labelPaint.shader = LinearGradient(
                        rect.left, rect.top, rect.right, rect.bottom, colors, null, Shader.TileMode.CLAMP
                    )
                } else {
                    labelPaint.color = preset.labelColor ?: Color.TRANSPARENT
                }

                // Folded Ribbon Flaps
                if (preset.hasFoldedRibbonFlaps && preset.labelSecondaryColor != null) {
                    val flapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = preset.labelSecondaryColor
                        style = Paint.Style.FILL
                    }
                    val flapPath = Path().apply {
                        moveTo(rect.left, rect.bottom)
                        lineTo(rect.left - 10f, rect.bottom + 6f)
                        lineTo(rect.left + 8f, rect.bottom)
                        close()
                        moveTo(rect.right, rect.top)
                        lineTo(rect.right + 10f, rect.top - 6f)
                        lineTo(rect.right - 8f, rect.top)
                        close()
                    }
                    canvas.drawPath(flapPath, flapPaint)
                }

                // Main Shape
                when (preset.labelShape) {
                    LabelShape.CAPSULE_FILL -> canvas.drawRoundRect(rect, 20f, 20f, labelPaint)
                    LabelShape.SLANTED_FILL -> {
                        val path = Path().apply {
                            moveTo(rect.left + 12f, rect.top)
                            lineTo(rect.right, rect.top)
                            lineTo(rect.right - 12f, rect.bottom)
                            lineTo(rect.left, rect.bottom)
                            close()
                        }
                        canvas.drawPath(path, labelPaint)
                    }
                    LabelShape.TAG_FILL -> {
                        val path = Path().apply {
                            moveTo(rect.left, rect.top)
                            lineTo(rect.right - 12f, rect.top)
                            lineTo(rect.right, rect.centerY())
                            lineTo(rect.right - 12f, rect.bottom)
                            lineTo(rect.left, rect.bottom)
                            close()
                        }
                        canvas.drawPath(path, labelPaint)
                    }
                    LabelShape.RIBBON_FILL -> {
                        val path = Path().apply {
                            moveTo(rect.left, rect.top)
                            lineTo(rect.left + 10f, rect.centerY())
                            lineTo(rect.left, rect.bottom)
                            lineTo(rect.right - 10f, rect.bottom)
                            lineTo(rect.right, rect.centerY())
                            lineTo(rect.right - 10f, rect.top)
                            close()
                        }
                        canvas.drawPath(path, labelPaint)
                    }
                    else -> canvas.drawRoundRect(rect, 10f, 10f, labelPaint)
                }

                // Inner Border Line
                if (preset.labelStrokeColor != null && (preset.labelStrokeWidth ?: 0f) > 0f) {
                    val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = preset.labelStrokeColor
                        style = Paint.Style.STROKE
                        strokeWidth = preset.labelStrokeWidth ?: 0f
                    }
                    canvas.drawRoundRect(
                        RectF(rect.left + 3f, rect.top + 3f, rect.right - 3f, rect.bottom - 3f),
                        8f, 8f, strokeP
                    )
                }

                // Glossy Highlight
                if (preset.hasGlossHighlight) {
                    val glossP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = LinearGradient(
                            rect.left, rect.top, rect.left, rect.centerY(),
                            Color.argb(100, 255, 255, 255), Color.argb(10, 255, 255, 255),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRoundRect(
                        RectF(rect.left + 1f, rect.top + 1f, rect.right - 1f, rect.centerY()),
                        8f, 8f, glossP
                    )
                }
            }

            // Draw Text
            if (preset.textGradient != null) {
                val colors = preset.textGradient.colors.toIntArray()
                textPaint.shader = LinearGradient(
                    cx - 30f, cy, cx + 30f, cy, colors, null, Shader.TileMode.CLAMP
                )
            } else {
                textPaint.color = preset.textColor ?: Color.BLACK
            }

            if ((preset.shadowRadius ?: 0f) > 0f && preset.shadowColor != null) {
                textPaint.setShadowLayer(
                    preset.shadowRadius ?: 0f,
                    preset.shadowDx ?: 0f,
                    preset.shadowDy ?: 0f,
                    preset.shadowColor
                )
            }

            canvas.drawText(text, cx, cy, textPaint)

            if ((preset.strokeWidth ?: 0f) > 0f && preset.strokeColor != null) {
                val strokeP = Paint(textPaint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = preset.strokeWidth ?: 0f
                    color = preset.strokeColor
                    shader = null
                }
                canvas.drawText(text, cx, cy, strokeP)
            }

            return bitmap
        }
    }
}
