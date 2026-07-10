package com.webscare.urducanvas.common.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.Xfermode
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import com.webscare.urducanvas.common.utils.Utils.vibrateSoft
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.createBitmap
import com.google.gson.Gson
import com.webscare.urducanvas.common.canvas.enums.BrushStyle
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.FeatherDirection
import com.webscare.urducanvas.common.canvas.enums.HAlign
import com.webscare.urducanvas.common.canvas.enums.Mode
import com.webscare.urducanvas.common.canvas.enums.MultiAlignMode
import com.webscare.urducanvas.common.canvas.enums.VAlign
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.canvas.model.GradientItem
import com.webscare.urducanvas.common.canvas.model.ExportOptions
import com.webscare.urducanvas.common.canvas.model.StrokeData
import com.webscare.urducanvas.common.canvas.sealed.ImageFilter
import com.webscare.urducanvas.common.utils.ImageAdjustmentHelper
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

@Suppress("LargeClass", "TooManyFunctions")
class CanvasView @JvmOverloads constructor(
    context: Context,
    override var canvasWidth: Int = 300,
    override var canvasHeight: Int = 300,
    attrs: AttributeSet? = null,
    var onEditTextRequested: ((CanvasElement) -> Unit)? = null,
    var onElementChanged: ((CanvasElement) -> Unit)? = null,
    var onElementRemoved: ((CanvasElement) -> Unit)? = null,
    var onElementSelected: ((List<CanvasElement>) -> Unit)? = null,
    var onStartBatchUpdate: ((String, String) -> Unit)? = null,
    var onEndBatchUpdate: ((String) -> Unit)? = null,
    var onColorPicked: ((Int) -> Unit)? = null,
    var onRequestOpenLayers: (() -> Unit)? = null,
    var onExitSelectionMode: (() -> Unit)? = null,
    var onStrokeCompleted: ((StrokeData) -> Unit)? = null,
    var onZoomChanged: ((Float) -> Unit)? = null,
    var onCanvasLongPressed: ((screenX: Float, screenY: Float) -> Unit)? = null,
) : View(context, attrs), CanvasStateAccess {

    override val stateContext: Context get() = context

    internal val gson: Gson by lazy {
        EntryPointAccessors.fromApplication(
            context,
            com.webscare.urducanvas.di.GsonEntryPoint::class.java,
        ).gson()
    }

    internal var isRotating = false
    override var colorPickerBitmap: Bitmap? = null
    override var isColorPickerMode = false

    override var activeSessionElement: CanvasElement? = null

    override var currentStrokePath: Path? = null
    override var currentStrokePaint: Paint? = null
    internal var currentStrokePoints = mutableListOf<Pair<Float, Float>>()
    override var isDrawing = false
    internal var currentBrushColor: Int = Color.BLACK
    internal var currentBrushThickness: Float = 20f
    internal var currentBrushHardness: Float = 1f
    internal var currentBrushStyle: BrushStyle = BrushStyle.PEN
    internal var currentBrushGradient: com.webscare.urducanvas.common.canvas.model.GradientItem? = null

    override var pickerX = 0f
    override var pickerY = 0f
    internal var isDraggingPicker = false
    override val desiredPickerIconSizePx = 64f
    private val desiredIconSizeDp = 20f
    val desiredIconScreenSizePx: Float
        get() = desiredIconSizeDp * resources.displayMetrics.density

    override var allowFreeDrag: Boolean = false
    override var activeGroupId: String? = null
    internal var inSelectionMode = false

    internal val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    internal val initialElementSizes = mutableMapOf<String, Pair<Float, Float>>()
    internal var suppressZoomCallback = false

    override val canvasElements = CopyOnWriteArrayList<CanvasElement>()
    private lateinit var backgroundElement: CanvasElement

    override var currentMode: Mode = Mode.NONE

    override var scale = 1f
    override var offsetX = 0f
    override var offsetY = 0f

    override var overallScale = 1f
    override var overallOffsetX = 0f
    override var overallOffsetY = 0f

    override val lastDrawnIconRect = mutableMapOf<String, RectF>()

    override val cacheManager = CanvasCacheManager(context)

    private val adjustmentScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val pendingAdjustmentJobs = mutableMapOf<String, Job>()

    override var showVerticalGuide = false
    override var showHorizontalGuide = false
    override var showRotationVerticalGuide = false
    override var showRotationHorizontalGuide = false

    override var showCanvasCenterVerticalSnap = false
    override var showCanvasCenterHorizontalSnap = false
    val canvasSnapThresholdPx = 8f

    override var showGrid = false
    override var showRuler = false

    internal var isPanMode = false
    internal var isCanvasPanLocked = false

    override val selectedElements: CopyOnWriteArrayList<CanvasElement> = CopyOnWriteArrayList()

    // Delegates
    internal val canvasRenderer = CanvasRenderer(this)
    private val touchHandler = CanvasTouchHandler(this)
    internal val elementManager = CanvasElementManager(this)
    private val exporter = CanvasExporter(this)
    private val alignmentHelper = CanvasAlignmentHelper(this)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return touchHandler.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvasRenderer.render(canvas)
    }

    fun resizeCanvas(newWidth: Int, newHeight: Int) {
        this.canvasWidth = newWidth
        this.canvasHeight = newHeight
        updateBackgroundToCanvas()
        requestLayout()
        invalidate()
    }

    fun setDrawingMode(enabled: Boolean, sessionElement: CanvasElement? = null) {
        isDrawing = enabled
        activeSessionElement = sessionElement
        invalidate()
    }

    fun updateBrushSettings(
        color: Int? = null,
        thickness: Float? = null,
        hardness: Float? = null,
        style: BrushStyle? = null,
        gradient: com.webscare.urducanvas.common.canvas.model.GradientItem? = null,
    ) {
        color?.let { currentBrushColor = it }
        thickness?.let { currentBrushThickness = it }
        hardness?.let { currentBrushHardness = it }
        style?.let { currentBrushStyle = it }
        gradient?.let { currentBrushGradient = it }
    }

    fun enableColorPicker() {
        isColorPickerMode = true
        pickerX = canvasWidth / 2f
        pickerY = canvasHeight / 2f
        invalidate()

        adjustmentScope.launch {
            val displayScale = minOf(
                width.toFloat() / canvasWidth.toFloat(),
                height.toFloat() / canvasHeight.toFloat(),
                1f,
            ).coerceAtLeast(0.1f)
            val bmpW = (canvasWidth * displayScale).toInt().coerceAtLeast(1)
            val bmpH = (canvasHeight * displayScale).toInt().coerceAtLeast(1)
            val bmp = createBitmap(bmpW, bmpH)
            exporter.renderCanvasTo(Canvas(bmp), displayScale)

            withContext(Dispatchers.Main) {
                colorPickerBitmap?.let { if (!it.isRecycled) it.recycle() }
                colorPickerBitmap = bmp
                invalidate()
            }
        }
    }

    fun disableColorPicker() {
        isColorPickerMode = false
        isDraggingPicker = false
        colorPickerBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        colorPickerBitmap = null
        invalidate()
    }

    fun setSelectionMode(enabled: Boolean) {
        if (inSelectionMode != enabled) {
            inSelectionMode = enabled
            invalidate()
        }
    }

    fun enterGroupEdit(groupId: String, focusChildId: String? = null) {
        activeGroupId = groupId
        currentMode = Mode.GROUP_EDIT
        canvasElements.forEach { it.isSelected = false }
        selectedElements.clear()
        val target = if (focusChildId != null) {
            canvasElements.firstOrNull { it.id == focusChildId && it.groupId == groupId }
        } else {
            canvasElements.filter { it.groupId == groupId }.maxByOrNull { it.zIndex }
        }
        target?.let {
            it.isSelected = true
            selectedElements.add(it)
            onElementSelected?.invoke(selectedElements)
        }
        invalidate()
    }

    fun exitGroupEdit() {
        activeGroupId = null
        currentMode = Mode.NONE
        canvasElements.forEach { it.isSelected = false }
        selectedElements.clear()
        onElementSelected?.invoke(emptyList())
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateBackgroundToCanvas()
        if (canvasElements.isEmpty()) {
            ensureBackgroundElement()
        }
    }

    private fun updateBackgroundToCanvas() {
        val bg = canvasElements.firstOrNull { it.type == ElementType.BACKGROUND } ?: return
        bg.logicalContentWidth = canvasWidth.toFloat()
        bg.logicalContentHeight = canvasHeight.toFloat()
        bg.x = canvasWidth / 2f
        bg.y = canvasHeight / 2f
        bg.scale = 1f
        onElementChanged?.invoke(bg)
    }

    private fun ensureBackgroundElement() {
        if (canvasElements.any { it.type == ElementType.BACKGROUND }) return
        if (!::backgroundElement.isInitialized) {
            Log.d("CanvasView", "No background element initialized — skipping creation.")
            return
        }

        val newBg = backgroundElement.copy().apply {
            type = ElementType.BACKGROUND
            isLocked = true
            isVisible = true
            backgroundColor = Color.WHITE
            x = canvasWidth / 2f
            y = canvasHeight / 2f
            logicalContentWidth = canvasWidth.toFloat()
            logicalContentHeight = canvasHeight.toFloat()
        }

        canvasElements.add(0, newBg)
        onElementChanged?.invoke(newBg)
        invalidate()
    }

    private fun resolveSelectedForCanvas(elements: List<CanvasElement>): List<CanvasElement> {
        val result = mutableListOf<CanvasElement>()
        val selectedGroupIds = elements
            .filter { it.isSelected && it.type == ElementType.GROUP }
            .map { it.id }.toSet()
        for (el in elements) {
            when {
                el.type == ElementType.GROUP -> {
                    if (el.isSelected) {
                        elements.filter { it.groupId == el.id }.forEach { child ->
                            if (result.none { r -> r.id == child.id }) result.add(child)
                        }
                    }
                }
                el.groupId != null && el.groupId in selectedGroupIds -> {
                    if (result.none { r -> r.id == el.id }) result.add(el)
                }
                el.isSelected -> result.add(el)
            }
        }
        return result
    }

    fun syncElements(newElements: List<CanvasElement>) {
        val oldSize = canvasElements.size
        canvasElements.clear()
        canvasElements.addAll(newElements.sortedBy { it.zIndex })

        val viewContext = context
        canvasElements.forEach { el ->
            if (el.context == null) el.context = viewContext
        }

        selectedElements.clear()
        if (newElements.size > oldSize) {
            val newcomer = canvasElements.last()
            if (newcomer.type != ElementType.BACKGROUND && newcomer.type != ElementType.GROUP) {
                canvasElements.forEach { it.isSelected = false }
                newcomer.isSelected = (newcomer.type != ElementType.DRAW)
                selectedElements.add(newcomer)
            } else {
                selectedElements.addAll(resolveSelectedForCanvas(canvasElements.toList()))
            }
        } else {
            selectedElements.addAll(resolveSelectedForCanvas(canvasElements.toList()))
        }

        val activeGesture = currentMode == Mode.DRAG ||
            currentMode == Mode.ROTATE ||
            currentMode == Mode.RESIZE ||
            currentMode == Mode.TRANSFORM
        if (!activeGesture) {
            val sole = selectedElements.singleOrNull()
            if (sole != null && sole.groupId != null) {
                activeGroupId = sole.groupId
                currentMode = Mode.GROUP_EDIT
            } else if (currentMode == Mode.GROUP_EDIT) {
                val allSameGroup = selectedElements.isNotEmpty() &&
                    selectedElements.all { it.groupId != null && it.groupId == activeGroupId }
                if (!allSameGroup) {
                    activeGroupId = null
                    currentMode = Mode.NONE
                }
            }
        }
        invalidate()
    }

    internal fun removeSelectedElement() {
        if (currentMode == Mode.GROUP_EDIT &&
            activeGroupId != null &&
            selectedElements.size == 1 &&
            selectedElements.first().groupId == activeGroupId
        ) {
            val child = selectedElements.first()
            val remainingSiblings = canvasElements.count {
                it.groupId == activeGroupId && it.id != child.id
            }
            val idsToRemove = if (remainingSiblings == 0) {
                val sentinel = canvasElements.firstOrNull {
                    it.type == ElementType.GROUP && it.id == activeGroupId
                }
                listOfNotNull(child, sentinel)
            } else {
                listOf(child)
            }
            idsToRemove.forEach { element ->
                canvasElements.remove(element)
                onElementRemoved?.invoke(element)
                cacheManager.removeAllFor(element.id)
                pendingAdjustmentJobs.remove(element.id)?.cancel()
            }
            selectedElements.clear()
            if (remainingSiblings == 0) {
                activeGroupId = null
                currentMode = Mode.NONE
            }
            invalidate()
            return
        }

        val groupIds = selectedElements.mapNotNull { it.groupId }.toSet()
        val sentinelsToRemove = canvasElements.filter {
            it.type == ElementType.GROUP && groupIds.contains(it.id)
        }
        val elementsToRemove = (selectedElements.toList() + sentinelsToRemove).distinctBy { it.id }
        elementsToRemove.forEach { element ->
            canvasElements.remove(element)
            onElementRemoved?.invoke(element)
            cacheManager.removeAllFor(element.id)
            pendingAdjustmentJobs.remove(element.id)?.cancel()
        }
        selectedElements.clear()
        invalidate()
    }

    fun applyImageFilter(filter: ImageFilter?) {
        val elementsToFilter = selectedElements.toList()
        elementsToFilter.forEach { element ->
            if (element != null && (element.type == ElementType.IMAGE || element.type == ElementType.STICKER)) {
                element.imageFilter = filter!!
                onElementChanged?.invoke(element)
                invalidate()
            }
        }
    }

    fun setFont(fontEntity: com.webscare.urducanvas.data.model.FontEntity) {
        selectedElements.filter { it.type == ElementType.TEXT }.forEach { element ->
            element.fontId = fontEntity.id.toString()
            if (fontEntity.file_path?.isNotBlank()!!) {
                try {
                    element.paint.typeface = Typeface.createFromFile(fontEntity.file_path)
                } catch (e: Exception) {
                    Log.e("CanvasView", "Error loading typeface from file: ${fontEntity.file_path}", e)
                    element.paint.typeface = Typeface.DEFAULT
                }
            }
            onElementChanged?.invoke(element)
        }
        invalidate()
    }

    fun setOpacity(opacity: Int) {
        selectedElements.forEach { element ->
            element.paint.alpha = opacity
            onElementChanged?.invoke(element)
        }
        invalidate()
    }

    fun setCanvasBackgroundColor(color: Int) {
        ensureBackgroundElement()
        canvasElements.forEach { element ->
            if (element.type == ElementType.BACKGROUND) {
                element.backgroundColor = color
                element.fillGradient = null
                element.bitmap = null
                element.bitmapData = null
            }
        }
        invalidate()
    }

    fun setCanvasBackgroundGradient(gradientItem: com.webscare.urducanvas.common.canvas.model.GradientItem) {
        ensureBackgroundElement()
        canvasElements.forEach { element ->
            if (element.type == ElementType.BACKGROUND) {
                element.backgroundColor = Color.WHITE
                element.fillGradient = gradientItem
                element.bitmap = null
                element.bitmapData = null
            }
        }
        invalidate()
    }

    fun setCanvasBackgroundImage(src: Bitmap) {
        ensureBackgroundElement()
        canvasElements.first { it.type == ElementType.BACKGROUND }.apply {
            fillGradient = null
            backgroundColor = Color.WHITE
            bitmap = src
        }
        invalidate()
    }

    fun clearSelection() {
        canvasElements.forEach { it.isSelected = false }
        onElementSelected?.invoke(emptyList())
    }

    internal fun resolveAdjustedBitmapAsync(element: CanvasElement, rawBitmap: Bitmap): Bitmap {
        val hasAnyAdjustment = element.hasLight || element.hasColor || element.hasDetail || element.hasBlur
        if (!hasAnyAdjustment) return rawBitmap

        val cached = element.cachedAdjustedBitmap
        if (!element.isAdjustmentDirty && cached != null && !cached.isRecycled) {
            return cached
        }

        val existing = pendingAdjustmentJobs[element.id]
        if (existing == null || !existing.isActive) {
            val job = adjustmentScope.launch {
                val ctx = element.context ?: context ?: return@launch
                val result = ImageAdjustmentHelper.applyAllAdjustments(ctx, rawBitmap, element)
                withContext(Dispatchers.Main) {
                    element.cachedAdjustedBitmap = result
                    element.isAdjustmentDirty = false
                    cacheManager.removeDisplay(element.id)
                    cacheManager.removeDisplay(element.id + "_bg")
                    pendingAdjustmentJobs.remove(element.id)
                    invalidate()
                }
            }
            pendingAdjustmentJobs[element.id] = job
        }

        return if (cached != null && !cached.isRecycled) cached else rawBitmap
    }

    internal fun getOrBuildDisplayBitmap(
        cacheKey: String,
        source: Bitmap,
        targetW: Int,
        targetH: Int,
    ): Bitmap {
        if (source.width <= targetW && source.height <= targetH) return source

        val cached = cacheManager.getDisplay(cacheKey)
        if (cached != null && !cached.bitmap.isRecycled) {
            val isActiveTransform = currentMode == Mode.DRAG || currentMode == Mode.ROTATE ||
                currentMode == Mode.RESIZE || currentMode == Mode.TRANSFORM ||
                currentMode == Mode.MULTI_TOUCH || currentMode == Mode.CANVAS_PAN
            if (isActiveTransform) {
                return cached.bitmap
            }

            val discreteW = (((targetW + 127) / 128) * 128).coerceAtMost(source.width)
            val discreteH = (((targetH + 127) / 128) * 128).coerceAtMost(source.height)

            if (cached.srcWidth == source.width && cached.srcHeight == source.height &&
                cached.dstWidth == discreteW && cached.dstHeight == discreteH
            ) {
                return cached.bitmap
            }
        }

        val discreteW = (((targetW + 127) / 128) * 128).coerceAtMost(source.width).coerceAtLeast(1)
        val discreteH = (((targetH + 127) / 128) * 128).coerceAtMost(source.height).coerceAtLeast(1)

        val scaled = Bitmap.createScaledBitmap(source, discreteW, discreteH, true)
        cacheManager.putDisplay(cacheKey, DisplayCacheEntry(
            bitmap = scaled,
            srcWidth = source.width,
            srcHeight = source.height,
            dstWidth = discreteW,
            dstHeight = discreteH,
        ))
        return scaled
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)

        val marginHorizontal = 20f * resources.displayMetrics.density
        val marginVertical = 10f * resources.displayMetrics.density

        val availableWidth = parentWidth - (marginHorizontal * 2)
        val availableHeight = parentHeight - (marginVertical * 2)

        val widthRatio = availableWidth / canvasWidth
        val heightRatio = availableHeight / canvasHeight

        scale = minOf(widthRatio, heightRatio)
        setMeasuredDimension(parentWidth, parentHeight)
    }

    override fun isColorDark(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        return luminance < 128
    }

    internal fun stepZoomOverall() {
        val next = when {
            overallScale < 0.9f -> 1.0f
            overallScale < 1.5f -> 2.0f
            overallScale < 2.5f -> 3.0f
            else -> 0.5f
        }
        animateOverallZoom(next)
        onZoomChanged?.invoke(next)
    }

    internal fun animateOverallZoom(toScale: Float) {
        val fromScale = overallScale
        ValueAnimator.ofFloat(fromScale, toScale).apply {
            duration = 400L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                overallScale = anim.animatedValue as Float
                clampOverallPan()
                invalidate()
            }
            start()
        }
    }

    internal fun screenToCanvas(sx: Float, sy: Float): Pair<Float, Float> {
        val pt = floatArrayOf(sx, sy)
        val transform = Matrix().apply {
            postScale(scale, scale)
            postTranslate(offsetX, offsetY)
            postScale(overallScale, overallScale, width / 2f, height / 2f)
            postTranslate(overallOffsetX, overallOffsetY)
        }
        val inverse = Matrix()
        if (transform.invert(inverse)) {
            inverse.mapPoints(pt)
        }
        Log.d("ScreenToCanvas", "Input=($sx,$sy) -> Output=(${pt[0]}, ${pt[1]})")
        return pt[0] to pt[1]
    }

    internal fun canvasToView(cx: Float, cy: Float): Pair<Float, Float> {
        val scaledWidth = canvasWidth * scale
        val scaledHeight = canvasHeight * scale
        val ox = (width - scaledWidth) / 2f
        val oy = (height - scaledHeight) / 2f
        val pivotX = width / 2f
        val pivotY = height / 2f

        val vx0 = cx * scale + ox
        val vy0 = cy * scale + oy

        val vx = (vx0 - pivotX) * overallScale + pivotX + overallOffsetX
        val vy = (vy0 - pivotY) * overallScale + pivotY + overallOffsetY

        return vx to vy
    }

    fun setGridEnabled(enabled: Boolean) {
        showGrid = enabled
        invalidate()
    }

    fun setRulerEnabled(enabled: Boolean) {
        showRuler = enabled
        invalidate()
    }

    fun setPanMode(enabled: Boolean) {
        isPanMode = enabled
        if (enabled && selectedElements.isNotEmpty()) {
            canvasElements.forEach { it.isSelected = false }
            selectedElements.clear()
            onElementSelected?.invoke(emptyList())
            invalidate()
        }
    }

    fun setCanvasPanLocked(locked: Boolean) {
        isCanvasPanLocked = locked
    }

    fun resetZoomAndPan() {
        overallScale = 1f
        overallOffsetX = 0f
        overallOffsetY = 0f
        clampOverallPan()
        invalidate()
    }

    fun setZoomLevel(zoom: Float) {
        if (suppressZoomCallback) return
        var newScale = zoom.coerceIn(0.5f, 3.0f)

        val snapTargets = listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f)
        val snappedTarget = snapTargets.firstOrNull { abs(newScale - it) <= 0.03f }
        if (snappedTarget != null) {
            if (overallScale != snappedTarget) vibrateSoft()
            newScale = snappedTarget
        }

        overallScale = newScale
        clampOverallPan()
        invalidate()
    }

    fun getCurrentZoom(): Float = overallScale

    fun isCanvasCentered(): Boolean =
        abs(overallOffsetX) <= canvasSnapThresholdPx && abs(overallOffsetY) <= canvasSnapThresholdPx

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        adjustmentScope.coroutineContext[Job]?.cancel()
        pendingAdjustmentJobs.values.forEach { it.cancel() }
        pendingAdjustmentJobs.clear()
        cacheManager.clearAll()
    }

    // Exporter delegations
    fun rasterizeSvgElement(drawable: Drawable, element: CanvasElement, canvasScale: Float = 1f): Bitmap =
        exporter.rasterizeSvgElement(drawable, element, canvasScale)

    fun exportCanvas(
        options: ExportOptions,
        jsonOutputPath: String,
        onProgress: ((percent: Int, stage: String) -> Unit)? = null
    ): Pair<Bitmap, File> = exporter.exportCanvas(options, jsonOutputPath, onProgress)

    fun exportCanvasThumbnailBitmap(
        maxWidth: Int = 300,
        maxHeight: Int = 300,
        onProgress: ((percent: Int, stage: String) -> Unit)? = null
    ): Pair<Bitmap, File> = exporter.exportCanvasThumbnailBitmap(maxWidth, maxHeight, onProgress)

    fun generatePreviewBitmap(maxWidth: Int = 300, maxHeight: Int = 300): Bitmap =
        exporter.generatePreviewBitmap(maxWidth, maxHeight)

    fun exportCanvasThumbnail(
        maxWidth: Int = 300,
        maxHeight: Int = 300,
        onProgress: ((percent: Int, stage: String) -> Unit)? = null
    ): Pair<Bitmap, File> = exporter.exportCanvasThumbnail(maxWidth, maxHeight, onProgress)

    suspend fun exportCanvasJson(jsonOutputPath: String) =
        exporter.exportCanvasJson(jsonOutputPath)

    // Alignment delegations
    fun alignHorizontal(align: HAlign, mode: MultiAlignMode = MultiAlignMode.CANVAS) =
        alignmentHelper.alignHorizontal(align, mode)

    fun alignVertical(align: VAlign, mode: MultiAlignMode = MultiAlignMode.CANVAS) =
        alignmentHelper.alignVertical(align, mode)

    internal fun checkRotationAlignment(element: CanvasElement) =
        alignmentHelper.checkRotationAlignment(element)

    internal fun checkDragSnap() =
        alignmentHelper.checkDragSnap()

    internal fun checkGroupRotationAlignment() =
        alignmentHelper.checkGroupRotationAlignment()

    internal fun checkCanvasPanSnap() =
        alignmentHelper.checkCanvasPanSnap()

    internal fun clampOverallPan() =
        alignmentHelper.clampOverallPan()

    internal fun computeBackgroundPanBounds(e: CanvasElement) =
        alignmentHelper.computeBackgroundPanBounds(e)

    // ElementManager bounds delegates
    internal fun getCombinedSelectedBounds(): RectF = elementManager.getCombinedSelectedBounds()
    internal fun getGroupRotatedBounds(): FloatArray = elementManager.getGroupRotatedBounds()
    internal fun getGroupRotatedPath(): Path? = elementManager.getGroupRotatedPath()
    internal fun getSelectionPath(): Path? = elementManager.getSelectionPath()
    internal fun getGroupTrueBounds(): FloatArray = elementManager.getGroupTrueBounds()

    // Rendering delegates called by helper renderers
    fun colorFilterFor(filter: ImageFilter?): ColorFilter? = ColorFilterFactory.colorFilterFor(filter)

    internal fun drawWithBlend(element: CanvasElement): Xfermode? =
        canvasRenderer.drawWithBlend(element)

    internal fun createGradientShader(
        gradientItem: GradientItem,
        width: Float,
        height: Float,
        translateX: Float = 0f,
        translateY: Float = 0f
    ) = canvasRenderer.createGradientShader(gradientItem, width, height, translateX, translateY)

    @Suppress("LongParameterList")
    internal fun drawFeatherMask(
        canvas: Canvas,
        elementId: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        featherRadius: Float,
        featherWidth: Float,
        direction: FeatherDirection = FeatherDirection.ALL
    ) = canvasRenderer.drawFeatherMask(
        canvas, elementId, left, top, right, bottom, featherRadius, featherWidth, direction
    )

    internal fun justifyText(
        canvas: Canvas,
        text: String,
        yOffset: Float,
        element: CanvasElement
    ) = canvasRenderer.justifyText(canvas, text, yOffset, element)
}
