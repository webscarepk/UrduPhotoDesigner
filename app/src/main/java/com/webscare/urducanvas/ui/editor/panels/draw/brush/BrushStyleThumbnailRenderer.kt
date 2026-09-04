package com.webscare.urducanvas.ui.editor.panels.draw.brush

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.PorterDuff
import androidx.collection.LruCache
import androidx.core.graphics.createBitmap
import com.webscare.urducanvas.common.canvas.enums.BrushCategory
import com.webscare.urducanvas.common.canvas.enums.BrushEngine
import com.webscare.urducanvas.common.canvas.enums.BrushStyle
import com.webscare.urducanvas.common.canvas.model.StrokeData
import com.webscare.urducanvas.common.utils.BrushRenderUtils

/**
 * Swatches for the style grid, drawn with the real renderer so a swatch never promises a
 * mark the brush does not make.
 *
 * The gesture behind each swatch is chosen per category, because a single swoop cannot
 * show what a brush does: markers read as overlapping slabs, texture brushes as filled
 * bands, fitted shapes have to be given a drag with real bounds or an arrow has nothing
 * to point along.
 */
object BrushStyleThumbnailRenderer {

    // One entry per selectable brush plus the legacy styles a saved project can surface.
    private val thumbnailCache = LruCache<String, Bitmap>(96)

    fun getCachedOrGenerateThumbnail(
        context: Context,
        style: BrushStyle
    ): Bitmap {
        val cacheKey = style.name
        thumbnailCache.get(cacheKey)?.let { return it }

        val density = context.resources.displayMetrics.density
        // Rendered above cell size so the grain and nib edges stay crisp when the grid
        // gives each swatch more room.
        val sizePx = (96 * density).toInt().coerceAtLeast(140)
        val bmp = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val w = sizePx.toFloat()
        val h = sizePx.toFloat()
        val thickness = (sizePx * 0.13f).coerceIn(10f, 26f)

        for (pass in previewPasses(style, w, h)) {
            val stroke = StrokeData(
                path = pass.path,
                color = previewColor(style),
                thickness = thickness * pass.widthScale,
                hardness = 1f,
                opacity = 1f,
                style = style,
                gradient = null
            )
            BrushRenderUtils.drawStrokePreview(
                canvas = canvas,
                stroke = stroke,
                paintAlpha = 255,
                width = sizePx,
                height = sizePx
            )
        }

        thumbnailCache.put(cacheKey, bmp)
        return bmp
    }

    private class Pass(val path: Path, val widthScale: Float = 1f)

