package com.webscare.urducanvas.ui.editor.panels.text.styles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.LabelShape
import com.webscare.urducanvas.data.model.TextStylePreset
import kotlin.math.min

object TextStyleThumbnailRenderer {

    private val thumbnailCache = LruCache<String, Bitmap>(200)

    fun clearCache() {
        thumbnailCache.evictAll()
    }

    fun getCachedOrGenerateThumbnail(context: Context, preset: TextStylePreset): Bitmap {
        val key = preset.id
        thumbnailCache.get(key)?.let { return it }

        val bmp = generatePresetThumbnail(context, preset)
        thumbnailCache.put(key, bmp)
        return bmp
    }

    private fun generatePresetThumbnail(context: Context, preset: TextStylePreset): Bitmap {
        val width = 180
        val height = 180
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Authentic Urdu font
        val urduTypeface = try {
            ResourcesCompat.getFont(context, R.font.default_canvas) ?: Typeface.DEFAULT_BOLD
        } catch (e: Exception) {
            Typeface.DEFAULT_BOLD
        }

        val text = "اردو"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 58f
            typeface = urduTypeface
            textAlign = Paint.Align.CENTER
        }

        val textWidth = textPaint.measureText(text)
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        val cx = width / 2f
        val cy = height / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f

        // ── LAYER 0: LABEL BACKGROUND ─────────────────────────────────────────
        if (preset.hasLabel) {
            val padX = 22f
            val padY = 12f
            val labelRect = RectF(
                (cx - textWidth / 2f - padX).coerceAtLeast(8f),
                (cy - textHeight / 2f - padY).coerceAtLeast(8f),
                (cx + textWidth / 2f + padX).coerceAtMost(width - 8f),
                (cy + textHeight / 2f + padY).coerceAtMost(height - 8f)
            )

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = if (isStrokeShape(preset.labelShape)) Paint.Style.STROKE else Paint.Style.FILL
                if (isStrokeShape(preset.labelShape)) {
                    strokeWidth = 3f
                }
            }

            if (preset.labelGradient != null) {
                val colors = preset.labelGradient.colors.toIntArray()
                labelPaint.shader = LinearGradient(
                    labelRect.left, labelRect.top, labelRect.right, labelRect.bottom,
                    colors, null, Shader.TileMode.CLAMP
                )
            } else {
                labelPaint.color = preset.labelColor
            }

