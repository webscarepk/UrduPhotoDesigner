package com.webscare.urducanvas.common.views

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.Xfermode
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import com.caverock.androidsvg.SVG
import com.google.gson.Gson
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.BlendType
import com.webscare.urducanvas.common.canvas.enums.BrushStyle
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.FeatherDirection
import com.webscare.urducanvas.common.canvas.enums.GradientType
import com.webscare.urducanvas.common.canvas.enums.HAlign
import com.webscare.urducanvas.common.canvas.enums.LabelShape
import com.webscare.urducanvas.common.canvas.enums.LetterCasing
import com.webscare.urducanvas.common.canvas.enums.ListStyle
import com.webscare.urducanvas.common.canvas.enums.Mode
import com.webscare.urducanvas.common.canvas.enums.MultiAlignMode
import com.webscare.urducanvas.common.canvas.enums.ShapeType
import com.webscare.urducanvas.common.canvas.enums.TextAlignment
import com.webscare.urducanvas.common.canvas.enums.TextDecoration
import com.webscare.urducanvas.common.canvas.enums.VAlign
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.canvas.model.ExportOptions
import com.webscare.urducanvas.common.canvas.model.GradientItem
import com.webscare.urducanvas.common.canvas.model.StrokeData
import com.webscare.urducanvas.common.canvas.sealed.ImageFilter
import com.webscare.urducanvas.common.utils.BrushRenderUtils.createBackgroundGradientShader
import com.webscare.urducanvas.common.utils.ImageAdjustmentHelper
import com.webscare.urducanvas.common.utils.isDarkModeEnabled
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.ImageProcessor.trimTransparentEdges
import com.webscare.urducanvas.common.utils.ShapeRenderUtils
import com.webscare.urducanvas.common.utils.Utils.vibrateSoft
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Objects
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

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

    private val gson: Gson by lazy {
        EntryPointAccessors.fromApplication(
            context,
            com.webscare.urducanvas.di.GsonEntryPoint::class.java,
        ).gson()
    }
    private var gestureDetector: GestureDetector
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
    internal var currentBrushGradient: GradientItem? = null

    override var pickerX = 0f
    override var pickerY = 0f
    internal var isDraggingPicker = false
    override val desiredPickerIconSizePx = 64f
    private val desiredIconSizeDp = 20f
    private val desiredIconScreenSizePx: Float
        get() = desiredIconSizeDp * resources.displayMetrics.density


    override var allowFreeDrag: Boolean = false
    private val checkerSize = 20
    private val light = "#F5F5F5".toColorInt()
    private val dark = "#DDDDDD".toColorInt()

    override var activeGroupId: String? = null
    internal var inSelectionMode = false


    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    internal val initialElementSizes = mutableMapOf<String, Pair<Float, Float>>()
    internal var suppressZoomCallback = false

    private val checkerShader: BitmapShader by lazy {
        // create a 2×2 tile
        val bmp = createBitmap(checkerSize * 2, checkerSize * 2)
        val c = Canvas(bmp)
        val p = Paint()

        // top-left & bottom-right = light
        p.color = light
        c.drawRect(0f, 0f, checkerSize.toFloat(), checkerSize.toFloat(), p)
        c.drawRect(
            checkerSize.toFloat(),
            checkerSize.toFloat(),
            (checkerSize * 2).toFloat(),
            (checkerSize * 2).toFloat(),
            p,
        )

        // top-right & bottom-left = dark
        p.color = dark
        c.drawRect(checkerSize.toFloat(), 0f, (checkerSize * 2).toFloat(), checkerSize.toFloat(), p)
        c.drawRect(0f, checkerSize.toFloat(), checkerSize.toFloat(), (checkerSize * 2).toFloat(), p)

        BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    internal val checkerPaint = Paint().apply { shader = checkerShader }

    internal val rotateLinePaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    override val canvasElements = CopyOnWriteArrayList<CanvasElement>()
    private lateinit var backgroundElement: CanvasElement



    override var currentMode: Mode = Mode.NONE














    // Finger midpoint (screen coords) at the moment the pinch started.
    // Used to zoom around the actual finger position, not the screen centre.



    // Canvas offset captured at pinch-start — combined with pinchFocusX/Y for
    // pivot-correct zoom math so the canvas never jumps when fingers lift.






    // Stores each element's scale at the START of a RESIZE handle gesture.
    // Used for absolute scale math (same approach as MULTI_TOUCH pinch) so both
    // resize mechanisms produce identical zoom levels for the same finger movement.



    override var scale = 1f
    override var offsetX = 0f
    override var offsetY = 0f

    // Overall canvas zoom & pan
    override var overallScale = 1f
    override var overallOffsetX = 0f
    override var overallOffsetY = 0f

    private var initialOverallScale = 1f

    override val lastDrawnIconRect = mutableMapOf<String, RectF>()

    // Per-element cache for the expensive pre-blurred shadow bitmap.
    // Key = elementId. Invalidated whenever shadow params change (detected via fingerprint).
    override val cacheManager = CanvasCacheManager(context)


    private fun isGestureActive(): Boolean = currentMode == Mode.DRAG ||
        currentMode == Mode.ROTATE ||
        currentMode == Mode.RESIZE ||
        currentMode == Mode.TRANSFORM ||
        currentMode == Mode.MULTI_TOUCH ||
        currentMode == Mode.CANVAS_PAN

    // ── Background coroutine scope for async image-adjustment processing ─────
    // applyAllAdjustments can take 100-500 ms on a full-res bitmap — never block onDraw.
    private val adjustmentScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Track in-flight jobs so we don't double-schedule for the same element.
    private val pendingAdjustmentJobs = mutableMapOf<String, Job>()

    // ── Reusable objects to eliminate per-frame allocations in onDraw ────────
    private val reusableRectF = RectF()
    private val reusableDrawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val reusableOpacityPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reusableBgPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val reusableStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isFilterBitmap = true
    }
    private val reusableBoxPaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
    }

    init {
        
    }

    private val alignmentPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 1f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val rotationTextPaint = Paint().apply {
        color = Color.BLACK
        textSize = 18f.dpToPx()
        style = Paint.Style.FILL
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.default_canvas)
    }

    private val rotationLabelPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val canvasShadowPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 30 // softness control
        maskFilter = BlurMaskFilter(100f, BlurMaskFilter.Blur.NORMAL)
    }

    internal val drawingModeOverlayPaint = Paint().apply {
        color = Color.argb(120, 255, 255, 255)
        style = Paint.Style.FILL
    }

    override var showVerticalGuide = false
    override var showHorizontalGuide = false
    override var showRotationVerticalGuide = false
    override var showRotationHorizontalGuide = false

    // ── Canvas pan center-snap guides ────────────────────────────
    override var showCanvasCenterVerticalSnap = false
    override var showCanvasCenterHorizontalSnap = false
    private val canvasSnapThresholdPx = 8f // screen-px proximity to trigger snap

    // Paint for the canvas-pan center snap lines (solid cyan, bold)
    private val canvasSnapPaint = Paint().apply {
        color = Color.parseColor("#00BCD4")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(14f, 6f), 0f)
    }

    // ── Grid overlay ─────────────────────────────────────────────
    override var showGrid = false
    private val gridPaint = Paint().apply {
        color = Color.argb(60, 0, 0, 0)
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = false
    }

    // ── Ruler overlay ─────────────────────────────────────────────
    override var showRuler = false
    private val rulerPaint = Paint().apply {
        color = Color.argb(180, 50, 50, 50)
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val rulerTextPaint = Paint().apply {
        color = Color.argb(200, 50, 50, 50)
        textSize = 8f.dpToPx()
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val rulerBgPaint = Paint().apply {
        color = Color.argb(160, 240, 240, 240)
        style = Paint.Style.FILL
    }

    // ── Pan mode (single-finger pan without selecting elements) ───
    private var isPanMode = false
    internal var isCanvasPanLocked = false
    internal val removeIcon: Drawable by lazy {
        AppCompatResources.getDrawable(context, R.drawable.ic_cross)!!
    }
    internal val resizeIcon: Drawable by lazy {
        AppCompatResources.getDrawable(context, R.drawable.ic_resize)!!
    }
    internal val rotateIcon: Drawable by lazy {
        AppCompatResources.getDrawable(context, R.drawable.ic_rotate)!!
    }
    internal val editIcon: Drawable by lazy {
        AppCompatResources.getDrawable(context, R.drawable.ic_edit_text)!!
    }

    internal val transformIcon: Drawable by lazy {
        AppCompatResources.getDrawable(context, R.drawable.ic_transform)!!
    }

    override val selectedElements: CopyOnWriteArrayList<CanvasElement> = CopyOnWriteArrayList()
    private var lastTouchedElement: CanvasElement? = null
    private val canvasRenderer = CanvasRenderer(this)
    private val touchHandler = CanvasTouchHandler(this)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return touchHandler.onTouchEvent(event)
    }

    private fun Float.dpToPx(): Float = this * resources.displayMetrics.density

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
        gradient: GradientItem? = null,
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

        // ── Render async at display resolution — never freeze the UI thread ──
        // The color picker only needs screen-pixel accuracy, not full canvas resolution.
        // We build the bitmap in the background and invalidate once it's ready.
        adjustmentScope.launch {
            val displayScale = minOf(
                width.toFloat() / canvasWidth.toFloat(),
                height.toFloat() / canvasHeight.toFloat(),
                1f, // never upscale — screen size is enough
            ).coerceAtLeast(0.1f)
            val bmpW = (canvasWidth * displayScale).toInt().coerceAtLeast(1)
            val bmpH = (canvasHeight * displayScale).toInt().coerceAtLeast(1)
            val bmp = createBitmap(bmpW, bmpH)
            renderCanvasTo(Canvas(bmp), displayScale)

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

    /**
     * Called from LayersFragment when the user double-taps a group header or
     * taps a child row while the group is selected. Puts CanvasView into
     * GROUP_EDIT mode for that group, selecting [focusChildId] if provided.
     * Kept for potential external use — currently GROUP_EDIT is managed inline.
     */
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

    /** Exits GROUP_EDIT mode, clears selection. */
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

    /**
     * If there isn’t already a background element, create one,
     * lock it, fill its fields, and insert it at index 0.
     */
    private fun ensureBackgroundElement() {
        // ✅ If user already has a background, do nothing
        if (canvasElements.any { it.type == ElementType.BACKGROUND }) return

        // ✅ If backgroundElement not initialized → skip creating anything
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

    /**
     * Call this for your horizontal buttons:
     *  – if one element: snaps to canvas LEFT/CENTER/RIGHT
     *  – if many:
     *     • CANVAS: treat group as block and snap its LEFT/CENTER/RIGHT to the art board
     *     • SELECTION: snap each element’s own LEFT/CENTER/RIGHT to the first element
     */
    fun alignHorizontal(
        align: HAlign,
        mode: MultiAlignMode = MultiAlignMode.CANVAS,
    ) {
        when {
            selectedElements.isEmpty() -> return

            selectedElements.size == 1 -> {
                val elem = selectedElements.first()

                if (elem.type == ElementType.BACKGROUND && elem.bitmap != null) {
                    val (xRange, _) = computeBackgroundPanBounds(elem)
                    val targetX = when (align) {
                        HAlign.LEFT -> xRange.start
                        HAlign.CENTER -> canvasWidth / 2f
                        HAlign.RIGHT -> xRange.endInclusive
                    }
                    // If range is invalid (start > end), fall back to center
                    val x = if (xRange.start <= xRange.endInclusive) {
                        targetX.coerceIn(xRange.start, xRange.endInclusive)
                    } else {
                        canvasWidth / 2f
                    }
                    elem.x = x
                    onElementChanged?.invoke(elem)
                    invalidate()
                    return
                }

                // Normal element path
                val halfW = elem.getLocalContentWidth() * elem.scale / 2f
                if (!halfW.isFinite() ||
                    !canvasWidth.toFloat()
                        .isFinite() ||
                    canvasWidth <= 0f
                ) {
                    return
                }

                val rawX = when (align) {
                    HAlign.LEFT -> halfW
                    HAlign.CENTER -> canvasWidth / 2f
                    HAlign.RIGHT -> canvasWidth - halfW
                }

                val minX = halfW
                val maxX = canvasWidth - halfW
                val oversized = (halfW * 2f) > canvasWidth

                elem.x = when {
                    oversized -> canvasWidth / 2f // element wider than canvas: center it
                    minX <= maxX -> rawX.coerceIn(minX, maxX) // normal case
                    else -> canvasWidth / 2f // safety fallback
                }

                onElementChanged?.invoke(elem)
                invalidate()
                return
            }

            mode == MultiAlignMode.CANVAS -> {
                val edges = selectedElements.map { e ->
                    val half = e.getLocalContentWidth() * e.scale / 2f
                    e.x - half to e.x + half
                }
                val groupLeft = edges.minOf { it.first }
                val groupRight = edges.maxOf { it.second }
                val groupW = groupRight - groupLeft
                val targetLeft = when (align) {
                    HAlign.LEFT -> 0f
                    HAlign.CENTER -> (canvasWidth - groupW) / 2f
                    HAlign.RIGHT -> canvasWidth - groupW
                }
                val dx = targetLeft - groupLeft
                selectedElements.forEach { e ->
                    e.x += dx
                    onElementChanged?.invoke(e)
                }
            }

            else -> {
                val first = selectedElements.first()
                val firstHalf = first.getLocalContentWidth() * first.scale / 2f
                val firstLeft = first.x - firstHalf
                val firstCenter = first.x
                val firstRight = first.x + firstHalf

                selectedElements.drop(1).forEach { e ->
                    val half = e.getLocalContentWidth() * e.scale / 2f
                    e.x = when (align) {
                        HAlign.LEFT -> firstLeft + half
                        HAlign.CENTER -> firstCenter
                        HAlign.RIGHT -> firstRight - half
                    }
                    onElementChanged?.invoke(e)
                }
            }
        }
        invalidate()
    }

    fun alignVertical(
        align: VAlign,
        mode: MultiAlignMode = MultiAlignMode.CANVAS,
    ) {
        when {
            selectedElements.isEmpty() -> return

            selectedElements.size == 1 -> {
                val elem = selectedElements.first()

                if (elem.type == ElementType.BACKGROUND && elem.bitmap != null) {
                    // special case background
                    val (_, yRange) = computeBackgroundPanBounds(elem)
                    val targetY = when (align) {
                        VAlign.TOP -> yRange.start
                        VAlign.MIDDLE -> canvasHeight / 2f
                        VAlign.BOTTOM -> yRange.endInclusive
                    }
                    // make sure we stay within those pan bounds
                    elem.y = targetY.coerceIn(yRange.start, yRange.endInclusive)
                    onElementChanged?.invoke(elem)
                    invalidate()
                    return
                }

                val halfH = elem.getLocalContentHeight() * elem.scale / 2f
                val rawY = when (align) {
                    VAlign.TOP -> halfH
                    VAlign.MIDDLE -> canvasHeight / 2f
                    VAlign.BOTTOM -> canvasHeight - halfH
                }
                val minY = halfH
                val maxY = canvasHeight - halfH
                val oversized = (halfH * 2f) > canvasHeight
                elem.y = when {
                    oversized -> canvasHeight / 2f // element taller than canvas: center it
                    minY <= maxY -> rawY.coerceIn(minY, maxY)
                    else -> canvasHeight / 2f // safety fallback
                }
                onElementChanged?.invoke(elem)
                invalidate()
                return
            }

            mode == MultiAlignMode.CANVAS -> {
                val edges = selectedElements.map { e ->
                    val half = e.getLocalContentHeight() * e.scale / 2f
                    e.y - half to e.y + half
                }
                val groupTop = edges.minOf { it.first }
                val groupBottom = edges.maxOf { it.second }
                val groupH = groupBottom - groupTop
                val targetTop = when (align) {
                    VAlign.TOP -> 0f
                    VAlign.MIDDLE -> (canvasHeight - groupH) / 2f
                    VAlign.BOTTOM -> canvasHeight - groupH
                }
                val dy = targetTop - groupTop
                selectedElements.forEach { e ->
                    e.y += dy
                    onElementChanged?.invoke(e)
                }
            }

            else -> {
                val first = selectedElements.first()
                val firstHalf = first.getLocalContentHeight() * first.scale / 2f
                val firstTop = first.y - firstHalf
                val firstCenter = first.y
                val firstBottom = first.y + firstHalf

                selectedElements.drop(1).forEach { e ->
                    val half = e.getLocalContentHeight() * e.scale / 2f
                    e.y = when (align) {
                        VAlign.TOP -> firstTop + half
                        VAlign.MIDDLE -> firstCenter
                        VAlign.BOTTOM -> firstBottom - half
                    }
                    onElementChanged?.invoke(e)
                }
            }
        }
        invalidate()
    }

    /**
     * Resolves which CanvasElements go into CanvasView.selectedElements.
     * Rule: GROUP sentinels never enter selectedElements (zero geometry).
     * When a sentinel has isSelected=true its children are added instead.
     * Regular selected non-group elements pass through unchanged.
     */
    private fun resolveSelectedForCanvas(elements: List<CanvasElement>): List<CanvasElement> {
        val result = mutableListOf<CanvasElement>()
        val selectedGroupIds = elements
            .filter { it.isSelected && it.type == ElementType.GROUP }
            .map { it.id }.toSet()
        for (el in elements) {
            when {
                el.type == ElementType.GROUP -> {
                    if (el.isSelected) {
                        // Expand: add children so bounds/handles work on real geometry
                        elements.filter { it.groupId == el.id }.forEach { child ->
                            if (result.none { r -> r.id == child.id }) result.add(child)
                        }
                    }
                    // Sentinel itself never enters selectedElements
                }
                el.groupId != null && el.groupId in selectedGroupIds -> {
                    // Child of a selected group -- may already be added above; avoid double-add
                    if (result.none { r -> r.id == el.id }) result.add(el)
                }
                el.isSelected -> result.add(el)
                else -> { /* not selected */ }
            }
        }
        return result
    }

    /**
     * Syncs the canvas elements with a new list from the ViewModel.
     * Updates the internal `selectedElements` list based on the `isSelected` flag of incoming elements.
     */
    fun syncElements(newElements: List<CanvasElement>) {
        val oldSize = canvasElements.size
        canvasElements.clear()
        canvasElements.addAll(newElements.sortedBy { it.zIndex })

        // Re-hydrate transient context on every element — it gets nulled during
        // serialization/export and by copy() in undo/redo paths.  Without this,
        // resolveAdjustedBitmapAsync silently skips blur (and all adjustments)
        // because ImageAdjustmentHelper needs a Context for RenderScript.
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

        // If exactly one group child is selected (e.g. from the layers panel),
        // enter GROUP_EDIT mode automatically so the first canvas drag moves only
        // that child and tap-outside exits the group cleanly.
        // IMPORTANT: never override an active gesture mode (DRAG/ROTATE/RESIZE/TRANSFORM).
        // syncElements fires on every canvasElements update — including during drag
        // when onElementChanged is called — and overriding Mode.DRAG here would break it.
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

    /**
     * Calculates the combined bounding box for all currently selected elements.
     * Returns an empty RectF if no elements are selected.
     */
    /** Returns an axis-aligned bounding box that covers all rotated elements */
    private fun getCombinedSelectedBounds(): RectF {
        val drawableSelected = selectedElements.filter { it.type != ElementType.GROUP }
        if (drawableSelected.isEmpty()) return RectF()

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        drawableSelected.forEach { element ->
            val corners = element.getRotatedCorners()
            for (i in corners.indices step 2) {
                val x = corners[i]
                val y = corners[i + 1]
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }

        return RectF(minX, minY, maxX, maxY)
    }

    /** Returns a straight rectangular path around all selected rotated elements. */
    private fun getGroupRotatedBounds(): FloatArray {
        // Collect all rotated corners from all selected elements
        val allPoints = mutableListOf<Float>()
        selectedElements.forEach { el ->
            allPoints.addAll(el.getRotatedCorners().toList())
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (i in allPoints.indices step 2) {
            val x = allPoints[i]
            val y = allPoints[i + 1]
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }

        // Straight axis-aligned bounding box (no extra rotation)
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    /** Returns a non-rotated rectangular path that covers all selected elements. */
    private fun getGroupRotatedPath(): Path? {
        if (selectedElements.size <= 1) return null

        val b = getGroupRotatedBounds()
        return Path().apply {
            moveTo(b[0], b[1])
            lineTo(b[2], b[1])
            lineTo(b[2], b[3])
            lineTo(b[0], b[3])
            close()
        }
    }

    private fun getSelectionPath(): Path? {
        if (selectedElements.isEmpty()) return null
        if (selectedElements.size == 1) {
            val c = selectedElements.first().getRotatedCorners()
            return Path().apply {
                moveTo(c[0], c[1])
                lineTo(c[2], c[3])
                lineTo(c[4], c[5])
                lineTo(c[6], c[7])
                close()
            }
        }
        // Multi-selection → fallback to axis aligned for now
        val b = getCombinedSelectedBounds()
        return Path().apply {
            addRect(b, Path.Direction.CW)
        }
    }

    internal fun removeSelectedElement() {
        if (currentMode == Mode.GROUP_EDIT &&
            activeGroupId != null &&
            selectedElements.size == 1 &&
            selectedElements.first().groupId == activeGroupId
        ) {
            // ── GROUP_EDIT: delete only the selected child, not the whole group ──
            // If it was the last child, auto-remove the sentinel too.
            val child = selectedElements.first()
            val remainingSiblings = canvasElements.count {
                it.groupId == activeGroupId && it.id != child.id
            }
            val idsToRemove = if (remainingSiblings == 0) {
                // Last child — remove child + sentinel
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

        // ── Normal delete: remove selected elements + owned GROUP sentinels ────
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
        val elementsToFilter =
            selectedElements.toList() // Create a copy to avoid concurrent modification
        elementsToFilter.forEach { element ->
            if (element != null && (element.type == ElementType.IMAGE || element.type == ElementType.STICKER)) {
                element.imageFilter = filter!!
                onElementChanged?.invoke(element) // Notify ViewModel of change
                invalidate()
            }
        }
    }

    fun setFont(fontEntity: com.webscare.urducanvas.data.model.FontEntity) {
        selectedElements.filter { it.type == ElementType.TEXT }.forEach { element ->
            element.fontId = fontEntity.id.toString()

            // Check if the file_path is not blank before attempting to create a typeface
            if (fontEntity.file_path?.isNotBlank()!!) {
                try {
                    element.paint.typeface = Typeface.createFromFile(fontEntity.file_path)
                } catch (e: Exception) {
                    Log.e("CanvasView", "Error loading typeface from file: ${fontEntity.file_path}", e)

                    element.paint.typeface = Typeface.DEFAULT
                }
            } else {
                // If file_path is blank, do not set the typeface.
                // The existing typeface on the element will remain, or you could explicitly
                // set it to a default system typeface if that's desired when no custom font is selected.
                // For example:
                // element.paint.typeface = Typeface.DEFAULT
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

    fun setCanvasBackgroundGradient(gradientItem: GradientItem) {
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
            bitmap = src // ← keep the full-size image
        }
        invalidate()
    }

    fun clearSelection() {
        canvasElements.forEach { it.isSelected = false }
        onElementSelected?.invoke(emptyList())
    }

    private fun renderCanvasTo(canvas: Canvas, scaleFactor: Float) {
        val scaledWidth = canvasWidth * scaleFactor
        val scaledHeight = canvasHeight * scaleFactor
        val offsetX = (canvas.width - scaledWidth) / 2f
        val offsetY = (canvas.height - scaledHeight) / 2f

        canvas.drawFilter = android.graphics.PaintFlagsDrawFilter(
            0,
            android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG,
        )

        canvas.withTranslation(offsetX, offsetY) {
            scale(scaleFactor, scaleFactor)
            canvasRenderer.drawCanvasElements(this, showOverlays = false, showCheckerboard = false)
        }
    }

    private fun addWatermark(canvas: Canvas, width: Int, height: Int) {
        val watermarkText = "UrduCanvas" // Watermark text

        // Load the custom font from the 'res/fonts' folder
        val watermarkTypeface = ResourcesCompat.getFont(context, R.font.default_canvas)

        // Create a paint object with desired properties
        val watermarkPaint = Paint().apply {
            color = "#000000".toColorInt() // Light gray color for the watermark
            textSize = 40f // Adjust text size for the watermark
            alpha = 50 // Semi-transparent effect (80 out of 255)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            style = Paint.Style.FILL
            typeface = watermarkTypeface
        }

        val horizontalStep = 250
        val verticalStep = 250
        val rotationAngle = 45f

        for (x in 0 until width step horizontalStep) {
            for (y in 0 until height step verticalStep) {
                val xPos = x + watermarkPaint.textSize / 2
                val yPos = y + watermarkPaint.textSize / 2

                canvas.withRotation(rotationAngle, xPos.toFloat(), yPos.toFloat()) {
                    drawText(watermarkText, xPos.toFloat(), yPos.toFloat(), watermarkPaint)
                }
            }
        }
    }

    fun rasterizeSvgElement(
        drawable: Drawable,
        element: CanvasElement,
        canvasScale: Float = 1f,
    ): Bitmap {
        if (drawable is BitmapDrawable) {
            drawable.bitmap?.let { return it }
        }

        // Get the logical content size — this is how big the SVG appears on canvas
        // before element.scale is applied by the canvas matrix
        val logicalW = element.logicalContentWidth.takeIf { it > 0 }
            ?: if (drawable is android.graphics.drawable.PictureDrawable) {
                drawable.picture.width.toFloat()
                    .takeIf { it > 0 } ?: 512f
            } else {
                drawable.intrinsicWidth.toFloat().takeIf { it > 0 } ?: 512f
            }

        val logicalH = element.logicalContentHeight.takeIf { it > 0 }
            ?: if (drawable is android.graphics.drawable.PictureDrawable) {
                drawable.picture.height.toFloat()
                    .takeIf { it > 0 } ?: 512f
            } else {
                drawable.intrinsicHeight.toFloat().takeIf { it > 0 } ?: 512f
            }

        // Actual displayed size on canvas = logicalSize * element.scale
        // We also apply canvasScale (export scale factor) so exported images are sharp too
        // Extra 2x oversample so user can still zoom in a bit without pixelation
        val oversample = 2f
        val bitmapW =
            (logicalW * element.scale * canvasScale * oversample).roundToInt().coerceAtLeast(1)
        val bitmapH =
            (logicalH * element.scale * canvasScale * oversample).roundToInt().coerceAtLeast(1)

        val bitmap = createBitmap(bitmapW, bitmapH)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, bitmapW, bitmapH)
        drawable.draw(canvas)

        val finalBitmap = if (element.applyWhiteTintInDarkMode && context.isDarkModeEnabled()) {
            val result = createBitmap(bitmapW, bitmapH)
            val tempCanvas = Canvas(result)
            val paint = Paint().apply {
                colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            }
            tempCanvas.drawBitmap(bitmap, 0f, 0f, paint)
            bitmap.recycle()
            result
        } else {
            bitmap
        }

        return finalBitmap
    }

    fun exportCanvas(
        options: ExportOptions,
        jsonOutputPath: String,
        onProgress: ((percent: Int, stage: String) -> Unit)? = null,
    ): Pair<Bitmap, File> { // ← File, not String
        val contentWidth = this.canvasWidth
        val contentHeight = this.canvasHeight

        val scaleFactor = options.resolution.scaleFactor.takeIf { it > 0f } ?: 1f
        val outputWidth = (contentWidth * scaleFactor).roundToInt()
        val outputHeight = (contentHeight * scaleFactor).roundToInt()

        onProgress?.invoke(10, "Preparing canvas")

        val bitmap = createBitmap(outputWidth, outputHeight)
        val canvas = Canvas(bitmap)

        if (options.format.format == Bitmap.CompressFormat.JPEG) {
            canvas.drawColor(Color.WHITE)
        } else {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }

        onProgress?.invoke(30, "Rendering design")
        onProgress?.invoke(40, "Just a few moments")

        renderCanvasTo(canvas, scaleFactor)
//        addWatermark(canvas, outputWidth, outputHeight)
        onProgress?.invoke(50, "Please wait")

        val elementsWithBitmap = canvasElements

        val total = elementsWithBitmap.size

        if (total > 0) {
            onProgress?.invoke(70, "Encoding image data")
            elementsWithBitmap.forEachIndexed { index, element ->
                when {
                    // ✅ SVG with data — no bitmap needed, SVG XML is already in svgData field
                    element.svgDrawable != null && element.svgData != null -> {
                        element.bitmapData = null // save space, SVG XML is the source of truth
                    }

                    // ⚠️ SVG without data (legacy) — rasterize as high-res fallback
                    element.svgDrawable != null && element.bitmap == null -> {
                        val rasterized =
                            rasterizeSvgElement(element.svgDrawable!!, element, scaleFactor)
                        element.bitmap = rasterized
                        element.bitmapData = ImageProcessor.bitmapToBase64Lossless(rasterized)
                    }

                    // Regular bitmap element
                    else -> {
                        element.bitmap?.let {
                            element.bitmapData = ImageProcessor.bitmapToBase64Lossless(it)
                        }
                    }
                }
                element.drawStrokes?.forEach { stroke -> stroke.serializePath() }
                val progress = 70 + ((index + 1) * 20 / total)
                onProgress?.invoke(progress, "Saving ${index + 1} of $total")
            }
        } else {
            onProgress?.invoke(90, "No bitmaps to encode")
        }

        // ✅ Stream JSON directly to a file — never builds a giant String in RAM
        val jsonFile = File(jsonOutputPath)
        jsonFile.bufferedWriter().use { writer ->
            gson.toJson(canvasElements, writer) // streaming overload — no StringBuffer
        }

        onProgress?.invoke(100, "Done")
        return Pair(bitmap, jsonFile) // ← return File, not String
    }

    /**
     * Lean thumbnail export for auto-save on back-press.
     *
     * Returns Pair<Bitmap, File> — the File is the serialized JSON written to a temp file
     * in cacheDir. The CALLER is responsible for copying/moving this file to its final
     * destination and then deleting it. This avoids ever holding the full JSON String in RAM.
     *
     * Unlike [exportCanvasThumbnail], this function NEVER calls [ImageProcessor.bitmapToBase64Lossless].
     * Elements whose bitmapData is already set are serialized as-is. Only SVGs without svgData
     * (legacy edge case) are re-encoded, using lossy JPEG via [ImageProcessor.bitmapToBase64].
     */
    fun exportCanvasThumbnailBitmap(
        maxWidth: Int = 300,
        maxHeight: Int = 300,
        onProgress: ((percent: Int, stage: String) -> Unit)? = null,
    ): Pair<Bitmap, File> {
        val contentWidth = this.canvasWidth
        val contentHeight = this.canvasHeight

        val aspectRatio = contentWidth.toFloat() / contentHeight
        val targetWidth: Int
        val targetHeight: Int
        if (aspectRatio >= 1f) {
            targetWidth = maxWidth
            targetHeight = (maxWidth / aspectRatio).toInt().coerceAtLeast(1)
        } else {
            targetHeight = maxHeight
            targetWidth = (maxHeight * aspectRatio).toInt().coerceAtLeast(1)
        }

        onProgress?.invoke(10, "Preparing thumbnail")

        // ── Render the visual thumbnail at small size ──────────────────────────
        val thumbnailBitmap = createBitmap(targetWidth, targetHeight)
        val canvas = Canvas(thumbnailBitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val scaleFactorX = targetWidth.toFloat() / contentWidth.toFloat()
        val scaleFactorY = targetHeight.toFloat() / contentHeight.toFloat()
        val scaleFactor = minOf(scaleFactorX, scaleFactorY)

        onProgress?.invoke(30, "Rendering thumbnail")
        renderCanvasTo(canvas, scaleFactor)

        // ── Serialize elements — reuse existing bitmapData, never re-encode ────
        // This is the critical difference from exportCanvasThumbnail: we do NOT
        // call bitmapToBase64Lossless on elements that already have bitmapData.
        // The only case we encode is an SVG that has no svgData and no bitmapData,
        // which is a legacy edge case.
        onProgress?.invoke(60, "Preparing project data")

        val elementsSnapshot = canvasElements.map { original ->
            original.copy().also { copy ->
                copy.svgDrawable = original.svgDrawable
                copy.bitmap = original.bitmap
                // bitmapData is already copied by .copy() — don't touch it
            }
        }

        val total = elementsSnapshot.size
        elementsSnapshot.forEachIndexed { index, element ->
            try {
                when {
                    // SVG with data — svgData is the source of truth, bitmapData not needed
                    element.svgDrawable != null && element.svgData != null -> {
                        element.bitmapData = null
                        element.bitmap = null
                    }

                    // SVG without svgData — legacy fallback: encode once at small size
                    element.svgDrawable != null && element.bitmapData == null -> {
                        val rasterized = rasterizeSvgElement(element.svgDrawable!!, element)
                        // Use lossy encoding for this fallback — it's just for restore
                        element.bitmapData = ImageProcessor.bitmapToBase64(rasterized)
                        rasterized.recycle()
                        element.bitmap = null
                    }

                    // Regular bitmap — bitmapData was already set when the bitmap was added.
                    // If it's missing for some reason, encode it now (should be rare).
                    element.bitmap != null && element.bitmapData == null -> {
                        element.bitmapData = ImageProcessor.bitmapToBase64(element.bitmap!!)
                        element.bitmap = null
                    }

                    // bitmapData already present — nothing to do, just clear transient fields
                    else -> {
                        element.bitmap = null
                    }
                }

                // Always clear non-serializable transient fields before Gson
                element.svgDrawable = null
                element.context = null
                element.originalTypeface = null
            } catch (e: Exception) {
                Log.e("CanvasView", "exportCanvasThumbnailBitmap: element ${element.id} failed", e)
                element.bitmapData = null
                element.svgDrawable = null
                element.bitmap = null
                element.context = null
            }

            element.drawStrokes?.forEach { stroke -> stroke.serializePath() }
            val progress = 60 + ((index + 1) * 30 / total.coerceAtLeast(1))
            onProgress?.invoke(progress, "Saving ${index + 1} of $total")
        }

        onProgress?.invoke(92, "Writing project file")

        // ── Stream JSON directly to a temp file — never read it back into RAM ──
        // The caller receives the File and is responsible for:
        //   1. Copying / moving it to the final jsonPath via streams (no readText).
        //   2. Deleting it afterwards.
        // This guarantees the serialized JSON never exists as a String in heap.
        val jsonFile = File(context.cacheDir, "thumb_meta_${System.currentTimeMillis()}.json")
        return try {
            jsonFile.bufferedWriter().use { writer ->
                gson.toJson(elementsSnapshot, writer)
            }
            if (jsonFile.length() < 2L) throw IOException("JSON serialization produced empty output")

            onProgress?.invoke(96, "Thumbnail ready")
            Pair(thumbnailBitmap, jsonFile) // ← File, not String
        } catch (e: Exception) {
            Log.e("CanvasView", "exportCanvasThumbnailBitmap failed: ${e.message}", e)
            jsonFile.delete()
            // Return an empty-array sentinel file so the caller always gets a valid File
            val fallback = File(context.cacheDir, "thumb_meta_fallback.json")
            fallback.writeText("[]")
            Pair(thumbnailBitmap, fallback)
        }
    }

    fun generatePreviewBitmap(maxWidth: Int = 300, maxHeight: Int = 300): Bitmap {
        val contentWidth = this.canvasWidth
        val contentHeight = this.canvasHeight

        val aspectRatio = contentWidth.toFloat() / contentHeight
        val targetWidth: Int
        val targetHeight: Int
        if (aspectRatio >= 1f) {
            targetWidth = maxWidth
            targetHeight = (maxWidth / aspectRatio).toInt().coerceAtLeast(1)
        } else {
            targetHeight = maxHeight
            targetWidth = (maxHeight * aspectRatio).toInt().coerceAtLeast(1)
        }

        val bitmap = createBitmap(targetWidth, targetHeight)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val scaleFactorX = targetWidth.toFloat() / contentWidth.toFloat()
        val scaleFactorY = targetHeight.toFloat() / contentHeight.toFloat()
        val scaleFactor = minOf(scaleFactorX, scaleFactorY)

        renderCanvasTo(canvas, scaleFactor)
        return bitmap
    }

    fun exportCanvasThumbnail(
        maxWidth: Int = 300,
        maxHeight: Int = 300,
        onProgress: ((percent: Int, stage: String) -> Unit)? = null,
    ): Pair<Bitmap, File> {
        val contentWidth = this.canvasWidth
        val contentHeight = this.canvasHeight

        val aspectRatio = contentWidth.toFloat() / contentHeight
        val targetWidth: Int
        val targetHeight: Int
        if (aspectRatio >= 1f) {
            targetWidth = maxWidth
            targetHeight = (maxWidth / aspectRatio).toInt()
        } else {
            targetHeight = maxHeight
            targetWidth = (maxHeight * aspectRatio).toInt()
        }

        onProgress?.invoke(10, "Preparing thumbnail")

        val bitmap = createBitmap(targetWidth, targetHeight)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val scaleFactorX = targetWidth.toFloat() / contentWidth.toFloat()
        val scaleFactorY = targetHeight.toFloat() / contentHeight.toFloat()
        val scaleFactor = minOf(scaleFactorX, scaleFactorY)

        onProgress?.invoke(30, "Rendering thumbnail")
        renderCanvasTo(canvas, scaleFactor)

        val elementsSnapshot = canvasElements.map { original ->
            original.copy().also { copy ->
                copy.svgDrawable = original.svgDrawable
                copy.bitmap = original.bitmap
            }
        }

        val total = elementsSnapshot.size
        if (total > 0) {
            onProgress?.invoke(70, "Encoding image data")
            elementsSnapshot.forEachIndexed { index, element ->
                try {
                    when {
                        // SVG with svgData — source of truth, no bitmap needed
                        element.svgDrawable != null && element.svgData != null -> {
                            element.bitmapData = null
                            element.bitmap = null
                        }

                        // SVG without svgData — rasterize as fallback
                        element.svgDrawable != null && element.bitmap == null -> {
                            val rasterized = rasterizeSvgElement(
                                element.svgDrawable!!,
                                element,
                            ) // canvasScale=1f default
                            element.bitmap = rasterized
                            element.bitmapData = ImageProcessor.bitmapToBase64(rasterized)
                            element.svgDrawable = null // ✅ clear @Transient — not serializable
                        }

                        // Regular bitmap
                        else -> {
                            element.bitmap?.let {
                                element.bitmapData = ImageProcessor.bitmapToBase64(it)
                            }
                        }
                    }

                    // ✅ Always clear transient fields before serialization —
                    // Gson will crash or produce malformed JSON on non-serializable objects
                    element.svgDrawable = null
                    element.bitmap = null
                    element.context = null
                    element.originalTypeface = null
                } catch (e: Exception) {
                    Log.e("CanvasView", "Failed to encode element ${element.id}: ${e.message}", e)
                    // Zero out problematic fields so Gson doesn't choke on this element
                    element.bitmapData = null
                    element.svgDrawable = null
                    element.bitmap = null
                    element.context = null
                }

                element.drawStrokes?.forEach { stroke -> stroke.serializePath() }
                val progress = 70 + ((index + 1) * 20 / total)
                onProgress?.invoke(progress, "Saving ${index + 1} of $total")
            }
        } else {
            onProgress?.invoke(90, "No bitmaps to encode")
        }

        // Serialize — catch exception explicitly so we know what actually failed
        val jsonFile = File(context.cacheDir, "thumb_meta_${System.currentTimeMillis()}.json")
        return try {
            jsonFile.bufferedWriter().use { writer ->
                gson.toJson(elementsSnapshot, writer)
            }

            if (jsonFile.length() < 2L) {
                throw IOException("JSON serialization produced empty output")
            }

            onProgress?.invoke(95, "Thumbnail ready")
            Pair(bitmap, jsonFile) // Return File — caller streams it, never loads full String
        } catch (e: Exception) {
            Log.e("CanvasView", "exportCanvasThumbnail failed: ${e.message}", e)
            jsonFile.delete()
            // Return an empty-array sentinel file so the caller always gets a valid File
            val fallback = File(context.cacheDir, "thumb_meta_fallback.json")
            fallback.writeText("[]")
            Pair(bitmap, fallback)
        }
    }

    suspend fun exportCanvasJson(jsonOutputPath: String): Unit = withContext(Dispatchers.IO) {
        val safeElements = canvasElements.toList().map { element ->
            element.copy(
                drawStrokes = element.drawStrokes?.toList()?.map { s ->
                    s.copy(path = s.path?.let { Path(it) })
                }?.toMutableList(),
            ).also { copy ->
                copy.svgDrawable = element.svgDrawable // ✅ restore @Transient
                copy.bitmap = element.bitmap
            }
        }

        safeElements.forEach { element ->
            when {
                // ✅ SVG with data — no bitmap needed, SVG XML is already in svgData field
                element.svgDrawable != null && element.svgData != null -> {
                    element.bitmapData = null // save space, SVG XML is the source of truth
                }

                // ⚠️ SVG without data (legacy) — rasterize as high-res fallback
                element.svgDrawable != null && element.bitmap == null -> {
                    val rasterized =
                        rasterizeSvgElement(element.svgDrawable!!, element, element.scale)
                    element.bitmap = rasterized
                    element.bitmapData = ImageProcessor.bitmapToBase64Lossless(rasterized)
                }

                // Regular bitmap element
                else -> {
                    element.bitmap?.let {
                        element.bitmapData = ImageProcessor.bitmapToBase64Lossless(it)
                    }
                }
            }
            element.drawStrokes?.forEach { stroke -> stroke.serializePath() }
        }

        // ✅ Stream to file — no String in RAM
        File(jsonOutputPath).bufferedWriter().use { writer ->
            gson.toJson(safeElements, writer)
        }
    }

    /**
     * Checks if the element's rotation is close to 0, 90, 180, or 270 degrees
     * and sets the rotation alignment guide flags accordingly.
     */
    private fun checkRotationAlignment(element: CanvasElement) {
        val rotationThreshold = 5f
        val normalizedRotation = (element.rotation % 360 + 360) % 360

        val snapAngles = listOf(0f, 90f, 180f, 270f, 360f)

        var snapped = false
        for (target in snapAngles) {
            if (abs(normalizedRotation - target) <= rotationThreshold) {
                if (element.rotation != target) {
                    element.rotation = target
                    vibrateSoft()
                }
                snapped = true
                break
            }
        }

        showRotationVerticalGuide =
            snapped &&
            (element.rotation == 0f || element.rotation == 180f || element.rotation == 360f)
        showRotationHorizontalGuide =
            snapped &&
            (element.rotation == 90f || element.rotation == 270f)
    }

    private fun checkDragSnap() {
        if (selectedElements.isEmpty()) return

        val snapThreshold = 5f

        if (selectedElements.size == 1) {
            // --- Single element snap ---
            val elem = selectedElements.first()
            var snapped = false

            // X snap
            if (abs(elem.x - canvasWidth / 2f) <= snapThreshold) {
                elem.x = canvasWidth / 2f
                if (!showVerticalGuide) vibrateSoft()
                showVerticalGuide = true
                snapped = true
            } else {
                showVerticalGuide = false
            }

            // Y snap
            if (abs(elem.y - canvasHeight / 2f) <= snapThreshold) {
                elem.y = canvasHeight / 2f
                if (!showHorizontalGuide) vibrateSoft()
                showHorizontalGuide = true
                snapped = true
            } else {
                showHorizontalGuide = false
            }

            if (snapped) onElementChanged?.invoke(elem)
        } else {
            // --- Group snap ---
            val bounds = getCombinedSelectedBounds()
            val centerX = bounds.centerX()
            val centerY = bounds.centerY()

            var dx = 0f
            var dy = 0f
            var snapped = false

            // X snap
            if (abs(centerX - canvasWidth / 2f) <= snapThreshold) {
                dx = canvasWidth / 2f - centerX
                if (!showVerticalGuide) vibrateSoft()
                showVerticalGuide = true
                snapped = true
            } else {
                showVerticalGuide = false
            }

            // Y snap
            if (abs(centerY - canvasHeight / 2f) <= snapThreshold) {
                dy = canvasHeight / 2f - centerY
                if (!showHorizontalGuide) vibrateSoft()
                showHorizontalGuide = true
                snapped = true
            } else {
                showHorizontalGuide = false
            }

            if (snapped && (dx != 0f || dy != 0f)) {
                selectedElements.forEach { e ->
                    e.x += dx
                    e.y += dy
                    onElementChanged?.invoke(e)
                }
            }
        }
    }

    private fun checkGroupRotationAlignment() {
        if (selectedElements.size <= 1) return

        val rotationThreshold = 5f
        val avgRotation = selectedElements.map { it.rotation }.average().toFloat()
        val normalized = (avgRotation % 360 + 360) % 360

        val snapAngles = listOf(0f, 90f, 180f, 270f, 360f)

        var snapped = false
        var snappedTarget: Float? = null

        for (target in snapAngles) {
            if (abs(normalized - target) <= rotationThreshold) {
                val delta = target - avgRotation
                selectedElements.forEach { e ->
                    e.rotation += delta
                    onElementChanged?.invoke(e)
                }
                vibrateSoft()
                snapped = true
                snappedTarget = target
                break
            }
        }

        showRotationVerticalGuide =
            snapped &&
            (snappedTarget == 0f || snappedTarget == 180f || snappedTarget == 360f)
        showRotationHorizontalGuide = snapped && (snappedTarget == 90f || snappedTarget == 270f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)

        val marginHorizontal = 20f * resources.displayMetrics.density // 20dp
        val marginVertical = 10f * resources.displayMetrics.density // 20dp

        val availableWidth = parentWidth - (marginHorizontal * 2)
        val availableHeight = parentHeight - (marginVertical * 2)

        val widthRatio = availableWidth / canvasWidth
        val heightRatio = availableHeight / canvasHeight

        scale = minOf(widthRatio, heightRatio)

        setMeasuredDimension(parentWidth, parentHeight)
    }

    /**
     * @return true if the color is “dark”, false if it’s “light”
     */
    override fun isColorDark(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        // compute luminance (0…255)
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b

        // threshold at 128 (mid‐point). <128 → dark; ≥128 → light
        return luminance < 128
    }

    private fun computeBackgroundPanBounds(e: CanvasElement): Pair<ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>> {
        val w = canvasWidth.toFloat()
        val h = canvasHeight.toFloat()
        val bmp = e.bitmap!!
        val scale = max(w / bmp.width, h / bmp.height)
        val sw = bmp.width * scale
        val sh = bmp.height * scale

        // Center can move between these so you never expose blank
        val xMin = w - sw / 2f // far right of image at right edge
        val xMax = sw / 2f // far left of image at left edge
        val yMin = h - sh / 2f
        val yMax = sh / 2f

        return (xMin..xMax) to (yMin..yMax)
    }

    internal fun drawLivePreviewStroke(canvas: Canvas) {
        if (currentStrokePath == null) return

        val tempStroke = StrokeData(
            path = currentStrokePath!!,
            color = currentBrushColor,
            thickness = currentBrushThickness,
            hardness = currentBrushHardness,
            style = currentBrushStyle,
            gradient = currentBrushGradient,
        )

        when (currentBrushStyle) {
            BrushStyle.BRUSH -> com.webscare.urducanvas.common.utils.BrushRenderUtils.drawBrushStroke(
                canvas,
                tempStroke,
                255,
            )

            BrushStyle.PEN -> com.webscare.urducanvas.common.utils.BrushRenderUtils.drawTaperedPenStroke(
                canvas,
                tempStroke,
                255,
            )

            BrushStyle.PENCIL -> {
                val paint = com.webscare.urducanvas.common.utils.BrushRenderUtils.makeStrokePaint(
                    tempStroke,
                    width,
                    height,
                ).apply {
                    pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
                    alpha = 180
                }
                canvas.drawPath(tempStroke.path!!, paint)
            }

            BrushStyle.HIGHLIGHTER -> {
                val paint = com.webscare.urducanvas.common.utils.BrushRenderUtils.makeStrokePaint(
                    tempStroke,
                    width,
                    height,
                ).apply {
                    alpha = 130
                    strokeCap = Paint.Cap.BUTT
                }
                canvas.drawPath(tempStroke.path!!, paint)
            }

            BrushStyle.MARKER -> {
                val paint = com.webscare.urducanvas.common.utils.BrushRenderUtils.makeStrokePaint(
                    tempStroke,
                    width,
                    height,
                ).apply {
                    alpha = 240
                    strokeCap = Paint.Cap.BUTT
                }
                canvas.drawPath(tempStroke.path!!, paint)
            }

            else -> {
                val paint = com.webscare.urducanvas.common.utils.BrushRenderUtils.makeStrokePaint(
                    tempStroke,
                    width,
                    height,
                )
                canvas.drawPath(tempStroke.path!!, paint)
            }
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvasRenderer.render(canvas)
    }

    internal fun drawGuides(canvas: Canvas) {
        if (showVerticalGuide) {
            canvas.drawLine(
                width / 2f,
                0f,
                width / 2f,
                height.toFloat(),
                alignmentPaint,
            )
        }

        if (showHorizontalGuide) {
            canvas.drawLine(
                0f,
                height / 2f,
                width.toFloat(),
                height / 2f,
                alignmentPaint,
            )
        }

        // Draw rotation alignment guides
        if (showRotationVerticalGuide) {
            // Draw a vertical line through the center of the canvas
            canvas.drawLine(
                width / 2f,
                0f,
                width / 2f,
                height.toFloat(),
                alignmentPaint,
            )
        }

        if (showRotationHorizontalGuide) {
            // Draw a horizontal line through the center of the canvas
            canvas.drawLine(
                0f,
                height / 2f,
                width.toFloat(),
                height / 2f,
                alignmentPaint,
            )
        }

        // ── Canvas pan CENTER-SNAP guides ─────────────────────────
        // Shown while dragging, as long as the canvas is near/at the center.
        if (showCanvasCenterVerticalSnap || showCanvasCenterHorizontalSnap) {
            // Show a subtle grid overlay on the canvas rect to make it obvious
            drawGrid(canvas)

            if (showCanvasCenterVerticalSnap) {
                // Vertical line through view center X
                canvas.drawLine(
                    width / 2f,
                    0f,
                    width / 2f,
                    height.toFloat(),
                    canvasSnapPaint,
                )
            }
            if (showCanvasCenterHorizontalSnap) {
                // Horizontal line through view center Y
                canvas.drawLine(
                    0f,
                    height / 2f,
                    width.toFloat(),
                    height / 2f,
                    canvasSnapPaint,
                )
            }
        }

        // ── GRID ─────────────────────────────────────────────────
        if (showGrid) {
            drawGrid(canvas)
        }

        // ── RULER ─────────────────────────────────────────────────
        if (showRuler) {
            drawRuler(canvas)
        }
    }

    fun colorFilterFor(filter: ImageFilter?): ColorFilter? = ColorFilterFactory.colorFilterFor(filter)

    fun getGroupTrueBounds(): FloatArray {
        if (selectedElements.isEmpty()) return floatArrayOf(0f, 0f, 0f, 0f)

        val allPoints = mutableListOf<Float>()
        selectedElements.forEach { el ->
            allPoints.addAll(el.getRotatedCorners().toList())
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (i in allPoints.indices step 2) {
            val x = allPoints[i]
            val y = allPoints[i + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }

        return floatArrayOf(minX, minY, maxX, maxY)
    }

    

    internal fun drawElementOverlays(canvas: Canvas, showOverlays: Boolean = true) {
        if (showOverlays && selectedElements.isNotEmpty()) {
            val desiredScreenStrokeWidth = 2f
            val dashLengthOnScreen = 10f
            val gapLengthOnScreen = 10f

            val localSpaceStrokeWidth = desiredScreenStrokeWidth / (scale * overallScale)
            val localDashLength = dashLengthOnScreen / (scale * overallScale)
            val localGapLength = gapLengthOnScreen / (scale * overallScale)

            reusableBoxPaint.color = Color.GRAY
            reusableBoxPaint.style = Paint.Style.STROKE
            reusableBoxPaint.pathEffect = DashPathEffect(floatArrayOf(localDashLength, localGapLength), 0f)
            reusableBoxPaint.strokeWidth = localSpaceStrokeWidth

            val rotatedPath = if (selectedElements.size > 1) {
                getGroupRotatedPath()
            } else {
                getSelectionPath()
            }
            if (rotatedPath != null) {
                canvas.drawPath(rotatedPath, reusableBoxPaint)
            }
            if (showOverlays && selectedElements.isNotEmpty() && currentMode == Mode.ROTATE) {
                val rotationValue: String
                val cx: Float
                val cy: Float

                if (selectedElements.size == 1) {
                    val element = selectedElements.first()
                    val bounds = element.getTightTextBounds()

                    val matrix = Matrix().apply {
                        postScale(
                            element.scale * if (element.isFlippedX) -1f else 1f,
                            element.scale * if (element.isFlippedY) -1f else 1f,
                        )
                        postRotate(element.rotation)
                        postTranslate(element.x, element.y)
                    }

                    val center = floatArrayOf(bounds.centerX(), bounds.centerY())
                    matrix.mapPoints(center)

                    cx = center[0]
                    cy = center[1]
                    rotationValue = "${element.rotation.roundToInt()}°"
                } else {
                    val bounds = getCombinedSelectedBounds()
                    cx = bounds.centerX()
                    cy = bounds.centerY()
                    val avgRotation = selectedElements.map { it.rotation }.average().toFloat()
                    rotationValue = "${avgRotation.roundToInt()}°"
                }

                // Measure text width/height
                val textBounds = Rect()
                rotationTextPaint.getTextBounds(rotationValue, 0, rotationValue.length, textBounds)
                val padding = 6f.dpToPx()

                val bgRect = RectF(
                    cx - textBounds.width() / 2f - padding,
                    cy - textBounds.height() / 2f - padding,
                    cx + textBounds.width() / 2f + padding,
                    cy + textBounds.height() / 2f + padding,
                )

                canvas.drawRoundRect(bgRect, 6f.dpToPx(), 6f.dpToPx(), rotationLabelPaint)

                val textY = cy - (textBounds.exactCenterY())
                canvas.drawText(rotationValue, cx, textY, rotationTextPaint)
            }
            if (selectedElements.any { !it.isLocked }) {
                val localIconDrawWidth = desiredIconScreenSizePx / (scale * overallScale)
                val localIconDrawHeight = desiredIconScreenSizePx / (scale * overallScale)

                val iconMap = mutableMapOf<String, Pair<Float, Float>>()

                if (selectedElements.size > 1) {
                    val c = getGroupRotatedBounds()
                    val left = c[0]
                    val top = c[1]
                    val right = c[2]
                    val bottom = c[3]

                    iconMap["delete"] = Pair(right, top)
                    iconMap["resize"] = Pair(right, bottom)
                    iconMap["rotate"] = Pair(left, bottom)
                } else if (selectedElements.size == 1) {
                    val element = selectedElements.first()

                    val corners = element.getRotatedCorners()
                    iconMap["delete"] = Pair(
                        corners[2],
                        corners[3],
                    )

                    iconMap["edit"] = Pair(
                        corners[0],
                        corners[1],
                    )
                    // Resize icon (bottom-left)
                    val topCenterX = (corners[0] + corners[2]) / 2f
                    val topCenterY = (corners[1] + corners[3]) / 2f

                    val offset = 80f

                    val rotateX = topCenterX
                    val rotateY = topCenterY - offset

                    iconMap["rotate"] = Pair(rotateX, rotateY)

                    // Rotate icon (bottom-right)
                    iconMap["resize"] = Pair(
                        corners[4],
                        corners[5],
                    )
                    if (element.type == ElementType.SHAPE) {
                        iconMap["transform"] = Pair(corners[6], corners[7])
                    }
                }

                iconMap.forEach { (iconName, position) ->
                    val iconBitmap = when (iconName) {
                        "delete" -> removeIcon
                        "rotate" -> rotateIcon
                        "resize" -> resizeIcon
                        "edit" -> editIcon
                        "transform" -> transformIcon
                        else -> null
                    }

                    iconBitmap?.let { bmp ->
                        var dstRect = RectF(
                            position.first - localIconDrawWidth / 2f,
                            position.second - localIconDrawHeight / 2f,
                            position.first + localIconDrawWidth / 2f,
                            position.second + localIconDrawHeight / 2f,
                        )

                        // --- ROTATE ICON HANDLING ---
                        if (iconName == "rotate" && selectedElements.isNotEmpty()) {
                            val (localTopCenter, localRotateIcon) = if (selectedElements.size == 1) {
                                // === SINGLE ELEMENT ===
                                val element = selectedElements.first()

                                if (isRotating) {
                                    val bounds = element.getTightTextBounds()

                                    val matrix = Matrix().apply {
                                        postScale(
                                            element.scale * if (element.isFlippedX) -1f else 1f,
                                            element.scale * if (element.isFlippedY) -1f else 1f,
                                        )
                                        postRotate(element.rotation)
                                        postTranslate(element.x, element.y)
                                    }

                                    val topCenter = floatArrayOf(bounds.centerX(), bounds.top)
                                    val fixedHandleLengthPx = 80f
                                    val rotateIcon = floatArrayOf(bounds.centerX(), bounds.top - (fixedHandleLengthPx / (scale * overallScale)))

                                    matrix.mapPoints(topCenter)
                                    matrix.mapPoints(rotateIcon)

                                    topCenter to rotateIcon
                                } else {
                                    // ===== SNAP CLEANLY TO SCREEN TOP =====
                                    val corners = element.getRotatedCorners()

                                    val yValues = listOf(
                                        corners[1],
                                        corners[3],
                                        corners[5],
                                        corners[7],
                                    )
                                    val topY = yValues.minOrNull() ?: 0f

                                    val xValues = listOf(
                                        corners[0],
                                        corners[2],
                                        corners[4],
                                        corners[6],
                                    )
                                    val leftX = xValues.minOrNull() ?: 0f
                                    val rightX = xValues.maxOrNull() ?: 0f
                                    val centerX = (leftX + rightX) / 2f

                                    val topCenter = floatArrayOf(centerX, topY)

                                    val fixedHandleLengthPx = 80f
                                    val rotateIcon = floatArrayOf(centerX, topY - (fixedHandleLengthPx / (scale * overallScale)))

                                    topCenter to rotateIcon
                                }
                            } else {
                                // === MULTI-SELECTION ===
                                val groupBounds = getGroupTrueBounds()

                                // Center of group bounds
                                val pivotX = (groupBounds[0] + groupBounds[2]) / 2f
                                val topY = groupBounds[1]

                                // --- Step 1: define top-center of the bounding box ---
                                val topCenter = floatArrayOf(pivotX, topY)

                                // --- Step 2: place handle directly above the box (fixed distance in screen px) ---
                                val fixedHandleLengthPx = 80f
                                val rotateIcon = floatArrayOf(pivotX, topY - (fixedHandleLengthPx / (scale * overallScale)))

                                topCenter to rotateIcon
                            }

                            rotateLinePaint.strokeWidth = 4f / (scale * overallScale)
                            rotateLinePaint.pathEffect = DashPathEffect(floatArrayOf(10f / (scale * overallScale), 10f / (scale * overallScale)), 0f)
                            val linePaint = rotateLinePaint

                            canvas.drawLine(
                                localTopCenter[0],
                                localTopCenter[1],
                                localRotateIcon[0],
                                localRotateIcon[1],
                                linePaint,
                            )

                            dstRect = RectF(
                                localRotateIcon[0] - localIconDrawWidth / 2f,
                                localRotateIcon[1] - localIconDrawHeight / 2f,
                                localRotateIcon[0] + localIconDrawWidth / 2f,
                                localRotateIcon[1] + localIconDrawHeight / 2f,
                            )
                        }

                        lastDrawnIconRect[iconName] = dstRect
                        bmp.setBounds(
                            dstRect.left.toInt(),
                            dstRect.top.toInt(),
                            dstRect.right.toInt(),
                            dstRect.bottom.toInt(),
                        )
                        bmp.draw(canvas)
                    }
                }
            }
        }
    }

    

    internal fun drawDrawElement(
        canvas: Canvas,
        element: CanvasElement,
    ) {
        element.drawStrokes?.forEach { stroke ->

            when (stroke.style) {
                BrushStyle.BRUSH -> {
                    com.webscare.urducanvas.common.utils.BrushRenderUtils.drawBrushStroke(
                        canvas,
                        stroke,
                        element.paintAlpha,
                    )
                }

                BrushStyle.PEN -> {
                    com.webscare.urducanvas.common.utils.BrushRenderUtils.drawTaperedPenStroke(
                        canvas,
                        stroke,
                        element.paintAlpha,
                    )
                }

                BrushStyle.HIGHLIGHTER -> {
                    val paint =
                        com.webscare.urducanvas.common.utils.BrushRenderUtils.makeStrokePaint(
                            stroke,
                            width,
                            height,
                        )
                    paint.alpha = element.paintAlpha
                    val offset = stroke.thickness * 0.3f
                    val path = Path(stroke.path)
                    val m = Matrix()
                    m.postTranslate(0f, offset)
                    path.transform(m)
                    canvas.drawPath(path, paint)
                }

                else -> {
                    val paint =
                        com.webscare.urducanvas.common.utils.BrushRenderUtils.makeStrokePaint(
                            stroke,
                            width,
                            height,
                        )
                    paint.alpha = element.paintAlpha
                    canvas.drawPath(stroke.path!!, paint)
                }
            }
        }

        // 🟢 Live in-progress stroke for active session
        if (element == activeSessionElement && currentStrokePath != null && currentStrokePaint != null) {
            canvas.drawPath(currentStrokePath!!, currentStrokePaint!!)
        }
    }

    

    // ─────────────────────────────────────────────────────────────────────────
    // drawFeatherMask — true rectangular edge feathering.
    //
    // Builds a 128×128 alpha mask where each pixel's alpha = product of four
    // independent edge ramps (top × bottom × left × right). Drawn scaled to
    // the image rect with DST_IN — correct rectangular feathering on all edges.
    //
    //   featherRadius (0–100) — seekbar value: how far inward the fade extends.
    //     Mapped with a square-root curve so low values (1–20) are immediately
    //     visible and the full range feels evenly distributed.
    //
    //   featherWidth (0–100)  — seekbar value: softness / transition smoothness.
    //     0  = hard linear ramp (sharp transition band).
    //     100 = very gradual smooth ease (photographic soft fade).
    //     Mapped to exponent 1.0 → 8.0 for a wide, perceptible range.
    // ─────────────────────────────────────────────────────────────────────────
    internal fun drawFeatherMask(
        canvas: Canvas,
        elementId: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        featherRadius: Float,
        featherWidth: Float,
        direction: FeatherDirection = FeatherDirection.ALL,
    ) {
        if (featherRadius <= 0f) return
        val w = right - left
        val h = bottom - top
        if (w <= 0f || h <= 0f) return

        val maskW = 128
        val maskH = 128

        val featherFp = Objects.hash(elementId, featherRadius, featherWidth, direction)
        val cached = cacheManager.featherBitmapCache[elementId]
        val maskBmp: Bitmap = if (cached != null && cached.fingerprint == featherFp && !cached.bitmap.isRecycled) {
            cached.bitmap
        } else {
            cached?.bitmap?.recycle()

            val fraction = sqrt((featherRadius / 100.0)).toFloat().coerceIn(0f, 1f)
            val bandX = (maskW / 2f) * fraction
            val bandY = (maskH / 2f) * fraction
            val exponent = 1.0 + ((100f - featherWidth) / 100.0) * 7.0

            // Which edges are active
            val doTop = direction == FeatherDirection.ALL || direction == FeatherDirection.TOP
            val doBottom = direction == FeatherDirection.ALL || direction == FeatherDirection.BOTTOM
            val doLeft = direction == FeatherDirection.ALL || direction == FeatherDirection.LEFT
            val doRight = direction == FeatherDirection.ALL || direction == FeatherDirection.RIGHT

            val pixels = IntArray(maskW * maskH)
            for (py in 0 until maskH) {
                val topRamp = if (doTop && bandY > 0f) smoothStep((py / bandY).coerceIn(0f, 1f), exponent) else 1f
                val botRamp = if (doBottom && bandY > 0f) smoothStep(((maskH - 1 - py) / bandY).coerceIn(0f, 1f), exponent) else 1f
                val vRamp = topRamp * botRamp

                for (px in 0 until maskW) {
                    val leftRamp = if (doLeft && bandX > 0f) smoothStep((px / bandX).coerceIn(0f, 1f), exponent) else 1f
                    val rightRamp = if (doRight && bandX > 0f) smoothStep(((maskW - 1 - px) / bandX).coerceIn(0f, 1f), exponent) else 1f
                    val alpha = (vRamp * leftRamp * rightRamp * 255f).toInt().coerceIn(0, 255)
                    pixels[py * maskW + px] = Color.argb(alpha, 0, 0, 0)
                }
            }

            val newBmp = Bitmap.createBitmap(maskW, maskH, Bitmap.Config.ARGB_8888)
            newBmp.setPixels(pixels, 0, maskW, 0, 0, maskW, maskH)
            FeatherCacheEntry(newBmp, featherFp).also { cacheManager.featherBitmapCache[elementId] = it }.bitmap
        }

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(maskBmp, null, RectF(left, top, right, bottom), maskPaint)
        maskPaint.xfermode = null
    }

    private fun smoothStep(t: Float, exponent: Double): Float {
        // Cubic Hermite S-curve, then raise to exponent for softness control.
        // t=0 → 0.0 (transparent edge), t=1 → 1.0 (fully opaque interior).
        val smooth = t * t * (3f - 2f * t)
        return Math.pow(smooth.toDouble(), exponent).toFloat().coerceIn(0f, 1f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: resolve the adjusted bitmap for display, scheduling async work
    // when the adjustment cache is dirty instead of blocking onDraw.
    // On the frame the adjustment is requested we fall back to the raw bitmap
    // (or the stale cache) — a one-frame visual glitch is far better than jank.
    // ─────────────────────────────────────────────────────────────────────────
    internal fun resolveAdjustedBitmapAsync(element: CanvasElement, rawBitmap: Bitmap): Bitmap {
        val hasAnyAdjustment = element.hasLight || element.hasColor || element.hasDetail || element.hasBlur
        if (!hasAnyAdjustment) {
            // No adjustments needed — raw bitmap is the final bitmap
            return rawBitmap
        }

        val cached = element.cachedAdjustedBitmap
        if (!element.isAdjustmentDirty && cached != null && !cached.isRecycled) {
            return cached // ✅ clean cache hit — zero work this frame
        }

        // Dirty or missing — schedule background processing (once per element)
        val existing = pendingAdjustmentJobs[element.id]
        if (existing == null || !existing.isActive) {
            val job = adjustmentScope.launch {
                // Prefer the element's context; fall back to the View's context
                // so adjustments (especially RenderScript blur) never silently skip.
                val ctx = element.context ?: context ?: return@launch
                val result = ImageAdjustmentHelper.applyAllAdjustments(
                    ctx,
                    rawBitmap,
                    element,
                )
                withContext(Dispatchers.Main) {
                    // Do NOT recycle the old cachedAdjustedBitmap immediately — the export
                    // pipeline runs on a background thread and may be mid-draw with a reference
                    // to the old bitmap. Recycling here causes "Canvas: trying to use a recycled
                    // bitmap" crashes. Let the old bitmap become unreachable and be GC'd instead.
                    element.cachedAdjustedBitmap = result
                    element.isAdjustmentDirty = false
                    // Invalidate display cache so next frame resamples from the fresh adjusted bitmap
                    cacheManager.removeDisplay(element.id)
                    cacheManager.removeDisplay(element.id + "_bg")
                    pendingAdjustmentJobs.remove(element.id)
                    invalidate()
                }
            }
            pendingAdjustmentJobs[element.id] = job
        }

        // Return stale cache or raw bitmap while the job runs — no UI freeze
        return if (cached != null && !cached.isRecycled) cached else rawBitmap
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: return a display-resolution proxy bitmap, building it only when
    // the source or target size changes.  The source (full-res) bitmap is never
    // modified — it stays intact for export at full quality.
    // ─────────────────────────────────────────────────────────────────────────
    internal fun getOrBuildDisplayBitmap(
        cacheKey: String,
        source: Bitmap,
        targetW: Int,
        targetH: Int,
    ): Bitmap {
        // If source IS already at or below display size, use it directly (no copy needed)
        if (source.width <= targetW && source.height <= targetH) return source

        val cached = cacheManager.getDisplay(cacheKey)
        if (cached != null && !cached.bitmap.isRecycled) {
            // During active gestures, reuse the cached proxy even if the size has changed,
            // to prevent heavy bitmap resizing on every frame.
            if (isGestureActive()) {
                return cached.bitmap
            }

            // Discretize target sizes to nearest multiple of 128px to reduce cache misses
            // from minor layout/scale updates when not in active gesture.
            val discreteW = (((targetW + 127) / 128) * 128).coerceAtMost(source.width)
            val discreteH = (((targetH + 127) / 128) * 128).coerceAtMost(source.height)

            if (cached.srcWidth == source.width &&
                cached.srcHeight == source.height &&
                cached.dstWidth == discreteW &&
                cached.dstHeight == discreteH
            ) {
                return cached.bitmap // ✅ cache hit
            }
        }

        // For the first frame of gesture or when no cache exists, use rounded/discretized size.
        val discreteW = (((targetW + 127) / 128) * 128).coerceAtMost(source.width).coerceAtLeast(1)
        val discreteH = (((targetH + 127) / 128) * 128).coerceAtMost(source.height).coerceAtLeast(1)

        // Build a high-quality downscale using FILTER_BITMAP_FLAG (bilinear)
        val scaled = Bitmap.createScaledBitmap(source, discreteW, discreteH, true)
        // Do NOT recycle the old cached bitmap immediately — the export pipeline runs on a
        // background thread and may hold a reference to it mid-draw. Let it become unreachable
        // and be GC'd rather than risk a "Canvas: trying to use a recycled bitmap" crash.
        cacheManager.putDisplay(cacheKey, DisplayCacheEntry(
            bitmap = scaled,
            srcWidth = source.width,
            srcHeight = source.height,
            dstWidth = discreteW,
            dstHeight = discreteH,
        ))
        return scaled
    }

    internal fun createGradientShader(
        gradientItem: GradientItem,
        width: Float,
        height: Float,
        translateX: Float = 0f,
        translateY: Float = 0f,
    ): Shader {
        val colors = gradientItem.colors.toIntArray()
        val positions = gradientItem.positions.toFloatArray()

        // relative center in element/canvas space
        val cxRel = width * gradientItem.centerX
        val cyRel = height * gradientItem.centerY

        // build the core shader centered at (0,0)
        var rawShader: Shader? = null
        // any rotation matrix (for sweep) that we'll need to merge later
        var localMatrix: Matrix? = null

        when (gradientItem.type) {
            GradientType.LINEAR -> {
                val theta = Math.toRadians(gradientItem.angle.toDouble())
                val halfLen = (hypot(width, height) * gradientItem.scale / 2f)
                val dx = (cos(theta) * halfLen).toFloat()
                val dy = (sin(theta) * halfLen).toFloat()

                rawShader = LinearGradient(
                    -dx,
                    -dy,
                    dx,
                    dy,
                    colors,
                    positions,
                    Shader.TileMode.CLAMP,
                )
            }

            GradientType.RADIAL -> {
                val radius =
                    min(width, height) / 2f * gradientItem.radialRadiusFactor * gradientItem.scale

                rawShader = RadialGradient(
                    0f,
                    0f,
                    radius,
                    colors,
                    positions,
                    Shader.TileMode.CLAMP,
                )
            }

            GradientType.SWEEP -> {
                rawShader = SweepGradient(0f, 0f, colors, positions)
                // pre-rotate the sweep start angle around the origin
                localMatrix = Matrix().apply {
                    postRotate(gradientItem.sweepStartAngle)
                }
            }
        }

        // now we need to translate the shader from (0,0) up to (cxRel, cyRel),
        // plus any extra translateX/translateY
        val finalMatrix = Matrix().apply {
            // if we had a sweep-rotation, start with that
            localMatrix?.let { set(it) }

            // then move into place
            postTranslate(cxRel + translateX, cyRel + translateY)
        }

        rawShader?.setLocalMatrix(finalMatrix)
        return rawShader!!
    }

    

    internal fun drawWithBlend(element: CanvasElement): Xfermode? = when (element.blendType) {
        BlendType.SRC -> PorterDuffXfermode(
            PorterDuff.Mode.SRC,
        )

        BlendType.NORMAL -> null
        BlendType.DARKEN -> PorterDuffXfermode(
            PorterDuff.Mode.DARKEN,
        )

        BlendType.LIGHTEN -> PorterDuffXfermode(
            PorterDuff.Mode.LIGHTEN,
        )

        BlendType.MULTIPLY -> PorterDuffXfermode(
            PorterDuff.Mode.MULTIPLY,
        )

        BlendType.SCREEN -> PorterDuffXfermode(
            PorterDuff.Mode.SCREEN,
        )

        else -> {}
    } as Xfermode?

    private fun isRTL(text: String): Boolean = text.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.ARABIC }

    internal fun justifyText(
        canvas: Canvas,
        text: String,
        yOffset: Float,
        element: CanvasElement,
    ) {
        if (element.paintAlpha == 0) return

        val isRTL = isRTL(text)
        val words = text.split(" ")
        if (words.size <= 1) {
            val x = -element.getLocalContentWidth() / 2f
            element.paint.alpha = element.paintAlpha
            canvas.drawText(text, x, yOffset, element.paint)
            return
        }
        val fillPaint = TextPaint(element.paint).apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = element.paintColor
            alpha = element.paintAlpha
            element.fillGradient?.let {
                val w = element.getLocalContentWidth()
                shader = createGradientShader(it, w, textSize)
            }
        }
        val strokePaint = if (element.hasStroke && element.strokeWidth > 0f) {
            TextPaint(fillPaint).apply {
                style = Paint.Style.STROKE
                strokeWidth = element.strokeWidth
                element.strokeGradient?.let {
                    val w = element.getLocalContentWidth()
                    shader = createGradientShader(it, w, textSize)
                } ?: run { color = element.strokeColor }
            }
        } else {
            null
        }

        val wordWidths = words.map { fillPaint.measureText(it) }
        val textWidth = wordWidths.sum()
        val totalWidth = element.getLocalContentWidth()
        val space = (totalWidth - textWidth) / (words.size - 1)

        // ✅ START POSITION
        var xOffset = if (isRTL) totalWidth / 2f else -totalWidth / 2f

        words.forEachIndexed { index, word ->

            val w = wordWidths[index]

            // ✅ Move before drawing
            if (isRTL) xOffset -= w

            // ---- Stroke ----
            strokePaint?.let {
                val old = it.alpha
                it.alpha = element.paintAlpha
                canvas.drawText(word, xOffset, yOffset, it)
                it.alpha = old
            }

            // ---- Fill ----
            canvas.drawText(word, xOffset, yOffset, fillPaint)

            // ✅ Move after drawing
            if (!isRTL) xOffset += w

            // ✅ Add justify spacing
            xOffset += if (index < words.size - 1) (if (isRTL) -space else space) else 0f
        }
    }

    // Helper functions for pinch distance and angle
    

    

    fun CanvasElement.containsPoint(
        px: Float,
        py: Float,
    ): Boolean {
        val bounds = getTightTextBounds()
        val corners = floatArrayOf(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.top,
            bounds.right,
            bounds.bottom,
            bounds.left,
            bounds.bottom,
        )
        val m = Matrix().apply {
            postScale(
                scale * if (isFlippedX) -1f else 1f,
                scale * if (isFlippedY) -1f else 1f,
            )
            postRotate(rotation)
            postTranslate(x, y)
        }
        m.mapPoints(corners)
        return pointInPolygon(px, py, corners)
    }

    private fun pointInPolygon(px: Float, py: Float, pts: FloatArray): Boolean {
        var result = false
        var j = pts.size - 2
        for (i in pts.indices step 2) {
            val xi = pts[i]
            val yi = pts[i + 1]
            val xj = pts[j]
            val yj = pts[j + 1]
            val intersect =
                ((yi > py) != (yj > py)) && (px < (xj - xi) * (py - yi) / (yj - yi) + xi)
            if (intersect) result = !result
            j = i
        }
        return result
    }

    

    internal fun stepZoomOverall() {
        // 50% → 100% → 200% → 300% → 50% cycle
        val next = when {
            overallScale < 0.9f -> 1.0f // 50%  → 100%
            overallScale < 1.5f -> 2.0f // 100% → 200%
            overallScale < 2.5f -> 3.0f // 200% → 300%
            else -> 0.5f // 300% → 50%
        }
        animateOverallZoom(next)
        onZoomChanged?.invoke(next) // popup label update karo
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

    @SuppressLint("ClickableViewAccessibility")
    

    /**
     * Called during CANVAS_PAN single-finger drag.
     * Snaps overallOffsetX/Y to zero (canvas centered) when the canvas center
     * comes within [canvasSnapThresholdPx] of the view center, and shows the
     * dashed cyan guide lines.  Vibrates once when snapping occurs.
     */
    internal fun checkCanvasPanSnap() {
        val prevSnapV = showCanvasCenterVerticalSnap
        val prevSnapH = showCanvasCenterHorizontalSnap

        // At overallOffsetX == 0 the canvas is horizontally centered.
        // At overallOffsetY == 0 the canvas is vertically centered.
        val snapX = abs(overallOffsetX) <= canvasSnapThresholdPx
        val snapY = abs(overallOffsetY) <= canvasSnapThresholdPx

        if (snapX) {
            if (!prevSnapV) vibrateSoft()
            overallOffsetX = 0f
        }
        if (snapY) {
            if (!prevSnapH) vibrateSoft()
            overallOffsetY = 0f
        }

        showCanvasCenterVerticalSnap = snapX
        showCanvasCenterHorizontalSnap = snapY
    }

    internal fun clampOverallPan() {
        val screenW = width.toFloat()
        val screenH = height.toFloat()

        // Scaled canvas size
        val scaledCanvasW = canvasWidth * scale * overallScale
        val scaledCanvasH = canvasHeight * scale * overallScale

        // 👇 25% margin (THIS IS KEY)
        val marginX = screenW * 0.25f
        val marginY = screenH * 0.25f

        // Center based movement limits
        val maxOffsetX = (scaledCanvasW / 2f) - marginX
        val maxOffsetY = (scaledCanvasH / 2f) - marginY

        // If canvas smaller than screen → center only
        val finalMaxOffsetX = if (scaledCanvasW < screenW) marginX else maxOffsetX
        val finalMaxOffsetY = if (scaledCanvasH < screenH) marginY else maxOffsetY

        overallOffsetX = overallOffsetX.coerceIn(-finalMaxOffsetX, finalMaxOffsetX)
        overallOffsetY = overallOffsetY.coerceIn(-finalMaxOffsetY, finalMaxOffsetY)
    }

    internal fun canvasToView(cx: Float, cy: Float): Pair<Float, Float> {
        val scaledWidth = canvasWidth * scale
        val scaledHeight = canvasHeight * scale
        val ox = (width - scaledWidth) / 2f // offsetX
        val oy = (height - scaledHeight) / 2f // offsetY
        val pivotX = width / 2f
        val pivotY = height / 2f

        // Step 1+2: canvas local → after inner scale+translate
        val vx0 = cx * scale + ox
        val vy0 = cy * scale + oy

        // Step 3: apply overallScale around pivot, then overallOffset
        val vx = (vx0 - pivotX) * overallScale + pivotX + overallOffsetX
        val vy = (vy0 - pivotY) * overallScale + pivotY + overallOffsetY

        return vx to vy
    }

    private fun drawGrid(canvas: Canvas) {
        val gridSpacing = 50f

        // canvasToView() use karo — exact same transform as onDraw
        val (left, top) = canvasToView(0f, 0f)
        val (right, bottom) = canvasToView(canvasWidth.toFloat(), canvasHeight.toFloat())

        val stepPx = gridSpacing * scale * overallScale
        if (stepPx < 2f) return

        gridPaint.strokeWidth = (1f / overallScale).coerceIn(0.8f, 1.5f)

        var x = left
        while (x <= right + 0.5f) {
            canvas.drawLine(x, top, x, bottom, gridPaint)
            x += stepPx
        }

        var y = top
        while (y <= bottom + 0.5f) {
            canvas.drawLine(left, y, right, y, gridPaint)
            y += stepPx
        }
    }

    private fun getCanvasBgColor(): Int {
        val bgElement = canvasElements.firstOrNull { it.type == ElementType.BACKGROUND }
        return bgElement?.backgroundColor ?: Color.WHITE
    }

    private fun isCanvasBgDark(): Boolean {
        val color = getCanvasBgColor()
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val luminance = 0.299f * r + 0.587f * g + 0.114f * b
        return luminance < 128f
    }

    internal fun drawCanvasShadow(canvas: Canvas) {
        val rect = RectF(
            0f,
            0f,
            canvasWidth.toFloat(),
            canvasHeight.toFloat(),
        )

        val spread = 40f // increase spread for softness

        val shadowRect = RectF(
            rect.left - spread,
            rect.top - spread,
            rect.right + spread,
            rect.bottom + spread,
        )

        val isDark = isCanvasBgDark()
        canvasShadowPaint.color = if (isDark) Color.WHITE else Color.BLACK
        canvasShadowPaint.alpha = if (isDark) 50 else 30

        canvas.drawRoundRect(
            shadowRect,
            30f,
            30f,
            canvasShadowPaint,
        )
    }

    private fun drawRuler(canvas: Canvas) {
        val rulerThicknessPx = 16f.dpToPx()
        val majorTickLen = rulerThicknessPx * 0.6f
        val minorTickLen = rulerThicknessPx * 0.3f

        // Canvas boundaries in view space
        val (canvasLeft, canvasTop) = canvasToView(0f, 0f)
        val (canvasRight, canvasBottom) = canvasToView(
            canvasWidth.toFloat(),
            canvasHeight.toFloat(),
        )

        val rawSpacing = canvasWidth / 8f
        // Round to nearest "nice" number: 50, 100, 200, 250, 500, 1000 etc
        val tickSpacing = niceNumber(rawSpacing)

        // View pixels per one canvas unit tick
        val stepViewPx = tickSpacing * scale * overallScale

        if (stepViewPx < 4f) return

        // ── TOP RULER background ─────────────────────────────────
        canvas.drawRect(
            canvasLeft,
            canvasTop,
            canvasRight,
            canvasTop + rulerThicknessPx,
            rulerBgPaint,
        )
        canvas.drawLine(
            canvasLeft,
            canvasTop + rulerThicknessPx,
            canvasRight,
            canvasTop + rulerThicknessPx,
            rulerPaint,
        )

        // ── LEFT RULER background ────────────────────────────────
        canvas.drawRect(
            canvasLeft,
            canvasTop,
            canvasLeft + rulerThicknessPx,
            canvasBottom,
            rulerBgPaint,
        )
        canvas.drawLine(
            canvasLeft + rulerThicknessPx,
            canvasTop,
            canvasLeft + rulerThicknessPx,
            canvasBottom,
            rulerPaint,
        )

        // Corner square
        canvas.drawRect(
            canvasLeft,
            canvasTop,
            canvasLeft + rulerThicknessPx,
            canvasTop + rulerThicknessPx,
            rulerBgPaint,
        )

        // ── TOP RULER ticks + labels (X axis) ───────────────────
        var tickIndex = 0
        var x = canvasLeft
        while (x <= canvasRight + 0.5f) {
            val isMajor = tickIndex % 5 == 0
            val tickLen = if (isMajor) majorTickLen else minorTickLen
            val tickTop = canvasTop + rulerThicknessPx - tickLen

            canvas.drawLine(x, tickTop, x, canvasTop + rulerThicknessPx, rulerPaint)

            if (isMajor) {
                canvas.drawText(
                    "${(tickIndex * tickSpacing).toInt()}",
                    x,
                    canvasTop + rulerThicknessPx - majorTickLen - 2f,
                    rulerTextPaint,
                )
            }
            x += stepViewPx
            tickIndex++
        }

        // ── LEFT RULER ticks + labels (Y axis) ──────────────────
        tickIndex = 0
        var y = canvasTop
        while (y <= canvasBottom + 0.5f) {
            val isMajor = tickIndex % 5 == 0
            val tickLen = if (isMajor) majorTickLen else minorTickLen
            val tickLeft = canvasLeft + rulerThicknessPx - tickLen

            canvas.drawLine(tickLeft, y, canvasLeft + rulerThicknessPx, y, rulerPaint)

            if (isMajor && tickIndex > 0) {
                canvas.withSave {
                    // Rotate text -90° so it reads bottom-to-top along left ruler
                    val labelX = canvasLeft + rulerThicknessPx - majorTickLen - 2f
                    rotate(-90f, labelX, y)
                    canvas.drawText(
                        "${(tickIndex * tickSpacing).toInt()}",
                        labelX,
                        y + rulerTextPaint.textSize / 3f,
                        rulerTextPaint,
                    )
                }
            }
            y += stepViewPx
            tickIndex++
        }
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

        // Snap to 50%, 100%, 150%, 200%, 250%, 300%
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

    /**
     * Returns true if the canvas is currently centered in both axes
     * (overallOffset within snap threshold on both axes).
     * Callers can use this to show a "Centered" badge in the toolbar.
     */
    fun isCanvasCentered(): Boolean = abs(overallOffsetX) <= canvasSnapThresholdPx && abs(overallOffsetY) <= canvasSnapThresholdPx

    private fun niceNumber(raw: Float): Float {
        val candidates = listOf(10f, 20f, 25f, 50f, 100f, 200f, 250f, 500f, 1000f, 2000f)
        return candidates.minByOrNull { abs(it - raw) } ?: 100f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Cancel all pending async adjustment jobs to prevent leaks
        adjustmentScope.coroutineContext[Job]?.cancel()
        pendingAdjustmentJobs.values.forEach { it.cancel() }
        pendingAdjustmentJobs.clear()
        // Release all display-proxy bitmaps
        cacheManager.clearAll()
    }
}
