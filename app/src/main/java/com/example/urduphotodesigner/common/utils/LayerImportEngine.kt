package com.example.urduphotodesigner.common.utils

import android.graphics.Bitmap
import android.util.Base64
import com.example.urduphotodesigner.common.canvas.enums.ElementType
import com.example.urduphotodesigner.common.canvas.model.CanvasElement
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

// simple holder instead of Quadruple
private data class Transform(val scale: Float, val rotation: Float, val tx: Float, val ty: Float)

class LayerImportEngine(
    private val groupsByRef: Map<COSBase, PDOptionalContentGroup>,
    private val collector: (CanvasElement) -> Unit
) : PDFStreamEngine() {

    private var currentLayer: PDOptionalContentGroup? = null

    // Correct signature for pdfbox-android
    override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
        when (operator.name) {
            "BDC" -> {
                val key = operands[0] as? COSName
                val rawRef = operands[1]
                if (key == COSName.OC) {
                    // unwrap indirect object if necessary
                    val actual = when (rawRef) {
                        is com.tom_roush.pdfbox.cos.COSObject -> rawRef.`object`
                        else                               -> rawRef
                    }
                    currentLayer = groupsByRef[actual]
                }
            }
            "EMC" -> {
                currentLayer = null
            }
            "Tf" -> {
                // delegate font/size plumbing to super
                super.processOperator(operator, operands)
            }
            "Tj", "'", "\"" -> {
                // show text
                if (currentLayer != null && operands[0] is COSString) {
                    super.processOperator(operator, operands)

                    val gs = graphicsState
                    val m = gs.currentTransformationMatrix
                    val (scale, rotation, tx, ty) = decomposeMatrix(m)

                    val cosStr = operands[0] as COSString
                    val text = cosStr.string

                    // PDFBox-Android stores colors as floats 0–1
                    val color = gs.nonStrokingColor.components
                        .map { (it * 255).toInt() }
                        .let { (r, g, b, a) -> (a shl 24) or (r shl 16) or (g shl 8) or b }

                    val fontSize = gs.textState.fontSize

                    collector(
                        CanvasElement(
                            id            = currentLayer!!.name,
                            type          = ElementType.TEXT,
                            x             = tx,
                            y             = ty,
                            scale         = scale,
                            rotation      = rotation,
                            paintColor    = color,
                            text          = text,
                            paintTextSize = fontSize
                        )
                    )
                }
            }
            "Do" -> {
                // draw XObject
                if (currentLayer != null) {
                    super.processOperator(operator, operands)

                    val gs = graphicsState
                    val m = gs.currentTransformationMatrix
                    val (scale, rotation, tx, ty) = decomposeMatrix(m)

                    val name = operands[0] as? COSName ?: return
                    val xobj = resources.getXObject(name)
                    if (xobj is PDImageXObject) {
                        val bitmap: Bitmap = xobj.image
                        val baos = encodeBitmapToBase64(bitmap)
                        collector(
                            CanvasElement(
                                id         = currentLayer!!.name,
                                type       = ElementType.IMAGE,
                                x          = tx,
                                y          = ty,
                                scale      = scale,
                                rotation   = rotation,
                                bitmapData = baos
                            )
                        )
                    }
                }
            }
            else -> super.processOperator(operator, operands)
        }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun decomposeMatrix(m: Matrix): Transform {
        val a = m.scaleX
        val b = m.shearY
        val e = m.translateX
        val f = m.translateY
        val scale = sqrt(a * a + b * b)
        val rotation = atan2(b, a) * (180f / PI.toFloat())
        return Transform(scale, rotation, e, f)
    }
}
