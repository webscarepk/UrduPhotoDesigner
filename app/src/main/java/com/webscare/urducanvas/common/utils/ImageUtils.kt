package com.webscare.urducanvas.common.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.model.CanvasElement

object ImageUtils {
    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) result = cursor.getString(idx)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "image_${System.currentTimeMillis()}.png"
    }

    fun getLayerNameForEntity(
        fileName: String?,
        altText: String?,
        category: String?,
        defaultFallback: String
    ): String {
        if (!altText.isNullOrBlank()) return altText.trim()
        if (!fileName.isNullOrBlank()) {
            val nameWithoutExt = fileName.substringBeforeLast(".")
            if (nameWithoutExt.isNotBlank()) return nameWithoutExt.replace('_', ' ').replace('-', ' ').capitalizeWords()
        }
        if (!category.isNullOrBlank()) return category.trim().capitalizeWords()
        return defaultFallback
    }

    fun getBaseLayerName(element: CanvasElement): String {
        val name = element.customName
        if (!name.isNullOrBlank()) return name
        return when (element.type) {
            ElementType.TEXT -> element.text.takeIf { it.isNotBlank() } ?: "Text"
            ElementType.IMAGE -> "Image"
            ElementType.SHAPE -> "Shape"
            ElementType.STICKER -> "Sticker"
            ElementType.BACKGROUND -> "Background"
            ElementType.TABLE -> "Table"
            else -> "Layer"
        }
    }

    fun computeUniqueLayerNames(elements: List<CanvasElement>): Map<String, String> {
        val counts = mutableMapOf<String, Int>()
        val result = mutableMapOf<String, String>()
        for (el in elements) {
            val baseName = getBaseLayerName(el)
            val currentCount = (counts[baseName] ?: 0) + 1
            counts[baseName] = currentCount
            result[el.id] = if (currentCount == 1) baseName else "$baseName $currentCount"
        }
        return result
    }

    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
}
