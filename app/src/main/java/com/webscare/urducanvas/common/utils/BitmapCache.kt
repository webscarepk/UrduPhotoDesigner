package com.webscare.urducanvas.common.utils

import android.graphics.Bitmap

object BitmapCache {
    private val cache = mutableMapOf<String, Bitmap>()

    fun put(key: String, bitmap: Bitmap) {
        cache[key] = bitmap
    }

    fun get(key: String): Bitmap? = cache[key]

    fun remove(key: String) {
        cache.remove(key)
    }
}
