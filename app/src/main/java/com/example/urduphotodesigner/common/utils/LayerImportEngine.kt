package com.example.urduphotodesigner.common.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.example.urduphotodesigner.common.canvas.enums.ElementType
import com.example.urduphotodesigner.common.canvas.model.CanvasElement
import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

class LayerImportEngine(
    private val groupsByRef: Map<COSBase, PDOptionalContentGroup>,
    private val onElement: (CanvasElement) -> Unit
) : PDFStreamEngine() {

    private var currentLayerName = "default"
    private var firstImageHandledAsBackground = false

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

            "Tf", "Tj", "TJ", "'", "\"", "Do" -> {
                super.processOperator(operator, operands)

                when (operator.name) {
                    "Tj", "'", "\"" -> {
                        val str = operands[0] as? COSString ?: return
                        handleText(str.string)
                    }

                    "TJ" -> {
                        val array = operands[0] as? COSArray ?: return
                        val combined = buildString {
                            array.forEach {
                                if (it is COSString) append(it.string)
                            }
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
                }
            }

            else -> super.processOperator(operator, operands)
        }
    }

    private fun handleText(text: String) {
        val gs = graphicsState
        val (scale, rotation, tx, ty) = decompose(gs.currentTransformationMatrix)

        val colorComponents = gs.nonStrokingColor.components.map { (it * 255).toInt() }
        val paintColor = when (colorComponents.size) {
            3 -> Color.rgb(colorComponents[0], colorComponents[1], colorComponents[2])
            4 -> Color.argb(
                colorComponents[3],
                colorComponents[0],
                colorComponents[1],
                colorComponents[2]
            )

            else -> Color.BLACK
        }

        val paintAlpha = (gs.nonStrokeAlphaConstant * 255).toInt()

        onElement(
            CanvasElement(
                type = ElementType.TEXT,
                text = text,
                x = tx,
                y = ty,
                scale = scale,
                rotation = rotation,
                paintColor = paintColor,
                paintTextSize = gs.textState.fontSize,
                paintAlpha = paintAlpha,
                id = currentLayerName
            )
        )
    }

    private fun handleImage(image: PDImageXObject) {
        val gs = graphicsState
        val (scale, rotation, tx, ty) = decompose(gs.currentTransformationMatrix)
        val b64 = encodeBitmapToBase64(image.image)
        val paintAlpha = (gs.nonStrokeAlphaConstant * 255).toInt()


        val type = if (!firstImageHandledAsBackground) {
            firstImageHandledAsBackground = true
            ElementType.BACKGROUND
        } else {
            ElementType.IMAGE
        }

        onElement(
            CanvasElement(
                type = type,
                x = tx,
                y = ty,
                scale = scale,
                rotation = rotation,
                bitmap = image.image,
                bitmapData = b64,
                paintAlpha = paintAlpha,
                id = currentLayerName
            )
        )
    }

    private fun processFormXObject(form: PDFormXObject) {
        try {
            val fakePage = PDPage().apply {
                resources = form.resources
                mediaBox = form.bBox
            }

            val nestedEngine = LayerImportEngine(groupsByRef, onElement)
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

    private fun decompose(m: Matrix): Transform {
        val a = m.scaleX
        val b = m.shearY
        val e = m.translateX
        val f = m.translateY
        val scale = sqrt(a * a + b * b)
        val rotation = atan2(b, a) * (180f / PI.toFloat())
        return Transform(scale, rotation, e, f)
    }

    private data class Transform(
        val scale: Float,
        val rotation: Float,
        val tx: Float,
        val ty: Float
    )
}