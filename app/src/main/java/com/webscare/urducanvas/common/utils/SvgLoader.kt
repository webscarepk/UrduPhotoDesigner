package com.webscare.urducanvas.common.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.PictureDrawable
import android.util.LruCache
import android.widget.ImageView
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object SvgLoader {

    // Max raster size for a GRID THUMBNAIL. SVGs declare arbitrary viewBoxes
    // (512, 1024, 2048…). The old path drew them via PictureDrawable at intrinsic
    // size, forcing HWUI to re-rasterize multi-MB bitmaps every frame (shimmer
    // invalidates constantly) -> GC thrash / multi-second freezes. We cap
    // thumbnails to this size. 128*128*4 = 64 KB max per thumbnail.
    private const val THUMB_MAX_PX = 128

    private val renderSemaphore = kotlinx.coroutines.sync.Semaphore(3)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .build()

    // ── Caches ────────────────────────────────────────────────────────────────

    // Parsed SVG objects (native Picture data, ~1-5 KB each).
    private val svgCache = object : LruCache<String, SVG>(200) {}

    // Raw XML strings, for re-parse without network.
    private val xmlCache = object : LruCache<String, String>(400) {}

    // Rasterized THUMBNAIL bitmaps, sized to the grid cell. THE KEY FIX: the grid
    // now displays a cheap immutable Bitmap (never re-rasterized per frame)
    // instead of a PictureDrawable. Sized by retained byte count; 12 MB budget
    // ≈ ~190 thumbnails at 128px — plenty for a scrolling grid.
    private val bitmapCache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * Synchronous cache peek. Returns the already-rasterized thumbnail bitmap if
     * present, else null. Lets the adapter set a cached thumbnail DURING bind with
     * zero coroutine hop and without starting shimmer — this removes the per-bind
     * hitch when scrolling back over already-seen cells.
     */
    fun peekThumbnail(url: String): Bitmap? = bitmapCache.get(url)

    // ─────────────────────────────────────────────────────────────────────────
    // Public: DISPLAY load (grid thumbnails) — bitmap path, low memory
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Load an SVG into [imageView] as a small cached BITMAP. Call from Main; work
     * runs on IO. Cancel the returned Job in onViewRecycled.
     *
     * Rasterizes ONCE at thumbnail size and caches the bitmap, so scrolling and
     * shimmer never re-rasterize large pictures (the GC-thrash freeze cause).
     *
     * NOTE: [onLoaded]'s first param is kept for source compatibility but is now
     * an empty placeholder — existing callers only use it to stop the shimmer and
     * to receive [svgXml]. If any caller actually consumes the PictureDrawable,
     * use resolve() instead.
     */
    fun load(
        url: String,
        imageView: ImageView,
        scope: CoroutineScope,
        cachedXml: String? = null,
        onLoaded: ((PictureDrawable, svgXml: String) -> Unit)? = null
    ): Job = scope.launch(Dispatchers.Main) {
        bitmapCache.get(url)?.let { bmp ->
            imageView.setImageBitmap(bmp)
            onLoaded?.invoke(EMPTY_PICTURE_DRAWABLE, xmlCache.get(url) ?: "")
            return@launch
        }

        val result = withContext(Dispatchers.IO) { rasterizeThumbnail(url, cachedXml) }
            ?: return@launch
        val (bmp, xml) = result
        imageView.setImageBitmap(bmp)
        onLoaded?.invoke(EMPTY_PICTURE_DRAWABLE, xml)
    }

    /**
     * Rasterize [url]'s SVG to a thumbnail bitmap (<= THUMB_MAX_PX), cache it,
     * and return it with the source xml. Safe on any background thread.
     */
    // ✅ Fix — suspend function with semaphore
    private suspend fun rasterizeThumbnail(url: String, cachedXml: String?): Pair<Bitmap, String>? =
        renderSemaphore.withPermit {
            rasterizeThumbnailInternal(url, cachedXml)
        }

    private fun rasterizeThumbnailInternal(url: String, cachedXml: String?): Pair<Bitmap, String>? {
        bitmapCache.get(url)?.let { return it to (xmlCache.get(url) ?: "") }

        val svg = getOrParseSvg(url, cachedXml) ?: return null
        val xml = xmlCache.get(url) ?: cachedXml ?: ""

        return runCatching {
            // Derive an aspect ratio: prefer explicit document width/height, but
            // those return -1 for viewBox-only SVGs, so fall back to the viewBox,
            // then to a square.
            var w = svg.documentWidth
            var h = svg.documentHeight
            if (w <= 0f || h <= 0f) {
                val vb = svg.documentViewBox
                if (vb != null && vb.width() > 0f && vb.height() > 0f) {
                    w = vb.width(); h = vb.height()
                } else {
                    w = THUMB_MAX_PX.toFloat(); h = THUMB_MAX_PX.toFloat()
                }
            }

            // Scale the longest side down to THUMB_MAX_PX (never up).
            val scale = (THUMB_MAX_PX / maxOf(w, h)).coerceAtMost(1f)
            val outW = (w * scale).toInt().coerceAtLeast(1)
            val outH = (h * scale).toInt().coerceAtLeast(1)

            val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            // The RectF viewPort scales the SVG to fill the target rect. This is
            // the only scaling needed — do NOT also mutate documentWidth/Height.
            svg.renderToCanvas(canvas, RectF(0f, 0f, outW.toFloat(), outH.toFloat()))

            bitmapCache.put(url, bmp)
            bmp to xml
        }.getOrNull()
    }

    private fun getOrParseSvg(url: String, cachedXml: String?): SVG? {
        svgCache.get(url)?.let { return it }
        val xml = cachedXml ?: xmlCache.get(url) ?: fetchXml(url) ?: return null
        return runCatching {
            val svg = SVG.getFromString(xml)
            svgCache.put(url, svg)
            xmlCache.put(url, xml)
            svg
        }.getOrNull()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public: TAP resolve (full-size, for placing on canvas) — unchanged contract
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolve SVG → (PictureDrawable, xml) at FULL size for canvas placement.
     * A full PictureDrawable is appropriate here (crisp, scalable canvas element)
     * and happens once per tap, not per grid cell per frame.
     * Safe on any background thread.
     */
    internal fun resolve(url: String, cachedXml: String?): Pair<PictureDrawable, String>? {
        val svg = getOrParseSvg(url, cachedXml) ?: return null
        val xml = xmlCache.get(url) ?: cachedXml ?: ""
        return runCatching { PictureDrawable(svg.renderToPicture()) to xml }.getOrNull()
    }

    /**
     * Pre-warm caches for [urls]. Also pre-rasterizes thumbnails so the first
     * scroll is instant and never blocks the main thread.
     */
    fun preload(urls: List<String>, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            for (url in urls) {
                if (!isActive) return@launch
                if (bitmapCache.get(url) != null) continue
                rasterizeThumbnail(url, null)  // now suspend, semaphore limits concurrency
            }
        }
    }

    fun clearCache() {
        svgCache.evictAll()
        xmlCache.evictAll()
        bitmapCache.evictAll()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private
    // ─────────────────────────────────────────────────────────────────────────

    private val EMPTY_PICTURE_DRAWABLE = PictureDrawable(android.graphics.Picture())

    private fun fetchXml(url: String): String? = runCatching {
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }.getOrNull()
}