package com.webscare.urducanvas.common.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.StaticLayout
import android.text.TextPaint

/**
 * Renders an emoji string to a square Bitmap using Android's full text
 * rendering pipeline (StaticLayout → Canvas).
 *
 * WHY StaticLayout:
 * canvas.drawText() bypasses Android's emoji rendering pipeline for complex
 * emoji sequences. StaticLayout uses the full text shaping engine including
 * NotoColorEmoji bitmap strikes — identical output to a TextView.
 *
 * WHY SQUARE OUTPUT:
 * Emoji are square glyphs. The canvas artboard should be 1:1.
 * We measure the real glyph dimensions, take the larger of width/height,
 * and produce a square bitmap of that size so the emoji is never distorted
 * or placed on a landscape/portrait artboard.
 */
object EmojiBitmapRenderer {

    /**
     * @param emojiChar  Emoji string (may be multi-codepoint sequence)
     * @param sizePx     Text size in pixels used for rendering.
     *                   The output bitmap will be square: max(glyphW, glyphH).
     *                   512 is large enough for crisp canvas placement.
     */
    fun render(emojiChar: String, sizePx: Int = 512): Bitmap {
        val paint = TextPaint().apply {
            textSize = sizePx.toFloat()
            isAntiAlias = true
        }

        // Layout with generous width — ensures single line, no wrapping
        @Suppress("DEPRECATION")
        val layout = StaticLayout(
            emojiChar,
            paint,
            sizePx * 2,
            android.text.Layout.Alignment.ALIGN_NORMAL,
            1f,
            0f,
            false,
        )

        // ── Square output ─────────────────────────────────────────────────────
        //
        // layout.width returns the full allocated width (sizePx * 2) — not the
        // actual glyph width. layout.getLineWidth(0) returns how many pixels
        // the first (only) line actually used — the real glyph width.
        //
        // We take max(glyphW, glyphH) and produce a square bitmap so the emoji
        // sits on a 1:1 artboard regardless of glyph aspect ratio.

        val glyphW = layout.getLineWidth(0).toInt().coerceAtLeast(1)
        val glyphH = layout.height.coerceAtLeast(1)
        val side = maxOf(glyphW, glyphH) // square — 1:1 artboard

        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Center the glyph in the square canvas
        val offsetX = ((side - glyphW) / 2f).coerceAtLeast(0f)
        val offsetY = ((side - glyphH) / 2f).coerceAtLeast(0f)
        canvas.translate(offsetX, offsetY)

        layout.draw(canvas)
        return bitmap
    }
}
