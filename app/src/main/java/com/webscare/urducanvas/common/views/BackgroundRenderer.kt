package com.webscare.urducanvas.common.views

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.graphics.withTranslation
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.canvas.sealed.ImageFilter
import com.webscare.urducanvas.common.utils.BrushRenderUtils.createBackgroundGradientShader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class BackgroundRenderer(private val view: CanvasView) {
    private val bgPaint = Paint()
    private val rectF = RectF()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 200)
    }

    fun draw(canvas: Canvas, e: CanvasElement) {
        val w = view.canvasWidth.toFloat()
        val h = view.canvasHeight.toFloat()

        bgPaint.reset()
        bgPaint.alpha = e.paintAlpha
        bgPaint.style = Paint.Style.FILL
        bgPaint.isAntiAlias = true

        val bmp = e.bitmap
        if (bmp != null && !bmp.isRecycled) {
            drawBitmapBg(canvas, e, bmp, w, h)
            return
        }

        val left = e.x - w / 2f
        val top = e.y - h / 2f
        val pivotX = w / 2f
        val pivotY = h / 2f

        drawFlatOrGradientBg(canvas, e, w, h, left, top, pivotX, pivotY)
    }

    private fun drawBitmapBg(canvas: Canvas, e: CanvasElement, bmp: Bitmap, w: Float, h: Float) {
        val baseScale = max(w / bmp.width, h / bmp.height)
        val totalScale = baseScale * e.scale

        val sw = bmp.width * totalScale
        val sh = bmp.height * totalScale

        if (!view.allowFreeDrag) {
            constrainBitmapBgDrag(e, sw, sh, w, h)
        }

        val left = e.x - sw / 2f
        val top = e.y - sh / 2f

        val adjustedBackground: Bitmap = view.resolveAdjustedBitmapAsync(e, bmp)

        val onScreenW = (sw * view.scale * view.overallScale).toInt().coerceIn(1, adjustedBackground.width)
        val onScreenH = (sh * view.scale * view.overallScale).toInt().coerceIn(1, adjustedBackground.height)
        val displayBmp = view.getOrBuildDisplayBitmap(e.id + "_bg", adjustedBackground, onScreenW, onScreenH)

        canvas.withTranslation(left, top) {
            scale(totalScale, totalScale)
            rotate(e.rotation, bmp.width / 2f, bmp.height / 2f)

            if (bmp.isRecycled) return@withTranslation

            bgPaint.colorFilter = view.colorFilterFor(e.imageFilter)
            bgPaint.maskFilter = null

            val dstRect = rectF.also { it.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()) }

            when (e.imageFilter) {
                ImageFilter.SoftBlur -> {
                    bgPaint.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
                    if (!displayBmp.isRecycled) drawBitmap(displayBmp, null, dstRect, bgPaint)
                }

                ImageFilter.Glow -> {
                    if (!displayBmp.isRecycled) drawBitmap(displayBmp, null, dstRect, bgPaint)
                    glowPaint.maskFilter = BlurMaskFilter(25f, BlurMaskFilter.Blur.OUTER)
                    if (!displayBmp.isRecycled) drawBitmap(displayBmp, null, dstRect, glowPaint)
                }

                else -> {
                    if (!displayBmp.isRecycled) drawBitmap(displayBmp, null, dstRect, bgPaint)
                }
            }
            if (e.hasOverlay && e.overlayOpacity > 0) {
                drawBitmapOverlay(this, e, bmp)
            }
        }
        bgPaint.xfermode = view.drawWithBlend(e)
    }

    private fun constrainBitmapBgDrag(e: CanvasElement, sw: Float, sh: Float, w: Float, h: Float) {
        val theta = Math.toRadians(e.rotation.toDouble())
        val cosA = abs(cos(theta))
        val sinA = abs(sin(theta))

        val halfW = (sw / 2) * cosA + (sh / 2) * sinA
        val halfH = (sw / 2) * sinA + (sh / 2) * cosA

        val xMin = halfW
        val xMax = w - halfW
        val yMax = h - halfH

        if (xMax >= xMin && yMax >= halfH) {
            e.x = e.x.coerceIn(xMin.toFloat(), xMax.toFloat())
            e.y = e.y.coerceIn(halfH.toFloat(), yMax.toFloat())
        } else {
            view.allowFreeDrag = true
        }
    }

    private fun drawBitmapOverlay(canvas: Canvas, e: CanvasElement, bmp: Bitmap) {
        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = e.overlayOpacity.coerceIn(0, 255)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }

        if (e.overlayGradient != null) {
            overlayPaint.shader = view.createGradientShader(e.overlayGradient!!, bmp.width.toFloat(), bmp.height.toFloat())
        } else {
            overlayPaint.color = e.overlayColor
        }
        canvas.drawRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), overlayPaint)
    }

    private fun drawFlatOrGradientBg(
        canvas: Canvas,
        e: CanvasElement,
        w: Float,
        h: Float,
        left: Float,
        top: Float,
        pivotX: Float,
        pivotY: Float
    ) {
        if (e.hasOverlay) {
            canvas.withTranslation(left, top) {
                scale(e.scale, e.scale, pivotX, pivotY)
                rotate(e.rotation, pivotX, pivotY)

                val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    alpha = e.overlayOpacity.coerceIn(0, 255)
                }
                if (e.overlayGradient != null) {
                    overlayPaint.shader = view.createGradientShader(e.overlayGradient!!, w, h)
                } else {
                    overlayPaint.color = e.overlayColor
                }
                bgPaint.alpha = e.paintAlpha
                drawRect(0f, 0f, w, h, overlayPaint)
                bgPaint.shader = null
            }
        } else {
            canvas.withTranslation(left, top) {
                scale(e.scale, e.scale, pivotX, pivotY)
                rotate(e.rotation, pivotX, pivotY)

                if (e.fillGradient != null) {
                    bgPaint.shader = createBackgroundGradientShader(e.fillGradient!!, w, h)
                } else {
                    bgPaint.shader = null
                    bgPaint.color = e.backgroundColor
                }

                bgPaint.alpha = e.paintAlpha
                drawRect(0f, 0f, w, h, bgPaint)
            }
        }
    }
}
