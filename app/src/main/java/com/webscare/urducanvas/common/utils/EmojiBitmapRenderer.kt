package com.webscare.urducanvas.common.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log

/**
 * Renders an emoji string to a square Bitmap using Android's full text
 * rendering pipeline (StaticLayout → Canvas).
 *
 * WHY StaticLayout:
 * canvas.drawText() bypasses Android's emoji rendering pipeline for complex
 * emoji sequences. StaticLayout uses the full text shaping engine including
 * NotoColorEmoji bitmap strikes — identical output to a TextView.
 *
 * WHY WE SHAPE SMALL AND SCALE UP:
 * Colour emoji are CBDT bitmap glyphs; NotoColorEmoji's strikes are 136×128px,
 * so laying text out at 512px asks the text pipeline for a glyph nine times
 * larger than anything the font holds, gains no detail, and is exactly the size
 * range where large-glyph rendering drops the mark and leaves a blank canvas —
 * which is what put invisible emoji on the artboard while the picker's TextView
 * (rendering at ~24sp) looked correct. We shape at [SHAPE_TEXT_SIZE_PX], then
 * scale the result up to the requested size with a filtered blit.
 *
 * WHY SQUARE OUTPUT:
 * Emoji are square glyphs. The canvas artboard should be 1:1.
 * We measure the real glyph dimensions, take the larger of width/height,
 * and produce a square bitmap of that size so the emoji is never distorted
 * or placed on a landscape/portrait artboard.
 */
object EmojiBitmapRenderer {

    private const val TAG = "EmojiBitmapRenderer"

    /** Comfortably above NotoColorEmoji's native strike size, well inside safe territory. */
    private const val SHAPE_TEXT_SIZE_PX = 160f

    /**
     * @param emojiChar  Emoji string (may be multi-codepoint sequence)
     * @param sizePx     Side length of the square bitmap returned. Independent of the
     *                   size the glyph is actually shaped at.
     */
    fun render(emojiChar: String, sizePx: Int = 512): Bitmap {
        val side = sizePx.coerceAtLeast(1)

        val shaped = shape(emojiChar)
        if (shaped != null && !isBlank(shaped)) {
            return scaleToSquare(shaped, side)
        }

        // Fallback: the same path the picker cell renders through. If StaticLayout
        // produced nothing, a plain drawText on a software canvas usually still does.
        Log.w(TAG, "StaticLayout produced a blank glyph for \"$emojiChar\" — falling back to drawText")
        val drawn = drawDirect(emojiChar)
        if (drawn != null) return scaleToSquare(drawn, side)

        Log.e(TAG, "Unable to render emoji \"$emojiChar\"; returning empty bitmap")
        return Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    }

    // ── Shaping ───────────────────────────────────────────────────────────────

    /** Lays the string out with the full shaping engine and crops to the real glyph box. */
    private fun shape(emojiChar: String): Bitmap? {
        if (emojiChar.isEmpty()) return null

        val paint = TextPaint().apply {
            textSize = SHAPE_TEXT_SIZE_PX
            isAntiAlias = true
            color = Color.BLACK      // only relevant for monochrome glyphs
        }

        @Suppress("DEPRECATION")
        val layout = StaticLayout(
            emojiChar,
            paint,
            (SHAPE_TEXT_SIZE_PX * 2).toInt(),
            android.text.Layout.Alignment.ALIGN_NORMAL,
            1f,
            0f,
            false
        )

        // layout.width is the full allocated width, not the glyph's. getLineWidth(0) is
        // how many pixels the single line actually used.
        val glyphW = layout.getLineWidth(0).toInt()
        val glyphH = layout.height
        if (glyphW <= 0 || glyphH <= 0) return null

        val box = maxOf(glyphW, glyphH)
        return try {
            val bmp = Bitmap.createBitmap(box, box, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.translate((box - glyphW) / 2f, (box - glyphH) / 2f)
            layout.draw(canvas)
            bmp
        } catch (e: Throwable) {
            Log.w(TAG, "StaticLayout draw failed for \"$emojiChar\"", e)
            null
        }
    }

    /** Last resort — measure with Paint and draw the glyph straight onto a software canvas. */
    private fun drawDirect(emojiChar: String): Bitmap? {
        if (emojiChar.isEmpty()) return null

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = SHAPE_TEXT_SIZE_PX
            color = Color.BLACK
        }

        val bounds = Rect()
        paint.getTextBounds(emojiChar, 0, emojiChar.length, bounds)
        val glyphW = maxOf(bounds.width(), paint.measureText(emojiChar).toInt())
        val glyphH = bounds.height()
        if (glyphW <= 0 || glyphH <= 0) return null

        val box = maxOf(glyphW, glyphH)
        return try {
            val bmp = Bitmap.createBitmap(box, box, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            // getTextBounds is relative to the origin/baseline, so shift the glyph box
            // back to (0,0) before centring it.
            val x = (box - glyphW) / 2f - bounds.left
            val y = (box - glyphH) / 2f - bounds.top
            canvas.drawText(emojiChar, x, y, paint)
            bmp
        } catch (e: Throwable) {
            Log.w(TAG, "drawText fallback failed for \"$emojiChar\"", e)
            null
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Samples the bitmap on a coarse grid looking for any non-transparent pixel. A blank
     * result here is the silent failure this class exists to catch, so it is worth the
     * few hundred reads rather than shipping an invisible sticker to the canvas.
     */
    private fun isBlank(bmp: Bitmap): Boolean {
        val step = maxOf(1, minOf(bmp.width, bmp.height) / 24)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                if (Color.alpha(bmp.getPixel(x, y)) != 0) return false
                x += step
            }
            y += step
        }
        return true
    }

    private fun scaleToSquare(source: Bitmap, side: Int): Bitmap {
        if (source.width == side && source.height == side) return source
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        canvas.drawBitmap(source, null, RectF(0f, 0f, side.toFloat(), side.toFloat()), paint)
        source.recycle()
        return out
    }
}
