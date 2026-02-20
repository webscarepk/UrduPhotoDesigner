package com.webscare.urducanvas.common.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
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
import androidx.core.graphics.get
import androidx.core.graphics.scale
import androidx.core.graphics.set
import androidx.core.graphics.withMatrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.sqrt

class BgRemovalCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    var onToolModeChanged: ((ToolMode?) -> Unit)? = null,
    var onActionModeChanged: ((ActionMode) -> Unit)? = null,
    var onPreviewChanged: ((Boolean) -> Unit)? = null,
    var onMaskConfirmed: ((Bitmap) -> Unit)? = null,
    var onProcessingChanged: ((Boolean) -> Unit)? = null,
    var onProcessingCancelled: (() -> Unit)? = null
) : View(context, attrs) {


    enum class ToolMode { BRUSH, RECTANGLE, ELLIPSE, MAGIC_WAND }
    enum class ActionMode { ADD, REMOVE }

    private var previewEnabled = false
    private var isApplyingMask = false

    // Magnifier variables
    private var showMagnifier = false
    private var magnifierX = 0f
    private var magnifierY = 0f
    private val magnifierRadius = 150f
    private val magnifierScale = 2f
    private val magnifierOffset = 300f

    private var wandTolerance: Int = 32

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
    private val paths = mutableListOf<Path>()
    private val donePaths = mutableListOf<Path>()
    private val undonePaths = mutableListOf<Path>()
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

    // Cached merged selection path (computed when paths change)
    private var selectionPath: Path? = null

    private var isRenderCacheDirty = true

    private var edgeMap: Bitmap? = null

    private val whiteOverlayPaint = Paint().apply {
        color = Color.argb(150, 255, 255, 255) // semi-transparent white
        style = Paint.Style.FILL
    }

    private val strokePaintAdd = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
        Paint.setPathEffect = DashPathEffect(floatArrayOf(12f, 12f), dashPhase)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        isDither = true
        Paint.setXfermode = null
    }

    private val strokePaintRemove = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
        Paint.setPathEffect = DashPathEffect(floatArrayOf(12f, 12f), dashPhase)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        isDither = true
        Paint.setXfermode = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isCancelled = false

    init {
        // ✅ marching ants animator
        ValueAnimator.ofFloat(0f, 12f).apply {
            ValueAnimator.setDuration = 500
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                dashPhase += 2f
                strokePaintAdd.pathEffect = DashPathEffect(floatArrayOf(12f, 12f), dashPhase)
                strokePaintRemove.pathEffect = DashPathEffect(floatArrayOf(12f, 12f), dashPhase)
                // Keep animation driven by animator — only invalidate here
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    fun setImage(bitmap: Bitmap) {
        imageBitmap = bitmap
        if (width > 0 && height > 0) calculateImageRect()
        scope.launch(Dispatchers.Default) {
            edgeMap = computeEdgeMap(bitmap)
        }
        markRenderCacheDirty()
        invalidate()
    }

    fun setPreviewMode(enabled: Boolean) {
        previewEnabled = enabled
        onPreviewChanged?.invoke(enabled)
        invalidate()
    }

    fun setToolMode(mode: ToolMode?) {
        toolMode = mode
        currentPath = null
        onToolModeChanged?.invoke(mode)
        invalidate()
    }

    fun getToolMode(): ToolMode? {
        return toolMode
    }

    fun setActionMode(mode: ActionMode) {
        actionMode = mode
        onActionModeChanged?.invoke(mode)
    }

    fun clearSelection() {
        paths.clear()
        donePaths.clear()
        undonePaths.clear()
        selectionPath = null
        markRenderCacheDirty()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateImageRect()
        markRenderCacheDirty()
    }

    private fun computeEdgeMap(bmp: Bitmap): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val edge = createBitmap(w, h)

        val gx = arrayOf(intArrayOf(-1, 0, 1), intArrayOf(-2, 0, 2), intArrayOf(-1, 0, 1))
        val gy = arrayOf(intArrayOf(-1, -2, -1), intArrayOf(0, 0, 0), intArrayOf(1, 2, 1))

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var sumX = 0
                var sumY = 0
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val color = bmp[x + kx, y + ky]
                        val gray = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3
                        sumX += gx[ky + 1][kx + 1] * gray
                        sumY += gy[ky + 1][kx + 1] * gray
                    }
                }
                val mag = (sqrt((sumX * sumX + sumY * sumY).toDouble())).toInt().coerceIn(0, 255)
                edge[x, y] = Color.rgb(mag, mag, mag)
            }
        }
        return edge
    }

    private suspend fun maskBufferToBitmap(
        buffer: FloatBuffer, maskWidth: Int, maskHeight: Int
    ): Bitmap {
        return withContext(Dispatchers.Default) {
            buffer.rewind()
            val bitmap = createBitmap(maskWidth, maskHeight)
            for (y in 0 until maskHeight) {
                for (x in 0 until maskWidth) {
                    val confidence = buffer.get()
                    val color = if (confidence > 0.5f) Color.WHITE else Color.TRANSPARENT
                    bitmap[x, y] = color
                }
            }
            bitmap
        }
    }

    fun applyGeneratedMask(buffer: FloatBuffer, maskWidth: Int, maskHeight: Int) {
        isApplyingMask = true
        scope.launch {
            onProcessingChanged?.invoke(true)
            val maskBitmap = maskBufferToBitmap(buffer, maskWidth, maskHeight)

            val scaled = withContext(Dispatchers.Default) {
                maskBitmap.scale(
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
            withContext(Dispatchers.Main) {
                commitPath(subjectPath)
                isApplyingMask = false
                onProcessingChanged?.invoke(false)
                invalidate()
            }
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
                    intArrayOf(1, 0),
                    intArrayOf(1, 1),
                    intArrayOf(0, 1),
                    intArrayOf(-1, 1),
                    intArrayOf(-1, 0),
                    intArrayOf(-1, -1),
                    intArrayOf(0, -1),
                    intArrayOf(1, -1)
                )
                var dir = 0
                do {
                    var found = false
                    for (i in 0 until 8) {
                        val ndir = (dir + i) % 8
                        val nx = x + dirs[ndir][0]
                        val ny = y + dirs[ndir][1]
                        if (nx in 0 until w && ny in 0 until h && mask[nx, ny] == Color.WHITE) {
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
                    if (!visited[y][x] && mask[x, y] == Color.WHITE) {
                        val isEdge =
                            mask[x - 1, y] == Color.TRANSPARENT || mask[x + 1, y] == Color.TRANSPARENT || mask[x, y - 1] == Color.TRANSPARENT || mask[x, y + 1] == Color.TRANSPARENT
                        if (isEdge) traceContour(x, y)
                    }
                }
            }
            path
        }
    }

    private fun commitPath(newPath: Path) {
        val finalPath = Path(newPath)

        if (selectionPath == null) {
            selectionPath = Path(finalPath)
        } else {
            val result = Path(selectionPath)
            if (actionMode == ActionMode.ADD) {
                result.op(result, finalPath, Path.Op.UNION)
            } else if (actionMode == ActionMode.REMOVE) {
                result.op(result, finalPath, Path.Op.DIFFERENCE)
            }
            selectionPath = result
        }

        // Save snapshot for undo/redo
        donePaths.add(Path(selectionPath))
        undonePaths.clear()

        markRenderCacheDirty()
        invalidate()
    }

    fun undo() {
        if (donePaths.isNotEmpty()) {
            undonePaths.add(donePaths.removeAt(donePaths.lastIndex))
            selectionPath = if (donePaths.isNotEmpty()) Path(donePaths.last()) else null
            markRenderCacheDirty()
            invalidate()
        }
    }

    fun redo() {
        if (undonePaths.isNotEmpty()) {
            val redoPath = undonePaths.removeAt(undonePaths.lastIndex)
            donePaths.add(redoPath)
            selectionPath = Path(redoPath)
            markRenderCacheDirty()
            invalidate()
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
                canvas.withMatrix(drawMatrix) {
                    drawBitmap(bmp, null, rect, null)  // Draw the image with transformations

                    // Draw the overlay with the current transformations
                    val saveCount = saveLayer(rect, null)
                    drawRect(rect, whiteOverlayPaint)  // White overlay for unselected areas

                    // Draw the selected area from cached selectionPath (cheap in onDraw)
                    val punchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        Paint.setXfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                    }

                    selectionPath?.let {
                        drawPath(it, punchPaint)
                    }

                    restoreToCount(saveCount)  // Restore the canvas after overlay drawing
                }
            }
        }

        // Live preview path (drawing the current path in progress)
        currentPath?.let {
            val paintStroke =
                if (actionMode == ActionMode.ADD) strokePaintAdd else strokePaintRemove
            canvas.withMatrix(drawMatrix) {
                when (toolMode) {
                    ToolMode.BRUSH -> drawPath(it, paintStroke)
                    ToolMode.RECTANGLE -> drawRect(startX, startY, endX, endY, paintStroke)
                    ToolMode.ELLIPSE -> drawOval(RectF(startX, startY, endX, endY), paintStroke)
                    else -> {}
                }
            }
        }

        // Apply the marching ants effect (animated dashed lines) to the selected path(s)
        selectionPath?.let { merged ->
            val transformed = Path(merged)
            transformed.transform(drawMatrix)
            // Draw marching ants stroke (animated dashed lines) with path effect
            val strokePaint = Paint(strokePaintAdd).apply {
                Paint.setPathEffect =
                    DashPathEffect(floatArrayOf(12f, 12f), dashPhase)  // Marching ants effect
            }
            canvas.drawPath(transformed, strokePaint)  // Draw the marching ants on selected area
        }

        // Update stroke width based on zoom level
        val scaledStrokeWidth =
            3f * (1 / scaleFactor).coerceAtLeast(0.5f)  // Scale stroke width with zoom

        strokePaintAdd.strokeWidth = scaledStrokeWidth
        strokePaintRemove.strokeWidth = scaledStrokeWidth

        // Update the dash phase for marching ants animation
        dashPhase += 2
        strokePaintAdd.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), dashPhase)
        strokePaintRemove.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), dashPhase)

        // NOTE: Removed unconditional postInvalidateOnAnimation() here to avoid continuous redraws.
        // The marching ants animator invalidates when it needs to.
    }

    private fun handleMagicWand(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_DOWN) {
            imageBitmap?.let { bmp ->
                imageRect?.let { rect ->
                    val mapped = mapToImageCoords(event.x, event.y)
                    val imgX = ((mapped[0] - rect.left) / rect.width()) * bmp.width
                    val imgY = ((mapped[1] - rect.top) / rect.height()) * bmp.height

                    val safeX = imgX.toInt().coerceIn(0, bmp.width - 1)
                    val safeY = imgY.toInt().coerceIn(0, bmp.height - 1)

                    val targetColor = bmp[safeX, safeY]

                    scope.launch {
                        onProcessingChanged?.invoke(true)

                        val workingBmp = if (bmp.width > 512) {
                            bmp.scale(512, (bmp.height * (512f / bmp.width)).toInt(), false)
                        } else bmp

                        val scaleX = bmp.width.toFloat() / workingBmp.width
                        val scaleY = bmp.height.toFloat() / workingBmp.height

                        val mask = floodFillMask(
                            workingBmp,
                            (safeX / scaleX).toInt(),
                            (safeY / scaleY).toInt(),
                            targetColor,
                            wandTolerance
                        )

                        val resizedMask = mask.scale(bmp.width, bmp.height, false)

                        val rawPath = maskToContourPath(resizedMask)

                        // scale path to imageRect
                        val matrix = Matrix()
                        matrix.setRectToRect(
                            RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()),
                            rect,
                            Matrix.ScaleToFit.FILL
                        )
                        rawPath.transform(matrix)

                        withContext(Dispatchers.Main) {
                            commitPath(rawPath)
                            onProcessingChanged?.invoke(false)
                        }
                    }

                }
            }
        }
    }

    fun invertSelection() {
        imageRect?.let { rect ->
            // 1. Create a full-rect covering the entire image area
            val fullPath = Path().apply {
                addRect(rect, Path.Direction.CW)
            }

            // 2. If there's an active selection, subtract it
            selectionPath?.let { sel ->
                val inverted = Path(fullPath)
                inverted.op(fullPath, sel, Path.Op.DIFFERENCE)
                selectionPath = inverted
            } ?: run {
                // If no selection, invert = select everything
                selectionPath = fullPath
            }

            // 3. Save snapshot for undo/redo
            donePaths.add(Path(selectionPath))
            undonePaths.clear()

            markRenderCacheDirty()
            invalidate()
        }
    }

    private suspend fun floodFillMask(
        bmp: Bitmap, startX: Int, startY: Int, targetColor: Int, tolerance: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = bmp.width
        val h = bmp.height
        val mask = createBitmap(w, h)
        val visited = Array(h) { BooleanArray(w) }

        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(startX to startY)
        var processed = 0
        while (stack.isNotEmpty()) {
            if (isCancelled) return@withContext mask
            val (x, y) = stack.removeLast()
            if (x !in 0 until w || y !in 0 until h) continue
            if (visited[y][x]) continue
            if (!isColorSimilar(targetColor, bmp[x, y], tolerance)) continue

            // expand left/right in one scan
            var lx = x
            while (lx >= 0 && !visited[y][lx] && isColorSimilar(targetColor, bmp[lx, y], tolerance)) {
                mask[lx, y] = Color.WHITE
                visited[y][lx] = true
                if (y > 0) stack.add(lx to y - 1)
                if (y < h - 1) stack.add(lx to y + 1)
                lx--
                processed++
            }
            var rx = x + 1
            while (rx < w && !visited[y][rx] && isColorSimilar(targetColor, bmp[rx, y], tolerance)) {
                mask[rx, y] = Color.WHITE
                visited[y][rx] = true
                if (y > 0) stack.add(rx to y - 1)
                if (y < h - 1) stack.add(rx to y + 1)
                rx++
                processed++
            }
            if (processed % 10_000 == 0) {
                withContext(Dispatchers.Main) {
                    onProcessingChanged?.invoke(true) // still working
                }
            }
        }
        mask
    }

    private fun isColorSimilar(c1: Int, c2: Int, tol: Int): Boolean {
        val r1 = Color.red(c1)
        val g1 = Color.green(c1)
        val b1 = Color.blue(c1)
        val r2 = Color.red(c2)
        val g2 = Color.green(c2)
        val b2 = Color.blue(c2)
        return (abs(r1 - r2) <= tol && abs(g1 - g2) <= tol && abs(b1 - b2) <= tol)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Normal rendering
        renderContent(canvas)
        if (showMagnifier && magnifierAlpha > 0f) {
            val offsetY =
                if (magnifierY - magnifierOffset - magnifierRadius < 0) magnifierOffset else -magnifierOffset
            val magnifierCenterX = magnifierX
            val magnifierCenterY = magnifierY + offsetY

            val clipPath = Path().apply {
                addCircle(magnifierCenterX, magnifierCenterY, magnifierRadius, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)

            // 🔹 Real-time render (includes currentPath)
            val fullBmp = createBitmap(width, height)
            val c = Canvas(fullBmp)
            renderContent(c)

            val srcRect = Rect(
                (magnifierX - magnifierRadius / magnifierScale).toInt()
                    .coerceIn(0, fullBmp.width - 1),
                (magnifierY - magnifierRadius / magnifierScale).toInt()
                    .coerceIn(0, fullBmp.height - 1),
                (magnifierX + magnifierRadius / magnifierScale).toInt().coerceAtMost(fullBmp.width),
                (magnifierY + magnifierRadius / magnifierScale).toInt().coerceAtMost(fullBmp.height)
            )
            val dstRect = Rect(
                (magnifierCenterX - magnifierRadius).toInt(),
                (magnifierCenterY - magnifierRadius).toInt(),
                (magnifierCenterX + magnifierRadius).toInt(),
                (magnifierCenterY + magnifierRadius).toInt()
            )
            canvas.drawBitmap(fullBmp, srcRect, dstRect, null)
            fullBmp.recycle()

            // Border
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 4f
                alpha = (magnifierAlpha * 255).toInt()
            }
            canvas.drawCircle(magnifierCenterX, magnifierCenterY, magnifierRadius, borderPaint)
        }
    }

    private fun markRenderCacheDirty() {
        isRenderCacheDirty = true
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

        // ✅ Brush/Shape Drawing
        if (event.pointerCount == 1 && toolMode != null) {
            when (toolMode) {
                ToolMode.BRUSH -> handleBrush(event)
                ToolMode.MAGIC_WAND -> handleMagicWand(event)
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
                markRenderCacheDirty()
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
            setToolMode(null)
            return true
        }

        override fun onScroll(
            e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float
        ): Boolean {
            if (scaleFactor > 1f) {
                isTransforming = true
                drawMatrix.postTranslate(-distanceX, -distanceY)
                clampTranslation()
                drawMatrix.invert(inverseMatrix)
                invalidate()
                setToolMode(null)
                markRenderCacheDirty()
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
            markRenderCacheDirty()
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
            markRenderCacheDirty()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            super.onScaleEnd(detector)
            // Stop transforming → require re-pick tool
            if (scaleFactor < 1f) {
                animateZoom(1f, width / 2f, height / 2f) // snap back to 1f
                zoomIndex = 0 // reset step cycle
            }
            setToolMode(null)
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
            ValueAnimator.setDuration = 200
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

    fun confirmMask() {
        exportMaskedImage()?.let { masked ->
            onMaskConfirmed?.invoke(masked)
        }
    }

    private fun featherMask(mask: Bitmap): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            Paint.setMaskFilter =
                BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
        }

        val output = createBitmap(mask.width, mask.height)
        val canvas = Canvas(output)
        canvas.drawBitmap(mask, 0f, 0f, paint)
        return output
    }

    fun previewMaskedImage(): Bitmap? {
        imageBitmap?.let { bmp ->
            val output = createBitmap(width, height)
            val canvas = Canvas(output)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            // Draw the original image
            imageRect?.let { rect ->
                canvas.drawBitmap(bmp, null, rect, null)
            }

            // Create mask bitmap
            val mask = createBitmap(width, height)
            val maskCanvas = Canvas(mask)
            val addPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }

            // 🔹 Use the final selectionPath instead of paths
            selectionPath?.let {
                maskCanvas.drawPath(it, addPaint)
            }

            // Apply mask
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                Paint.setXfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            canvas.drawBitmap(mask, 0f, 0f, paint)

            return output
        }
        return null
    }

    fun exportMaskedImage(): Bitmap? {
        imageBitmap?.let { bmp ->
            if (selectionPath == null || imageRect == null) return null

            // 🔹 Step 1: Generate full masked image (like your original code)
            val fullMasked = createBitmap(width, height)
            val fullCanvas = Canvas(fullMasked)
            fullCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            // draw original image into rect
            fullCanvas.drawBitmap(bmp, null, imageRect!!, null)

            // make mask bitmap
            val mask = createBitmap(width, height)
            val maskCanvas = Canvas(mask)
            val addPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            selectionPath?.let {
                maskCanvas.drawPath(it, addPaint)
            }

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                Paint.setXfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            val smoothMask = featherMask(mask) // radius tweakable
            fullCanvas.drawBitmap(smoothMask, 0f, 0f, paint)

            // 🔹 Step 2: Crop masked image to tight bounds
            val bounds = Rect()
            if (!fullMasked.getBounds(bounds)) return fullMasked

            return Bitmap.createBitmap(
                fullMasked, bounds.left, bounds.top, bounds.width(), bounds.height()
            )
        }
        return null
    }

    private fun Bitmap.getBounds(outRect: Rect): Boolean {
        val w = width
        val h = height
        val pixels = IntArray(w * h)
        getPixels(pixels, 0, w, 0, 0, w, h)

        var left = w
        var right = 0
        var top = h
        var bottom = 0
        var found = false

        for (y in 0 until h) {
            for (x in 0 until w) {
                if (pixels[y * w + x] != Color.TRANSPARENT) {
                    found = true
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }

        return if (found) {
            outRect.set(left, top, right, bottom)
            true
        } else {
            false
        }
    }

    fun cancelProcessing() {
        isCancelled = true
        scope.coroutineContext.cancelChildren() // cancel ongoing coroutines
        onProcessingChanged?.invoke(false)
        onProcessingCancelled?.invoke()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelProcessing()
    }
}
