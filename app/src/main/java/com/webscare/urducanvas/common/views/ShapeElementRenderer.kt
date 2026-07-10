package com.webscare.urducanvas.common.views

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.graphics.withSave
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.canvas.enums.FeatherDirection
import com.webscare.urducanvas.common.canvas.enums.ShapeType
import com.webscare.urducanvas.common.canvas.sealed.ImageFilter
import com.webscare.urducanvas.common.utils.ShapeRenderUtils

class ShapeElementRenderer(private val view: CanvasView) {

    fun draw(canvas: Canvas, element: CanvasElement) {
        val localHalfW = element.logicalContentWidth / 2f
        val localHalfH = element.logicalContentHeight / 2f
        val localRect = RectF(-localHalfW, -localHalfH, localHalfW, localHalfH)

        val shapeType = element.shapeType ?: ShapeType.RECTANGLE
        val cornerRadius = if (element.shapeHasCorner) {
            element.shapeCornerRadius
        } else {
            0f
        }

        val path = ShapeRenderUtils.buildShapePath(shapeType, localRect, cornerRadius)

        // 1️⃣ SHADOW (DRAW FIRST - BEHIND EVERYTHING)
        if (element.hasShadow && element.shadowOpacity > 0) {
            val shadowColor = Color.argb(
                element.shadowOpacity,
                Color.red(element.shadowColor),
                Color.green(element.shadowColor),
                Color.blue(element.shadowColor),
            )

            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = shadowColor
                maskFilter = BlurMaskFilter(
                    element.shadowRadius.coerceAtLeast(0.1f),
                    BlurMaskFilter.Blur.NORMAL,
                )
            }

            canvas.save()
            canvas.translate(element.shadowDx, element.shadowDy)
            ShapeRenderUtils.withCornerEffect(shadowPaint, cornerRadius, shapeType) {
                canvas.drawPath(path, shadowPaint)
            }
            canvas.restore()
        }

        // 2️⃣ SHAPE FILL
        if (element.shapeHasFill) {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL

                if (element.shapeFillGradient != null) {
                    shader = view.createGradientShader(
                        element.shapeFillGradient!!,
                        localRect.width(),
                        localRect.height(),
                    )
                } else {
                    color = element.shapeFillColor ?: Color.TRANSPARENT
                }

                alpha = element.paintAlpha
            }

            ShapeRenderUtils.withCornerEffect(fillPaint, cornerRadius, shapeType) {
                canvas.drawPath(path, fillPaint)
            }
        }

        // 3️⃣ IMAGE + PERFECT MASK
        element.bitmap?.let { bmp ->
            if (bmp.isRecycled) return

            canvas.withSave {
                val finalBitmap =
                    if (element.hasLight || element.hasColor || element.hasDetail || element.hasBlur) {
                        view.resolveAdjustedBitmapAsync(element, bmp)
                    } else {
                        bmp
                    }

                val srcW = finalBitmap.width.toFloat()
                val srcH = finalBitmap.height.toFloat()

                val scaleX = localRect.width() / srcW
                val scaleY = localRect.height() / srcH

                val baseScale = when (element.imageFitMode) {
                    "contain" -> minOf(scaleX, scaleY)
                    "stretch" -> scaleX
                    else -> maxOf(scaleX, scaleY)
                }

                val finalScale = baseScale * (element.imageScale.takeIf { it != 0f } ?: 1f)
                val drawW = srcW * finalScale
                val drawH = srcH * finalScale

                val dx = localRect.left + (localRect.width() - drawW) / 2f + element.imagePanX
                val dy = localRect.top + (localRect.height() - drawH) / 2f + element.imagePanY

                val matrix = Matrix().apply {
                    postScale(finalScale, finalScale)
                    postTranslate(dx, dy)
                }

                // ---------- MASK LAYER ----------
                canvas.saveLayer(
                    localRect,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        alpha = element.paintAlpha
                    },
                )

                if (element.shapeHasFill) {
                    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        if (element.shapeFillGradient != null) {
                            shader = view.createGradientShader(
                                element.shapeFillGradient!!,
                                localRect.width(),
                                localRect.height(),
                            )
                        } else {
                            color = element.shapeFillColor ?: Color.TRANSPARENT
                        }
                    }
                    ShapeRenderUtils.withCornerEffect(fillPaint, cornerRadius, shapeType) {
                        canvas.drawPath(path, fillPaint)
                    }
                } else {
                    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = Color.WHITE
                    }
                    ShapeRenderUtils.withCornerEffect(maskPaint, cornerRadius, shapeType) {
                        canvas.drawPath(path, maskPaint)
                    }
                }

                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = view.colorFilterFor(element.imageFilter)
                    isFilterBitmap = true
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                }

                // ---------- IMAGE + FILTER ----------
                when (element.imageFilter) {
                    ImageFilter.SoftBlur -> {
                        paint.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
                        canvas.drawBitmap(finalBitmap, matrix, paint)
                    }

                    ImageFilter.Glow -> {
                        canvas.drawBitmap(finalBitmap, matrix, paint)
                        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.argb(180, 255, 255, 200)
                            maskFilter = BlurMaskFilter(25f, BlurMaskFilter.Blur.OUTER)
                        }
                        canvas.drawBitmap(finalBitmap, matrix, glowPaint)
                    }

                    else -> {
                        canvas.drawBitmap(finalBitmap, matrix, paint)
                    }
                }

                paint.xfermode = null

                // ---------- OVERLAY ----------
                if (element.hasOverlay && element.overlayOpacity > 0) {
                    val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        alpha = element.overlayOpacity.coerceIn(0, 255)
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)

                        if (element.overlayGradient != null) {
                            shader = view.createGradientShader(
                                element.overlayGradient!!,
                                localRect.width(),
                                localRect.height(),
                            )
                        } else {
                            color = element.overlayColor
                        }
                    }

                    canvas.drawRect(
                        localRect.left,
                        localRect.top,
                        localRect.right,
                        localRect.bottom,
                        overlayPaint,
                    )

                    overlayPaint.xfermode = null
                }

                // ── Feather ──
                if (element.hasFeather && element.featherRadius > 0f) {
                    view.drawFeatherMask(
                        canvas, element.id,
                        localRect.left, localRect.top, localRect.right, localRect.bottom,
                        element.featherRadius, element.featherWidth, element.featherDirection ?: FeatherDirection.ALL,
                    )
                }

                canvas.restore()
            }
        }

        // 4️⃣ STROKE (TOP MOST)
        if (element.shapeHasStroke) {
            val scaleSafe = element.scale.takeIf { it > 0f } ?: 1f
            val visualStrokeWidth = (element.shapeStrokeWidth ?: 1f) / scaleSafe

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = visualStrokeWidth

                if (element.shapeStrokeGradient != null) {
                    shader = view.createGradientShader(
                        element.shapeStrokeGradient!!,
                        localRect.width(),
                        localRect.height(),
                    )
                } else {
                    color = element.shapeStrokeColor ?: Color.BLACK
                }

                alpha = element.paintAlpha
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }

            ShapeRenderUtils.withCornerEffect(strokePaint, cornerRadius, shapeType) {
                canvas.drawPath(path, strokePaint)
            }
        }
    }
}
