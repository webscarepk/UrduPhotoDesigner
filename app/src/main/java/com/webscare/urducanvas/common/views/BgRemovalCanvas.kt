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
    var onProcessingCancelled: (() -> Unit)? = null,
    var onZoomChanged: ((Float) -> Unit)? = null,
    var onMaskStateChanged: ((Boolean) -> Unit)? = null
) : View(context, attrs) {


    enum class ToolMode { BRUSH, RECTANGLE, ELLIPSE, MAGIC_WAND }
    enum class ActionMode { ADD, REMOVE }

    private var previewEnabled = false
    private var isApplyingMask = false

    /** Returns true when a non-empty selection mask exists on the canvas. */
    fun hasMask(): Boolean = selectionPath != null && !selectionPath!!.isEmpty

    private fun notifyMaskState() {
        onMaskStateChanged?.invoke(hasMask())
    }

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

    private var isTransforming = false  // zoom/pan in progress
    private var isScaling = false
    private var lastScaleEndTime = 0L
    private var lastPanX = 0f
    private var lastPanY = 0f

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

    // Cached merged selection path (computed when paths change).
    // Built from scanline rectangles for fast and precise masking (DST_OUT punch)
    private var selectionPath: Path? = null

    // Separate outline-only path used exclusively for marching ants rendering.
    // Derived from selectionPath via buildOutlinePath so the result is a clean outer contour.
    private var selectionOutlinePath: Path? = null

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
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        isDither = true
        xfermode = null
    }

    private val antsBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val antsFrontPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private var magicWandJob: kotlinx.coroutines.Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isCancelled = false

    init {
        // Marching ants animator - fast, smooth single outer-contour animation
        ValueAnimator.ofFloat(0f, 16f).apply {
            duration = 400
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                dashPhase = animator.animatedValue as Float
                antsFrontPaint.pathEffect = DashPathEffect(floatArrayOf(8f, 8f), -dashPhase)
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    fun setImage(bitmap: Bitmap) {
        imageBitmap = bitmap
        if (width > 0 && height > 0) calculateImageRect()
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

    fun setWandTolerance(tolerance: Int) {
        wandTolerance = tolerance.coerceIn(0, 255)
    }

    fun getWandTolerance(): Int = wandTolerance

    fun clearSelection() {
        paths.clear()
        donePaths.clear()
        undonePaths.clear()
        selectionPath = null
        selectionOutlinePath = null
        markRenderCacheDirty()
        invalidate()
        notifyMaskState()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateImageRect()
        markRenderCacheDirty()
    }

    private suspend fun maskBufferToBitmap(
        buffer: FloatBuffer, maskWidth: Int, maskHeight: Int
    ): Bitmap {
        return withContext(Dispatchers.Default) {
            buffer.rewind()
            val bitmap = createBitmap(maskWidth, maskHeight)
            val pixels = IntArray(maskWidth * maskHeight)

            for (i in 0 until (maskWidth * maskHeight)) {
                if (buffer.hasRemaining()) {
                    val confidence = buffer.get()
                    pixels[i] = if (confidence >= 0.12f) Color.WHITE else Color.TRANSPARENT
                }
            }
            bitmap.setPixels(pixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)
            bitmap
        }
    }

    fun applyGeneratedMask(buffer: FloatBuffer, maskWidth: Int, maskHeight: Int) {
        isApplyingMask = true
        scope.launch {
            onProcessingChanged?.invoke(true)
            val maskBitmap = maskBufferToBitmap(buffer, maskWidth, maskHeight)

            val subjectPath = withContext(Dispatchers.Default) {
                maskToContourPath(maskBitmap)
            }.apply {
                val matrix = Matrix()
                imageRect?.let { rect ->
                    matrix.setRectToRect(
                        RectF(0f, 0f, maskBitmap.width.toFloat(), maskBitmap.height.toFloat()),
                        rect,
                        Matrix.ScaleToFit.FILL
                    )
                    transform(matrix)
                }
            }
            withContext(Dispatchers.Main) {
                selectionPath = null
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
            val pixels = IntArray(w * h)
            mask.getPixels(pixels, 0, w, 0, 0, w, h)

            for (y in 0 until h) {
                var x = 0
                while (x < w) {
                    if (isCancelled) return@withContext path
                    if ((pixels[y * w + x] ushr 24) > 127) {
                        val xStart = x
                        while (x < w && (pixels[y * w + x] ushr 24) > 127) {
                            x++
                        }
                        path.addRect(
                            xStart.toFloat(),
                            y.toFloat(),
                            x.toFloat(),
                            (y + 1).toFloat(),
                            Path.Direction.CW
                        )
                    } else {
                        x++
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
            val result = Path()
            if (actionMode == ActionMode.ADD) {
                result.op(selectionPath!!, finalPath, Path.Op.UNION)
            } else if (actionMode == ActionMode.REMOVE) {
                result.op(selectionPath!!, finalPath, Path.Op.DIFFERENCE)
            }
            selectionPath = result
        }

        // Save snapshot for undo/redo
        donePaths.add(Path(selectionPath))
        undonePaths.clear()

        markRenderCacheDirty()
        invalidate()
        notifyMaskState()
    }

    fun undo() {
        if (donePaths.isNotEmpty()) {
            undonePaths.add(donePaths.removeAt(donePaths.lastIndex))
            selectionPath = if (donePaths.isNotEmpty()) Path(donePaths.last()) else null
            markRenderCacheDirty()
            invalidate()
            notifyMaskState()
        }
    }

    fun redo() {
        if (undonePaths.isNotEmpty()) {
            val redoPath = undonePaths.removeAt(undonePaths.lastIndex)
            donePaths.add(redoPath)
            selectionPath = Path(redoPath)
            markRenderCacheDirty()
            invalidate()
            notifyMaskState()
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
        val bmp = imageBitmap ?: return
        val rect = imageRect ?: return

        canvas.withMatrix(drawMatrix) {
            if (previewEnabled) {
                // In preview mode: draw only the masked selection with transparent background
                val saveCount = saveLayer(rect, null)
                drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                val shaderMatrix = Matrix()
                shaderMatrix.setRectToRect(
                    RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()),
                    rect,
                    Matrix.ScaleToFit.FILL
                )
                val bmpShader = android.graphics.BitmapShader(
                    bmp,
                    android.graphics.Shader.TileMode.CLAMP,
                    android.graphics.Shader.TileMode.CLAMP
                ).apply {
                    setLocalMatrix(shaderMatrix)
                }
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = bmpShader
                }

                selectionPath?.let {
                    drawPath(it, paint)
                }
                restoreToCount(saveCount)
            } else {
                // In edit mode: draw original image, then semi-transparent white overlay with selection punched out
                drawBitmap(bmp, null, rect, null)

                val saveCount = saveLayer(rect, null)
                drawRect(rect, whiteOverlayPaint)

                val punchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                }

                selectionPath?.let {
                    drawPath(it, punchPaint)
                }

                restoreToCount(saveCount)
            }
        }

        // Draw selection helper elements only when NOT in preview mode
        if (!previewEnabled) {
            // Live preview path (drawing current brush or shape in progress)
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

            // Draw marching ants - ONLY on the outer boundary outline, no internal scanline artifacts
            selectionOutlinePath?.let { outline ->
                val currentWidth = 2f / scaleFactor
                antsBackPaint.strokeWidth = currentWidth
                antsFrontPaint.strokeWidth = currentWidth

                canvas.withMatrix(drawMatrix) {
                    drawPath(outline, antsBackPaint)
                    drawPath(outline, antsFrontPaint)
                }
            }
        }

        // Update stroke width based on zoom level
        val scaledStrokeWidth = 3f * (1 / scaleFactor).coerceAtLeast(0.5f)
        strokePaintAdd.strokeWidth = scaledStrokeWidth
        strokePaintRemove.strokeWidth = scaledStrokeWidth
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

                    magicWandJob?.cancel()
                    magicWandJob = scope.launch {
                        val maxDim = 512
                        val workingBmp = if (bmp.width > maxDim || bmp.height > maxDim) {
                            val scale = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
                            bmp.scale((bmp.width * scale).toInt(), (bmp.height * scale).toInt(), false)
                        } else bmp

                        val scaleX = bmp.width.toFloat() / workingBmp.width
                        val scaleY = bmp.height.toFloat() / workingBmp.height

                        val mask = floodFillMask(
                            workingBmp,
                            (safeX / scaleX).toInt().coerceIn(0, workingBmp.width - 1),
                            (safeY / scaleY).toInt().coerceIn(0, workingBmp.height - 1),
                            targetColor,
                            wandTolerance
                        )

                        val rawPath = maskToContourPath(mask)

                        // scale path from low-res mask directly to imageRect (screen space)
                        val matrix = Matrix()
                        matrix.setRectToRect(
                            RectF(0f, 0f, mask.width.toFloat(), mask.height.toFloat()),
                            rect,
                            Matrix.ScaleToFit.FILL
                        )
                        rawPath.transform(matrix)

                        withContext(Dispatchers.Main) {
                            commitPath(rawPath)
                        }
                    }
                }
            }
        }
    }

    fun invertSelection() {
        imageRect?.let { rect ->
            val fullPath = Path().apply {
                addRect(rect, Path.Direction.CW)
            }

            selectionPath?.let { sel ->
                val inverted = Path(fullPath)
                inverted.op(fullPath, sel, Path.Op.DIFFERENCE)
                selectionPath = inverted
            } ?: run {
                selectionPath = fullPath
            }

            donePaths.add(Path(selectionPath))
            undonePaths.clear()

            markRenderCacheDirty()
            invalidate()
            notifyMaskState()
        }
    }

    private suspend fun floodFillMask(
        bmp: Bitmap, startX: Int, startY: Int, targetColor: Int, tolerance: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = bmp.width
        val h = bmp.height

        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val maskPixels = IntArray(w * h)
        val visited = BooleanArray(w * h)

        val queue = java.util.ArrayDeque<Int>()
        val startIdx = startY * w + startX
        if (startIdx in 0 until (w * h)) {
            queue.add(startIdx)
            visited[startIdx] = true
        }

        while (queue.isNotEmpty()) {
            if (isCancelled) break
            val idx = queue.removeFirst()
            val x = idx % w
            val y = idx / w

            maskPixels[idx] = Color.WHITE

            // 4-connected neighbors
            if (x > 0) {
                val nextIdx = idx - 1
                if (!visited[nextIdx] && isColorSimilar(targetColor, pixels[nextIdx], tolerance)) {
                    visited[nextIdx] = true
                    queue.add(nextIdx)
                }
            }
            if (x < w - 1) {
                val nextIdx = idx + 1
                if (!visited[nextIdx] && isColorSimilar(targetColor, pixels[nextIdx], tolerance)) {
                    visited[nextIdx] = true
                    queue.add(nextIdx)
                }
            }
            if (y > 0) {
                val nextIdx = idx - w
                if (!visited[nextIdx] && isColorSimilar(targetColor, pixels[nextIdx], tolerance)) {
                    visited[nextIdx] = true
                    queue.add(nextIdx)
                }
            }
            if (y < h - 1) {
                val nextIdx = idx + w
                if (!visited[nextIdx] && isColorSimilar(targetColor, pixels[nextIdx], tolerance)) {
                    visited[nextIdx] = true
                    queue.add(nextIdx)
                }
            }
        }

        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        mask.setPixels(maskPixels, 0, w, 0, 0, w, h)
        mask
    }

    private fun isColorSimilar(c1: Int, c2: Int, tol: Int): Boolean {
        val a1 = Color.alpha(c1)
        val a2 = Color.alpha(c2)
        if (abs(a1 - a2) > tol) return false
        if (a1 < 10 && a2 < 10) return true

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
        renderContent(canvas)
    }

    private fun markRenderCacheDirty() {
        isRenderCacheDirty = true
        selectionOutlinePath = buildOutlinePath(selectionPath)
    }

    /**
     * Derives a clean, continuous outer boundary contour for marching ants.
     * Traces connected perimeter loops so DashPathEffect smoothly marches along the outline
     * without blinking or internal scanline artifacts.
     */
    private fun buildOutlinePath(source: Path?): Path? {
        source ?: return null
        val rect = imageRect ?: return source

        val scale = 0.25f // 1/4 resolution for ultra fast continuous contour extraction
        val w = (rect.width() * scale).toInt().coerceAtLeast(1)
        val h = (rect.height() * scale).toInt().coerceAtLeast(1)

        val bmp = createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val c = Canvas(bmp)
        val m = Matrix()
        m.setRectToRect(rect, RectF(0f, 0f, w.toFloat(), h.toFloat()), Matrix.ScaleToFit.FILL)
        c.setMatrix(m)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        c.drawPath(source, paint)

        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        bmp.recycle()

        fun isMasked(x: Int, y: Int): Boolean {
            if (x !in 0 until w || y !in 0 until h) return false
            return (pixels[y * w + x] ushr 24) > 127
        }

        val outline = Path()
        val visited = Array(h) { BooleanArray(w) }
        val dx = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
        val dy = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

        for (y in 0 until h) {
            for (x in 0 until w) {
                if (isMasked(x, y) && !visited[y][x]) {
                    val isEdge = !isMasked(x - 1, y) || !isMasked(x + 1, y) ||
                            !isMasked(x, y - 1) || !isMasked(x, y + 1)
                    if (isEdge) {
                        var currX = x
                        var currY = y
                        var dir = 0
                        outline.moveTo(currX.toFloat() + 0.5f, currY.toFloat() + 0.5f)
                        visited[currY][currX] = true

                        var steps = 0
                        val maxSteps = w * h
                        do {
                            var found = false
                            for (i in 0 until 8) {
                                val nDir = (dir + i) % 8
                                val nx = currX + dx[nDir]
                                val ny = currY + dy[nDir]
                                if (nx in 0 until w && ny in 0 until h && isMasked(nx, ny)) {
                                    val neighborIsEdge = !isMasked(nx - 1, ny) || !isMasked(nx + 1, ny) ||
                                            !isMasked(nx, ny - 1) || !isMasked(nx, ny + 1)
                                    if (neighborIsEdge) {
                                        currX = nx
                                        currY = ny
                                        visited[currY][currX] = true
                                        outline.lineTo(currX.toFloat() + 0.5f, currY.toFloat() + 0.5f)
                                        dir = (nDir + 5) % 8
                                        found = true
                                        break
                                    }
                                }
                            }
                            steps++
                            if (!found || steps > maxSteps) break
                        } while (!(currX == x && currY == y))
                        outline.close()
                    }
                }
            }
        }

        val invM = Matrix()
        invM.setRectToRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), rect, Matrix.ScaleToFit.FILL)
        outline.transform(invM)
        return outline
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val scaleHandled = scaleDetector.onTouchEvent(event)

        val now = System.currentTimeMillis()
        if (event.pointerCount >= 2 || isScaling || (now - lastScaleEndTime < 250L)) {
            if (showMagnifier) {
                animateMagnifier(false)
            }
            currentPath = null
            lastPanX = event.x
            lastPanY = event.y
            if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP) {
                lastScaleEndTime = now
            }
            return true
        }

        if (now - lastScaleEndTime < 250L) {
            lastPanX = event.x
            lastPanY = event.y
            return true
        }

        if (event.pointerCount == 1 && toolMode != null) {
            when (toolMode) {
                ToolMode.BRUSH -> handleBrush(event)
                ToolMode.MAGIC_WAND -> handleMagicWand(event)
                ToolMode.RECTANGLE, ToolMode.ELLIPSE -> handleShape(event)
                else -> {}
            }
            return true
        }

        val gestureHandled = gestureDetector.onTouchEvent(event)

        if (toolMode == null && event.pointerCount == 1) {
            if (action == MotionEvent.ACTION_MOVE) {
                val dx = event.x - lastPanX
                val dy = event.y - lastPanY
                lastPanX = event.x
                lastPanY = event.y

                if (abs(dx) < 300f && abs(dy) < 300f) {
                    drawMatrix.postTranslate(dx, dy)
                    clampTranslation()
                    invalidate()
                    markRenderCacheDirty()
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                lastPanX = 0f
                lastPanY = 0f
            }
            return true
        }

        lastPanX = event.x
        lastPanY = event.y
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

    fun getScaleFactor(): Float = scaleFactor

    fun zoomIn() {
        val target = (scaleFactor * 1.25f).coerceAtMost(5f)
        animateZoom(target, width / 2f, height / 2f)
    }

    fun zoomOut() {
        val target = (scaleFactor / 1.25f).coerceAtLeast(1f)
        animateZoom(target, width / 2f, height / 2f)
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
            onZoomChanged?.invoke(scaleFactor)
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
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            isTransforming = true
            isScaling = true
            val scale = detector.scaleFactor
            val newScale = (scaleFactor * scale).coerceAtMost(5f)

            val factor = newScale / scaleFactor
            scaleFactor = newScale
            drawMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            clampTranslation()
            drawMatrix.invert(inverseMatrix)
            onZoomChanged?.invoke(scaleFactor)
            invalidate()
            markRenderCacheDirty()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            super.onScaleEnd(detector)
            isScaling = false
            lastScaleEndTime = System.currentTimeMillis()
            if (scaleFactor < 1f) {
                animateZoom(1f, width / 2f, height / 2f)
                zoomIndex = 0
            }
            setToolMode(null)
            isTransforming = false
        }
    }

    private fun mapToImageCoords(x: Float, y: Float): FloatArray {
        val pts = floatArrayOf(x, y)
        inverseMatrix.mapPoints(pts)
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
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                currentPath?.lineTo(x, y)
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
        val mapped = mapToImageCoords(event.x, event.y)
        val x = mapped[0]
        val y = mapped[1]

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = x
                startY = y
                endX = startX
                endY = startY
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                endX = x
                endY = y

                val preview = Path()
                if (toolMode == ToolMode.RECTANGLE) {
                    preview.addRect(startX, startY, endX, endY, Path.Direction.CW)
                } else if (toolMode == ToolMode.ELLIPSE) {
                    preview.addOval(RectF(startX, startY, endX, endY), Path.Direction.CW)
                }
                currentPath = preview
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
            maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
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

            imageRect?.let { rect ->
                canvas.drawBitmap(bmp, null, rect, null)
            }

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
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            canvas.drawBitmap(mask, 0f, 0f, paint)

            return output
        }
        return null
    }

    fun exportMaskedImage(): Bitmap? {
        imageBitmap?.let { bmp ->
            if (selectionPath == null || imageRect == null) return null

            val fullMasked = createBitmap(width, height)
            val fullCanvas = Canvas(fullMasked)
            fullCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            fullCanvas.drawBitmap(bmp, null, imageRect!!, null)

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
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            val smoothMask = featherMask(mask)
            fullCanvas.drawBitmap(smoothMask, 0f, 0f, paint)

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
        scope.coroutineContext.cancelChildren()
        onProcessingChanged?.invoke(false)
        onProcessingCancelled?.invoke()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelProcessing()
    }
}
