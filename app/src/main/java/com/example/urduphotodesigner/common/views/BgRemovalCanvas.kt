package com.example.urduphotodesigner.common.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.createBitmap

class BgRemovalCanvas @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class ToolMode { BRUSH, RECTANGLE, ELLIPSE }
    enum class ActionMode { ADD, REMOVE }

    private var toolMode: ToolMode = ToolMode.BRUSH
    private var actionMode: ActionMode = ActionMode.ADD

    private val paths = mutableListOf<Pair<Path, ActionMode>>() // store paths with mode
    private var currentPath: Path? = null

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f

    private var imageBitmap: Bitmap? = null
    private var imageRect: RectF? = null // where image is drawn (keeps aspect ratio)

    // marching ants
    private var dashPhase = 0f

    private val whiteOverlayPaint = Paint().apply {
        color = Color.argb(150, 255, 255, 255) // semi-transparent white
        style = Paint.Style.FILL
    }

    private val strokePaintAdd = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), dashPhase)
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

        // draw image
        imageBitmap?.let { bmp ->
            imageRect?.let { rect ->
                canvas.drawBitmap(bmp, null, rect, null)
            }
        }

        // draw soft white overlay
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), whiteOverlayPaint)

        // subtract/add selections
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        val addPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        val removePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 255, 255, 255) // extra white for REMOVE
            style = Paint.Style.FILL
        }

        val saveLayer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), whiteOverlayPaint)

        for ((path, mode) in paths) {
            if (mode == ActionMode.ADD) {
                canvas.drawPath(path, addPaint) // remove overlay
                canvas.drawPath(path, strokePaintAdd) // stroke
            } else {
                canvas.drawPath(path, removePaint) // add overlay
                canvas.drawPath(path, strokePaintRemove) // stroke
            }
        }

        currentPath?.let {
            val paintStroke = if (actionMode == ActionMode.ADD) strokePaintAdd else strokePaintRemove
            if (toolMode == ToolMode.BRUSH) {
                canvas.drawPath(it, paintStroke)
            } else if (toolMode == ToolMode.RECTANGLE) {
                canvas.drawRect(startX, startY, endX, endY, paintStroke)
            } else if (toolMode == ToolMode.ELLIPSE) {
                canvas.drawOval(RectF(startX, startY, endX, endY), paintStroke)
            }
        }

        canvas.restoreToCount(saveLayer)

        // marching ants animate
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
                currentPath?.let { paths.add(it to actionMode) }
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
                paths.add(path to actionMode)
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
            val removePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }

            for ((path, mode) in paths) {
                maskCanvas.drawPath(path, if (mode == ActionMode.ADD) addPaint else removePaint)
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
