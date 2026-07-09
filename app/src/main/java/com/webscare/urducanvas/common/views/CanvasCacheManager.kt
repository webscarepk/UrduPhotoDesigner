package com.webscare.urducanvas.common.views

import android.content.Context
import android.graphics.Bitmap

data class ShadowCacheEntry(
    val bitmap: Bitmap,
    val fingerprint: Int, // hash of all shadow params that affect the blur output
    val scaleX: Float,
    val scaleY: Float,
    val offsetX: Float, // scaled offset[0] from extractAlpha
    val offsetY: Float, // scaled offset[1] from extractAlpha
)

data class StrokeCacheEntry(val bitmap: Bitmap, val fingerprint: Int)

data class DisplayCacheEntry(
    val bitmap: Bitmap,
    val srcWidth: Int, // width of the source bitmap this was scaled from
    val srcHeight: Int,
    val dstWidth: Int, // on-screen pixel size when this was built
    val dstHeight: Int,
)

data class FeatherCacheEntry(val bitmap: Bitmap, val fingerprint: Int)

class CanvasCacheManager(context: Context) {
    val shadowBitmapCache = mutableMapOf<String, ShadowCacheEntry>()
    val strokeBitmapCache = mutableMapOf<String, StrokeCacheEntry>()
    val displayBitmapCache = mutableMapOf<String, DisplayCacheEntry>()
    val rawSvgBitmapCache = mutableMapOf<String, Bitmap>()
    val featherBitmapCache = mutableMapOf<String, FeatherCacheEntry>()

    fun removeAllFor(elementId: String) {
        shadowBitmapCache.remove(elementId)?.bitmap?.recycle()
        shadowBitmapCache.remove(elementId + "_img_shadow")?.bitmap?.recycle()
        strokeBitmapCache.remove(elementId)?.bitmap?.recycle()
        strokeBitmapCache.remove(elementId + "_img")?.bitmap?.recycle()
        displayBitmapCache.remove(elementId)?.bitmap?.recycle()
        displayBitmapCache.remove(elementId + "_bg")?.bitmap?.recycle()
        rawSvgBitmapCache.remove(elementId)?.recycle()
        featherBitmapCache.remove(elementId)?.bitmap?.recycle()
    }

    fun removeDisplay(key: String) {
        displayBitmapCache.remove(key)
    }

    fun getDisplay(key: String): DisplayCacheEntry? = displayBitmapCache[key]

    fun putDisplay(key: String, entry: DisplayCacheEntry) {
        displayBitmapCache[key] = entry
    }

    fun clearAll() {
        shadowBitmapCache.values.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        shadowBitmapCache.clear()

        strokeBitmapCache.values.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        strokeBitmapCache.clear()

        displayBitmapCache.values.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        displayBitmapCache.clear()

        rawSvgBitmapCache.values.forEach { if (!it.isRecycled) it.recycle() }
        rawSvgBitmapCache.clear()

        featherBitmapCache.values.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        featherBitmapCache.clear()
    }
}
