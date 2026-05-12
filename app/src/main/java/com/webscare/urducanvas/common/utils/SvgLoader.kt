package com.webscare.urducanvas.common.utils

import android.graphics.drawable.PictureDrawable
import android.util.LruCache
import android.widget.ImageView
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object SvgLoader {

    // 200 parsed SVG objects — 20 tabs × ~30 visible = 600 theoretical max,
    // but LRU eviction keeps working items hot. Each SVG object is ~1-5 KB
    // of native Picture data (not decoded bitmaps), so 200 ≈ under 1 MB total.
    private val svgCache = object : LruCache<String, SVG>(200) {}

    // 400 raw XML strings cached in memory. Average SVG XML ~3-8 KB,
    // so 400 × 8 KB = ~3 MB worst case. Lets us re-parse instantly on
    // cache eviction without hitting the network again.
    private val xmlCache = object : LruCache<String, String>(400) {}

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .build()

    // ─────────────────────────────────────────────────────────────────────────
    // Public: display load
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Load an SVG into [imageView]. Call from Main thread; work runs on IO.
     * Cancel the returned Job in onViewRecycled.
     *
     * @param cachedXml  XML string from Room — skips network on repeat opens.
     * @param onLoaded   Main-thread callback; persist [svgXml] to Room here.
     */
    fun load(
        url: String,
        imageView: ImageView,
        scope: CoroutineScope,
        cachedXml: String? = null,
        onLoaded: ((PictureDrawable, svgXml: String) -> Unit)? = null
    ): Job = scope.launch(Dispatchers.Main) {
        val (drawable, xml) = withContext(Dispatchers.IO) {
            resolve(url, cachedXml)
        } ?: return@launch
        imageView.setImageDrawable(drawable)
        onLoaded?.invoke(drawable, xml)
    }

    /**
     * Synchronously resolve SVG → (drawable, xml).
     * Safe to call from any background thread.
     * [internal] so ImagesAdapter tap path can call it directly,
     * avoiding the latch/CompletableDeferred dance.
     */
    internal fun resolve(url: String, cachedXml: String?): Pair<PictureDrawable, String>? {
        // L1: parsed SVG object → just re-render, no parse, no network
        svgCache.get(url)?.let { svg ->
            return runCatching {
                PictureDrawable(svg.renderToPicture()) to (xmlCache.get(url) ?: "")
            }.getOrNull()
        }

        // L2: have XML string → parse once, cache object
        val xml: String = cachedXml
            ?: xmlCache.get(url)
            ?: fetchXml(url)
            ?: return null

        return runCatching {
            val svg = SVG.getFromString(xml)
            svgCache.put(url, svg)
            xmlCache.put(url, xml)
            PictureDrawable(svg.renderToPicture()) to xml
        }.getOrNull()
    }

    /**
     * Pre-warm the cache for [urls]. Checks both caches before any network call.
     * Sequential on IO so we don't spike bandwidth on tab open.
     */
    fun preload(urls: List<String>, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            for (url in urls) {
                if (svgCache.get(url) != null) continue
                val xml = xmlCache.get(url) ?: fetchXml(url) ?: continue
                runCatching {
                    svgCache.put(url, SVG.getFromString(xml))
                    xmlCache.put(url, xml)
                }
            }
        }
    }

    fun clearCache() {
        svgCache.evictAll()
        xmlCache.evictAll()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private
    // ─────────────────────────────────────────────────────────────────────────

    private fun fetchXml(url: String): String? = runCatching {
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }.getOrNull()
}