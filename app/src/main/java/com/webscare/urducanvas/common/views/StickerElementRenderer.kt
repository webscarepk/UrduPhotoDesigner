package com.webscare.urducanvas.common.views

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.caverock.androidsvg.SVG
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.canvas.enums.FeatherDirection
import com.webscare.urducanvas.common.canvas.sealed.ImageFilter
import android.util.Log
import com.webscare.urducanvas.common.utils.ImageProcessor.trimTransparentEdges
import java.util.Objects
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class StickerElementRenderer(private val view: CanvasView) {

    fun draw(canvas: Canvas, element: CanvasElement) {
        val needsOpacityLayer = element.paintAlpha < 255
        if (needsOpacityLayer) {
            val opacityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = element.paintAlpha
            }
            canvas.saveLayer(null, opacityPaint)
        }

        element.svgDrawable?.let { drawable ->
            val w = element.logicalContentWidth.takeIf { it > 0 }
                ?: drawable.picture.width.toFloat().takeIf { it > 0 } ?: 200f
            val h = element.logicalContentHeight.takeIf { it > 0 }
                ?: drawable.picture.height.toFloat().takeIf { it > 0 } ?: 200f
            val left = -w / 2f
            val top = -h / 2f

            val hasAnyAdjustment = element.hasLight || element.hasColor || element.hasDetail || element.hasBlur
            val hasImageFilter = element.imageFilter != ImageFilter.None
            val needsRaster = hasAnyAdjustment || hasImageFilter

            // 1. Get or build the raw SVG bitmap (unadjusted)
            val finalBitmap: Bitmap? = if (needsRaster) {
                var rawSvg = view.cacheManager.rawSvgBitmapCache[element.id]
                if (rawSvg == null || rawSvg.isRecycled) {
                    val svgData = element.svgData
                    rawSvg = if (svgData != null) {
                        try {
                            val svg = SVG.getFromString(svgData)
                            val vb = svg.documentViewBox
                            val nativeW = if (vb != null && vb.width() > 0) vb.width() else w
                            val nativeH = if (vb != null && vb.height() > 0) vb.height() else h

                            val scale = minOf(2048f / nativeW, 2048f / nativeH, 2f)
                            val bmpW = (nativeW * scale).toInt().coerceAtLeast(1)
                            val bmpH = (nativeH * scale).toInt().coerceAtLeast(1)

                            val raw = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                            svg.documentWidth = bmpW.toFloat()
                            svg.documentHeight = bmpH.toFloat()
                            svg.renderToCanvas(Canvas(raw))
                            val trimmed = raw.trimTransparentEdges()
                            if (trimmed != raw) raw.recycle()
                            trimmed
                        } catch (e: Exception) {
                            Log.e("StickerRenderer", "SVG parsing to bitmap failed", e)
                            Bitmap.createBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                                drawable.setBounds(0, 0, w.toInt(), h.toInt())
                                drawable.draw(Canvas(it))
                            }
                        }
                    } else {
                        Bitmap.createBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                            drawable.setBounds(0, 0, w.toInt(), h.toInt())
                            drawable.draw(Canvas(it))
                        }
                    }
                    view.cacheManager.rawSvgBitmapCache[element.id] = rawSvg
                }
                view.resolveAdjustedBitmapAsync(element, rawSvg)
            } else {
                null
            }

            // Draw rect bounds
            val drawW: Float
            val drawH: Float
            val bl: Float
            val bt: Float
            val br: Float
            val bb: Float
            if (finalBitmap != null) {
                val bitmapAspect = finalBitmap.width.toFloat() / finalBitmap.height.toFloat()
                val logicalAspect = w / h
                if (bitmapAspect > logicalAspect) {
                    drawW = w
                    drawH = w / bitmapAspect
                } else {
                    drawW = h * bitmapAspect
                    drawH = h
                }
                bl = -drawW / 2f
                bt = -drawH / 2f
                br = drawW / 2f
                bb = drawH / 2f
            } else {
                drawW = w
                drawH = h
                bl = left
                bt = top
                br = left + w
                bb = top + h
            }

            // ── Shadow ──
            if (element.hasShadow && element.shadowOpacity > 0) {
                val shadowFp = Objects.hash(element.id, element.shadowRadius, element.shadowColor, element.shadowOpacity, drawW.toInt(), drawH.toInt())
                val cached = view.cacheManager.shadowBitmapCache[element.id]
                val entry: ShadowCacheEntry = if (cached != null && cached.fingerprint == shadowFp && !cached.bitmap.isRecycled) {
                    cached
                } else {
                    cached?.bitmap?.recycle()
                    val shadowSource = finalBitmap ?: createBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)).also { bmp ->
                        drawable.setBounds(0, 0, w.toInt(), h.toInt())
                        drawable.draw(Canvas(bmp))
                    }
                    val srcW = shadowSource.width.toFloat()
                    val srcH = shadowSource.height.toFloat()
                    val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        maskFilter = BlurMaskFilter(element.shadowRadius.coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)
                    }
                    val offset = IntArray(2)
                    val blurredBitmap = shadowSource.extractAlpha(blurPaint, offset)
                    if (finalBitmap == null) shadowSource.recycle()

                    val scaleX = (br - bl) / srcW
                    val scaleY = (bb - bt) / srcH
                    ShadowCacheEntry(blurredBitmap, shadowFp, scaleX, scaleY, offset[0] * scaleX, offset[1] * scaleY).also {
                        view.cacheManager.shadowBitmapCache[element.id] = it
                    }
                }

                val shadowColor = Color.argb(element.shadowOpacity.coerceIn(0, 255), Color.red(element.shadowColor), Color.green(element.shadowColor), Color.blue(element.shadowColor))
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    isFilterBitmap = true
                    colorFilter = android.graphics.PorterDuffColorFilter(shadowColor, PorterDuff.Mode.SRC_IN)
                }
                val dstLeft = bl + entry.offsetX + element.shadowDx
                val dstTop = bt + entry.offsetY + element.shadowDy
                val dstRight = dstLeft + entry.bitmap.width * entry.scaleX
                val dstBottom = dstTop + entry.bitmap.height * entry.scaleY

                canvas.save()
                if (!entry.bitmap.isRecycled) {
                    canvas.drawBitmap(entry.bitmap, null, RectF(dstLeft, dstTop, dstRight, dstBottom), shadowPaint)
                }
                canvas.restore()
            }

            // ── Stroke ──
            if (element.hasStroke && element.strokeWidth > 0f) {
                val strokeFp = Objects.hash(element.id, element.strokeWidth, drawW.toInt(), drawH.toInt())
                val cachedStroke = view.cacheManager.strokeBitmapCache[element.id]
                val strokedAlphaMask: Bitmap = if (cachedStroke != null && cachedStroke.fingerprint == strokeFp && !cachedStroke.bitmap.isRecycled) {
                    cachedStroke.bitmap
                } else {
                    cachedStroke?.bitmap?.recycle()
                    val strokeSource = finalBitmap ?: createBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)).also { bmp ->
                        drawable.setBounds(0, 0, w.toInt(), h.toInt())
                        drawable.draw(Canvas(bmp))
                    }
                    val strokeAlpha = strokeSource.extractAlpha()
                    if (finalBitmap == null) strokeSource.recycle()

                    val strokeWidthInt = element.strokeWidth.roundToInt().coerceAtLeast(1)
                    val maskW = strokeAlpha.width + 2 * strokeWidthInt
                    val maskH = strokeAlpha.height + 2 * strokeWidthInt
                    val preRendered = Bitmap.createBitmap(maskW, maskH, Bitmap.Config.ALPHA_8)
                    val maskCanvas = Canvas(preRendered)
                    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    for (angle in 0 until 360 step 10) {
                        val rad = Math.toRadians(angle.toDouble())
                        val dx = (strokeWidthInt * cos(rad)).toFloat()
                        val dy = (strokeWidthInt * sin(rad)).toFloat()
                        maskCanvas.drawBitmap(strokeAlpha, strokeWidthInt + dx, strokeWidthInt + dy, maskPaint)
                    }
                    strokeAlpha.recycle()
                    StrokeCacheEntry(preRendered, strokeFp).also {
                        view.cacheManager.strokeBitmapCache[element.id] = it
                    }.bitmap
                }

                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    isFilterBitmap = true
                    if (element.strokeGradient != null) {
                        shader = view.createGradientShader(element.strokeGradient!!, drawW, drawH)
                    } else {
                        color = element.strokeColor
                    }
                }
                canvas.save()
                val strokeWidth = element.strokeWidth
                val strokeRect = RectF(bl - strokeWidth, bt - strokeWidth, br + strokeWidth, bb + strokeWidth)
                if (!strokedAlphaMask.isRecycled) {
                    canvas.drawBitmap(strokedAlphaMask, null, strokeRect, strokePaint)
                }
                canvas.restore()
            }

            // ── Main draw ──
            if (finalBitmap != null) {
                val needsLayer = (element.hasOverlay && element.overlayOpacity > 0) || (element.hasFeather && element.featherRadius > 0f)
                if (needsLayer) {
                    canvas.saveLayer(bl, bt, br, bb, null)
                } else {
                    canvas.save()
                }

                val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    isFilterBitmap = true
                    colorFilter = view.colorFilterFor(element.imageFilter)
                }
                val destRect = RectF(bl, bt, br, bb)
                if (!finalBitmap.isRecycled) {
                    when (element.imageFilter) {
                        ImageFilter.SoftBlur -> {
                            drawPaint.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
                            canvas.drawBitmap(finalBitmap, null, destRect, drawPaint)
                        }
                        ImageFilter.Glow -> {
                            canvas.drawBitmap(finalBitmap, null, destRect, drawPaint)
                            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.argb(180, 255, 255, 200)
                                maskFilter = BlurMaskFilter(25f, BlurMaskFilter.Blur.OUTER)
                            }
                            canvas.drawBitmap(finalBitmap, null, destRect, glowPaint)
                        }
                        else -> {
                            canvas.drawBitmap(finalBitmap, null, destRect, drawPaint)
                        }
                    }
                }

                if (element.hasOverlay && element.overlayOpacity > 0) {
                    val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        alpha = element.overlayOpacity.coerceIn(0, 255)
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                    }
                    if (element.overlayGradient != null) {
                        overlayPaint.shader = view.createGradientShader(element.overlayGradient!!, drawW, drawH)
                    } else {
                        overlayPaint.color = element.overlayColor
                    }
                    canvas.drawRect(bl, bt, br, bb, overlayPaint)
                }

                if (element.hasFeather && element.featherRadius > 0f) {
                    view.drawFeatherMask(canvas, element.id, bl, bt, br, bb, element.featherRadius, element.featherWidth, element.featherDirection ?: FeatherDirection.ALL)
                }
                canvas.restore()
            } else {
                canvas.save()
                val prevAlpha = drawable.alpha
                drawable.alpha = element.paintAlpha
                drawable.setBounds(bl.toInt(), bt.toInt(), br.toInt(), bb.toInt())
                drawable.draw(canvas)
                drawable.alpha = prevAlpha
                canvas.restore()
            }
        }

        if (needsOpacityLayer) {
            canvas.restore()
        }
    }
}
