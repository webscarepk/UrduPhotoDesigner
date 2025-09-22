package com.example.urduphotodesigner.common.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.common.InputImage
import java.nio.FloatBuffer
import androidx.core.graphics.scale

class BgRemovalCanvas @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class ToolMode { BRUSH, RECTANGLE, ELLIPSE }
    enum class ActionMode { ADD, REMOVE }

    private var toolMode: ToolMode = ToolMode.BRUSH
    private var actionMode: ActionMode = ActionMode.ADD

    private val paths = mutableListOf<Path>() // store paths with mode
    private var currentPath: Path? = null

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f

    private var imageBitmap: Bitmap? = null
    private var imageRect: RectF? = null // where image is drawn (keeps aspect ratio)

    // marching ants
    private var dashPhase = 0f
    private var subjectMaskBitmap: Bitmap? = null

    private val whiteOverlayPaint = Paint().apply {
        color = Color.argb(150, 255, 255, 255) // semi-transparent white
        style = Paint.Style.FILL
    }

    private val strokePaintAdd = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 12f), dashPhase)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        xfermode = null
    }

    private val strokePaintRemove = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), dashPhase)
    }

    fun setImage(bitmap: Bitmap) {
        imageBitmap = bitmap
        if (width > 0 && height > 0) calculateImageRect()
        invalidate()
    }

    fun setToolMode(mode: ToolMode) {
        toolMode = mode
        invalidate()
    }

    fun setActionMode(mode: ActionMode) {
        actionMode = mode
    }

    fun clearSelection() {
        paths.clear()
        currentPath = null
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateImageRect()
    }

    private fun maskBufferToBitmap(buffer: FloatBuffer, maskWidth: Int, maskHeight: Int): Bitmap {
        buffer.rewind()
        val bitmap = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)

        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                val confidence = buffer.get()
                val color = if (confidence > 0.5f) {
                    Color.WHITE  // subject = opaque
                } else {
                    Color.TRANSPARENT // background = transparent, NOT black
                }
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }

    fun applyGeneratedMask(buffer: FloatBuffer, maskWidth: Int, maskHeight: Int) {
        val maskBitmap = maskBufferToBitmap(buffer, maskWidth, maskHeight)

        imageRect?.let { rect ->
            val scaled = Bitmap.createScaledBitmap(
                maskBitmap,
                rect.width().toInt(),
                rect.height().toInt(),
                false // no smoothing, keep hard edges
            )
            subjectMaskBitmap = scaled

            val subjectPath = maskToContourPath(subjectMaskBitmap!!).apply {
                val matrix = Matrix()
                matrix.setRectToRect(
                    RectF(0f, 0f, subjectMaskBitmap!!.width.toFloat(), subjectMaskBitmap!!.height.toFloat()),
                    rect,
                    Matrix.ScaleToFit.FILL
                )
                transform(matrix)
            }
            commitPath(subjectPath)
        }

        invalidate()
    }

    private fun maskToContourPath(mask: Bitmap): Path {
        val path = Path()
        val w = mask.width
        val h = mask.height

        val visited = Array(h) { BooleanArray(w) }

        fun traceContour(startX: Int, startY: Int) {
            var x = startX
            var y = startY
            path.moveTo(x.toFloat(), y.toFloat())

            val dirs = arrayOf(
                intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(0, 1), intArrayOf(-1, 1),
                intArrayOf(-1, 0), intArrayOf(-1, -1), intArrayOf(0, -1), intArrayOf(1, -1)
            )

            var dir = 0
            do {
                var found = false
                for (i in 0 until 8) {
                    val ndir = (dir + i) % 8
                    val nx = x + dirs[ndir][0]
                    val ny = y + dirs[ndir][1]
                    if (nx in 0 until w && ny in 0 until h && mask.getPixel(nx, ny) == Color.WHITE) {
                        x = nx
                        y = ny
                        path.lineTo(x.toFloat(), y.toFloat())
                        visited[y][x] = true
                        dir = (ndir + 6) % 8 // turn left next
                        found = true
                        break
                    }
                }
                if (!found) break
            } while (!(x == startX && y == startY))
            path.close()
        }

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                if (!visited[y][x] && mask.getPixel(x, y) == Color.WHITE) {
                    val isEdge =
                        mask.getPixel(x - 1, y) == Color.TRANSPARENT ||
                                mask.getPixel(x + 1, y) == Color.TRANSPARENT ||
                                mask.getPixel(x, y - 1) == Color.TRANSPARENT ||
                                mask.getPixel(x, y + 1) == Color.TRANSPARENT
                    if (isEdge) {
                        traceContour(x, y)
                    }
                }
            }
        }

        return path
    }

    private fun commitPath(newPath: Path) {
        if (actionMode == ActionMode.ADD) {
            var merged = false
            val iterator = paths.listIterator()

            while (iterator.hasNext()) {
                val existing = iterator.next()
                val test = Path(existing)
                test.op(test, newPath, Path.Op.INTERSECT)

                if (!test.isEmpty) { // ✅ they overlap
                    val union = Path(existing)
                    union.op(union, newPath, Path.Op.UNION)
                    iterator.set(union)   // replace existing with merged
                    merged = true
                    break
                }
            }

            if (!merged) {
                paths.add(Path(newPath)) // no overlap → new independent path
            }
        } else if (actionMode == ActionMode.REMOVE) {
            val iterator = paths.listIterator()
            while (iterator.hasNext()) {
                val existing = iterator.next()
                val diff = Path(existing)
                diff.op(diff, newPath, Path.Op.DIFFERENCE)

                if (!diff.isEmpty) {
                    iterator.set(diff)
                } else {
                    iterator.remove()
                }
            }
        }

        invalidate()
    }

    private fun calculateImageRect() {
        imageBitmap?.let { bmp ->
            val viewRatio = width.toFloat() / height.toFloat()
            val imgRatio = bmp.width.toFloat() / bmp.height.toFloat()

            imageRect = if (imgRatio > viewRatio) {
                val scaledHeight = width.toFloat() / imgRatio
                RectF(0f, (height - scaledHeight) / 2f, width.toFloat(), (height + scaledHeight) / 2f)
            } else {
                val scaledWidth = height.toFloat() * imgRatio
                RectF((width - scaledWidth) / 2f, 0f, (width + scaledWidth) / 2f, height.toFloat())
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw original image
        imageBitmap?.let { bmp ->
            imageRect?.let { rect ->
                // draw image
                canvas.drawBitmap(bmp, null, rect, null)

                // 🔹 Open a saveLayer for overlay + selections
                val saveCount = canvas.saveLayer(rect, null)

                // Step 1: draw semi-transparent overlay
                canvas.drawRect(rect, whiteOverlayPaint)

                // Step 2: punch subject mask
                subjectMaskBitmap?.let { mask ->
                    val punchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                    }
                    canvas.drawBitmap(mask, null, rect, punchPaint)
                }

                // Step 3: punch user ADD/REMOVE paths inside same layer
                val addPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                }

                for (path in paths) {
                    canvas.drawPath(path, addPaint)

                    val strokePaint = Paint(strokePaintAdd).apply {
                        pathEffect = DashPathEffect(floatArrayOf(12f, 12f), dashPhase)
                    }
                    canvas.drawPath(path, strokePaint)
                }

                // 🔹 Close the saveLayer → overlay + mask + paths merged
                canvas.restoreToCount(saveCount)
            }
        }

//        // 4. Draw contour marching ants (subject auto-select)
//        subjectContourPath?.let { contour ->
//            val strokePaint = Paint(strokePaintAdd).apply {
//                pathEffect = DashPathEffect(floatArrayOf(12f, 12f), dashPhase)
//            }
//            canvas.drawPath(contour, strokePaint)
//        }

        // 5. Draw currently active shape preview (not committed yet)
        currentPath?.let {
            val paintStroke = if (actionMode == ActionMode.ADD) strokePaintAdd else strokePaintRemove
            when (toolMode) {
                ToolMode.BRUSH -> canvas.drawPath(it, paintStroke)
                ToolMode.RECTANGLE -> canvas.drawRect(startX, startY, endX, endY, paintStroke)
                ToolMode.ELLIPSE -> canvas.drawOval(RectF(startX, startY, endX, endY), paintStroke)
            }
        }

        // marching ants animation
        dashPhase += 2f
        strokePaintAdd.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), dashPhase)
        strokePaintRemove.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), dashPhase)
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (toolMode) {
            ToolMode.BRUSH -> handleBrush(event)
            ToolMode.RECTANGLE, ToolMode.ELLIPSE -> handleShape(event)
        }
        return true
    }

    private fun handleBrush(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> currentPath = Path().apply { moveTo(event.x, event.y) }
            MotionEvent.ACTION_MOVE -> {
                currentPath?.lineTo(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                currentPath?.let { commitPath(it) }
                currentPath = null
                invalidate()
            }
        }
    }

    private fun handleShape(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                endX = startX
                endY = startY
            }
            MotionEvent.ACTION_MOVE -> {
                endX = event.x
                endY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val path = Path()
                if (toolMode == ToolMode.RECTANGLE) {
                    path.addRect(startX, startY, endX, endY, Path.Direction.CW)
                } else if (toolMode == ToolMode.ELLIPSE) {
                    path.addOval(RectF(startX, startY, endX, endY), Path.Direction.CW)
                }
                commitPath(path)
                invalidate()
            }
        }
    }

    fun exportMaskedImage(): Bitmap? {
        imageBitmap?.let { bmp ->
            val output = createBitmap(width, height)
            val canvas = Canvas(output)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            imageRect?.let { rect ->
                canvas.drawBitmap(bmp, null, rect, null)
            }

            val mask = createBitmap(width, height)
            val maskCanvas = Canvas(mask)

            val addPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }

            for (path in paths) {
                maskCanvas.drawPath(path,addPaint)
            }

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            canvas.drawBitmap(mask, 0f, 0f, paint)

            return output
        }
        return null
    }
}
