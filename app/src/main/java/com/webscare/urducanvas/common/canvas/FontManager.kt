package com.webscare.urducanvas.common.canvas

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.data.model.FontEntity
import java.io.File

object FontManager {

    fun isFontFileValid(path: String): Boolean {
        return try {
            val file = File(path)
            if (!file.exists() || file.length() < 4) return false
            val bytes = ByteArray(4)
            file.inputStream().use { it.read(bytes) }
            val magic = ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
            magic == 0x00010000 ||
                magic == 0x4F54544F ||
                magic == 0x74727565 ||
                magic == 0x74797031
        } catch (e: Exception) {
            Log.e("FontManager", "isFontFileValid failed for $path", e)
            false
        }
    }

    fun getTypefaceForElement(
        element: CanvasElement,
        localFonts: List<FontEntity>,
        context: Context
    ): Typeface {
        return if (element.type == ElementType.TEXT && element.fontId != null) {
            val font = localFonts.find { it.id.toString() == element.fontId }
            font?.file_path?.takeIf { it.isNotBlank() }?.let { path ->
                try {
                    Typeface.createFromFile(path)
                } catch (e: Exception) {
                    Log.e("FontManager", "Failed to load typeface from file: $path", e)
                    ResourcesCompat.getFont(context, R.font.default_canvas) ?: Typeface.DEFAULT
                }
            } ?: ResourcesCompat.getFont(context, R.font.default_canvas) ?: Typeface.DEFAULT
        } else {
            ResourcesCompat.getFont(context, R.font.default_canvas) ?: Typeface.DEFAULT
        }
    }

    fun applyTypefaceFromFontList(
        element: CanvasElement,
        localFonts: List<FontEntity>,
        context: Context
    ): Typeface {
        val fontId = element.fontId
        return fontId?.let { id ->
            localFonts.firstOrNull { it.id.toString() == id }?.file_path
                ?.takeIf { it.isNotBlank() && File(it).exists() && isFontFileValid(it) }
                ?.let { path ->
                    try {
                        Typeface.createFromFile(path)
                    } catch (e: Exception) {
                        Log.e("FontManager", "Failed to create typeface from path: $path", e)
                        null
                    }
                }
        } ?: ResourcesCompat.getFont(context, R.font.default_canvas) ?: Typeface.DEFAULT
    }
}
