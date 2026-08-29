package com.webscare.urducanvas.ui.editor.panels.draw.brush

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.PorterDuff
import androidx.collection.LruCache
import androidx.core.graphics.createBitmap
import com.webscare.urducanvas.common.canvas.enums.BrushStyle
import com.webscare.urducanvas.common.canvas.model.StrokeData
import com.webscare.urducanvas.common.utils.BrushRenderUtils

object BrushStyleThumbnailRenderer {

    private val thumbnailCache = LruCache<String, Bitmap>(30)

    fun getCachedOrGenerateThumbnail(
        context: Context,
        style: BrushStyle
    ): Bitmap {
        val cacheKey = style.name
        thumbnailCache.get(cacheKey)?.let { return it }

        val density = context.resources.displayMetrics.density
        val sizePx = (72 * density).toInt().coerceAtLeast(100)
        val bmp = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val strokePath = Path().apply {
            val w = sizePx.toFloat()
            val h = sizePx.toFloat()
            when (style) {
                BrushStyle.FINE_LINER, BrushStyle.INK_PEN, BrushStyle.BRUSH_PEN -> {
                    moveTo(w * 0.15f, h * 0.72f)
                    cubicTo(w * 0.35f, h * 0.35f, w * 0.65f, h * 0.65f, w * 0.85f, h * 0.32f)
                }
                BrushStyle.CALLIGRAPHY -> {
                    moveTo(w * 0.2f, h * 0.7f)
                    cubicTo(w * 0.38f, h * 0.32f, w * 0.68f, h * 0.62f, w * 0.82f, h * 0.28f)
                }
                else -> {
                    moveTo(w * 0.15f, h * 0.65f)
                    cubicTo(w * 0.35f, h * 0.35f, w * 0.65f, h * 0.45f, w * 0.85f, h * 0.4f)
                }
            }
        }

        val thickness = (sizePx * 0.18f).coerceIn(12f, 26f)
        val stroke = StrokeData(
            path = strokePath,
            color = Color.BLACK,
            thickness = thickness,
            hardness = 1f,
            style = style,
            gradient = null
        )

        BrushRenderUtils.drawStrokePreview(
            canvas = canvas,
            stroke = stroke,
            paintAlpha = 255,
            width = sizePx,
            height = sizePx,
            makePaint = BrushRenderUtils::makeStrokePaint,
            drawBrush = BrushRenderUtils::drawBrushStroke,
            drawPen = BrushRenderUtils::drawTaperedPenStroke
        )

        thumbnailCache.put(cacheKey, bmp)
        return bmp
    }
}