            // Draw Ribbon Fold Flaps if present
            if (preset.hasFoldedRibbonFlaps && preset.labelSecondaryColor != null) {
                val flapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = preset.labelSecondaryColor
                    style = Paint.Style.FILL
                }
                val flapPath = Path().apply {
                    moveTo(labelRect.left, labelRect.bottom)
                    lineTo(labelRect.left - 10f, labelRect.bottom + 6f)
                    lineTo(labelRect.left + 8f, labelRect.bottom)
                    close()
                    moveTo(labelRect.right, labelRect.top)
                    lineTo(labelRect.right + 10f, labelRect.top - 6f)
                    lineTo(labelRect.right - 8f, labelRect.top)
                    close()
                }
                canvas.drawPath(flapPath, flapPaint)
            }

            // Render Exact Shape
            drawLabelShape(canvas, preset.labelShape, labelRect, labelPaint)

            // Inner Stroke / Border
            if (preset.labelStrokeColor != null && preset.labelStrokeWidth > 0f) {
                val innerStrokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = preset.labelStrokeColor
                    style = Paint.Style.STROKE
                    strokeWidth = preset.labelStrokeWidth
                }
                val inset = preset.labelStrokeWidth * 1.5f + 2f
                val insetRect = RectF(
                    labelRect.left + inset,
                    labelRect.top + inset,
                    labelRect.right - inset,
                    labelRect.bottom - inset
                )
                canvas.drawRoundRect(insetRect, 10f, 10f, innerStrokeP)
            }

            // Glossy Shine Highlight
            if (preset.hasGlossHighlight) {
                val glossP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        labelRect.left, labelRect.top, labelRect.left, labelRect.centerY(),
                        Color.argb(120, 255, 255, 255), Color.argb(10, 255, 255, 255),
                        Shader.TileMode.CLAMP
                    )
                }
                val glossRect = RectF(labelRect.left + 1f, labelRect.top + 1f, labelRect.right - 1f, labelRect.centerY())
                canvas.drawRoundRect(glossRect, 10f, 10f, glossP)
            }
        }

        // ── LAYER 1a: DOUBLE STEP 2 EXTRUSION ────────────────────────────────
        if (preset.hasDoubleExtrude && preset.extrudeStep2Depth > 0f) {
            val step2Paint = Paint(textPaint).apply {
                shader = null
                color = preset.extrudeStep2Color ?: Color.BLACK
                maskFilter = null
                style = Paint.Style.FILL
            }
            val steps = (preset.extrudeStep2Depth.toInt()).coerceIn(1, 16)
            for (step in 1..steps) {
                val stepFrac = step.toFloat() / steps
                val ex = cx + preset.extrudeStep2Dx * stepFrac
                val ey = cy + preset.extrudeStep2Dy * stepFrac
                canvas.drawText(text, ex, ey, step2Paint)
            }
        }

        // ── LAYER 1b: 3D BLOCK EXTRUSION / HARD OFFSET LAYER ──────────────────
        if (preset.has3dExtrude) {
            val extrudePaint = Paint(textPaint).apply {
                shader = null
                color = preset.extrudeColor ?: Color.BLACK
                maskFilter = null
                style = Paint.Style.FILL
            }
            val depth = if (preset.extrudeDepth > 0f) preset.extrudeDepth else kotlin.math.hypot(preset.extrudeDx, preset.extrudeDy)
            val steps = (depth.toInt()).coerceIn(1, 16)
            for (step in 1..steps) {
                val stepFrac = step.toFloat() / steps
                val ex = cx + preset.extrudeDx * stepFrac
                val ey = cy + preset.extrudeDy * stepFrac
                canvas.drawText(text, ex, ey, extrudePaint)
            }
        }

        // ── LAYER 2: SHADOW / SOFT GLOW / HARD OFFSET DROP ───────────────────
        if (preset.shadowColor != null && (preset.shadowRadius > 0f || preset.shadowDx != 0f || preset.shadowDy != 0f)) {
            val baseAlpha = Color.alpha(preset.shadowColor).takeIf { it > 0 } ?: 255
            val effectiveAlpha = ((preset.shadowOpacity.coerceIn(0, 255) / 255f) * baseAlpha).toInt()
            val sc = (preset.shadowColor and 0x00FFFFFF) or (effectiveAlpha shl 24)
            val shadowPaint = Paint(textPaint).apply {
                shader = null
                color = sc
                maskFilter = if (preset.shadowRadius > 0.5f) BlurMaskFilter(preset.shadowRadius, BlurMaskFilter.Blur.NORMAL) else null
            }
            canvas.drawText(text, cx + preset.shadowDx, cy + preset.shadowDy, shadowPaint)
        }

        // ── LAYER 2b: OUTER GLOW ──────────────────────────────────────────────
        if (preset.hasOuterGlow && preset.outerGlowRadius > 0f && preset.outerGlowOpacity > 0) {
            val baseAlpha = Color.alpha(preset.outerGlowColor ?: Color.CYAN).takeIf { it > 0 } ?: 255
            val effectiveAlpha = ((preset.outerGlowOpacity.coerceIn(0, 255) / 255f) * baseAlpha).toInt()
            val glowCol = ((preset.outerGlowColor ?: Color.CYAN) and 0x00FFFFFF) or (effectiveAlpha shl 24)
            val glowPaint = Paint(textPaint).apply {
                shader = null
                color = glowCol
                maskFilter = BlurMaskFilter(preset.outerGlowRadius.coerceAtLeast(0.5f), BlurMaskFilter.Blur.OUTER)
            }
            canvas.drawText(text, cx, cy, glowPaint)
        }

        // ── LAYER 3: OUTER UNDER-STROKE / SECONDARY CONTOUR ──────────────────
        val effectiveUnderStrokeWidth = if (preset.hasUnderStroke && preset.underStrokeWidth > 0f) {
            preset.underStrokeWidth
        } else if (preset.strokeWidth > 0f && preset.strokeColor != null && preset.textColor != Color.TRANSPARENT && preset.textGradient == null) {
            preset.strokeWidth * 1.8f
        } else 0f

        val effectiveUnderStrokeColor = preset.underStrokeColor ?: preset.strokeColor

        if (effectiveUnderStrokeWidth > 0f && effectiveUnderStrokeColor != null) {
            val underStrokePaint = Paint(textPaint).apply {
                style = Paint.Style.STROKE
                strokeWidth = effectiveUnderStrokeWidth
                color = effectiveUnderStrokeColor
                shader = null
                maskFilter = null
            }
            canvas.drawText(text, cx, cy, underStrokePaint)
        }

        // ── LAYER 4: INNER STROKE / PRIMARY STROKE ────────────────────────────
        val isStrokeOnly = preset.textColor == Color.TRANSPARENT && preset.textGradient == null
        if (isStrokeOnly && preset.strokeColor != null) {
            val strokePaint = Paint(textPaint).apply {
                style = Paint.Style.STROKE
                strokeWidth = preset.strokeWidth.takeIf { it > 0f } ?: 2.5f
                color = preset.strokeColor
                shader = null
                maskFilter = null
            }
            canvas.drawText(text, cx, cy, strokePaint)
        } else if (preset.strokeColor != null && preset.strokeWidth > 0f && !preset.hasUnderStroke) {
            val strokePaint = Paint(textPaint).apply {
                style = Paint.Style.STROKE
                strokeWidth = preset.strokeWidth
                color = preset.strokeColor
                shader = null
                maskFilter = null
            }
            canvas.drawText(text, cx, cy, strokePaint)
        }

        // ── LAYER 5a: ANAGLYPH 3D STEREOSCOPIC SPLIT ──────────────────────────
        if (preset.hasAnaglyph && preset.anaglyphOffset > 0f) {
            val anaglyphPaint1 = Paint(textPaint).apply {
                shader = null
                color = preset.anaglyphColor1 ?: Color.parseColor("#FF0055")
                maskFilter = null
            }
            val anaglyphPaint2 = Paint(textPaint).apply {
                shader = null
                color = preset.anaglyphColor2 ?: Color.parseColor("#00E5FF")
                maskFilter = null
            }
            canvas.drawText(text, cx - preset.anaglyphOffset, cy, anaglyphPaint1)
            canvas.drawText(text, cx + preset.anaglyphOffset, cy, anaglyphPaint2)
        }

        // ── LAYER 5b: 3D CHISEL BEVEL ─────────────────────────────────────────
        if (preset.hasBevel && preset.bevelDepth > 0f) {
            val bevelShadowPaint = Paint(textPaint).apply {
                shader = null
                color = preset.bevelShadowColor ?: Color.parseColor("#80000000")
                maskFilter = null
            }
            val bevelHighlightPaint = Paint(textPaint).apply {
                shader = null
                color = preset.bevelHighlightColor ?: Color.parseColor("#80FFFFFF")
                maskFilter = null
            }
            canvas.drawText(text, cx + preset.bevelDepth, cy + preset.bevelDepth, bevelShadowPaint)
            canvas.drawText(text, cx - preset.bevelDepth, cy - preset.bevelDepth, bevelHighlightPaint)
        }

        // ── LAYER 5c: 3D EMBOSS & DEBOSS ──────────────────────────────────────
        if (preset.hasEmboss && preset.embossDepth > 0f) {
            val highlightPaint = Paint(textPaint).apply {
                shader = null
                color = preset.embossHighlightColor ?: Color.parseColor("#80FFFFFF")
                maskFilter = null
            }
            val shadowPaint = Paint(textPaint).apply {
                shader = null
                color = preset.embossShadowColor ?: Color.parseColor("#80000000")
                maskFilter = null
            }
            if (preset.isDebossed) {
                canvas.drawText(text, cx - preset.embossDepth, cy - preset.embossDepth, shadowPaint)
                canvas.drawText(text, cx + preset.embossDepth, cy + preset.embossDepth, highlightPaint)
            } else {
                canvas.drawText(text, cx - preset.embossDepth, cy - preset.embossDepth, highlightPaint)
                canvas.drawText(text, cx + preset.embossDepth, cy + preset.embossDepth, shadowPaint)
            }
        }

        // ── LAYER 5d: MAIN FILL ───────────────────────────────────────────────
        if (!isStrokeOnly) {
            if (preset.textGradient != null) {
                val colors = preset.textGradient.colors.toIntArray()
                textPaint.shader = LinearGradient(
                    cx - 35f, cy - 20f, cx + 35f, cy + 20f,
                    colors, null, Shader.TileMode.CLAMP
                )
            } else {
                textPaint.shader = null
                textPaint.color = preset.textColor ?: Color.BLACK
            }
            textPaint.maskFilter = null
            canvas.drawText(text, cx, cy, textPaint)
        }

        // ── LAYER 5e: INNER GLOW ──────────────────────────────────────────────
        if (preset.hasInnerGlow && preset.innerGlowRadius > 0f && preset.innerGlowOpacity > 0) {
            val baseAlpha = Color.alpha(preset.innerGlowColor ?: Color.WHITE).takeIf { it > 0 } ?: 255
            val effectiveAlpha = ((preset.innerGlowOpacity.coerceIn(0, 255) / 255f) * baseAlpha).toInt()
            val glowCol = ((preset.innerGlowColor ?: Color.WHITE) and 0x00FFFFFF) or (effectiveAlpha shl 24)
            val innerGlowPaint = Paint(textPaint).apply {
                shader = null
                color = glowCol
                maskFilter = BlurMaskFilter(preset.innerGlowRadius.coerceAtLeast(0.5f), BlurMaskFilter.Blur.INNER)
            }
            canvas.drawText(text, cx, cy, innerGlowPaint)
        }

        return bitmap
    }

    private fun drawLabelShape(canvas: Canvas, shape: LabelShape, rect: RectF, paint: Paint) {
        when (shape) {
            LabelShape.RECTANGLE_FILL, LabelShape.RECTANGLE_STROKE -> {
                canvas.drawRect(rect, paint)
            }

            LabelShape.OVAL_FILL, LabelShape.OVAL_STROKE -> {
                canvas.drawOval(rect, paint)
            }

            LabelShape.CIRCLE_FILL, LabelShape.CIRCLE_STROKE -> {
                val r = min(rect.width(), rect.height()) / 2f
                canvas.drawCircle(rect.centerX(), rect.centerY(), r, paint)
            }

            LabelShape.ROUNDED_RECTANGLE_FILL, LabelShape.ROUNDED_RECTANGLE_STROKE -> {
                canvas.drawRoundRect(rect, 14f, 14f, paint)
            }

            LabelShape.CAPSULE_FILL, LabelShape.CAPSULE_STROKE -> {
                val pillRadius = min(rect.width(), rect.height()) / 2f
                canvas.drawRoundRect(rect, pillRadius, pillRadius, paint)
            }

            LabelShape.TAG_FILL, LabelShape.TAG_STROKE -> {
                val arrowWidth = rect.height() * 0.35f
                val path = Path().apply {
                    moveTo(rect.left, rect.top)
                    lineTo(rect.right - arrowWidth, rect.top)
                    lineTo(rect.right, rect.centerY())
                    lineTo(rect.right - arrowWidth, rect.bottom)
                    lineTo(rect.left, rect.bottom)
                    close()
                }
                canvas.drawPath(path, paint)
            }

            LabelShape.REVERSE_TAG_FILL, LabelShape.REVERSE_TAG_STROKE -> {
                val arrowWidth = rect.height() * 0.35f
                val path = Path().apply {
                    moveTo(rect.left + arrowWidth, rect.top)
                    lineTo(rect.right, rect.top)
                    lineTo(rect.right, rect.bottom)
                    lineTo(rect.left + arrowWidth, rect.bottom)
                    lineTo(rect.left, rect.centerY())
                    close()
                }
                canvas.drawPath(path, paint)
            }

            LabelShape.RIBBON_FILL, LabelShape.RIBBON_STROKE -> {
                val notch = rect.height() * 0.25f
                val path = Path().apply {
                    moveTo(rect.left, rect.top)
                    lineTo(rect.left + notch, rect.centerY())
                    lineTo(rect.left, rect.bottom)
                    lineTo(rect.right - notch, rect.bottom)
                    lineTo(rect.right, rect.centerY())
                    lineTo(rect.right - notch, rect.top)
                    close()
                }
                canvas.drawPath(path, paint)
            }

            LabelShape.SLANTED_FILL, LabelShape.SLANTED_STROKE -> {
                val slant = rect.height() * 0.3f
                val path = Path().apply {
                    moveTo(rect.left + slant, rect.top)
                    lineTo(rect.right, rect.top)
                    lineTo(rect.right - slant, rect.bottom)
                    lineTo(rect.left, rect.bottom)
                    close()
                }
                canvas.drawPath(path, paint)
            }

            LabelShape.BADGE_FILL, LabelShape.BADGE_STROKE -> {
                val chamfer = min(rect.width(), rect.height()) * 0.22f
                val path = Path().apply {
                    moveTo(rect.left + chamfer, rect.top)
                    lineTo(rect.right - chamfer, rect.top)
                    lineTo(rect.right, rect.top + chamfer)
                    lineTo(rect.right, rect.bottom - chamfer)
                    lineTo(rect.right - chamfer, rect.bottom)
                    lineTo(rect.left + chamfer, rect.bottom)
                    lineTo(rect.left, rect.bottom - chamfer)
                    lineTo(rect.left, rect.top + chamfer)
                    close()
                }
                canvas.drawPath(path, paint)
            }

            LabelShape.HEXAGON_BADGE_FILL, LabelShape.HEXAGON_BADGE_STROKE -> {
                val hex = rect.height() * 0.28f
                val path = Path().apply {
                    moveTo(rect.left + hex, rect.top)
                    lineTo(rect.right - hex, rect.top)
                    lineTo(rect.right, rect.centerY())
                    lineTo(rect.right - hex, rect.bottom)
                    lineTo(rect.left + hex, rect.bottom)
                    lineTo(rect.left, rect.centerY())
                    close()
                }
                canvas.drawPath(path, paint)
            }

            LabelShape.DIAMOND_SHIELD_FILL, LabelShape.DIAMOND_SHIELD_STROKE -> {
                val path = Path().apply {
                    moveTo(rect.centerX(), rect.top)
                    lineTo(rect.right, rect.top + rect.height() * 0.25f)
                    lineTo(rect.centerX(), rect.bottom)
                    lineTo(rect.left, rect.top + rect.height() * 0.25f)
                    close()
                }
                canvas.drawPath(path, paint)
            }

            LabelShape.UNDERLINE_BAR_FILL, LabelShape.UNDERLINE_BAR_STROKE -> {
                val barHeight = 6f
                val barRect = RectF(rect.left, rect.bottom - barHeight, rect.right, rect.bottom)
                canvas.drawRoundRect(barRect, 3f, 3f, paint)
            }

            LabelShape.SPEECH_BUBBLE_FILL, LabelShape.SPEECH_BUBBLE_STROKE -> {
                val path = Path().apply {
                    val rx = 14f
                    addRoundRect(RectF(rect.left, rect.top, rect.right, rect.bottom - 8f), rx, rx, Path.Direction.CW)
                    moveTo(rect.left + 22f, rect.bottom - 8f)
                    lineTo(rect.left + 14f, rect.bottom)
                    lineTo(rect.left + 34f, rect.bottom - 8f)
                }
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun isStrokeShape(shape: LabelShape): Boolean {
        return shape in listOf(
            LabelShape.RECTANGLE_STROKE,
            LabelShape.OVAL_STROKE,
            LabelShape.CIRCLE_STROKE,
            LabelShape.ROUNDED_RECTANGLE_STROKE,
            LabelShape.CAPSULE_STROKE,
            LabelShape.TAG_STROKE,
            LabelShape.REVERSE_TAG_STROKE,
            LabelShape.RIBBON_STROKE,
            LabelShape.SLANTED_STROKE,
            LabelShape.BADGE_STROKE,
            LabelShape.HEXAGON_BADGE_STROKE,
            LabelShape.DIAMOND_SHIELD_STROKE,
            LabelShape.UNDERLINE_BAR_STROKE,
            LabelShape.SPEECH_BUBBLE_STROKE
        )
    }
}
