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

    // Raster size for thumbnails. 384 px is crisp on 3× screens even in the
    // expanded grid (≈120 dp column → 360 px needed).
    private const val THUMB_MAX_PX = 384

    // 6 concurrent renders for fast first-scroll population.
    private val renderSemaphore = kotlinx.coroutines.sync.Semaphore(6)

    @Volatile
    private var appContext: android.content.Context? = null

    fun init(context: android.content.Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    private val httpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                if (request.url.toString().endsWith(".svg", ignoreCase = true)) {
                    response.newBuilder()
                        .header("Cache-Control", "public, max-age=2592000") // Cache SVGs locally on disk for 30 days
                        .build()
                } else {
                    response
                }
            }
        
        appContext?.let { ctx ->
            val cacheSize = 50 * 1024 * 1024L // 50 MiB
            val cacheDir = java.io.File(ctx.cacheDir, "svg_cache")
            builder.cache(okhttp3.Cache(cacheDir, cacheSize))
        }
        
        builder.build()
    }

    // ── Caches ────────────────────────────────────────────────────────────────

    // Parsed SVG objects (native Picture data, ~1-5 KB each).
    private val svgCache = object : LruCache<String, SVG>(200) {}

    // Raw XML strings, for re-parse without network.
    private val xmlCache = object : LruCache<String, String>(400) {}

    // At 256 px, each tile is 256*256*4 = 256 KB. 32 MB ≈ 125 tiles — enough
    // for a full grid without eviction churn.
    private val bitmapCache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val isMultiColorCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val isSingleColorDarkCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /**
     * Synchronous cache peek. Returns the already-rasterized thumbnail bitmap if
     * present, else null. Lets the adapter set a cached thumbnail DURING bind with
     * zero coroutine hop and without starting shimmer — this removes the per-bind
     * hitch when scrolling back over already-seen cells.
     */
    fun peekThumbnail(url: String, applyWhiteTint: Boolean = false): Bitmap? {
        val tintedCacheKey = if (applyWhiteTint) "${url}_white" else url
        return bitmapCache.get(tintedCacheKey)
    }

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
    fun isMultiColorSvg(xml: String): Boolean {
        if (xml.isEmpty()) return false
        val key = xml.hashCode().toString()
        return isMultiColorCache.getOrPut(key) {
            val uniqueColors = mutableSetOf<String>()
            
            // 1. Match hex colors: #FFF, #123456, #AABBCCDD, etc.
            val hexRegex = Regex("#([0-9a-fA-F]{3,8})(?![0-9a-fA-F])")
            for (match in hexRegex.findAll(xml)) {
                val hex = normalizeHexColor(match.value)
                uniqueColors.add(hex)
            }
            
            // 2. Match XML attributes: fill="red", stroke="black", stop-color="#fff"
            val attrRegex = Regex("""(fill|stroke|stop-color)\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            for (match in attrRegex.findAll(xml)) {
                val color = match.groups[2]?.value?.trim()?.lowercase() ?: continue
                if (color != "none" && color != "currentColor" && color != "inherit" && !color.startsWith("url(")) {
                    if (color.startsWith("#")) {
                        uniqueColors.add(normalizeHexColor(color))
                    } else {
                        uniqueColors.add(color)
                    }
                }
            }
            
            // 3. Match CSS properties: fill:red, stroke:#fff, etc.
            val styleRegex = Regex("""(fill|stroke|stop-color)\s*:\s*([^;"]+)""", RegexOption.IGNORE_CASE)
            for (match in styleRegex.findAll(xml)) {
                val color = match.groups[2]?.value?.trim()?.lowercase() ?: continue
                if (color != "none" && color != "currentColor" && color != "inherit" && !color.startsWith("url(")) {
                    if (color.startsWith("#")) {
                        uniqueColors.add(normalizeHexColor(color))
                    } else {
                        uniqueColors.add(color)
                    }
                }
            }

            // 4. Match rgb/rgba colors: rgb(255, 99, 71), rgba(0, 0, 0, 0.5)
            val rgbRegex = Regex("""rgba?\s*\(\s*\d+\s*,\s*\d+\s*,\s*\d+\s*(?:,\s*[\d.]+\s*)?\)""", RegexOption.IGNORE_CASE)
            for (match in rgbRegex.findAll(xml)) {
                uniqueColors.add(match.value.replace(" ", "").lowercase())
            }

            uniqueColors.size >= 3
        }
    }

    fun isSingleColorDarkSvg(xml: String): Boolean {
        if (xml.isEmpty()) return false
        val key = xml.hashCode().toString()
        return isSingleColorDarkCache.getOrPut(key) {
            runCatching {
                val svg = SVG.getFromString(xml)
                val width = 16
                val height = 16
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                
                svg.documentWidth = width.toFloat()
                svg.documentHeight = height.toFloat()
                
                if (svg.documentViewBox != null) {
                    val origAspectRatio = svg.documentPreserveAspectRatio
                    try {
                        svg.documentPreserveAspectRatio = com.caverock.androidsvg.PreserveAspectRatio.LETTERBOX
                        svg.renderToCanvas(canvas, RectF(0f, 0f, width.toFloat(), height.toFloat()))
                    } finally {
                        svg.documentPreserveAspectRatio = origAspectRatio
                    }
                } else {
                    svg.renderToCanvas(canvas)
                }

                // Gather colors from solid pixels (alpha >= 180 to skip anti-aliased edges)
                val uniqueColors = mutableSetOf<Int>()
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val pixel = bmp.getPixel(x, y)
                        val alpha = (pixel ushr 24) and 0xFF
                        if (alpha >= 180) {
                            val r = (pixel ushr 16) and 0xFF
                            val g = (pixel ushr 8) and 0xFF
                            val b = pixel and 0xFF
                            uniqueColors.add(android.graphics.Color.rgb(r, g, b))
                        }
                    }
                }

                // If empty due to thin strokes/lines, retry with lower alpha threshold
                if (uniqueColors.isEmpty()) {
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            val pixel = bmp.getPixel(x, y)
                            val alpha = (pixel ushr 24) and 0xFF
                            if (alpha >= 40) {
                                val r = (pixel ushr 16) and 0xFF
                                val g = (pixel ushr 8) and 0xFF
                                val b = pixel and 0xFF
                                uniqueColors.add(android.graphics.Color.rgb(r, g, b))
                            }
                        }
                    }
                }

                bmp.recycle()

                if (uniqueColors.isEmpty()) {
                    return@getOrPut false
                }

                // Group colors that are very close to each other (Euclidean distance < 40)
                val groups = mutableListOf<Int>()
                for (color in uniqueColors) {
                    val r = android.graphics.Color.red(color)
                    val g = android.graphics.Color.green(color)
                    val b = android.graphics.Color.blue(color)
                    
                    var matched = false
                    for (gColor in groups) {
                        val gr = android.graphics.Color.red(gColor)
                        val gg = android.graphics.Color.green(gColor)
                        val gb = android.graphics.Color.blue(gColor)
                        
                        val dist = Math.sqrt(((r - gr) * (r - gr) + (g - gg) * (g - gg) + (b - gb) * (b - gb)).toDouble())
                        if (dist < 40.0) {
                            matched = true
                            break
                        }
                    }
                    if (!matched) {
                        groups.add(color)
                    }
                }

                // Only tint if it's single/duo-colored (groups <= 2) and ALL groups are dark (luminance < 80.0)
                if (groups.size in 1..2) {
                    groups.all { color ->
                        val r = android.graphics.Color.red(color)
                        val g = android.graphics.Color.green(color)
                        val b = android.graphics.Color.blue(color)
                        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
                        luminance < 80.0
                    }
                } else {
                    false
                }
            }.getOrDefault(false)
        }
    }

    private fun normalizeHexColor(hex: String): String {
        val clean = hex.removePrefix("#").lowercase()
        return when (clean.length) {
            3 -> "${clean[0]}${clean[0]}${clean[1]}${clean[1]}${clean[2]}${clean[2]}"
            4 -> "${clean[0]}${clean[0]}${clean[1]}${clean[1]}${clean[2]}${clean[2]}${clean[3]}${clean[3]}"
            else -> clean
        }
    }

    fun load(
        url: String,
        imageView: ImageView,
        scope: CoroutineScope,
        cachedXml: String? = null,
        maxPx: Int = THUMB_MAX_PX,
        applyWhiteTint: Boolean = false,
        onLoaded: ((PictureDrawable, svgXml: String) -> Unit)? = null
    ): Job {
        val cacheKey = if (maxPx == THUMB_MAX_PX) url else "${url}_$maxPx"
        
        // Fast synchronous check of memory cache
        val xmlFromCache = xmlCache.get(url)
        val key = xmlFromCache?.hashCode()?.toString()
        val hasCachedDarkState = key != null && isSingleColorDarkCache.containsKey(key)
        val tintedCacheKey = if (applyWhiteTint && xmlFromCache != null && hasCachedDarkState) {
            val isDark = isSingleColorDarkCache[key] ?: false
            if (isDark) "${cacheKey}_white" else cacheKey
        } else {
            if (applyWhiteTint) "${cacheKey}_white" else cacheKey
        }
        
        bitmapCache.get(tintedCacheKey)?.let { bmp ->
            imageView.setImageBitmap(bmp)
            onLoaded?.invoke(EMPTY_PICTURE_DRAWABLE, xmlFromCache ?: "")
            return kotlinx.coroutines.Job().apply { complete() }
        }

        // Always delegate parsing, check, and rasterization to Dispatchers.IO asynchronously
        return scope.launch(Dispatchers.Main) {
            val result = withContext(Dispatchers.IO) {
                rasterizeThumbnail(url, cachedXml, maxPx, applyWhiteTint)
            } ?: return@launch
            val (bmp, xml) = result
            imageView.setImageBitmap(bmp)
            onLoaded?.invoke(EMPTY_PICTURE_DRAWABLE, xml)
        }
    }

    /**
     * Rasterize [url]'s SVG to a thumbnail bitmap (<= THUMB_MAX_PX), cache it,
     * and return it with the source xml. Safe on any background thread.
     */
    // ✅ Fix — suspend function with semaphore
    private suspend fun rasterizeThumbnail(url: String, cachedXml: String?, maxPx: Int = THUMB_MAX_PX, applyWhiteTint: Boolean = false): Pair<Bitmap, String>? =
        renderSemaphore.withPermit {
            rasterizeThumbnailInternal(url, cachedXml, maxPx, applyWhiteTint)
        }

    private fun rasterizeThumbnailInternal(url: String, cachedXml: String?, maxPx: Int = THUMB_MAX_PX, applyWhiteTint: Boolean = false): Pair<Bitmap, String>? {
        val cacheKey = if (maxPx == THUMB_MAX_PX) url else "${url}_$maxPx"
        
        val svg = getOrParseSvg(url, cachedXml) ?: return null
        val xml = xmlCache.get(url) ?: cachedXml ?: ""
        
        val actualWhiteTint = applyWhiteTint && isSingleColorDarkSvg(xml)
        val tintedCacheKey = if (actualWhiteTint) "${cacheKey}_white" else cacheKey
        
        bitmapCache.get(tintedCacheKey)?.let { return it to xml }

        return runCatching {
            // Prefer viewBox dimensions to get correct coordinates/aspect ratio,
            // fallback to document dimensions, then to standard default.
            val vb = svg.documentViewBox
            var w = if (vb != null && vb.width() > 0f && vb.height() > 0f) vb.width() else svg.documentWidth
            var h = if (vb != null && vb.width() > 0f && vb.height() > 0f) vb.height() else svg.documentHeight
            if (w <= 0f || h <= 0f) {
                w = maxPx.toFloat()
                h = maxPx.toFloat()
            }

            // Scale the longest side to maxPx.
            val scale = maxPx / maxOf(w, h)
            val outW = (w * scale).toInt().coerceAtLeast(1)
            val outH = (h * scale).toInt().coerceAtLeast(1)

            val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            
            // Override document dimensions to match the output bitmap bounds
            svg.documentWidth = outW.toFloat()
            svg.documentHeight = outH.toFloat()

            if (svg.documentViewBox != null) {
                val origAspectRatio = svg.documentPreserveAspectRatio
                try {
                    svg.documentPreserveAspectRatio = com.caverock.androidsvg.PreserveAspectRatio.LETTERBOX
                    svg.renderToCanvas(canvas, RectF(0f, 0f, outW.toFloat(), outH.toFloat()))
                } finally {
                    svg.documentPreserveAspectRatio = origAspectRatio
                }
            } else {
                // If there's no viewBox, scale the canvas itself to render the vector shapes at high resolution
                canvas.scale(scale, scale)
                svg.renderToCanvas(canvas)
            }

            val finalBmp = if (actualWhiteTint) bmp.tintWhite() else bmp
            bitmapCache.put(tintedCacheKey, finalBmp)
            finalBmp to xml
        }.getOrNull()
    }

    private fun Bitmap.tintWhite(): Bitmap {
        val result = Bitmap.createBitmap(width, height, config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = android.graphics.Paint().apply {
            colorFilter = android.graphics.PorterDuffColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(this, 0f, 0f, paint)
        return result
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
    internal fun resolve(url: String, cachedXml: String?, applyWhiteTint: Boolean = false): Pair<PictureDrawable, String>? {
        val svg = getOrParseSvg(url, cachedXml) ?: return null
        val xml = xmlCache.get(url) ?: cachedXml ?: ""
        return runCatching {
            val vb = svg.documentViewBox
            var w = if (vb != null && vb.width() > 0f && vb.height() > 0f) vb.width() else svg.documentWidth
            var h = if (vb != null && vb.width() > 0f && vb.height() > 0f) vb.height() else svg.documentHeight
            if (w <= 0f || h <= 0f) {
                w = 512f
                h = 512f
            }
            svg.documentWidth = w
            svg.documentHeight = h
            PictureDrawable(svg.renderToPicture()) to xml
        }.getOrNull()
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