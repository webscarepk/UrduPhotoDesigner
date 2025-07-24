// LayerImportEngine.kt (Final Version - Accurate x/y, scale, and layer extraction)

package com.example.urduphotodesigner.common.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.util.Base64
import com.example.urduphotodesigner.common.canvas.enums.ElementType
import com.example.urduphotodesigner.common.canvas.model.CanvasElement
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import kotlin.math.*

class LayerImportEngine(
    private val groupsByRef: Map<COSBase, PDOptionalContentGroup>,
    private val onElement: (CanvasElement) -> Unit,
    private val onCanvasSize: (CanvasSize) -> Unit
) : PDFStreamEngine() {

    private var currentLayerName = "default"
    private var uniqueElementId = 0
    private var pdfHeight = 0f
    private var pdfWidth = 0f
    private var backgroundHandled = false
    private var activeTextMatrix: Matrix? = null

    fun processDocument(document: PDDocument) {
        document.pages.forEachIndexed { index, page ->
            if (index == 0) {
                val box = page.mediaBox
                pdfHeight = box.height
                pdfWidth = box.width
                onCanvasSize(CanvasSize("", 0, box.width, box.height))
            }
            processPage(page as PDPage)
        }
    }

    override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
        when (operator.name) {
            "BDC" -> {
                val key = operands[0] as? COSName
                if (key == COSName.OC) {
                    val rawRef = operands[1]
                    val actual = (rawRef as? COSObject)?.`object` ?: rawRef
                    currentLayerName = groupsByRef[actual]?.name ?: "default"
                }
            }
            "EMC" -> currentLayerName = "default"
            "Tj", "TJ", "'", "\"", "Do", "re", "f", "Tm", "Tf" -> {
                when (operator.name) {
                    "Tj", "'", "\"" -> {
                        val str = operands[0] as? COSString ?: return
                        handleText(str.string)
                    }
                    "TJ" -> {
                        val array = operands[0] as? COSArray ?: return
                        val combined = buildString {
                            array.forEach { if (it is COSString) append(it.string) }
                        }
                        handleText(combined)
                    }
                    "Do" -> {
                        val name = operands[0] as? COSName ?: return
                        when (val obj = resources.getXObject(name)) {
                            is PDImageXObject -> handleImage(obj)
                            is PDFormXObject -> processFormXObject(obj)
                        }
                    }
                    "Tm" -> {
                        if (operands.size >= 6) {
                            val a = (operands[0] as COSNumber).floatValue()
                            val b = (operands[1] as COSNumber).floatValue()
                            val c = (operands[2] as COSNumber).floatValue()
                            val d = (operands[3] as COSNumber).floatValue()
                            val e = (operands[4] as COSNumber).floatValue()
                            val f = (operands[5] as COSNumber).floatValue()
                            activeTextMatrix = Matrix(a, b, c, d, e, f)
                        }
                    }
                    "Tf" -> {
                        if (operands.size == 2) {
                            val fontName = operands[0] as COSName
                            val size = (operands[1] as COSNumber).floatValue()

                            val font = resources.getFont(fontName)
                            if (font != null) {
                                graphicsState.textState.font = font
                                graphicsState.textState.fontSize = size
                            }
                        }
                    }
                    "re" -> handleRectFill(operands)
                }
            }
        }
        super.processOperator(operator, operands)
    }

    private fun Matrix.transformer(point: PointF): PointF {
        val x = point.x * scaleX + point.y * shearX + translateX
        val y = point.x * shearY + point.y * scaleY + translateY
        return PointF(x, y)
    }

    private fun handleText(text: String) {
        val gs = graphicsState
        val tm = activeTextMatrix ?: Matrix()
        val ctm = gs.currentTransformationMatrix
        val textMatrix = tm.multiply(ctm)

        val transformed = textMatrix.transformer(PointF(0f, 0f))
        val tx = transformed.x
        val ty = pdfHeight - transformed.y

        val scaleX = sqrt(textMatrix.scaleX * textMatrix.scaleX + textMatrix.shearX * textMatrix.shearX)
        val scaleY = sqrt(textMatrix.scaleY * textMatrix.scaleY + textMatrix.shearY * textMatrix.shearY)
        val rotation = atan2(textMatrix.shearY, textMatrix.scaleY) * (180f / PI.toFloat())

        val fontSize = gs.textState.fontSize

        val color = gs.nonStrokingColor.components.map { (it * 255).toInt().coerceIn(0, 255) }
        val paintColor = when (color.size) {
            3 -> Color.rgb(color[0], color[1], color[2])
            4 -> Color.argb(color[3], color[0], color[1], color[2])
            else -> Color.BLACK
        }
        val paintAlpha = (gs.nonStrokeAlphaConstant * 255).toInt()

        onElement(
            CanvasElement(
                type = ElementType.TEXT,
                text = text,
                x = tx,
                y = ty - (fontSize * 0.75f),
                rotation = rotation,
                paintColor = paintColor,
                paintTextSize = fontSize * scaleX,
                paintAlpha = paintAlpha,
                id = "${currentLayerName}_${uniqueElementId++}"
            )
        )
    }

    private fun handleImage(image: PDImageXObject) {
        val gs = graphicsState
        val m = gs.currentTransformationMatrix

        val scaleX = sqrt(m.scaleX * m.scaleX + m.shearX * m.shearX)
        val scaleY = sqrt(m.scaleY * m.scaleY + m.shearY * m.shearY)

        val width = image.width * scaleX
        val height = image.height * scaleY

        val origin = m.transformPoint(0f, 0f)
        val tx = origin.x
        val ty = pdfHeight - origin.y - height

        val paintAlpha = (gs.nonStrokeAlphaConstant * 255).toInt()
        val b64 = encodeBitmapToBase64(image.image)

        val type = if (!backgroundHandled) {
            backgroundHandled = true
            ElementType.BACKGROUND
        } else {
            ElementType.IMAGE
        }

        onElement(
            CanvasElement(
                type = type,
                x = tx,
                y = ty,
                scale = 1f,
                rotation = 0f,
                bitmap = image.image,
                bitmapData = b64,
                paintAlpha = paintAlpha,
                logicalContentWidth = width,
                logicalContentHeight = height,
                id = "${currentLayerName}_${uniqueElementId++}"
            )
        )
    }

    private fun handleRectFill(operands: MutableList<COSBase>) {
        val x = (operands[0] as COSNumber).floatValue()
        val y = (operands[1] as COSNumber).floatValue()
        val w = (operands[2] as COSNumber).floatValue()
        val h = (operands[3] as COSNumber).floatValue()

        val correctedY = pdfHeight - y - h

        val color = graphicsState.nonStrokingColor.components.map { (it * 255).toInt().coerceIn(0, 255) }
        val paintColor = when (color.size) {
            3 -> Color.rgb(color[0], color[1], color[2])
            4 -> Color.argb(color[3], color[0], color[1], color[2])
            else -> Color.GRAY
        }
        val paintAlpha = (graphicsState.nonStrokeAlphaConstant * 255).toInt()

        val type = if (!backgroundHandled && w > (pdfWidth * 0.9f) && h > (pdfHeight * 0.9f)) {
            backgroundHandled = true
            ElementType.BACKGROUND
        } else {
            ElementType.IMAGE
        }

        onElement(
            CanvasElement(
                type = type,
                x = x,
                y = correctedY,
                paintColor = paintColor,
                paintAlpha = paintAlpha,
                logicalContentWidth = w,
                logicalContentHeight = h,
                id = "${currentLayerName}_${uniqueElementId++}"
            )
        )
    }

    private fun processFormXObject(form: PDFormXObject) {
        try {
            val fakePage = PDPage().apply {
                resources = form.resources
                mediaBox = form.bBox
            }
            val nestedEngine = LayerImportEngine(groupsByRef, onElement, onCanvasSize)
            nestedEngine.pdfHeight = this.pdfHeight
            nestedEngine.backgroundHandled = this.backgroundHandled
            nestedEngine.processPage(fakePage)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
    }
}
