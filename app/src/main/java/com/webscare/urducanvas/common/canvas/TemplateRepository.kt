package com.webscare.urducanvas.common.canvas

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.PictureDrawable
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import com.google.gson.Gson
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.model.AdjustmentValues
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.ImageProcessor.trimTransparentEdges
import com.webscare.urducanvas.data.model.ExportResult
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.viewmodels.FontGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class TemplateRepository @Inject constructor(
    private val gson: Gson,
    private val fontGate: FontGate,
) {
    suspend fun parseAndHydrateTemplate(
        exportResult: ExportResult,
        context: Context,
        localFonts: List<FontEntity>,
        onProgress: (String, Int) -> Unit
    ): List<CanvasElement>? = withContext(Dispatchers.Default) {
        try {
            onProgress("Reading file", 10)
            val elements = readAndParseJsonFile(context, exportResult.jsonPath, onProgress) ?: return@withContext null

            onProgress("Preparing fonts", 40)
            val requiredFontIds = elements.filter { it.type == ElementType.TEXT }
                .mapNotNull { it.fontId }
                .distinct()
            fontGate.ensureFonts(requiredFontIds)
            onProgress("Fonts ready", 55)

            onProgress("Hydrating elements", 60)
            val hydratedElements = elements.map { raw ->
                val element = raw.copy(context = context).apply {
                    if (type == ElementType.DRAW && !drawStrokes.isNullOrEmpty()) {
                        drawStrokes?.forEach { stroke -> stroke.restorePath() }
                    }
                }
                restoreWithContextBackground(element, context, localFonts)
            }

            onProgress("Applying fonts", 70)
            applyTextFontTypefaces(hydratedElements, context, localFonts)

            onProgress("Loading background", 80)
            hydratedElements
        } catch (e: Exception) {
            Log.e("TemplateRepository", "parseAndHydrateTemplate failed: ${e.message}", e)
            null
        }
    }

    private fun readAndParseJsonFile(
        context: Context,
        jsonFilePath: String,
        onProgress: (String, Int) -> Unit
    ): List<CanvasElement>? {
        val sourceFile = File(jsonFilePath)
        if (!sourceFile.exists()) {
            Log.e("TemplateRepository", "Template file not found: $jsonFilePath")
            return null
        }

        onProgress("Parsing JSON", 30)
        val tempJson = File(context.cacheDir, "open_${System.currentTimeMillis()}.json")
        val jsonFile = try {
            com.webscare.urducanvas.common.canvas.io.ProjectCodec
                .toPlainJsonFile(sourceFile, tempJson)
        } catch (e: com.webscare.urducanvas.common.canvas.io.ProjectCodec.BadProjectFileException) {
            Log.e("TemplateRepository", "Bad/foreign project file: ${e.message}")
            tempJson.delete()
            return null
        }

        val elements: List<CanvasElement> = jsonFile.bufferedReader().use { reader ->
            gson.fromJson(reader, Array<CanvasElement>::class.java).toList()
        }
        if (jsonFile.absolutePath == tempJson.absolutePath) tempJson.delete()
        return elements
    }

    private fun applyTextFontTypefaces(
        elements: List<CanvasElement>,
        context: Context,
        localFonts: List<FontEntity>
    ) {
        elements.forEach { element ->
            if (element.type == ElementType.TEXT && element.fontId != null) {
                val font = localFonts.find { it.id.toString() == element.fontId }
                if (font?.file_path?.isNotBlank() == true) {
                    try {
                        element.paint.typeface = Typeface.createFromFile(font.file_path)
                    } catch (e: Exception) {
                        Log.e("TemplateRepository", "Failed to load typeface during template load", e)
                        element.paint.typeface = ResourcesCompat.getFont(context, R.font.default_canvas) ?: Typeface.DEFAULT
                    }
                }
            }
        }
    }

    private fun restoreWithContextBackground(
        element: CanvasElement,
        context: Context,
        localFonts: List<FontEntity>
    ): CanvasElement {
        val restored = element.copy(context = context).apply {
            updatePaintProperties()
            when (type) {
                ElementType.TEXT -> {
                    paint.typeface = FontManager.applyTypefaceFromFontList(this, localFonts, context)
                }

                ElementType.IMAGE -> {
                    restoreBitmapData(this)
                }

                ElementType.STICKER -> {
                    restoreStickerElement(this)
                }

                ElementType.SHAPE -> {
                    restoreBitmapData(this)
                }

                ElementType.BACKGROUND -> {
                    restoreBitmapData(this)
                }

                ElementType.DRAW -> {
                    if (drawStrokes.isNullOrEmpty()) {
                        restoreBitmapData(this)
                    } else {
                        drawStrokes?.forEach { stroke -> stroke.restorePath() }
                    }
                }

                else -> { /* no extra work */ }
            }
        }
        return restored
    }

    private fun restoreBitmapData(element: CanvasElement) {
        element.bitmapData?.let { data ->
            element.bitmap = ImageProcessor.base64ToBitmap(data)
        }
    }

    private fun restoreStickerElement(element: CanvasElement) {
        val svgData = element.svgData
        if (svgData != null) {
            try {
                val svg = com.caverock.androidsvg.SVG.getFromString(svgData)
                val vb = svg.documentViewBox
                var w = if (vb != null && vb.width() > 0f && vb.height() > 0f) vb.width() else svg.documentWidth
                var h = if (vb != null && vb.width() > 0f && vb.height() > 0f) vb.height() else svg.documentHeight
                if (w <= 0f || h <= 0f) {
                    w = 512f
                    h = 512f
                }
                svg.documentWidth = w
                svg.documentHeight = h

                element.svgDrawable = PictureDrawable(svg.renderToPicture()).trimTransparentEdges()
                element.bitmap = null
            } catch (e: Exception) {
                Log.e("TemplateRepository", "SVG restore in restoreWithContextBackground failed, falling back to bitmapData", e)
                element.bitmapData?.let { data ->
                    element.bitmap = ImageProcessor.base64ToBitmap(data)
                }
            }
        } else {
            element.bitmapData?.let { data ->
                element.bitmap = ImageProcessor.base64ToBitmap(data)
            }
        }
    }
}
