package com.webscare.urducanvas.ui.editor.panels.text.styles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.LabelShape
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.TextStylePreset

class TextStylesGridAdapter(
    private var presets: List<TextStylePreset>,
    private val onPresetClick: (TextStylePreset) -> Unit
) : RecyclerView.Adapter<TextStylesGridAdapter.PresetViewHolder>() {

    var selectedPresetId: String? = null
        set(value) {
            val old = field
            field = value
            if (old != value) {
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
        val cardView = holder.itemView as? com.google.android.material.card.MaterialCardView
        if (cardView != null) {
            val strokePx = (1.5f * cardView.context.resources.displayMetrics.density + 0.5f).toInt()
            cardView.strokeWidth = if (isSelected) strokePx else 0
            cardView.strokeColor = androidx.core.content.ContextCompat.getColor(cardView.context, R.color.appColor)
        }

        val bmp = generatePresetThumbnail(holder.itemView.context, preset)
        holder.previewImg.setImageBitmap(bmp)

        holder.itemView.addPressEffect {
            selectedPresetId = preset.id
            onPresetClick(preset)
        }
    }

    override fun getItemCount(): Int = presets.size

    class PresetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val previewImg: ImageView = view.findViewById(R.id.presetPreviewImage)
        val titleTxt: TextView = view.findViewById(R.id.presetTitleText)
    }

    private fun generatePresetThumbnail(context: android.content.Context, preset: TextStylePreset): Bitmap {
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
            val rect = RectF(cx - textWidth / 2f - padX, height / 2f - textHeight / 2f - padY, cx + textWidth / 2f + padX, height / 2f + textHeight / 2f + padY)

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

            if (preset.labelGradient != null) {
                val colors = preset.labelGradient.colors.toIntArray()
                labelPaint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, colors, null, Shader.TileMode.CLAMP)
            } else {
                labelPaint.color = preset.labelColor
            }

            // Folded Ribbon Flaps
            if (preset.hasFoldedRibbonFlaps && preset.labelSecondaryColor != null) {
                val flapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = preset.labelSecondaryColor; style = Paint.Style.FILL }
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
            if (preset.labelStrokeColor != null && preset.labelStrokeWidth > 0f) {
                val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = preset.labelStrokeColor
                    style = Paint.Style.STROKE
                    strokeWidth = preset.labelStrokeWidth
                }
                canvas.drawRoundRect(RectF(rect.left + 3f, rect.top + 3f, rect.right - 3f, rect.bottom - 3f), 8f, 8f, strokeP)
            }

            // Glossy Highlight
            if (preset.hasGlossHighlight) {
                val glossP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(rect.left, rect.top, rect.left, rect.centerY(), Color.argb(100, 255, 255, 255), Color.argb(10, 255, 255, 255), Shader.TileMode.CLAMP)
                }
                canvas.drawRoundRect(RectF(rect.left + 1f, rect.top + 1f, rect.right - 1f, rect.centerY()), 8f, 8f, glossP)
            }
        }

        // Draw Text
        if (preset.textGradient != null) {
            val colors = preset.textGradient.colors.toIntArray()
            textPaint.shader = LinearGradient(cx - 30f, cy, cx + 30f, cy, colors, null, Shader.TileMode.CLAMP)
        } else {
            textPaint.color = preset.textColor ?: Color.BLACK
        }

        if (preset.shadowRadius > 0f && preset.shadowColor != null) {
            textPaint.setShadowLayer(preset.shadowRadius, preset.shadowDx, preset.shadowDy, preset.shadowColor)
        }

        canvas.drawText(text, cx, cy, textPaint)

        if (preset.strokeWidth > 0f && preset.strokeColor != null) {
            val strokeP = Paint(textPaint).apply {
                style = Paint.Style.STROKE
                strokeWidth = preset.strokeWidth
                color = preset.strokeColor
                shader = null
            }
            canvas.drawText(text, cx, cy, strokeP)
        }

        return bitmap
    }
}
