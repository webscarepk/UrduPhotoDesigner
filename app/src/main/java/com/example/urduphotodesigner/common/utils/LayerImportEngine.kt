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
import com.tom_roush.pdfbox.cos.COSObject
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

    private var currentLayerName: String = "default"

    override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
        when (operator.name) {
            "BDC" -> {
                // start a new OCG scope if it's a real layer
                val key = operands[0] as? COSName
                if (key == COSName.OC) {
                    val rawRef = operands[1]
                    val actual = (rawRef as? COSObject)?.`object` ?: rawRef
                    currentLayerName = groupsByRef[actual]?.name ?: "default"
                }
            }
            "EMC" -> {
                // exit layer scope
                currentLayerName = "default"
            }
            "Tf", "Tj", "'", "\"", "Do" -> {
                // delegate all graphics and text ops to super to set up state
                super.processOperator(operator, operands)

                // after super, inspect what happened
                when (operator.name) {
                    "Tj", "'", "\"" -> {
                        // text showing
                        val cosStr = operands[0] as? COSString ?: return
                        val gs = graphicsState
                        val (scale, rotation, tx, ty) = decompose(gs.currentTransformationMatrix)
                        val color = gs.nonStrokingColor.components
                            .map { (it * 255).toInt() }
                            .let { (r, g, b, a) -> (a shl 24) or (r shl 16) or (g shl 8) or b }
                        val fontSize = gs.textState.fontSize

                        collector(
                            CanvasElement(
                                id            = currentLayerName,
                                type          = ElementType.TEXT,
                                x             = tx,
                                y             = ty,
                                scale         = scale,
                                rotation      = rotation,
                                paintColor    = color,
                                text          = cosStr.string,
                                paintTextSize = fontSize
                            )
                        )
                    }
                    "Do" -> {
                        // image drawing
                        val name = operands[0] as? COSName ?: return
                        val xobj = resources.getXObject(name)
                        if (xobj is PDImageXObject) {
                            val gs = graphicsState
                            val (scale, rotation, tx, ty) = decompose(gs.currentTransformationMatrix)
                            val b64 = encodeBitmapToBase64(xobj.image)

                            collector(
                                CanvasElement(
                                    id         = currentLayerName,
                                    type       = ElementType.IMAGE,
                                    x          = tx,
                                    y          = ty,
                                    scale      = scale,
                                    rotation   = rotation,
                                    bitmapData = b64
                                )
                            )
                        }
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

    private fun decompose(m: Matrix): Transform {
        val a = m.scaleX; val b = m.shearY; val e = m.translateX; val f = m.translateY
        val scale = sqrt(a * a + b * b)
        val rotation = atan2(b, a) * (180f / PI.toFloat())
        return Transform(scale, rotation, e, f)
    }
}