package com.example.urduphotodesigner.common.utils

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.util.Base64
import com.example.urduphotodesigner.common.canvas.enums.ElementType
import com.example.urduphotodesigner.common.canvas.model.CanvasElement
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
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

    private var currentPath = mutableListOf<PointF>()
    private var pathClosed = false

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
                onCanvasSize(CanvasSize("", 0, ptToPx(box.width), ptToPx(box.height)))
            }
            processPage(page as PDPage)
        }
    }

    fun ptToPx(pt: Float, dpi: Float = 300f): Float =
        pt * (dpi / 72f)

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
            "m", "l", "c", "h", "re", "f", "F", "f*", "S", "B", "B*", "b", "b*", "sh" -> {
                when (operator.name) {
                    "m" -> moveTo(operands)
                    "l" -> lineTo(operands)
                    "c" -> curveTo(operands)
                    "h" -> closePath()
                    "re" -> handleRectFill(operands) // Already exists
                    "f", "F", "f*" -> fillPath()
                    "S" -> strokePath()
                    "B", "B*", "b", "b*" -> fillAndStrokePath()
                    "sh" -> handleShading(operands)
                }
            }
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

    private fun transformPoint(x: Float, y: Float): PointF {
        val point = PointF(x, y)
        val transformed = graphicsState.currentTransformationMatrix.transformer(point)
        return PointF(transformed.x, pdfHeight - transformed.y)
    }

    private fun fillPath() {
        if (currentPath.isNotEmpty()) {
            val color = resolveColor(graphicsState.nonStrokingColor)
            val alpha = (graphicsState.nonStrokeAlphaConstant * 255).toInt()

            val bitmap = pathToBitmap(currentPath, color, alpha, filled = true)
            val b64 = encodeBitmapToBase64(bitmap)

            val bounds = getPathBounds(currentPath)

            onElement(
                CanvasElement(
                    type = ElementType.IMAGE,
                    x = bounds.left,
                    y = bounds.top,
                    bitmap = bitmap,
                    bitmapData = b64,
                    paintAlpha = alpha,
                    logicalContentWidth = bounds.width(),
                    logicalContentHeight = bounds.height(),
                    id = "${currentLayerName}_${uniqueElementId++}"
                )
            )
            currentPath.clear()
            pathClosed = false
        }
    }

    private fun handleShading(operands: List<COSBase>) {
        val name = operands[0] as? COSName ?: return
        val shading = resources.getShading(name) ?: return

        // Use a placeholder gradient or render actual shading if needed
        val placeholder = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(placeholder)
        val paint = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 100f, 100f,
                Color.LTGRAY, Color.DKGRAY,
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, 100f, 100f, paint)

        val b64 = encodeBitmapToBase64(placeholder)

        onElement(
            CanvasElement(
                type = ElementType.IMAGE,
                x = 0f,
                y = 0f,
                bitmap = placeholder,
                bitmapData = b64,
                paintAlpha = 255,
                logicalContentWidth = 100f,
                logicalContentHeight = 100f,
                id = "${currentLayerName}_${uniqueElementId++}"
            )
        )
    }

    private fun transformAndFlipPoint(matrix: Matrix, x: Float, y: Float): PointF {
        val raw = PointF(x, y)
        val transformed = matrix.transformer(raw)
        return PointF(transformed.x, pdfHeight - transformed.y)
    }

    private fun pathToBitmap(path: List<PointF>, color: Int, alpha: Int, filled: Boolean): Bitmap {
        val bounds = path.fold(RectF()) { rect, point ->
            rect.union(point.x, point.y)
            rect
        }

        val width = max(1f, bounds.width()).toInt()
        val height = max(1f, bounds.height()).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val paint = android.graphics.Paint().apply {
            this.color = color
            this.alpha = alpha
            this.style = if (filled) android.graphics.Paint.Style.FILL else android.graphics.Paint.Style.STROKE
            this.strokeWidth = 2f
            isAntiAlias = true
        }

        val androidPath = android.graphics.Path().apply {
            if (path.isNotEmpty()) {
                moveTo(path[0].x - bounds.left, path[0].y - bounds.top)
                for (i in 1 until path.size) {
                    lineTo(path[i].x - bounds.left, path[i].y - bounds.top)
                }
            }
        }

        canvas.drawPath(androidPath, paint)
        return bitmap
    }

    private fun strokePath() {
        if (currentPath.isNotEmpty()) {
            val color = resolveColor(graphicsState.strokingColor)
            val alpha = (graphicsState.nonStrokeAlphaConstant * 255).toInt()

            val bitmap = pathToBitmap(currentPath, color, alpha, filled = false)
            val b64 = encodeBitmapToBase64(bitmap)

            val bounds = getPathBounds(currentPath)

            onElement(
                CanvasElement(
                    type = ElementType.IMAGE,
                    x = bounds.left,
                    y = bounds.top,
                    bitmap = bitmap,
                    bitmapData = b64,
                    paintAlpha = alpha,
                    logicalContentWidth = bounds.width(),
                    logicalContentHeight = bounds.height(),
                    id = "${currentLayerName}_${uniqueElementId++}"
                )
            )
            currentPath.clear()
            pathClosed = false
        }
    }

    private fun fillAndStrokePath() {
        fillPath()
        strokePath()
    }

    private fun moveTo(operands: List<COSBase>) {
        val x = (operands[0] as COSNumber).floatValue()
        val y = (operands[1] as COSNumber).floatValue()
        currentPath.clear()
        currentPath.add(transformPoint(x, y))
    }

    private fun lineTo(operands: List<COSBase>) {
        val x = (operands[0] as COSNumber).floatValue()
        val y = (operands[1] as COSNumber).floatValue()
        currentPath.add(transformPoint(x, y))
    }

    private fun curveTo(operands: List<COSBase>) {
        // Simplified: treat as lines for now
        val x3 = (operands[4] as COSNumber).floatValue()
        val y3 = (operands[5] as COSNumber).floatValue()
        currentPath.add(transformPoint(x3, y3))
    }

    private fun closePath() {
        pathClosed = true
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
        val fullMatrix = tm.multiply(ctm)

        val pos = transformAndFlipPoint(fullMatrix, 0f, 0f)

        val tx = pos.x
        val ty = pos.y

        val scaleX = sqrt(fullMatrix.scaleX * fullMatrix.scaleX + fullMatrix.shearX * fullMatrix.shearX)
        val scaleY = sqrt(fullMatrix.scaleY * fullMatrix.scaleY + fullMatrix.shearY * fullMatrix.shearY)
        val rotation = atan2(fullMatrix.shearY, fullMatrix.scaleY) * (180f / PI.toFloat())

        val fontSize = gs.textState.fontSize
        val paintTextSize = fontSize * scaleX

        val color = resolveColor(gs.nonStrokingColor)
        val paintAlpha = (gs.nonStrokeAlphaConstant * 255).toInt()

        onElement(
            CanvasElement(
                type = ElementType.TEXT,
                text = text,
                x = tx,
                y = ty - (fontSize * 0.75f * scaleY), // vertical alignment fix
                rotation = rotation,
                paintColor = color,
                paintTextSize = paintTextSize,
                paintAlpha = paintAlpha,
                id = "${currentLayerName}_${uniqueElementId++}"
            )
        )
    }

    private fun getPathBounds(path: List<PointF>): RectF {
        val rect = RectF()
        path.forEach { point -> rect.union(point.x, point.y) }
        return rect
    }

    private fun resolveColor(pdColor: PDColor): Int {
        return try {
            val rgb = pdColor.colorSpace.toRGB(pdColor.components)
            val r = (rgb[0] * 255).toInt().coerceIn(0, 255)
            val g = (rgb[1] * 255).toInt().coerceIn(0, 255)
            val b = (rgb[2] * 255).toInt().coerceIn(0, 255)
            Color.rgb(r, g, b)
        } catch (e: Exception) {
            Color.BLACK
        }
    }

    private fun handleImage(image: PDImageXObject) {
        val gs = graphicsState
        val m = gs.currentTransformationMatrix

        val pos = transformAndFlipPoint(m, 0f, 0f)

        val tx = pos.x
        val ty = pos.y

        val scaleX = sqrt(m.scaleX * m.scaleX + m.shearX * m.shearX)
        val scaleY = sqrt(m.scaleY * m.scaleY + m.shearY * m.shearY)

        val width = image.width * scaleX
        val height = image.height * scaleY

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
                y = ty - height, // move down by height
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
        // 1. Raw PDF values
        val rawX = (operands[0] as COSNumber).floatValue()
        val rawY = (operands[1] as COSNumber).floatValue()
        val w    = (operands[2] as COSNumber).floatValue()
        val h    = (operands[3] as COSNumber).floatValue()

        // 2. Grab the current transformation matrix
        val ctm = graphicsState.currentTransformationMatrix

        // 3. Compute the four corners (so we know true width/height in device space)
        val topLeft     = transformAndFlipPoint(ctm, rawX,     rawY + h)
        val topRight    = transformAndFlipPoint(ctm, rawX + w, rawY + h)
        val bottomLeft  = transformAndFlipPoint(ctm, rawX,     rawY)

        // 4. Device‑space dimensions
        val devWidth  = abs(topRight.x  - topLeft.x)
        val devHeight = abs(topLeft.y   - bottomLeft.y)

        // 5. Paint info (your existing logic)
        val color = graphicsState.nonStrokingColor
            .components
            .map { (it * 255).toInt().coerceIn(0,255) }
            .let { comps ->
                when (comps.size) {
                    4 -> Color.argb(comps[3], comps[0], comps[1], comps[2])
                    3 -> Color.rgb(comps[0], comps[1], comps[2])
                    else -> Color.GRAY
                }
            }
        val alpha = (graphicsState.nonStrokeAlphaConstant * 255).toInt()
        val type  = if (!backgroundHandled && w > (pdfWidth * 0.9f) && h > (pdfHeight * 0.9f)) {
            backgroundHandled = true
            ElementType.BACKGROUND
        } else {
            ElementType.IMAGE
        }

        // 6. Emit at the transformed top‑left
        onElement(CanvasElement(
            type                = type,
            x                   = topLeft.x,
            y                   = topLeft.y,
            paintColor          = color,
            paintAlpha          = alpha,
            logicalContentWidth = devWidth,
            logicalContentHeight= devHeight,
            id                  = "${currentLayerName}_${uniqueElementId++}"
        ))
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