    /**
     * The gesture, or gestures, a swatch is drawn from. Several categories need more than
     * one pass — a marker swatch is unreadable as a single line, and a texture swatch has
     * to be a filled block to show its grain.
     */
    private fun previewPasses(style: BrushStyle, w: Float, h: Float): List<Pass> =
        when {
            // Fitted shapes need a drag with real width and height or the shape has no
            // bounds to fit: a frame collapses, an arrow has no direction.
            style.engine == BrushEngine.FITTED -> listOf(
                Pass(Path().apply {
                    moveTo(w * 0.18f, h * 0.68f)
                    lineTo(w * 0.82f, h * 0.32f)
                }, widthScale = 0.55f)
            )

            style.engine == BrushEngine.SCATTERED -> listOf(
                Pass(Path().apply {
                    moveTo(w * 0.15f, h * 0.55f)
                    cubicTo(w * 0.35f, h * 0.25f, w * 0.65f, h * 0.75f, w * 0.85f, h * 0.45f)
                }, widthScale = 0.7f)
            )

            style.engine == BrushEngine.SPRITE -> listOf(
                Pass(Path().apply {
                    moveTo(w * 0.3f, h * 0.78f)
                    cubicTo(w * 0.45f, h * 0.55f, w * 0.55f, h * 0.45f, w * 0.62f, h * 0.22f)
                }, widthScale = 0.75f)
            )

            // Markers read as overlapping slabs, the way they do on the reference sheet:
            // two heavy horizontal bars with a third shorter one crossing under them.
            style.category == BrushCategory.MARKER -> listOf(
                Pass(Path().apply {
                    moveTo(w * 0.10f, h * 0.32f)
                    lineTo(w * 0.90f, h * 0.30f)
                }, widthScale = 1.5f),
                Pass(Path().apply {
                    moveTo(w * 0.22f, h * 0.70f)
                    lineTo(w * 0.78f, h * 0.68f)
                }, widthScale = 1.2f)
            )

            // Texture brushes are shown as a filled block so the grain has somewhere to
            // sit — a single line of any grain just reads as a thin line.
            style.category == BrushCategory.TEXTURE -> (0..5).map { row ->
                val y = h * (0.24f + row * 0.105f)
                Pass(Path().apply {
                    moveTo(w * 0.12f, y)
                    lineTo(w * 0.88f, y)
                }, widthScale = 0.9f)
            }

            // Paint is a loaded brush: one broad sweep plus a shorter second pass, so the
            // bristle streaks cross and the swatch reads as pigment rather than a line.
            style.category == BrushCategory.PAINT -> listOf(
                Pass(Path().apply {
                    moveTo(w * 0.12f, h * 0.60f)
                    cubicTo(w * 0.34f, h * 0.28f, w * 0.62f, h * 0.66f, w * 0.88f, h * 0.34f)
                }, widthScale = 1.3f),
                Pass(Path().apply {
                    moveTo(w * 0.20f, h * 0.76f)
                    cubicTo(w * 0.40f, h * 0.52f, w * 0.62f, h * 0.82f, w * 0.82f, h * 0.58f)
                }, widthScale = 0.9f)
            )

            // Spray needs length and overlap for the particles to build into a cloud.
            style.category == BrushCategory.SPRAY -> listOf(
                Pass(Path().apply {
                    moveTo(w * 0.20f, h * 0.42f)
                    lineTo(w * 0.80f, h * 0.40f)
                }, widthScale = 1.2f),
                Pass(Path().apply {
                    moveTo(w * 0.28f, h * 0.66f)
                    lineTo(w * 0.72f, h * 0.64f)
                }, widthScale = 0.9f)
            )

            // Basic: a few clean lines of different weight, as on the sheet — the family is
            // defined by weight, so one stroke cannot tell Monoline from Bold Line.
            style.category == BrushCategory.BASIC -> listOf(
                Pass(Path().apply {
                    moveTo(w * 0.12f, h * 0.36f)
                    cubicTo(w * 0.36f, h * 0.14f, w * 0.62f, h * 0.42f, w * 0.88f, h * 0.22f)
                }, widthScale = 0.6f),
                Pass(Path().apply {
                    moveTo(w * 0.12f, h * 0.78f)
                    cubicTo(w * 0.36f, h * 0.56f, w * 0.62f, h * 0.84f, w * 0.88f, h * 0.64f)
                }, widthScale = 1.25f)
            )

            // Hatching is what a pencil swatch has to show — a single line reads as a pen.
            style.category == BrushCategory.PENCIL_SKETCH -> listOf(
                Pass(Path().apply {
                    var y = h * 0.28f
                    moveTo(w * 0.18f, y)
                    var toRight = false
                    while (y < h * 0.74f) {
                        y += h * 0.115f
                        lineTo(if (toRight) w * 0.82f else w * 0.18f, y)
                        toRight = !toRight
                    }
                }, widthScale = 0.7f)
            )

            // The Urdu and Arabic nibs are the point of the app, so their swatch is a real
            // calligraphic sweep — a loop and a downstroke, where thick and thin both show.
            style.category == BrushCategory.URDU_ARABIC -> listOf(
                Pass(Path().apply {
                    moveTo(w * 0.82f, h * 0.30f)
                    cubicTo(w * 0.55f, h * 0.12f, w * 0.22f, h * 0.34f, w * 0.34f, h * 0.56f)
                    cubicTo(w * 0.44f, h * 0.74f, w * 0.74f, h * 0.70f, w * 0.86f, h * 0.80f)
                })
            )

            // Ink: two broad blade strokes, as on the sheet.
            style.category == BrushCategory.INK -> listOf(
                Pass(Path().apply {
                    moveTo(w * 0.10f, h * 0.36f)
                    cubicTo(w * 0.34f, h * 0.16f, w * 0.64f, h * 0.36f, w * 0.90f, h * 0.20f)
                }, widthScale = 1.4f),
                Pass(Path().apply {
                    moveTo(w * 0.12f, h * 0.78f)
                    cubicTo(w * 0.36f, h * 0.58f, w * 0.64f, h * 0.78f, w * 0.88f, h * 0.62f)
                }, widthScale = 1.15f)
            )

            style.category == BrushCategory.GLOW -> listOf(
                Pass(Path().apply {
                    moveTo(w * 0.15f, h * 0.62f)
                    cubicTo(w * 0.32f, h * 0.20f, w * 0.48f, h * 0.80f, w * 0.64f, h * 0.36f)
                    quadTo(w * 0.76f, h * 0.14f, w * 0.86f, h * 0.44f)
                }, widthScale = 0.8f)
            )

            else -> listOf(
                Pass(Path().apply {
                    moveTo(w * 0.15f, h * 0.65f)
                    cubicTo(w * 0.35f, h * 0.35f, w * 0.65f, h * 0.45f, w * 0.85f, h * 0.4f)
                })
            )
        }

    /**
     * Every swatch is drawn in ink.
     *
     * The grid answers "what does this brush do", not "what colour is it" — and a brush
     * lays down black until the user picks a colour, so a tinted swatch would promise
     * something the canvas does not deliver. Choosing a colour never repaints the grid.
     */
    private fun previewColor(style: BrushStyle): Int = Color.BLACK
}
