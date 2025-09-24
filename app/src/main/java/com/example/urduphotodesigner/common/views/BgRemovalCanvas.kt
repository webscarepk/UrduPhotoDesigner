package com.example.urduphotodesigner.common.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

class BgRemovalCanvas @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class ToolMode { BRUSH, RECTANGLE, ELLIPSE }
    enum class ActionMode { ADD, REMOVE }
    private var isApplyingMask = false

    // Magnifier variables
    private var showMagnifier = false
    private var magnifierX = 0f
    private var magnifierY = 0f
    private val magnifierRadius = 120f
    private val magnifierScale = 2f
    private val magnifierOffset = 200f

    // Animation alpha (0 → hidden, 1 → fully visible)
    private var magnifierAlpha = 0f
    private var magnifierAnimator: ValueAnimator? = null

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())
    private var zoomSteps = floatArrayOf(1f, 2f, 4f, 8f)
    private var zoomIndex = 0

    private val drawMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private var scaleFactor = 1f

    private var isTransforming = false  // ✅ zoom/pan in progress

    private var toolMode: ToolMode? = ToolMode.BRUSH
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

    private val donePaths = mutableListOf<Path>()
    private val undonePaths = mutableListOf<Path>()

    private val strokePaintAdd = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 12f), dashPhase)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        isDither = true
        xfermode = null
    }

    private val strokePaintRemove = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 12f), dashPhase)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        isDither = true
        xfermode = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        // ✅ marching ants animator
        ValueAnimator.ofFloat(0f, 12f).apply {
            duration = 300
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                dashPhase += 2f
                strokePaintAdd.pathEffect = DashPathEffect(floatArrayOf(12f, 12f), dashPhase)
                strokePaintRemove.pathEffect = DashPathEffect(floatArrayOf(12f, 12f), dashPhase)
                postInvalidateOnAnimation() // lightweight invalidate
            }
            start()
        }
    }

    fun setImage(bitmap: Bitmap) {
        imageBitmap = bitmap
        if (width > 0 && height > 0) calculateImageRect()
        invalidate()
    }

    fun setToolMode(mode: ToolMode?) {
        toolMode = mode
        currentPath = null
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

    private suspend fun maskBufferToBitmap(buffer: FloatBuffer, maskWidth: Int, maskHeight: Int): Bitmap {
        return withContext(Dispatchers.Default) {
            buffer.rewind()
            val bitmap = createBitmap(maskWidth, maskHeight)
            for (y in 0 until maskHeight) {
                for (x in 0 until maskWidth) {
                    val confidence = buffer.get()
                    val color = if (confidence > 0.5f) Color.WHITE else Color.TRANSPARENT
                    bitmap.setPixel(x, y, color)
                }
            }
            bitmap
        }
    }

    fun applyGeneratedMask(buffer: FloatBuffer, maskWidth: Int, maskHeight: Int) {
        isApplyingMask = true
        scope.launch {
            val maskBitmap = maskBufferToBitmap(buffer, maskWidth, maskHeight)

            val scaled = withContext(Dispatchers.Default) {
                Bitmap.createScaledBitmap(
                    maskBitmap,
                    imageRect?.width()?.toInt() ?: maskWidth,
                    imageRect?.height()?.toInt() ?: maskHeight,
                    false
                )
            }

            subjectMaskBitmap = scaled

            val subjectPath = withContext(Dispatchers.Default) {
                maskToContourPath(scaled)
            }.apply {
                val matrix = Matrix()
                imageRect?.let { rect ->
                    matrix.setRectToRect(
                        RectF(0f, 0f, scaled.width.toFloat(), scaled.height.toFloat()),
                        rect,
                        Matrix.ScaleToFit.FILL
                    )
                    transform(matrix)
                }
            }
            commitPath(subjectPath)
            donePaths.add(Path(subjectPath))
            undonePaths.clear()
            isApplyingMask = false
            invalidate()
        }
    }

    private suspend fun maskToContourPath(mask: Bitmap): Path {
        return withContext(Dispatchers.Default) {
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
                            x = nx; y = ny
                            path.lineTo(x.toFloat(), y.toFloat())
                            visited[y][x] = true
                            dir = (ndir + 6) % 8
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
                        val isEdge = mask.getPixel(x - 1, y) == Color.TRANSPARENT ||
                                mask.getPixel(x + 1, y) == Color.TRANSPARENT ||
                                mask.getPixel(x, y - 1) == Color.TRANSPARENT ||
                                mask.getPixel(x, y + 1) == Color.TRANSPARENT
                        if (isEdge) traceContour(x, y)
                    }
                }
            }
            path
        }
    }

    private fun commitPath(newPath: Path) {
        val finalPath = Path(newPath)

        if (actionMode == ActionMode.ADD) {
            var merged = false
            val iterator = paths.listIterator()

            while (iterator.hasNext()) {
                val existing = iterator.next()
                val test = Path(existing)
                test.op(test, finalPath, Path.Op.INTERSECT)

                if (!test.isEmpty) { // ✅ overlap → merge
                    val union = Path(existing)
                    union.op(union, finalPath, Path.Op.UNION)
                    iterator.set(union)
                    donePaths.add(Path(union)) // save snapshot for undo
                    undonePaths.clear()        // new action clears redo history
                    merged = true
                    break
                }
            }

            if (!merged) {
                paths.add(finalPath)
                donePaths.add(Path(finalPath))
                undonePaths.clear()
            }
        } else if (actionMode == ActionMode.REMOVE) {
            val iterator = paths.listIterator()
            var removedRegion: RectF? = null

            while (iterator.hasNext()) {
                val existing = iterator.next()
                val diff = Path(existing)
                diff.op(diff, finalPath, Path.Op.DIFFERENCE)

                if (!diff.isEmpty) {
                    iterator.set(diff)
                    donePaths.add(Path(diff))
                    undonePaths.clear()

                    // Track the removed region for restoring the overlay
                    removedRegion = calculateRemovedRegion(existing, finalPath)
                } else {
                    iterator.remove()
                }
            }

            // Redraw the overlay in the removed area
            removedRegion?.let { restoreOverlay(it) }
        }

        invalidate()
    }

    private fun calculateRemovedRegion(existingPath: Path, removePath: Path): RectF {
        // Assuming removePath is a rectangle or a simple path, calculate the bounding box
        // of the region where removal happened.
        val bounds = RectF()
        existingPath.computeBounds(bounds, true)
        return bounds
    }

    private fun restoreOverlay(region: RectF) {
        // Restore the overlay in the region that was removed
        val canvas = Canvas()  // You can reuse the canvas where drawing occurs
        canvas.drawRect(region, whiteOverlayPaint)  // Draw the white overlay in the removed area
    }

    fun undo() {
        if (donePaths.isNotEmpty()) {
            val last = donePaths.removeAt(donePaths.size - 1)
            undonePaths.add(last)
            rebuildPaths()
            invalidate()
        }
    }

    fun redo() {
        if (undonePaths.isNotEmpty()) {
            val last = undonePaths.removeAt(undonePaths.size - 1)
            donePaths.add(last)
            rebuildPaths()
            invalidate()
        }
    }

    private fun rebuildPaths() {
        paths.clear()
        for (p in donePaths) {
            paths.add(Path(p))
        }
    }

    private fun calculateImageRect() {
        imageBitmap?.let { bmp ->
            val viewRatio = width.toFloat() / height.toFloat()
            val imgRatio = bmp.width.toFloat() / bmp.height.toFloat()

            imageRect = if (imgRatio > viewRatio) {
                val scaledHeight = width.toFloat() / imgRatio
                RectF(
                    0f, (height - scaledHeight) / 2f, width.toFloat(), (height + scaledHeight) / 2f
                )
            } else {
                val scaledWidth = height.toFloat() * imgRatio
                RectF((width - scaledWidth) / 2f, 0f, (width + scaledWidth) / 2f, height.toFloat())
            }
        }
    }

    private fun renderContent(canvas: Canvas) {
        imageBitmap?.let { bmp ->
            imageRect?.let { rect ->
                canvas.save()
                canvas.concat(drawMatrix)
                canvas.drawBitmap(bmp, null, rect, null)

                val saveCount = canvas.saveLayer(rect, null)
                canvas.drawRect(rect, whiteOverlayPaint)

                // Punch subject mask
                subjectMaskBitmap?.let { mask ->
                    val punchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                    }
                    canvas.drawBitmap(mask, null, rect, punchPaint)
                }

                // Punch user paths
                if (paths.isNotEmpty()) {
                    val merged = Path(paths[0])
                    for (i in 1 until paths.size) {
                        merged.op(merged, paths[i], Path.Op.UNION)
                    }
                    val punchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                    }
                    canvas.drawPath(merged, punchPaint)

                    // marching ants stroke
                    val strokePaint = Paint(strokePaintAdd).apply {
                        pathEffect = DashPathEffect(floatArrayOf(12f, 12f), dashPhase)
                    }
                    canvas.drawPath(merged, strokePaint)
                }

                canvas.restoreToCount(saveCount)
                canvas.restore()
            }
        }

        // Live preview path
        currentPath?.let {
            val paintStroke = if (actionMode == ActionMode.ADD) strokePaintAdd else strokePaintRemove
            canvas.save()
            canvas.concat(drawMatrix)
            when (toolMode) {
                ToolMode.BRUSH -> canvas.drawPath(it, paintStroke)
                ToolMode.RECTANGLE -> canvas.drawRect(startX, startY, endX, endY, paintStroke)
                ToolMode.ELLIPSE -> canvas.drawOval(RectF(startX, startY, endX, endY), paintStroke)
                else -> {}
            }
            canvas.restore()
        }

        val scaledStrokeWidth = 3f * (1 / scaleFactor).coerceAtLeast(0.5f)

        strokePaintAdd.strokeWidth = scaledStrokeWidth
        strokePaintRemove.strokeWidth = scaledStrokeWidth

        dashPhase += 2
        strokePaintAdd.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), dashPhase)
        strokePaintRemove.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), dashPhase)

        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Normal rendering
        renderContent(canvas)
        if (showMagnifier && magnifierAlpha > 0f) {
            val saveCount = canvas.save()

            val offsetY = if (magnifierY - magnifierOffset - magnifierRadius < 0)
                magnifierOffset else -magnifierOffset
            val magnifierCenterX = magnifierX
            val magnifierCenterY = magnifierY + offsetY

            // Clip circle
            val clipPath = Path().apply {
                addCircle(magnifierCenterX, magnifierCenterY, magnifierRadius, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)

            // Render full view into bitmap (with drawMatrix + overlays + paths)
            val fullBmp = renderToBitmap()

            // Define source rect around the finger
            val srcRect = Rect(
                (magnifierX - magnifierRadius / magnifierScale).toInt(),
                (magnifierY - magnifierRadius / magnifierScale).toInt(),
                (magnifierX + magnifierRadius / magnifierScale).toInt(),
                (magnifierY + magnifierRadius / magnifierScale).toInt()
            )

            // Destination rect = circle bounds
            val dstRect = Rect(
                (magnifierCenterX - magnifierRadius).toInt(),
                (magnifierCenterY - magnifierRadius).toInt(),
                (magnifierCenterX + magnifierRadius).toInt(),
                (magnifierCenterY + magnifierRadius).toInt()
            )

            // Draw cropped + scaled area
            canvas.drawBitmap(fullBmp, srcRect, dstRect, null)

            // Border circle
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 4f
                alpha = (magnifierAlpha * 255).toInt()
            }
            canvas.drawCircle(magnifierCenterX, magnifierCenterY, magnifierRadius, borderPaint)

            canvas.restoreToCount(saveCount)
        }
    }

    private fun renderToBitmap(): Bitmap {
        val bmp = createBitmap(width, height)
        val c = Canvas(bmp)
        renderContent(c) // <-- this includes drawMatrix, overlay, paths
        return bmp
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scaleHandled = scaleDetector.onTouchEvent(event)

        if (event.pointerCount >= 2) {
            // Hide the magnifier during zooming
            if (showMagnifier) {
                animateMagnifier(false) // Fade out magnifier when zooming starts
            }
            return true
        }

        // Prevent magnifier from showing if mask is being applied
        if (isApplyingMask) {
            if (showMagnifier) {
                animateMagnifier(false) // Ensure magnifier is hidden during mask application
            }
            return true
        }

        // ✅ Brush/Shape Drawing
        if (event.pointerCount == 1 && toolMode != null) {
            when (toolMode) {
                ToolMode.BRUSH -> handleBrush(event)
                ToolMode.RECTANGLE, ToolMode.ELLIPSE -> handleShape(event)
                else -> {}
            }
            return true
        }

        // ✅ Double tap works regardless of tool
        val gestureHandled = gestureDetector.onTouchEvent(event)

        // ✅ Pinch zoom
        if (event.pointerCount >= 2) {
            isTransforming = true
            return true
        }

        // ✅ Pan Mode
        if (toolMode == null && event.pointerCount == 1) {
            if (event.action == MotionEvent.ACTION_MOVE && event.historySize > 0) {
                val dx = event.x - event.getHistoricalX(0)
                val dy = event.y - event.getHistoricalY(0)
                drawMatrix.postTranslate(dx, dy)
                clampTranslation()
                invalidate()
            }
            return true
        }

        return scaleHandled || gestureHandled || super.onTouchEvent(event)
    }

    inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            isTransforming = true

            // Move to next step
            zoomIndex = (zoomIndex + 1) % zoomSteps.size
            val targetZoom = zoomSteps[zoomIndex]

            // Animate smoothly
            animateZoom(targetZoom, e.x, e.y)
            toolMode = null
            return true
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (scaleFactor > 1f) {
                isTransforming = true
                drawMatrix.postTranslate(-distanceX, -distanceY)
                clampTranslation()
                drawMatrix.invert(inverseMatrix)
                invalidate()
                toolMode = null
                return true
            }
            return false
        }
    }

    private fun animateZoom(target: Float, focusX: Float, focusY: Float) {
        val start = scaleFactor
        val animator = ValueAnimator.ofFloat(start, target)
        animator.duration = 250
        animator.addUpdateListener { valueAnimator ->
            val scale = valueAnimator.animatedValue as Float
            val factor = scale / scaleFactor
            drawMatrix.postScale(factor, factor, focusX, focusY)
            scaleFactor = scale
            clampTranslation()
            drawMatrix.invert(inverseMatrix)
            invalidate()
        }
        animator.start()
    }

    private fun getTransformedRect(): RectF? {
        imageRect?.let { rect ->
            val transformed = RectF(rect)
            drawMatrix.mapRect(transformed)
            return transformed
        }
        return null
    }

    private fun clampTranslation() {
        val transformed = getTransformedRect() ?: return

        var dx = 0f
        var dy = 0f

        if (transformed.width() <= width) {
            // Center horizontally
            dx = width / 2f - transformed.centerX()
        } else {
            if (transformed.left > 0) dx = -transformed.left
            if (transformed.right < width) dx = width - transformed.right
        }

        if (transformed.height() <= height) {
            // Center vertically
            dy = height / 2f - transformed.centerY()
        } else {
            if (transformed.top > 0) dy = -transformed.top
            if (transformed.bottom < height) dy = height - transformed.bottom
        }

        drawMatrix.postTranslate(dx, dy)
        drawMatrix.invert(inverseMatrix)
    }

    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            isTransforming = true
            val scale = detector.scaleFactor
            val newScale = (scaleFactor * scale).coerceAtMost(5f)

            val factor = newScale / scaleFactor
            scaleFactor = newScale
            drawMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            clampTranslation()
            drawMatrix.invert(inverseMatrix)
            invalidate()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            super.onScaleEnd(detector)
            // Stop transforming → require re-pick tool
            if (scaleFactor < 1f) {
                animateZoom(1f, width / 2f, height / 2f) // snap back to 1f
                zoomIndex = 0 // reset step cycle
            }
            toolMode = null
            isTransforming = false
        }
    }

    private fun mapToImageCoords(x: Float, y: Float): FloatArray {
        val pts = floatArrayOf(x, y)
        inverseMatrix.mapPoints(pts)
        // Adjust to rect space
        imageRect?.let {
            pts[0] = pts[0].coerceIn(it.left, it.right)
            pts[1] = pts[1].coerceIn(it.top, it.bottom)
        }
        return pts
    }

    private fun animateMagnifier(show: Boolean) {
        magnifierAnimator?.cancel()
        val start = magnifierAlpha
        val end = if (show) 1f else 0f

        magnifierAnimator = ValueAnimator.ofFloat(start, end).apply {
            duration = 200
            addUpdateListener {
                magnifierAlpha = it.animatedValue as Float
                if (!show && magnifierAlpha == 0f) {
                    showMagnifier = false
                }
                invalidate()
            }
            start()
        }
    }

    private fun handleBrush(event: MotionEvent) {
        val mapped = mapToImageCoords(event.x, event.y)
        val x = mapped[0]
        val y = mapped[1]

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = Path().apply { moveTo(x, y) }

                // 🔹 Magnifier start
                magnifierX = event.x
                magnifierY = event.y
                if (!showMagnifier) {
                    showMagnifier = true
                    animateMagnifier(true) // fade in
                }

                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                currentPath?.lineTo(x, y)

                // 🔹 Update magnifier position
                magnifierX = event.x
                magnifierY = event.y

                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                currentPath?.let { commitPath(it) }
                currentPath = null

                // 🔹 Hide magnifier
                if (showMagnifier) {
                    animateMagnifier(false) // fade out
                }

                invalidate()
            }
        }
    }

    private fun handleShape(event: MotionEvent) {
        val mapped = mapToImageCoords(event.x, event.y)
        val x = mapped[0]
        val y = mapped[1]

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = x
                startY = y
                endX = startX
                endY = startY

                // 🔹 Magnifier start
                magnifierX = event.x
                magnifierY = event.y
                if (!showMagnifier) {
                    showMagnifier = true
                    animateMagnifier(true) // fade in
                }

                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                endX = x
                endY = y

                // 🔹 create preview path
                val preview = Path()
                if (toolMode == ToolMode.RECTANGLE) {
                    preview.addRect(startX, startY, endX, endY, Path.Direction.CW)
                } else if (toolMode == ToolMode.ELLIPSE) {
                    preview.addOval(RectF(startX, startY, endX, endY), Path.Direction.CW)
                }
                currentPath = preview

                // 🔹 Update magnifier position
                magnifierX = event.x
                magnifierY = event.y

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
                currentPath = null

                // 🔹 Hide magnifier
                if (showMagnifier) {
                    animateMagnifier(false) // fade out
                }

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

            val addPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }

            for (path in paths) {
                maskCanvas.drawPath(path, addPaint)
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
