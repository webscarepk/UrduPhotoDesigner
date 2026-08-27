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
import android.graphics.drawable.PictureDrawable
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
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.ImageProcessor.trimTransparentEdges
import com.webscare.urducanvas.common.utils.ShapeRenderUtils
import com.webscare.urducanvas.common.utils.Utils.vibrateSoft
import com.webscare.urducanvas.common.utils.isDarkModeEnabled
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
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
    var canvasWidth: Int = 300,
    var canvasHeight: Int = 300,
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
    var onProcessingStateChanged: ((Boolean) -> Unit)? = null,
    var onExitTableEditMode: (() -> Unit)? = null,
    var onTableCellSelected: ((row: Int, col: Int) -> Unit)? = null,
    var onTableCellToggleSelected: ((row: Int, col: Int) -> Unit)? = null,
    var onTableMultiSelectChanged: ((Boolean) -> Unit)? = null
) : View(context, attrs) {

    var isTableEditMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isTableMultiSelectMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isTableResizeMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var draggedColDividerIndex: Int = -1
    private var draggedRowDividerIndex: Int = -1

    private val gson: Gson by lazy {
        EntryPointAccessors.fromApplication(
            context, com.webscare.urducanvas.di.GsonEntryPoint::class.java
        ).gson()
    }
    private var gestureDetector: GestureDetector
    private var isRotating = false
    private var colorPickerBitmap: Bitmap? = null
    private var isColorPickerMode = false

    private var activeSessionElement: CanvasElement? = null

    private var currentStrokePath: Path? = null
    private var currentStrokePaint: Paint? = null
    private var currentStrokePoints = mutableListOf<Pair<Float, Float>>()
    private var isDrawing = false
    private var currentBrushColor: Int = Color.BLACK
    private var currentBrushThickness: Float = 20f
    private var currentBrushHardness: Float = 1f
    private var currentBrushStyle: BrushStyle = BrushStyle.PEN
    private var currentBrushGradient: GradientItem? = null

    private var pickerX = 0f
    private var pickerY = 0f
    private var isDraggingPicker = false
    private val desiredPickerIconSizePx = 64f
    private val desiredIconSizeDp = 20f
    private val desiredIconScreenSizePx: Float
        get() = desiredIconSizeDp * resources.displayMetrics.density

    private var iconTouched: String? = null
    private var allowFreeDrag: Boolean = false
    private val checkerSize = 20
    private val light = "#F5F5F5".toColorInt()
    private val dark = "#DDDDDD".toColorInt()

    private var activeGroupId: String? = null
    private var inSelectionMode = false
    private var touchedDownElement: CanvasElement? = null
    private var isDragCandidate = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val initialElementSizes = mutableMapOf<String, Pair<Float, Float>>()
    private val initialTextSizes = mutableMapOf<String, Float>()
    private val initialUnwrappedWidths = mutableMapOf<String, Float>()
    private val initialMinWordWidths = mutableMapOf<String, Float>()
    private var suppressZoomCallback = false

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
            p
        )

        // top-right & bottom-left = dark
        p.color = dark
        c.drawRect(checkerSize.toFloat(), 0f, (checkerSize * 2).toFloat(), checkerSize.toFloat(), p)
        c.drawRect(0f, checkerSize.toFloat(), checkerSize.toFloat(), (checkerSize * 2).toFloat(), p)

        BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    private val checkerPaint = Paint().apply { shader = checkerShader }

    private val rotateLinePaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val canvasElements = CopyOnWriteArrayList<CanvasElement>()
    private lateinit var backgroundElement: CanvasElement
    private var isExportRendering = false

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var currentMode: Mode = Mode.NONE

    private var initialElementRotations = mutableMapOf<String, Float>()

    private var initialElementPositionsRelativeToGroupPivot =
        mutableMapOf<String, Pair<Float, Float>>()
    private var initialAngle = 0f
    private var initialGroupPivotX = 0f
    private var initialGroupPivotY = 0f

    private var initialPinchDistance = 0f
    private var initialPinchAngle = 0f
    private var initialScale = 1f
    private var initialRotation = 0f

    // Finger midpoint (screen coords) at the moment the pinch started.
    // Used to zoom around the actual finger position, not the screen centre.
    private var pinchFocusX = 0f
    private var pinchFocusY = 0f

    // Canvas offset captured at pinch-start — combined with pinchFocusX/Y for
    // pivot-correct zoom math so the canvas never jumps when fingers lift.
    private var initialOffsetXAtPinch = 0f
    private var initialOffsetYAtPinch = 0f

    private val resizeLastSignX = mutableMapOf<String, Float>()
    private val resizeLastSignY = mutableMapOf<String, Float>()

    // Stores each element's scale at the START of a RESIZE handle gesture.
    // Used for absolute scale math (same approach as MULTI_TOUCH pinch) so both
    // resize mechanisms produce identical zoom levels for the same finger movement.
    private val resizeInitialScales = mutableMapOf<String, Float>()
    private var resizeStartDist = 0f   // distance from finger to pivot at gesture start

    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    // Overall canvas zoom & pan
    private var overallScale = 1f
    private var overallOffsetX = 0f
    private var overallOffsetY = 0f

    private var initialOverallScale = 1f

    private val lastDrawnIconRect = mutableMapOf<String, RectF>()

    // Per-element cache for the expensive pre-blurred shadow bitmap.
    // Key = elementId. Invalidated whenever shadow params change (detected via fingerprint).
    private data class ShadowCacheEntry(
        val bitmap: Bitmap,
        val fingerprint: Int,   // hash of all shadow params that affect the blur output
        val scaleX: Float,
        val scaleY: Float,
        val offsetX: Float,     // scaled offset[0] from extractAlpha
        val offsetY: Float      // scaled offset[1] from extractAlpha
    )

    private val shadowBitmapCache = mutableMapOf<String, ShadowCacheEntry>()

    // Per-element cache for the stroke alpha bitmap (36 drawBitmap calls/frame otherwise).
    private data class StrokeCacheEntry(val bitmap: Bitmap, val fingerprint: Int)

    private val strokeBitmapCache = mutableMapOf<String, StrokeCacheEntry>()

    // ── Display-resolution bitmap cache ──────────────────────────────────────
    // Full-res bitmaps (e.g. 12MP) are stored in element.bitmap for export quality.
    // For display we downscale to the actual on-screen pixel size so the GPU only
    // samples what it actually needs to draw.  Key = elementId.
    private data class DisplayCacheEntry(
        val bitmap: Bitmap, val srcWidth: Int,   // width of the source bitmap this was scaled from
        val srcHeight: Int, val dstWidth: Int,   // on-screen pixel size when this was built
        val dstHeight: Int
    )

    private val displayBitmapCache = mutableMapOf<String, DisplayCacheEntry>()

    // Per-element cache for raw rasterized SVG bitmaps
    private val rawSvgBitmapCache = mutableMapOf<String, Bitmap>()

    // Per-element cache for pre-rendered feather masks
    private data class FeatherCacheEntry(val bitmap: Bitmap, val fingerprint: Int)

    private val featherBitmapCache = mutableMapOf<String, FeatherCacheEntry>()

    private fun isGestureActive(): Boolean {
        return currentMode == Mode.DRAG || currentMode == Mode.ROTATE || currentMode == Mode.RESIZE || currentMode == Mode.TRANSFORM || currentMode == Mode.MULTI_TOUCH || currentMode == Mode.CANVAS_PAN
    }

    // ── Background coroutine scope for async image-adjustment processing ─────
    // applyAllAdjustments can take 100-500 ms on a full-res bitmap — never block onDraw.
    private val adjustmentScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Track in-flight jobs so we don't double-schedule for the same element.
    private val pendingAdjustmentJobs = mutableMapOf<String, Job>()

    var isProcessingAdjustments: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                postInvalidateOnAnimation()
            }
        }

    var processingElementId: String? = null
        set(value) {
            if (field != value) {
                field = value
                postInvalidateOnAnimation()
            }
        }

    private fun isElementProcessing(elementId: String): Boolean {
        return pendingAdjustmentJobs.containsKey(elementId) ||
            (isProcessingAdjustments && (processingElementId == null || processingElementId == elementId))
    }

    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }

    // ── Reusable objects to eliminate per-frame allocations in onDraw ────────
    private val reusableRectF = RectF()
    private val reusableDrawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val reusableOpacityPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reusableBgPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val reusableStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isFilterBitmap = true
    }
    private val reusableBoxPaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
    }

    init {
        gestureDetector = GestureDetector(context, GestureListener())
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
        alpha = 30   // softness control
        maskFilter = BlurMaskFilter(100f, BlurMaskFilter.Blur.NORMAL)
    }

    private val drawingModeOverlayPaint = Paint().apply {
        color = Color.argb(120, 255, 255, 255)
        style = Paint.Style.FILL
    }

    var isSmartSnappingEnabled: Boolean = true
    data class SnapLine(val position: Float, val isHorizontal: Boolean)

    private val activeSnapLines = mutableListOf<SnapLine>()

    private var showVerticalGuide = false
    private var showHorizontalGuide = false
    private var showRotationVerticalGuide = false
    private var showRotationHorizontalGuide = false

    // ── Canvas pan center-snap guides ────────────────────────────
    private var showCanvasCenterVerticalSnap = false
    private var showCanvasCenterHorizontalSnap = false
    private val canvasSnapThresholdPx = 8f   // screen-px proximity to trigger snap

    // Paint for the canvas-pan center snap lines (solid cyan, bold)
    private val canvasSnapPaint = Paint().apply {
        color = Color.parseColor("#00BCD4")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(14f, 6f), 0f)
    }

    // ── Grid overlay ─────────────────────────────────────────────
    private var showGrid = false
    private val gridPaint = Paint().apply {
        color = Color.argb(60, 0, 0, 0)
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = false
    }

    // ── Ruler overlay ─────────────────────────────────────────────
    private var rulerState: com.webscare.urducanvas.common.canvas.enums.RulerState =
        com.webscare.urducanvas.common.canvas.enums.RulerState.OFF
    var topRulerY: Float = 0f
    var bottomRulerY: Float = 0f
    var leftRulerX: Float = 0f
    var rightRulerX: Float = 0f

    private enum class DraggingRuler { NONE, TOP, BOTTOM, LEFT, RIGHT }

    private var activeDraggingRuler = DraggingRuler.NONE

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
    private val rulerIndicatorPaint = Paint().apply {
        color = Color.parseColor("#E91E63")
        strokeWidth = 1.5f.dpToPx()
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val rulerBadgeBgPaint = Paint().apply {
        color = Color.parseColor("#00BCD4")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val rulerBadgeTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 9f.dpToPx()
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    var localFonts: List<com.webscare.urducanvas.data.model.FontEntity> = emptyList()

    // ── Pan mode (single-finger pan without selecting elements) ───
    private var isPanMode = false
    private var isCanvasPanLocked = false
    private val removeIcon: Drawable by lazy {
        AppCompatResources.getDrawable(context, R.drawable.ic_cross)!!
    }
    private val resizeIcon: Drawable by lazy {
        AppCompatResources.getDrawable(context, R.drawable.ic_resize)!!
    }
    private val rotateIcon: Drawable by lazy {
        AppCompatResources.getDrawable(context, R.drawable.ic_rotate)!!
    }
    private val editIcon: Drawable by lazy {
        AppCompatResources.getDrawable(context, R.drawable.ic_edit_text)!!
    }

    private val transformIcon: Drawable by lazy {
        AppCompatResources.getDrawable(context, R.drawable.ic_transform)!!
    }

    private val tableColResizeDrawable: Drawable? by lazy {
        AppCompatResources.getDrawable(context, R.drawable.ic_table_col_resize)
    }

    private val tableRowResizeDrawable: Drawable? by lazy {
        AppCompatResources.getDrawable(context, R.drawable.ic_table_row_resize)
    }

    private var selectedElements: CopyOnWriteArrayList<CanvasElement> = CopyOnWriteArrayList()
    private var lastTouchedElement: CanvasElement? = null

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
        gradient: GradientItem? = null
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
                1f   // never upscale — screen size is enough
            ).coerceAtLeast(0.1f)
            val bmpW = (canvasWidth * displayScale).toInt().coerceAtLeast(1)
            val bmpH = (canvasHeight * displayScale).toInt().coerceAtLeast(1)
            val bmp = createBitmap(bmpW, bmpH)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            renderCanvasTo(canvas, displayScale)

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
        val target =
            if (focusChildId != null) canvasElements.firstOrNull { it.id == focusChildId && it.groupId == groupId }
            else canvasElements.filter { it.groupId == groupId }.maxByOrNull { it.zIndex }
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

    /** Returns the groupId currently being edited, or null. */
    fun getActiveGroupId(): String? = activeGroupId

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

    private fun getElementAxisAlignedBounds(e: CanvasElement): RectF {
        val corners = e.getRotatedCorners()
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in corners.indices step 2) {
            val cx = corners[i]
            val cy = corners[i + 1]
            if (cx < minX) minX = cx
            if (cy < minY) minY = cy
            if (cx > maxX) maxX = cx
            if (cy > maxY) maxY = cy
        }
        return RectF(minX, minY, maxX, maxY)
    }

    /**
     * Call this for your horizontal buttons:
     *  – if one element: snaps to canvas LEFT/CENTER/RIGHT
     *  – if many:
     *     • CANVAS: treat group as block and snap its LEFT/CENTER/RIGHT to the art board
     *     • SELECTION: snap each element’s own LEFT/CENTER/RIGHT to the first element
     */
    fun alignHorizontal(
        align: HAlign, mode: MultiAlignMode = MultiAlignMode.CANVAS
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

                // Normal element path using rotation-aware bounds
                val bounds = getElementAxisAlignedBounds(elem)
                val boundsW = bounds.width()
                if (!boundsW.isFinite() || !canvasWidth.toFloat()
                        .isFinite() || canvasWidth <= 0f
                ) return

                val targetLeft = when (align) {
                    HAlign.LEFT -> 0f
                    HAlign.CENTER -> (canvasWidth - boundsW) / 2f
                    HAlign.RIGHT -> canvasWidth - boundsW
                }

                val oversized = boundsW > canvasWidth
                val dx = when {
                    oversized -> (canvasWidth / 2f) - bounds.centerX()
                    else -> {
                        val coercedLeft = targetLeft.coerceIn(0f, canvasWidth - boundsW)
                        coercedLeft - bounds.left
                    }
                }

                elem.x += dx
                onElementChanged?.invoke(elem)
                invalidate()
                return
            }

            mode == MultiAlignMode.CANVAS -> {
                val edges = selectedElements.map { e ->
                    val bounds = getElementAxisAlignedBounds(e)
                    bounds.left to bounds.right
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
                val firstBounds = getElementAxisAlignedBounds(first)
                val firstLeft = firstBounds.left
                val firstCenter = firstBounds.centerX()
                val firstRight = firstBounds.right

                selectedElements.drop(1).forEach { e ->
                    val bounds = getElementAxisAlignedBounds(e)
                    val dx = when (align) {
                        HAlign.LEFT -> firstLeft - bounds.left
                        HAlign.CENTER -> firstCenter - bounds.centerX()
                        HAlign.RIGHT -> firstRight - bounds.right
                    }
                    e.x += dx
                    onElementChanged?.invoke(e)
                }
            }
        }
        invalidate()
    }

    fun alignVertical(
        align: VAlign, mode: MultiAlignMode = MultiAlignMode.CANVAS
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

                // Normal element path using rotation-aware bounds
                val bounds = getElementAxisAlignedBounds(elem)
                val boundsH = bounds.height()
                if (!boundsH.isFinite() || canvasHeight <= 0f) return

                val targetTop = when (align) {
                    VAlign.TOP -> 0f
                    VAlign.MIDDLE -> (canvasHeight - boundsH) / 2f
                    VAlign.BOTTOM -> canvasHeight - boundsH
                }

                val oversized = boundsH > canvasHeight
                val dy = when {
                    oversized -> (canvasHeight / 2f) - bounds.centerY()
                    else -> {
                        val coercedTop = targetTop.coerceIn(0f, canvasHeight - boundsH)
                        coercedTop - bounds.top
                    }
                }

                elem.y += dy
                onElementChanged?.invoke(elem)
                invalidate()
                return
            }

            mode == MultiAlignMode.CANVAS -> {
                val edges = selectedElements.map { e ->
                    val bounds = getElementAxisAlignedBounds(e)
                    bounds.top to bounds.bottom
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
                val firstBounds = getElementAxisAlignedBounds(first)
                val firstTop = firstBounds.top
                val firstCenter = firstBounds.centerY()
                val firstBottom = firstBounds.bottom

                selectedElements.drop(1).forEach { e ->
                    val bounds = getElementAxisAlignedBounds(e)
                    val dy = when (align) {
                        VAlign.TOP -> firstTop - bounds.top
                        VAlign.MIDDLE -> firstCenter - bounds.centerY()
                        VAlign.BOTTOM -> firstBottom - bounds.bottom
                    }
                    e.y += dy
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
        val selectedGroupIds =
            elements.filter { it.isSelected && it.type == ElementType.GROUP }.map { it.id }.toSet()
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
                else -> { /* not selected */
                }
            }
        }
        return result
    }

    /**
     * Syncs the canvas elements with a new list from the ViewModel.
     * Updates the internal `selectedElements` list based on the `isSelected` flag of incoming elements.
     */
    fun syncElements(newElements: List<CanvasElement>) {
        val existingMap = canvasElements.associateBy { it.id }
        val oldSize = canvasElements.size

        // Preserve cached adjusted bitmap & clean dirty flag across syncs if image adjustments haven't changed
        // This prevents drag/move/rotate/scale/selection updates from clearing the cache or triggering re-processing
        newElements.forEach { newEl ->
            val existing = existingMap[newEl.id]
            if (existing != null) {
                val adjustmentsEqual = existing.adjustments == newEl.adjustments &&
                        existing.blurValue == newEl.blurValue &&
                        existing.hasBlur == newEl.hasBlur &&
                        existing.imageFilter == newEl.imageFilter &&
                        existing.filterIntensity == newEl.filterIntensity &&
                        existing.bitmap === newEl.bitmap &&
                        existing.bitmapData == newEl.bitmapData

                if (adjustmentsEqual) {
                    if (newEl.cachedAdjustedBitmap == null) {
                        newEl.cachedAdjustedBitmap = existing.cachedAdjustedBitmap
                    }
                    if (existing.cachedAdjustedBitmap != null && !existing.isAdjustmentDirty) {
                        newEl.isAdjustmentDirty = false
                    }
                }
            }
        }

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
        val activeGesture =
            currentMode == Mode.DRAG || currentMode == Mode.ROTATE || currentMode == Mode.RESIZE || currentMode == Mode.TRANSFORM
        if (!activeGesture) {
            val sole = selectedElements.singleOrNull()
            if (sole != null && sole.groupId != null) {
                activeGroupId = sole.groupId
                currentMode = Mode.GROUP_EDIT
            } else if (currentMode == Mode.GROUP_EDIT) {
                val allSameGroup =
                    selectedElements.isNotEmpty() && selectedElements.all { it.groupId != null && it.groupId == activeGroupId }
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

    private fun removeSelectedElement() {
        if (currentMode == Mode.GROUP_EDIT && activeGroupId != null && selectedElements.size == 1 && selectedElements.first().groupId == activeGroupId) {
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
                shadowBitmapCache.remove(element.id)?.bitmap?.recycle()
                shadowBitmapCache.remove(element.id + "_img_shadow")?.bitmap?.recycle()
                strokeBitmapCache.remove(element.id)?.bitmap?.recycle()
                strokeBitmapCache.remove(element.id + "_img")?.bitmap?.recycle()
                displayBitmapCache.remove(element.id)?.bitmap?.recycle()
                displayBitmapCache.remove(element.id + "_bg")?.bitmap?.recycle()
                rawSvgBitmapCache.remove(element.id)?.recycle()
                featherBitmapCache.remove(element.id)?.bitmap?.recycle()
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
            shadowBitmapCache.remove(element.id)?.bitmap?.recycle()
            shadowBitmapCache.remove(element.id + "_img_shadow")?.bitmap?.recycle()
            strokeBitmapCache.remove(element.id)?.bitmap?.recycle()
            strokeBitmapCache.remove(element.id + "_img")?.bitmap?.recycle()
            displayBitmapCache.remove(element.id)?.bitmap?.recycle()
            displayBitmapCache.remove(element.id + "_bg")?.bitmap?.recycle()
            rawSvgBitmapCache.remove(element.id)?.recycle()
            featherBitmapCache.remove(element.id)?.bitmap?.recycle()
            pendingAdjustmentJobs.remove(element.id)?.cancel()
        }
        selectedElements.clear()
        invalidate()
    }

    fun applyImageFilter(filter: ImageFilter?, intensity: Float = 1.0f) {
        val elementsToFilter =
            selectedElements.toList() // Create a copy to avoid concurrent modification
        elementsToFilter.forEach { element ->
            if (element != null && (element.type == ElementType.IMAGE || element.type == ElementType.STICKER)) {
                if (filter != null) {
                    element.imageFilter = filter
                }
                element.filterIntensity = intensity
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
                    val tf = Typeface.createFromFile(fontEntity.file_path)
                    element.originalTypeface = tf
                    element.paint.typeface = tf
                } catch (e: Exception) {
                    // Handle potential errors if the file path is valid but the file itself is corrupt or unreadable
                    // You might log the error or set a default typeface here if needed
                    println("Error loading typeface from file: ${fontEntity.file_path}. Error: ${e.message}")

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
            bitmap = src        // ← keep the full-size image
        }
        invalidate()
    }

    fun clearSelection() {
        canvasElements.forEach { it.isSelected = false }
        onElementSelected?.invoke(emptyList())
    }

    private fun renderCanvasTo(canvas: Canvas, scaleFactor: Float) {
        val wasExporting = isExportRendering
        isExportRendering = true
        try {
            val scaledWidth = canvasWidth * scaleFactor
            val scaledHeight = canvasHeight * scaleFactor
            val offsetX = (canvas.width - scaledWidth) / 2f
            val offsetY = (canvas.height - scaledHeight) / 2f

            canvas.drawFilter = android.graphics.PaintFlagsDrawFilter(
                0, android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG
            )

            canvas.withTranslation(offsetX, offsetY) {
                scale(scaleFactor, scaleFactor)
                this@CanvasView.drawCanvasElements(this, showOverlays = false, showCheckerboard = false)
            }
        } finally {
            isExportRendering = wasExporting
        }
    }

    private fun ensureElementHydrated(element: CanvasElement) {
        if (element.type == ElementType.TEXT) {
            val tf = element.originalTypeface ?: element.paint.typeface
            if (tf != null) {
                element.originalTypeface = tf
                element.paint.typeface = tf
            }
        }
        if (element.svgDrawable == null && (element.bitmap == null || element.bitmap?.isRecycled == true)) {
            if (!element.svgData.isNullOrBlank()) {
                try {
                    val svg = com.caverock.androidsvg.SVG.getFromString(element.svgData)
                    val vb = svg.documentViewBox
                    var w = if (vb != null && vb.width() > 0f && vb.height() > 0f) vb.width() else svg.documentWidth
                    var h = if (vb != null && vb.width() > 0f && vb.height() > 0f) vb.height() else svg.documentHeight
                    if (w <= 0f || h <= 0f) {
                        w = 512f
                        h = 512f
                    }
                    svg.documentWidth = w
                    svg.documentHeight = h

                    element.svgDrawable = PictureDrawable(svg.renderToPicture()).trimTransparentEdges()
                    element.bitmap = null
                } catch (e: Exception) {
                    element.bitmapData?.let { data ->
                        if (data.isNotBlank()) element.bitmap = ImageProcessor.base64ToBitmap(data)
                    }
                }
            } else if (element.type == ElementType.DRAW && !element.drawStrokes.isNullOrEmpty()) {
                element.drawStrokes?.forEach { stroke -> stroke.restorePath() }
            } else {
                element.bitmapData?.let { data ->
                    if (data.isNotBlank()) element.bitmap = ImageProcessor.base64ToBitmap(data)
                }
            }
        }
    }

    private fun addWatermark(canvas: Canvas, width: Int, height: Int) {
        val watermarkText = "UrduCanvas"  // Watermark text

        // Load the custom font from the 'res/fonts' folder
        val watermarkTypeface = ResourcesCompat.getFont(context, R.font.default_canvas)

        // Create a paint object with desired properties
        val watermarkPaint = Paint().apply {
            color = "#000000".toColorInt()  // Light gray color for the watermark
            textSize = 40f  // Adjust text size for the watermark
            alpha = 50  // Semi-transparent effect (80 out of 255)
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
        drawable: Drawable, element: CanvasElement, canvasScale: Float = 1f
    ): Bitmap {
        if (drawable is BitmapDrawable) {
            drawable.bitmap?.let { return it }
        }

        // Get the logical content size — this is how big the SVG appears on canvas
        // before element.scale is applied by the canvas matrix
        val logicalW = element.logicalContentWidth.takeIf { it > 0 }
            ?: if (drawable is android.graphics.drawable.PictureDrawable) drawable.picture.width.toFloat()
                .takeIf { it > 0 } ?: 512f
            else drawable.intrinsicWidth.toFloat().takeIf { it > 0 } ?: 512f

        val logicalH = element.logicalContentHeight.takeIf { it > 0 }
            ?: if (drawable is android.graphics.drawable.PictureDrawable) drawable.picture.height.toFloat()
                .takeIf { it > 0 } ?: 512f
            else drawable.intrinsicHeight.toFloat().takeIf { it > 0 } ?: 512f

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
        onProgress: ((percent: Int, stage: String) -> Unit)? = null
    ): Pair<Bitmap, File> {                          // ← File, not String
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
                        element.bitmapData = null  // save space, SVG XML is the source of truth
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
                        element.bitmap?.takeIf { !it.isRecycled }?.let {
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
            gson.toJson(canvasElements, writer)   // streaming overload — no StringBuffer
        }

        onProgress?.invoke(100, "Done")
        return Pair(bitmap, jsonFile)             // ← return File, not String
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
        onProgress: ((percent: Int, stage: String) -> Unit)? = null
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
                        element.bitmap?.takeIf { !it.isRecycled }?.let { bmp ->
                            element.bitmapData = ImageProcessor.bitmapToBase64(bmp)
                        }
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
                Log.e(
                    "CanvasView",
                    "exportCanvasThumbnailBitmap: element ${element.id} failed: ${e.message}"
                )
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
            Pair(thumbnailBitmap, jsonFile)  // ← File, not String

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
        onProgress: ((percent: Int, stage: String) -> Unit)? = null
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
                                element.svgDrawable!!, element
                            ) // canvasScale=1f default
                            element.bitmap = rasterized
                            element.bitmapData = ImageProcessor.bitmapToBase64(rasterized)
                            element.svgDrawable = null  // ✅ clear @Transient — not serializable
                        }

                        // Regular bitmap
                        else -> {
                            element.bitmap?.takeIf { !it.isRecycled }?.let {
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
            Pair(bitmap, jsonFile)  // Return File — caller streams it, never loads full String

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
                }?.toMutableList()
            ).also { copy ->
                copy.svgDrawable = element.svgDrawable  // ✅ restore @Transient
                copy.bitmap = element.bitmap
            }
        }

        safeElements.forEach { element ->
            when {
                // ✅ SVG with data — no bitmap needed, SVG XML is already in svgData field
                element.svgDrawable != null && element.svgData != null -> {
                    element.bitmapData = null  // save space, SVG XML is the source of truth
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
                    element.bitmap?.takeIf { !it.isRecycled }?.let {
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
        if (!isSmartSnappingEnabled) {
            showRotationVerticalGuide = false
            showRotationHorizontalGuide = false
            return
        }
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
            snapped && (element.rotation == 0f || element.rotation == 180f || element.rotation == 360f)
        showRotationHorizontalGuide =
            snapped && (element.rotation == 90f || element.rotation == 270f)
    }

    private data class SnapTarget(val position: Float, val priority: Int)

    private fun checkDragSnap() {
        if (!isSmartSnappingEnabled || selectedElements.isEmpty()) {
            activeSnapLines.clear()
            showVerticalGuide = false
            showHorizontalGuide = false
            return
        }

        // 1. Clear previous snapping lines
        activeSnapLines.clear()

        // 2. Scale-aware snap threshold (~6 screen pixels to keep drag fluid)
        val snapThreshold = 6f / (overallScale * scale)

        // 3. Determine boundaries of dragged items
        val bounds = if (selectedElements.size == 1) {
            getElementAxisAlignedBounds(selectedElements.first())
        } else {
            getCombinedSelectedBounds()
        }

        val dragXAnchors = listOf(bounds.left, bounds.centerX(), bounds.right)
        val dragYAnchors = listOf(bounds.top, bounds.centerY(), bounds.bottom)

        val targetXAnchors = mutableListOf<SnapTarget>()
        val targetYAnchors = mutableListOf<SnapTarget>()

        // 4a. Priority 1: Ruler guidelines (Active when RulerState != OFF)
        val isTwoSides =
            rulerState == com.webscare.urducanvas.common.canvas.enums.RulerState.TWO_SIDES
        val isFourSides =
            rulerState == com.webscare.urducanvas.common.canvas.enums.RulerState.FOUR_SIDES
        if (isTwoSides || isFourSides) {
            targetXAnchors.add(SnapTarget(leftRulerX, priority = 1))
            targetYAnchors.add(SnapTarget(topRulerY, priority = 1))
            if (isFourSides) {
                targetXAnchors.add(SnapTarget(rightRulerX, priority = 1))
                targetYAnchors.add(SnapTarget(bottomRulerY, priority = 1))
            }
        }

        // 4b. Priority 2: Canvas targets (edges and center)
        targetXAnchors.add(SnapTarget(0f, priority = 2))
        targetXAnchors.add(SnapTarget(canvasWidth / 2f, priority = 2))
        targetXAnchors.add(SnapTarget(canvasWidth.toFloat(), priority = 2))

        targetYAnchors.add(SnapTarget(0f, priority = 2))
        targetYAnchors.add(SnapTarget(canvasHeight / 2f, priority = 2))
        targetYAnchors.add(SnapTarget(canvasHeight.toFloat(), priority = 2))

        // 4c. Priority 3: Other elements targets
        val selectedIds = selectedElements.map { it.id }.toSet()
        canvasElements.forEach { e ->
            if (e.id !in selectedIds && e.type != ElementType.BACKGROUND && e.type != ElementType.GROUP) {
                val eBounds = getElementAxisAlignedBounds(e)
                targetXAnchors.add(SnapTarget(eBounds.left, priority = 3))
                targetXAnchors.add(SnapTarget(eBounds.centerX(), priority = 3))
                targetXAnchors.add(SnapTarget(eBounds.right, priority = 3))

                targetYAnchors.add(SnapTarget(eBounds.top, priority = 3))
                targetYAnchors.add(SnapTarget(eBounds.centerY(), priority = 3))
                targetYAnchors.add(SnapTarget(eBounds.bottom, priority = 3))
            }
        }

        // 4d. Priority 4: Canvas grid targets (Active when showGrid == true)
        if (showGrid) {
            val gridSpacing = 50f
            var gx = 0f
            while (gx <= canvasWidth) {
                targetXAnchors.add(SnapTarget(gx, priority = 4))
                gx += gridSpacing
            }
            var gy = 0f
            while (gy <= canvasHeight) {
                targetYAnchors.add(SnapTarget(gy, priority = 4))
                gy += gridSpacing
            }
        }

        // 5. Find closest snap respecting priority hierarchy
        var bestDeltaX = Float.MAX_VALUE
        var bestSnapLineX: Float? = null
        var bestPriorityX = Int.MAX_VALUE

        for (dragX in dragXAnchors) {
            for (target in targetXAnchors) {
                val delta = target.position - dragX
                val absDelta = abs(delta)
                if (absDelta <= snapThreshold) {
                    if (target.priority < bestPriorityX || (target.priority == bestPriorityX && absDelta < abs(
                            bestDeltaX
                        ))
                    ) {
                        bestPriorityX = target.priority
                        bestDeltaX = delta
                        bestSnapLineX = target.position
                    }
                }
            }
        }

        var bestDeltaY = Float.MAX_VALUE
        var bestSnapLineY: Float? = null
        var bestPriorityY = Int.MAX_VALUE

        for (dragY in dragYAnchors) {
            for (target in targetYAnchors) {
                val delta = target.position - dragY
                val absDelta = abs(delta)
                if (absDelta <= snapThreshold) {
                    if (target.priority < bestPriorityY || (target.priority == bestPriorityY && absDelta < abs(
                            bestDeltaY
                        ))
                    ) {
                        bestPriorityY = target.priority
                        bestDeltaY = delta
                        bestSnapLineY = target.position
                    }
                }
            }
        }

        // 6. Apply snapping updates and draw active snap lines
        val xSnapped = bestSnapLineX != null
        val ySnapped = bestSnapLineY != null

        val dx = if (xSnapped) bestDeltaX else 0f
        val dy = if (ySnapped) bestDeltaY else 0f

        if (xSnapped || ySnapped) {
            selectedElements.forEach { e ->
                if (xSnapped) e.x += dx
                if (ySnapped) e.y += dy
                onElementChanged?.invoke(e)
            }

            if (xSnapped) {
                activeSnapLines.add(SnapLine(bestSnapLineX!!, isHorizontal = false))
                if (!showVerticalGuide) vibrateSoft()
                showVerticalGuide = true
            } else {
                showVerticalGuide = false
            }

            if (ySnapped) {
                activeSnapLines.add(SnapLine(bestSnapLineY!!, isHorizontal = true))
                if (!showHorizontalGuide) vibrateSoft()
                showHorizontalGuide = true
            } else {
                showHorizontalGuide = false
            }
        } else {
            showVerticalGuide = false
            showHorizontalGuide = false
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
            snapped && (snappedTarget == 0f || snappedTarget == 180f || snappedTarget == 360f)
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
    private fun isColorDark(@ColorInt color: Int): Boolean {
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
        val xMin = w - sw / 2f     // far right of image at right edge
        val xMax = sw / 2f       // far left of image at left edge
        val yMin = h - sh / 2f
        val yMax = sh / 2f

        return (xMin..xMax) to (yMin..yMax)
    }

    private fun drawLivePreviewStroke(canvas: Canvas) {
        val path = currentStrokePath ?: return

        val tempStroke = StrokeData(
            path = path,
            color = currentBrushColor,
            thickness = currentBrushThickness,
            hardness = currentBrushHardness,
            style = currentBrushStyle,
            gradient = currentBrushGradient
        )

        com.webscare.urducanvas.common.utils.BrushRenderUtils.drawSingleStroke(
            canvas, tempStroke, 255
        )
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.withSave {

            val pivotX = width / 2f
            val pivotY = height / 2f

            translate(overallOffsetX, overallOffsetY)
            scale(overallScale, overallScale, pivotX, pivotY)

            // Draw canvas content centered
            val scaledWidth = canvasWidth * scale
            val scaledHeight = canvasHeight * scale
            offsetX = (width - scaledWidth) / 2f
            offsetY = (height - scaledHeight) / 2f

            withTranslation(offsetX, offsetY) {
                scale(scale, scale)
                if (isDrawing) {
                    drawCanvasShadow(this)
                    // Draw canvas elements normally (dimmed by overlay on top — no saveLayer needed)
                    drawCanvasElements(this, showOverlays = false, showCheckerboard = false)

                    // Draw semi-transparent overlay on top — no offscreen bitmap, just a rect
                    drawRect(
                        0f,
                        0f,
                        canvasWidth.toFloat(),
                        canvasHeight.toFloat(),
                        drawingModeOverlayPaint
                    )

                    // Draw all committed session strokes ABOVE the overlay
                    activeSessionElement?.let { session ->
                        if (!session.drawStrokes.isNullOrEmpty()) {
                            drawDrawElement(this, session)
                        }
                    }

                    // Draw the current live in-progress stroke ABOVE everything
                    if (currentStrokePath != null && currentStrokePaint != null) {
                        drawLivePreviewStroke(this)
                    }
                } else if (isTableEditMode) {
                    drawCanvasShadow(this)
                    val activeTable = selectedElements.firstOrNull { it.type == ElementType.TABLE }
                    val nonTableIds = canvasElements.filter { it.id != activeTable?.id }.map { it.id }.toSet()
                    drawCanvasElements(this, showOverlays = false, showCheckerboard = false, isolatedIds = nonTableIds)
                    drawRect(
                        0f,
                        0f,
                        canvasWidth.toFloat(),
                        canvasHeight.toFloat(),
                        drawingModeOverlayPaint
                    )
                    activeTable?.let { tableElement ->
                        drawCanvasElements(this, showOverlays = false, showCheckerboard = false, isolatedIds = setOf(tableElement.id))
                    }
                } else {
                    drawCanvasShadow(this)
                    if (activeGroupId != null) {
                        // Isolation mode (Illustrator/Photoshop style):
                        // 1. Draw all elements at reduced opacity
                        // 2. Draw white overlay at 50% to wash out non-group content
                        // 3. Draw only the active group's children at full opacity on top
                        drawCanvasElements(this, showOverlays = false)
                        drawRect(
                            0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), Paint().apply {
                                color = Color.argb(140, 255, 255, 255); style = Paint.Style.FILL
                            })
                        // Re-draw group children at full opacity on top
                        val groupChildIds =
                            canvasElements.filter { it.groupId == activeGroupId }.map { it.id }
                                .toSet()
                        drawCanvasElements(
                            this,
                            showOverlays = true,
                            showCheckerboard = false,
                            isolatedIds = groupChildIds
                        )
                    } else {
                        // Normal render
                        drawCanvasElements(this)
                    }
                }
            }
        }

        // ── Canvas pan CENTER-SNAP guides ─────────────────────────
        // Shown while dragging, as long as the canvas is near/at the center.
        if (showCanvasCenterVerticalSnap || showCanvasCenterHorizontalSnap) {
            // Show a subtle grid overlay on the canvas rect to make it obvious
            drawGrid(canvas)

            if (showCanvasCenterVerticalSnap) {
                // Vertical line through view center X
                canvas.drawLine(
                    width / 2f, 0f, width / 2f, height.toFloat(), canvasSnapPaint
                )
            }
            if (showCanvasCenterHorizontalSnap) {
                // Horizontal line through view center Y
                canvas.drawLine(
                    0f, height / 2f, width.toFloat(), height / 2f, canvasSnapPaint
                )
            }
        }

        // ── GRID ─────────────────────────────────────────────────
        if (showGrid) {
            drawGrid(canvas)
        }

        // ── RULER ─────────────────────────────────────────────────
        if (rulerState != com.webscare.urducanvas.common.canvas.enums.RulerState.OFF) {
            drawRuler(canvas)
        }

        // ── COLOR PICKER EYEDROPPER ────────────────────────────────
        // Always drawn at the very top of onDraw so it is never hidden by isDrawing,
        // showOverlays=false, or any canvas elements / dim overlays.
        if (isColorPickerMode) {
            drawColorPickerOverlay(canvas)
        }
    }

    fun colorFilterFor(filter: ImageFilter?, intensity: Float = 1.0f): ColorFilter? {
        return ImageFilter.getColorFilter(filter, intensity)
    }

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

    private fun drawCanvasElements(
        canvas: Canvas, showOverlays: Boolean = true, showCheckerboard: Boolean = true,
        // When non-null, only elements whose id is in this set are drawn.
        // Used by GROUP_EDIT isolation mode to re-draw only the active group at full opacity.
        isolatedIds: Set<String>? = null
    ) {
        canvas.save()
        val clipRect = RectF(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat())
        canvas.clipRect(clipRect)

        if (showCheckerboard) {
            canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), checkerPaint)
        }

        // Draw all elements
        canvasElements.forEach { element ->
            if (!element.isVisible) return@forEach
            // GROUP sentinels are structural only -- no visual, skip immediately
            if (element.type == ElementType.GROUP) return@forEach
            // Isolation mode: only draw elements in the isolated set
            if (isolatedIds != null && element.id !in isolatedIds) return@forEach

            ensureElementHydrated(element)

            if (element.type == ElementType.BACKGROUND) {
                drawBackgroundElement(canvas, element)
                return@forEach
            }

            // ── Full-canvas image layer — drawn with cover-fill like background ──
            if (element.type == ElementType.IMAGE && element.logicalContentWidth == canvasWidth.toFloat() && element.logicalContentHeight == canvasHeight.toFloat() && element.imageFitMode == "cover") {
                drawBackgroundElement(canvas, element)
                return@forEach
            } else {
                canvas.withTranslation(element.x, element.y) {
                    canvas.rotate(element.rotation)
                    val fx = if (element.isFlippedX) -1f else 1f
                    val fy = if (element.isFlippedY) -1f else 1f
                    canvas.scale(element.scale * fx, element.scale * fy)

                    val needsLayer = element.blendType != BlendType.NORMAL || element.paintAlpha < 255
                    if (needsLayer) {
                        reusableOpacityPaint.reset()
                        reusableOpacityPaint.isAntiAlias = true
                        reusableOpacityPaint.alpha = element.paintAlpha
                        reusableOpacityPaint.xfermode = drawWithBlend(element)
                        canvas.saveLayer(null, reusableOpacityPaint)
                    }

                    when {
                        element.type == ElementType.DRAW && element.bitmap == null -> {
                            drawDrawElement(canvas, element)
                        }

                        element.type == ElementType.SHAPE -> drawShapeElement(
                            canvas, element
                        )

                        element.type == ElementType.TEXT -> drawTextElement(
                            canvas, element
                        )

                        element.type == ElementType.TABLE -> drawTableElement(
                            canvas, element
                        )

                        else -> {
                            element.svgDrawable?.let { drawable ->
                                val w = element.logicalContentWidth.takeIf { it > 0 }
                                    ?: drawable.picture.width.toFloat().takeIf { it > 0 } ?: 200f
                                val h = element.logicalContentHeight.takeIf { it > 0 }
                                    ?: drawable.picture.height.toFloat().takeIf { it > 0 } ?: 200f
                                val left = -w / 2f
                                val top = -h / 2f

                                val hasAnyAdjustment =
                                    element.hasLight || element.hasColor || element.hasDetail || element.hasBlur
                                val hasImageFilter = element.imageFilter != ImageFilter.None
                                val needsRaster = hasAnyAdjustment || hasImageFilter

                                // ── CACHE — rasterize once, reuse every frame ─────────────────────
                                // OOM fix: never create a bitmap inside onDraw on every frame.
                                // We store the rasterized result on element.bitmap and reuse it.
                                // It is invalidated (set null) whenever adjustments/filters change.
                                val finalBitmap: Bitmap? = if (needsRaster) {
                                    // 1. Get or build the raw SVG bitmap (unadjusted)
                                    var rawSvg = rawSvgBitmapCache[element.id]
                                    if (rawSvg == null || rawSvg.isRecycled) {
                                        val svgData = element.svgData
                                        rawSvg = if (svgData != null) {
                                            try {
                                                val svg = SVG.getFromString(svgData)
                                                val vb = svg.documentViewBox
                                                val nativeW =
                                                    if (vb != null && vb.width() > 0) vb.width() else w
                                                val nativeH =
                                                    if (vb != null && vb.height() > 0) vb.height() else h

                                                // Cap at 2048 to prevent OOM on large SVGs
                                                val scale =
                                                    minOf(2048f / nativeW, 2048f / nativeH, 2f)
                                                val bmpW =
                                                    (nativeW * scale).toInt().coerceAtLeast(1)
                                                val bmpH =
                                                    (nativeH * scale).toInt().coerceAtLeast(1)

                                                val raw = Bitmap.createBitmap(
                                                    bmpW, bmpH, Bitmap.Config.ARGB_8888
                                                )
                                                svg.documentWidth = bmpW.toFloat()
                                                svg.documentHeight = bmpH.toFloat()
                                                svg.renderToCanvas(Canvas(raw))
                                                val trimmed = raw.trimTransparentEdges()
                                                if (trimmed != raw) raw.recycle()
                                                trimmed
                                            } catch (e: Throwable) {
                                                if (e is OutOfMemoryError) System.gc()
                                                try {
                                                    Bitmap.createBitmap(
                                                        w.toInt().coerceAtLeast(1),
                                                        h.toInt().coerceAtLeast(1),
                                                        Bitmap.Config.ARGB_8888
                                                    ).also {
                                                        drawable.setBounds(0, 0, w.toInt(), h.toInt())
                                                        drawable.draw(Canvas(it))
                                                    }
                                                } catch (t: Throwable) {
                                                    null
                                                }
                                            }
                                        } else {
                                            Bitmap.createBitmap(
                                                w.toInt().coerceAtLeast(1),
                                                h.toInt().coerceAtLeast(1),
                                                Bitmap.Config.ARGB_8888
                                            ).also {
                                                drawable.setBounds(0, 0, w.toInt(), h.toInt())
                                                drawable.draw(Canvas(it))
                                            }
                                        }
                                        if (rawSvg != null) {
                                            rawSvgBitmapCache[element.id] = rawSvg
                                        }
                                    }

                                    // 2. Resolve adjusted bitmap asynchronously
                                    rawSvg?.let { resolveAdjustedBitmapAsync(element, it) }
                                } else null

                                // ── Compute draw rect (aspect-ratio-correct, shared by shadow/stroke/main) ──
                                val drawW: Float
                                val drawH: Float
                                val bl: Float
                                val bt: Float
                                val br: Float
                                val bb: Float
                                if (finalBitmap != null) {
                                    val bitmapAspect =
                                        finalBitmap.width.toFloat() / finalBitmap.height.toFloat()
                                    val logicalAspect = w / h
                                    if (bitmapAspect > logicalAspect) {
                                        drawW = w; drawH = w / bitmapAspect
                                    } else {
                                        drawW = h * bitmapAspect; drawH = h
                                    }
                                    bl = -drawW / 2f; bt = -drawH / 2f
                                    br = drawW / 2f; bb = drawH / 2f
                                } else {
                                    drawW = w; drawH = h
                                    bl = left; bt = top; br = left + w; bb = top + h
                                }

                                // ── Shadow ───────────────────────────────────────────────────────────
                                if (element.hasShadow && element.shadowOpacity > 0) {
                                    // Fingerprint covers every param that changes the blurred bitmap shape.
                                    // shadowDx/Dy are NOT included — they are applied at draw time, not
                                    // baked into the blur, so changing offset doesn't need a re-bake.
                                    val shadowFp = Objects.hash(
                                        element.id,
                                        element.shadowRadius,
                                        element.shadowColor,
                                        element.shadowOpacity,
                                        drawW.toInt(),
                                        drawH.toInt()
                                    )

                                    val cached = shadowBitmapCache[element.id]
                                    val entry: ShadowCacheEntry =
                                        if (cached != null && cached.fingerprint == shadowFp && !cached.bitmap.isRecycled) {
                                            cached // ✅ reuse — no blur work this frame
                                        } else {
                                            // Cache miss or params changed — build once and store
                                            cached?.bitmap?.recycle()

                                            val shadowSource = finalBitmap ?: createBitmap(
                                                w.toInt().coerceAtLeast(1),
                                                h.toInt().coerceAtLeast(1)
                                            ).also { bmp ->
                                                Canvas(bmp).also { c ->
                                                    drawable.setBounds(0, 0, w.toInt(), h.toInt())
                                                    drawable.draw(c)
                                                }
                                            }

                                            val srcW = shadowSource.width.toFloat()
                                            val srcH = shadowSource.height.toFloat()

                                            // extractAlpha(paint, offset) pre-bakes BlurMaskFilter into
                                            // the returned bitmap. Required because Android silently drops
                                            // maskFilter on drawBitmap(..., dstRectF, paint) scaled draws.
                                            val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                                maskFilter = BlurMaskFilter(
                                                    element.shadowRadius.coerceAtLeast(0.1f),
                                                    BlurMaskFilter.Blur.NORMAL
                                                )
                                            }
                                            val offset = IntArray(2)
                                            val blurredBitmap =
                                                shadowSource.extractAlpha(blurPaint, offset)
                                            if (finalBitmap == null) shadowSource.recycle()

                                            // Scale offset into draw-rect space so it lands correctly
                                            // when the blurred bitmap is drawn into bl/bt/br/bb
                                            val scaleX = (br - bl) / srcW
                                            val scaleY = (bb - bt) / srcH

                                            ShadowCacheEntry(
                                                bitmap = blurredBitmap,
                                                fingerprint = shadowFp,
                                                scaleX = scaleX,
                                                scaleY = scaleY,
                                                offsetX = offset[0] * scaleX,
                                                offsetY = offset[1] * scaleY
                                            ).also { shadowBitmapCache[element.id] = it }
                                        }

                                    val shadowColor = Color.argb(
                                        element.shadowOpacity.coerceIn(0, 255),
                                        Color.red(element.shadowColor),
                                        Color.green(element.shadowColor),
                                        Color.blue(element.shadowColor)
                                    )
                                    val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        isFilterBitmap = true
                                        colorFilter = android.graphics.PorterDuffColorFilter(
                                            shadowColor, PorterDuff.Mode.SRC_IN
                                        )
                                    }

                                    val dstLeft = bl + entry.offsetX + element.shadowDx
                                    val dstTop = bt + entry.offsetY + element.shadowDy
                                    val dstRight = dstLeft + entry.bitmap.width * entry.scaleX
                                    val dstBottom = dstTop + entry.bitmap.height * entry.scaleY

                                    canvas.save()
                                    if (!entry.bitmap.isRecycled) canvas.drawBitmap(
                                        entry.bitmap,
                                        null,
                                        RectF(dstLeft, dstTop, dstRight, dstBottom),
                                        drawPaint
                                    )
                                    canvas.restore()
                                }

                                // ── Stroke & Feather Setup ───────────────────────────────────────────
                                val hasFeather = element.hasFeather && element.featherRadius > 0f
                                val hasStroke = element.hasStroke && element.strokeWidth > 0f
                                val strokeWidth = if (hasStroke) element.strokeWidth else 0f

                                val layerLeft = bl - strokeWidth
                                val layerTop = bt - strokeWidth
                                val layerRight = br + strokeWidth
                                val layerBottom = bb + strokeWidth

                                val needsLayer = (element.hasOverlay && element.overlayOpacity > 0) || hasFeather
                                if (needsLayer) {
                                    canvas.saveLayer(layerLeft, layerTop, layerRight, layerBottom, null)
                                }

                                if (hasStroke) {
                                    // Stroke alpha bitmap only depends on the shape, not color/gradient —
                                    // so fingerprint on shape dimensions only. Color is applied at draw time.
                                    val strokeFp = Objects.hash(
                                        element.id,
                                        element.strokeWidth,
                                        drawW.toInt(),
                                        drawH.toInt()
                                    )

                                    val cachedStroke = strokeBitmapCache[element.id]
                                    val strokedAlphaMask: Bitmap =
                                        if (cachedStroke != null && cachedStroke.fingerprint == strokeFp && !cachedStroke.bitmap.isRecycled) {
                                            cachedStroke.bitmap // ✅ reuse
                                        } else {
                                            cachedStroke?.bitmap?.recycle()
                                            val strokeSource = finalBitmap ?: createBitmap(
                                                w.toInt().coerceAtLeast(1),
                                                h.toInt().coerceAtLeast(1)
                                            ).also { bmp ->
                                                Canvas(bmp).also { c ->
                                                    drawable.setBounds(0, 0, w.toInt(), h.toInt())
                                                    drawable.draw(c)
                                                }
                                            }
                                            val strokeAlpha = strokeSource.extractAlpha()
                                            if (finalBitmap == null) strokeSource.recycle()

                                            // Pre-render the stroke onto a single ALPHA_8 bitmap
                                            val strokeWidthInt =
                                                element.strokeWidth.roundToInt().coerceAtLeast(1)
                                            val maskW = strokeAlpha.width + 2 * strokeWidthInt
                                            val maskH = strokeAlpha.height + 2 * strokeWidthInt
                                            val preRendered = Bitmap.createBitmap(
                                                maskW, maskH, Bitmap.Config.ALPHA_8
                                            )
                                            val maskCanvas = Canvas(preRendered)
                                            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                                            for (angle in 0 until 360 step 10) {
                                                val rad = Math.toRadians(angle.toDouble())
                                                val dx = (strokeWidthInt * cos(rad)).toFloat()
                                                val dy = (strokeWidthInt * sin(rad)).toFloat()
                                                maskCanvas.drawBitmap(
                                                    strokeAlpha,
                                                    strokeWidthInt + dx,
                                                    strokeWidthInt + dy,
                                                    maskPaint
                                                )
                                            }
                                            strokeAlpha.recycle()

                                            StrokeCacheEntry(
                                                preRendered, strokeFp
                                            ).also { strokeBitmapCache[element.id] = it }.bitmap
                                        }

                                    reusableStrokePaint.reset()
                                    reusableStrokePaint.style = Paint.Style.FILL
                                    reusableStrokePaint.isFilterBitmap = true
                                    if (element.strokeGradient != null) {
                                        reusableStrokePaint.shader = createGradientShader(
                                            element.strokeGradient!!, drawW, drawH
                                        )
                                    } else {
                                        reusableStrokePaint.shader = null
                                        reusableStrokePaint.color = element.strokeColor
                                    }
                                    if (!hasFeather) canvas.save()
                                    reusableRectF.set(layerLeft, layerTop, layerRight, layerBottom)
                                    if (!strokedAlphaMask.isRecycled) {
                                        canvas.drawBitmap(
                                            strokedAlphaMask,
                                            null,
                                            reusableRectF,
                                            reusableStrokePaint
                                        )
                                    }
                                    if (!hasFeather) canvas.restore()
                                }

                                // ── Main draw ─────────────────────────────────────────────────────────
                                if (finalBitmap != null) {
                                    if (!hasFeather && !needsLayer) canvas.save()
                                    else if (!hasFeather && needsLayer) {
                                        // Save already called above for needsLayer
                                    }

                                    reusableDrawPaint.reset()
                                    reusableDrawPaint.isAntiAlias = true
                                    reusableDrawPaint.isFilterBitmap = true
                                    reusableDrawPaint.colorFilter =
                                        colorFilterFor(element.imageFilter, element.filterIntensity)

                                    reusableRectF.set(bl, bt, br, bb)
                                    if (!finalBitmap.isRecycled) when (element.imageFilter) {
                                        ImageFilter.SoftBlur -> {
                                            reusableDrawPaint.maskFilter =
                                                BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
                                            canvas.drawBitmap(
                                                finalBitmap, null, reusableRectF, reusableDrawPaint
                                            )
                                        }

                                        ImageFilter.Glow -> {
                                            canvas.drawBitmap(
                                                finalBitmap, null, reusableRectF, reusableDrawPaint
                                            )
                                            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                                color = Color.argb(180, 255, 255, 200)
                                                maskFilter =
                                                    BlurMaskFilter(25f, BlurMaskFilter.Blur.OUTER)
                                                colorFilter = colorFilterFor(element.imageFilter, element.filterIntensity)
                                            }
                                            canvas.drawBitmap(
                                                finalBitmap, null, reusableRectF, glowPaint
                                            )
                                        }

                                        else -> {
                                            canvas.drawBitmap(
                                                finalBitmap, null, reusableRectF, reusableDrawPaint
                                            )
                                        }
                                    }

                                    if (element.hasOverlay && element.overlayOpacity > 0) {
                                        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                            alpha = element.overlayOpacity.coerceIn(0, 255)
                                            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                                            if (element.overlayGradient != null) {
                                                shader = createGradientShader(
                                                    element.overlayGradient!!, drawW, drawH
                                                )
                                            } else {
                                                color = element.overlayColor
                                            }
                                        }
                                        canvas.drawRect(bl, bt, br, bb, overlayPaint)
                                    }

                                    // ── Feather: soft edge fade, instant GPU, no pixel loops ────────
                                    if (hasFeather) {
                                        drawFeatherMask(
                                            canvas,
                                            element.id,
                                            if (hasStroke) layerLeft else bl,
                                            if (hasStroke) layerTop else bt,
                                            if (hasStroke) layerRight else br,
                                            if (hasStroke) layerBottom else bb,
                                            element.featherRadius,
                                            element.featherWidth,
                                            element.featherDirection ?: FeatherDirection.ALL,
                                            element.featherBiasX,
                                            element.featherBiasY
                                        )
                                    }
                                    if (isElementProcessing(element.id)) {
                                        drawShimmerOverlay(canvas, reusableRectF)
                                    }

                                    canvas.restore()
                                    // Do NOT recycle finalBitmap here -- it IS element.bitmap, cached across
                                    // frames and read concurrently by the export thread.
                                } else {
                                    // ── Pure vector path — no adjustments, no filters ────────────────
                                    drawable.setBounds(
                                        left.toInt(),
                                        top.toInt(),
                                        (left + w).toInt(),
                                        (top + h).toInt()
                                    )

                                    canvas.saveLayer(left, top, left + w, top + h, null)
                                    drawable.draw(canvas)

                                    // Overlay
                                    if (element.hasOverlay && element.overlayOpacity > 0) {
                                        canvas.drawRect(
                                            left,
                                            top,
                                            left + w,
                                            top + h,
                                            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                                alpha = element.overlayOpacity.coerceIn(0, 255)
                                                xfermode =
                                                    PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                                                if (element.overlayGradient != null) {
                                                    shader = createGradientShader(
                                                        element.overlayGradient!!, w, h
                                                    )
                                                } else {
                                                    color = element.overlayColor
                                                }
                                            })
                                    }

                                    // ── Feather: soft edge fade, instant GPU, no pixel loops ────────
                                    if (hasFeather) {
                                        drawFeatherMask(
                                            canvas,
                                            element.id,
                                            if (hasStroke) layerLeft else left,
                                            if (hasStroke) layerTop else top,
                                            if (hasStroke) layerRight else left + w,
                                            if (hasStroke) layerBottom else top + h,
                                            element.featherRadius,
                                            element.featherWidth,
                                            element.featherDirection ?: FeatherDirection.ALL,
                                            element.featherBiasX,
                                            element.featherBiasY
                                        )
                                    }

                                    canvas.restore()
                                }

                                return@let
                            }

                            element.bitmap?.let { bmp ->
                                if (bmp.isRecycled) {
                                    Log.w("CanvasView", "drawCanvasElements: bitmap is recycled for element ${element.id} (${element.customName ?: element.type})")
                                    return@let
                                }

                                // ── Async adjustment: never block onDraw with full-res processing ──
                                val finalBitmap: Bitmap = resolveAdjustedBitmapAsync(element, bmp)

                                val w = finalBitmap.width.toFloat()
                                val h = finalBitmap.height.toFloat()
                                val left = -w / 2f
                                val top = -h / 2f

                                // ── Display-resolution downscale ─────────────────────────────────
                                // Draw a screen-sized proxy; full-res finalBitmap stays intact for export.
                                val onScreenW =
                                    (element.getLocalContentWidth() * element.scale * scale * overallScale).toInt()
                                        .coerceIn(1, finalBitmap.width)
                                val onScreenH =
                                    (element.getLocalContentHeight() * element.scale * scale * overallScale).toInt()
                                        .coerceIn(1, finalBitmap.height)
                                val displayBmp = getOrBuildDisplayBitmap(
                                    element.id, finalBitmap, onScreenW, onScreenH
                                )

                                // ── Cached shadow (Problem 3 fix for IMAGE type) ─────────────────
                                if (element.hasShadow && element.shadowOpacity > 0) {
                                    val shadowFp = Objects.hash(
                                        element.id + "_img_shadow",
                                        element.shadowRadius,
                                        element.shadowColor,
                                        element.shadowOpacity,
                                        finalBitmap.width,
                                        finalBitmap.height
                                    )
                                    val cached = shadowBitmapCache[element.id + "_img_shadow"]
                                    val entry: ShadowCacheEntry =
                                        if (cached != null && cached.fingerprint == shadowFp && !cached.bitmap.isRecycled) {
                                            cached
                                        } else {
                                            cached?.bitmap?.recycle()
                                            val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                                maskFilter = BlurMaskFilter(
                                                    element.shadowRadius.coerceAtLeast(0.1f),
                                                    BlurMaskFilter.Blur.NORMAL
                                                )
                                            }
                                            val offset = IntArray(2)
                                            val blurred =
                                                finalBitmap.extractAlpha(blurPaint, offset)
                                            ShadowCacheEntry(
                                                bitmap = blurred,
                                                fingerprint = shadowFp,
                                                scaleX = 1f,
                                                scaleY = 1f,
                                                offsetX = offset[0].toFloat(),
                                                offsetY = offset[1].toFloat()
                                            ).also {
                                                shadowBitmapCache[element.id + "_img_shadow"] = it
                                            }
                                        }

                                    val shadowColor = Color.argb(
                                        element.shadowOpacity.coerceIn(0, 255),
                                        Color.red(element.shadowColor),
                                        Color.green(element.shadowColor),
                                        Color.blue(element.shadowColor)
                                    )
                                    reusableDrawPaint.reset()
                                    reusableDrawPaint.isAntiAlias = true
                                    reusableDrawPaint.isFilterBitmap = true
                                    reusableDrawPaint.colorFilter =
                                        android.graphics.PorterDuffColorFilter(
                                            shadowColor, PorterDuff.Mode.SRC_IN
                                        )

                                    val dstLeft = left + entry.offsetX + element.shadowDx
                                    val dstTop = top + entry.offsetY + element.shadowDy
                                    reusableRectF.set(
                                        dstLeft,
                                        dstTop,
                                        dstLeft + entry.bitmap.width,
                                        dstTop + entry.bitmap.height
                                    )

                                    canvas.save()
                                    if (!entry.bitmap.isRecycled) {
                                        canvas.drawBitmap(
                                            entry.bitmap, null, reusableRectF, reusableDrawPaint
                                        )
                                    }
                                    canvas.restore()
                                }

                                // ── Stroke & Feather Setup ───────────────────────────────────────────
                                val hasFeather = element.hasFeather && element.featherRadius > 0f
                                val hasStroke = element.hasStroke && element.strokeWidth > 0f
                                val strokeWidth = if (hasStroke) element.strokeWidth else 0f

                                val layerLeft = left - strokeWidth
                                val layerTop = top - strokeWidth
                                val layerRight = left + w + strokeWidth
                                val layerBottom = top + h + strokeWidth

                                val needsImgLayer = (element.hasOverlay && element.overlayOpacity > 0) || hasFeather
                                if (needsImgLayer) {
                                    canvas.saveLayer(layerLeft, layerTop, layerRight, layerBottom, null)
                                }

                                // ── Cached stroke (Problem 3 fix for IMAGE type) ─────────────────
                                if (hasStroke) {
                                    val strokeFp = Objects.hash(
                                        element.id + "_img",
                                        element.strokeWidth,
                                        finalBitmap.width,
                                        finalBitmap.height
                                    )
                                    val cachedStroke = strokeBitmapCache[element.id + "_img"]
                                    val strokedAlphaMask: Bitmap =
                                        if (cachedStroke != null && cachedStroke.fingerprint == strokeFp && !cachedStroke.bitmap.isRecycled) {
                                            cachedStroke.bitmap
                                        } else {
                                            cachedStroke?.bitmap?.recycle()
                                            val strokeAlpha = finalBitmap.extractAlpha()

                                            // Pre-render the stroke onto a single ALPHA_8 bitmap
                                            val strokeWidthInt =
                                                element.strokeWidth.roundToInt().coerceAtLeast(1)
                                            val maskW = strokeAlpha.width + 2 * strokeWidthInt
                                            val maskH = strokeAlpha.height + 2 * strokeWidthInt
                                            val preRendered = Bitmap.createBitmap(
                                                maskW, maskH, Bitmap.Config.ALPHA_8
                                            )
                                            val maskCanvas = Canvas(preRendered)
                                            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                                            for (angle in 0 until 360 step 10) {
                                                val rad = Math.toRadians(angle.toDouble())
                                                val dx = (strokeWidthInt * cos(rad)).toFloat()
                                                val dy = (strokeWidthInt * sin(rad)).toFloat()
                                                maskCanvas.drawBitmap(
                                                    strokeAlpha,
                                                    strokeWidthInt + dx,
                                                    strokeWidthInt + dy,
                                                    maskPaint
                                                )
                                            }
                                            strokeAlpha.recycle()

                                            StrokeCacheEntry(preRendered, strokeFp).also {
                                                    strokeBitmapCache[element.id + "_img"] = it
                                                }.bitmap
                                        }

                                    reusableStrokePaint.reset()
                                    reusableStrokePaint.style = Paint.Style.FILL
                                    reusableStrokePaint.isFilterBitmap = true
                                    if (element.strokeGradient != null) {
                                        reusableStrokePaint.shader =
                                            createGradientShader(element.strokeGradient!!, w, h)
                                    } else {
                                        reusableStrokePaint.shader = null
                                        reusableStrokePaint.color = element.strokeColor
                                    }

                                    if (!hasFeather) canvas.save()
                                    reusableRectF.set(layerLeft, layerTop, layerRight, layerBottom)
                                    if (!strokedAlphaMask.isRecycled) {
                                        canvas.drawBitmap(
                                            strokedAlphaMask,
                                            null,
                                            reusableRectF,
                                            reusableStrokePaint
                                        )
                                    }
                                    if (!hasFeather) canvas.restore()
                                }

                                if (!hasFeather) {
                                    canvas.saveLayer(left, top, left + w, top + h, null)
                                }

                                reusableDrawPaint.reset()
                                reusableDrawPaint.isAntiAlias = true
                                reusableDrawPaint.isFilterBitmap = true
                                reusableDrawPaint.colorFilter = colorFilterFor(element.imageFilter, element.filterIntensity)

                                // Draw display-resolution proxy — same visual result, fraction of GPU work
                                reusableRectF.set(left, top, left + w, top + h)
                                if (!displayBmp.isRecycled) {
                                    when (element.imageFilter) {
                                        ImageFilter.SoftBlur -> {
                                            reusableDrawPaint.maskFilter =
                                                BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
                                            canvas.drawBitmap(
                                                displayBmp, null, reusableRectF, reusableDrawPaint
                                            )
                                        }

                                        ImageFilter.Glow -> {
                                            canvas.drawBitmap(
                                                displayBmp, null, reusableRectF, reusableDrawPaint
                                            )
                                            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                                color = Color.argb(180, 255, 255, 200)
                                                maskFilter =
                                                    BlurMaskFilter(25f, BlurMaskFilter.Blur.OUTER)
                                                colorFilter = colorFilterFor(element.imageFilter, element.filterIntensity)
                                            }
                                            canvas.drawBitmap(
                                                displayBmp, null, reusableRectF, glowPaint
                                            )
                                        }

                                        else -> {
                                            canvas.drawBitmap(
                                                displayBmp, null, reusableRectF, reusableDrawPaint
                                            )
                                        }
                                    }
                                }

                                if (element.hasOverlay && element.overlayOpacity > 0) {
                                    val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        alpha = element.overlayOpacity.coerceIn(0, 255)
                                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                                    }
                                    if (element.overlayGradient != null) {
                                        overlayPaint.shader =
                                            createGradientShader(element.overlayGradient!!, w, h)
                                    } else {
                                        overlayPaint.color = element.overlayColor
                                    }
                                    canvas.drawRect(left, top, left + w, top + h, overlayPaint)
                                }

                                // ── Feather: soft edge fade, instant GPU, no pixel loops ────────────
                                if (hasFeather) {
                                    drawFeatherMask(
                                        canvas,
                                        element.id,
                                        if (hasStroke) layerLeft else left,
                                        if (hasStroke) layerTop else top,
                                        if (hasStroke) layerRight else left + w,
                                        if (hasStroke) layerBottom else top + h,
                                        element.featherRadius,
                                        element.featherWidth,
                                        element.featherDirection ?: FeatherDirection.ALL,
                                        element.featherBiasX,
                                        element.featherBiasY
                                    )
                                }
                                if (isElementProcessing(element.id)) {
                                    drawShimmerOverlay(canvas, reusableRectF)
                                }
                                canvas.restore()  // restore saveLayer opened above for feather compositing
                            }
                            if (element.svgDrawable == null && element.bitmap == null) {
                                Log.w("CanvasView", "drawCanvasElements: element ${element.id} (${element.customName ?: element.type}) has null svgDrawable and null bitmap after hydration")
                            }
                        }
                    }

                    if (needsLayer) canvas.restore()
                }
            }
        }

        // ── Draw active snap lines inside artboard clip ──
        if (activeSnapLines.isNotEmpty()) {
            val density = resources.displayMetrics.density
            val totalScale = overallScale * scale
            alignmentPaint.strokeWidth = (0.8f * density) / totalScale
            val dashSize = 10f * density / totalScale
            alignmentPaint.pathEffect = DashPathEffect(floatArrayOf(dashSize, dashSize), 0f)

            activeSnapLines.forEach { snapLine ->
                if (snapLine.isHorizontal) {
                    canvas.drawLine(
                        0f,
                        snapLine.position,
                        canvasWidth.toFloat(),
                        snapLine.position,
                        alignmentPaint
                    )
                } else {
                    canvas.drawLine(
                        snapLine.position,
                        0f,
                        snapLine.position,
                        canvasHeight.toFloat(),
                        alignmentPaint
                    )
                }
            }
        }

        // ── Draw rotation alignment guides inside artboard clip ──
        if (showRotationVerticalGuide || showRotationHorizontalGuide) {
            val density = resources.displayMetrics.density
            val totalScale = overallScale * scale
            alignmentPaint.strokeWidth = (0.8f * density) / totalScale
            val dashSize = 10f * density / totalScale
            alignmentPaint.pathEffect = DashPathEffect(floatArrayOf(dashSize, dashSize), 0f)

            if (showRotationVerticalGuide) {
                canvas.drawLine(
                    canvasWidth / 2f, 0f, canvasWidth / 2f, canvasHeight.toFloat(), alignmentPaint
                )
            }
            if (showRotationHorizontalGuide) {
                canvas.drawLine(
                    0f, canvasHeight / 2f, canvasWidth.toFloat(), canvasHeight / 2f, alignmentPaint
                )
            }
        }

        canvas.restore()
        drawElementOverlays(canvas, showOverlays)
    }

    private fun drawTableElement(canvas: Canvas, element: CanvasElement) {
        val tableData = element.tableData ?: com.webscare.urducanvas.common.canvas.model.TableData.createDefault().also { element.tableData = it }
        val totalW = element.logicalContentWidth.takeIf { it > 0f } ?: (canvasWidth * 0.8f)
        val totalH = element.logicalContentHeight.takeIf { it > 0f } ?: 300f
        if (element.logicalContentWidth <= 0f) element.logicalContentWidth = totalW
        if (element.logicalContentHeight <= 0f) element.logicalContentHeight = totalH

        val cache = (element.tableLayoutCache as? com.webscare.urducanvas.common.canvas.cache.TableLayoutCache)
            ?.takeIf { it.width == totalW && it.height == totalH && it.rows == tableData.rows && it.cols == tableData.cols }
            ?: com.webscare.urducanvas.common.canvas.cache.TableLayoutCache.build(
                data = tableData,
                totalW = totalW,
                totalH = totalH,
                fontLookup = { fontId ->
                    if (fontId == null) null
                    else {
                        val font = localFonts.find { it.id.toString() == fontId }
                        font?.file_path?.takeIf { it.isNotBlank() }?.let { path ->
                            try {
                                if (path.startsWith("fonts/") || !path.startsWith("/")) {
                                    Typeface.createFromAsset(context.assets, path)
                                } else {
                                    Typeface.createFromFile(path)
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }
            ).also { element.tableLayoutCache = it }

        val left = -totalW / 2f
        val top = -totalH / 2f
        val right = totalW / 2f
        val bottom = totalH / 2f

        // Pass 0: Table Outer Shadow
        if (element.hasShadow && element.shadowRadius > 0f) {
            val alpha = ((element.shadowOpacity.coerceIn(0, 255) / 255f) * Color.alpha(element.shadowColor)).toInt()
            val sColor = Color.argb(alpha, Color.red(element.shadowColor), Color.green(element.shadowColor), Color.blue(element.shadowColor))
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = sColor
                maskFilter = BlurMaskFilter(element.shadowRadius.coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)
            }
            val shadowRect = RectF(left + element.shadowDx, top + element.shadowDy, right + element.shadowDx, bottom + element.shadowDy)
            if (tableData.cornerRadius > 0f) {
                canvas.drawRoundRect(shadowRect, tableData.cornerRadius, tableData.cornerRadius, shadowPaint)
            } else {
                canvas.drawRect(shadowRect, shadowPaint)
            }
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = tableData.borderColor
            strokeWidth = tableData.borderWidth
            val bGrad = tableData.borderGradient
            if (bGrad != null && bGrad.colors.isNotEmpty()) {
                val colors = bGrad.colors.toIntArray()
                val positions = bGrad.positions.takeIf { it.size == colors.size }?.toFloatArray()
                shader = LinearGradient(left, top, right, bottom, colors, positions, Shader.TileMode.CLAMP)
            }
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val tableRect = RectF(left, top, right, bottom)
        val density = resources.displayMetrics.density
        val radiusPx = (tableData.cornerRadius * density).coerceAtLeast(0f)
        val clipSave = if (radiusPx > 0f) {
            val count = canvas.save()
            val path = Path().apply { addRoundRect(tableRect, radiusPx, radiusPx, Path.Direction.CW) }
            canvas.clipPath(path)
            count
        } else -1

        // Pass 1: Cell Background Fills
        for (r in 0 until cache.rows) {
            for (c in 0 until cache.cols) {
                val layout = cache.cellLayouts[r][c]
                val bgGrad = layout.style.bgGradient
                if (bgGrad != null && bgGrad.colors.isNotEmpty()) {
                    val colors = bgGrad.colors.toIntArray()
                    val positions = bgGrad.positions.takeIf { it.size == colors.size }?.toFloatArray()
                    fillPaint.shader = LinearGradient(layout.rect.left, layout.rect.top, layout.rect.right, layout.rect.bottom, colors, positions, Shader.TileMode.CLAMP)
                    canvas.drawRect(layout.rect, fillPaint)
                    fillPaint.shader = null
                } else {
                    val bg = layout.style.bgColor ?: Color.WHITE
                    if (bg != Color.TRANSPARENT) {
                        fillPaint.color = bg
                        fillPaint.shader = null
                        canvas.drawRect(layout.rect, fillPaint)
                    }
                }
            }
        }

        when (tableData.borderMode) {
            com.webscare.urducanvas.common.canvas.enums.TableBorderMode.ALL -> {
                for (r in 0..cache.rows) {
                    val y = top + cache.rowHeightsPx.take(r).sum()
                    canvas.drawLine(left, y, right, y, borderPaint)
                }
                for (c in 0..cache.cols) {
                    val x = left + cache.colWidthsPx.take(c).sum()
                    canvas.drawLine(x, top, x, bottom, borderPaint)
                }
                if (radiusPx > 0f) {
                    canvas.drawRoundRect(tableRect, radiusPx, radiusPx, borderPaint)
                }
            }
            com.webscare.urducanvas.common.canvas.enums.TableBorderMode.OUTER -> {
                if (radiusPx > 0f) {
                    canvas.drawRoundRect(tableRect, radiusPx, radiusPx, borderPaint)
                } else {
                    canvas.drawRect(tableRect, borderPaint)
                }
            }
            com.webscare.urducanvas.common.canvas.enums.TableBorderMode.INNER -> {
                for (r in 1 until cache.rows) {
                    val y = top + cache.rowHeightsPx.take(r).sum()
                    canvas.drawLine(left, y, right, y, borderPaint)
                }
                for (c in 1 until cache.cols) {
                    val x = left + cache.colWidthsPx.take(c).sum()
                    canvas.drawLine(x, top, x, bottom, borderPaint)
                }
            }
            com.webscare.urducanvas.common.canvas.enums.TableBorderMode.HORIZONTAL -> {
                for (r in 0..cache.rows) {
                    val y = top + cache.rowHeightsPx.take(r).sum()
                    canvas.drawLine(left, y, right, y, borderPaint)
                }
            }
            com.webscare.urducanvas.common.canvas.enums.TableBorderMode.VERTICAL -> {
                for (c in 0..cache.cols) {
                    val x = left + cache.colWidthsPx.take(c).sum()
                    canvas.drawLine(x, top, x, bottom, borderPaint)
                }
            }
            com.webscare.urducanvas.common.canvas.enums.TableBorderMode.NONE -> {
                // No borders
            }
        }

        // Pass 3: Cell Texts (with strict cell bounds clipping)
        for (r in 0 until cache.rows) {
            for (c in 0 until cache.cols) {
                val layout = cache.cellLayouts[r][c]
                if (layout.lines.isEmpty()) continue

                val rect = layout.rect
                val padH = tableData.paddingH
                val padV = tableData.paddingV

                val cellSave = canvas.save()
                canvas.clipRect(rect)

                val fm = layout.paint.fontMetrics
                val lineSpacingMult = (layout.style.lineSpacing ?: 1.0f).coerceAtLeast(0.5f)
                val lineHeight = (fm.descent - fm.ascent) * lineSpacingMult
                val totalTextH = layout.lines.size * lineHeight

                val startY = when (layout.style.vAlign) {
                    com.webscare.urducanvas.common.canvas.enums.VAlign.TOP -> rect.top + padV - fm.ascent
                    com.webscare.urducanvas.common.canvas.enums.VAlign.BOTTOM -> rect.bottom - padV - totalTextH - fm.ascent
                    else -> rect.centerY() - (totalTextH / 2f) - fm.ascent
                }

                val tGrad = layout.style.textGradient
                if (tGrad != null && tGrad.colors.isNotEmpty()) {
                    val colors = tGrad.colors.toIntArray()
                    val positions = tGrad.positions.takeIf { it.size == colors.size }?.toFloatArray()
                    layout.paint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, colors, positions, Shader.TileMode.CLAMP)
                } else {
                    layout.paint.shader = null
                    layout.paint.color = layout.style.textColor ?: Color.BLACK
                }

                var currentY = startY
                for (line in layout.lines) {
                    val textW = layout.paint.measureText(line)
                    val align = layout.style.hAlign ?: if (tableData.isRTL) TextAlignment.RIGHT else TextAlignment.LEFT
                    val textX = when (align) {
                        TextAlignment.LEFT -> rect.left + padH
                        TextAlignment.CENTER -> rect.centerX() - (textW / 2f)
                        TextAlignment.RIGHT, TextAlignment.JUSTIFY -> rect.right - padH - textW
                    }
                    canvas.drawText(line, textX, currentY, layout.paint)
                    currentY += lineHeight
                }

                canvas.restoreToCount(cellSave)
            }
        }

        // Pass 4: Cell Selection Highlights (Adaptive dark/light overlay)
        if (tableData.selectedCells.isNotEmpty() && (element.isSelected || isTableEditMode)) {
            val fillHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            for (cellPair in tableData.selectedCells) {
                val sr = cellPair.first
                val sc = cellPair.second
                if (sr in 0 until cache.rows && sc in 0 until cache.cols) {
                    val cellLayout = cache.cellLayouts[sr][sc]
                    val cellRect = cellLayout.rect

                    val isDark = when {
                        cellLayout.style.bgGradient != null && cellLayout.style.bgGradient!!.colors.isNotEmpty() -> {
                            val colors = cellLayout.style.bgGradient!!.colors
                            val avgLuminance = colors.map { androidx.core.graphics.ColorUtils.calculateLuminance(it) }.average()
                            avgLuminance < 0.5
                        }
                        cellLayout.style.bgColor != null && cellLayout.style.bgColor != Color.TRANSPARENT -> {
                            androidx.core.graphics.ColorUtils.calculateLuminance(cellLayout.style.bgColor!!) < 0.5
                        }
                        else -> false // Default table cell background is light (white)
                    }

                    fillHighlight.color = if (isDark) {
                        Color.argb(70, 255, 255, 255) // White overlay with lower opacity for dark cells
                    } else {
                        Color.argb(50, 0, 0, 0)       // Black overlay with lower opacity for light cells
                    }
                    canvas.drawRect(cellRect, fillHighlight)
                }
            }
        }

        // Pass 5: Resize Handles (when in table resize mode)
        if (isTableResizeMode && isTableEditMode && element.isSelected) {
            val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.WHITE
            }
            val badgeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.parseColor("#CCCCCC")
                strokeWidth = 1f * density
            }
            val badgeRadius = (desiredIconSizeDp / 2f) * density
            val iconSize = (14f * density).toInt()

            // Col divider handles at center of vertical stroke
            val centerY = (top + bottom) / 2f
            for (c in 1 until cache.cols) {
                val x = left + cache.colWidthsPx.take(c).sum()
                canvas.drawCircle(x, centerY, badgeRadius, badgeBg)
                canvas.drawCircle(x, centerY, badgeRadius, badgeStroke)
                tableColResizeDrawable?.let { d ->
                    d.setBounds((x - iconSize / 2).toInt(), (centerY - iconSize / 2).toInt(), (x + iconSize / 2).toInt(), (centerY + iconSize / 2).toInt())
                    d.draw(canvas)
                }
            }

            // Row divider handles at center of horizontal stroke
            val centerX = (left + right) / 2f
            for (r in 1 until cache.rows) {
                val y = top + cache.rowHeightsPx.take(r).sum()
                canvas.drawCircle(centerX, y, badgeRadius, badgeBg)
                canvas.drawCircle(centerX, y, badgeRadius, badgeStroke)
                tableRowResizeDrawable?.let { d ->
                    d.setBounds((centerX - iconSize / 2).toInt(), (y - iconSize / 2).toInt(), (centerX + iconSize / 2).toInt(), (y + iconSize / 2).toInt())
                    d.draw(canvas)
                }
            }
        }

        if (clipSave >= 0) {
            canvas.restoreToCount(clipSave)
        }
    }

    private fun getTableCellAt(element: CanvasElement, canvasX: Float, canvasY: Float): Pair<Int, Int>? {
        val tableData = element.tableData ?: return null
        val totalW = element.logicalContentWidth.takeIf { it > 0f } ?: (canvasWidth * 0.8f)
        val totalH = element.logicalContentHeight.takeIf { it > 0f } ?: 300f

        val cache = (element.tableLayoutCache as? com.webscare.urducanvas.common.canvas.cache.TableLayoutCache)
            ?.takeIf { it.width == totalW && it.height == totalH && it.rows == tableData.rows && it.cols == tableData.cols }
            ?: com.webscare.urducanvas.common.canvas.cache.TableLayoutCache.build(
                data = tableData,
                totalW = totalW,
                totalH = totalH,
                fontLookup = { null }
            )

        val forwardMatrix = Matrix().apply {
            val fx = if (element.isFlippedX) -1f else 1f
            val fy = if (element.isFlippedY) -1f else 1f
            postScale(element.scale * fx, element.scale * fy)
            postRotate(element.rotation)
            postTranslate(element.x, element.y)
        }
        val inverseMatrix = Matrix()
        if (!forwardMatrix.invert(inverseMatrix)) {
            return null
        }
        val pts = floatArrayOf(canvasX, canvasY)
        inverseMatrix.mapPoints(pts)
        val lx = pts[0]
        val ly = pts[1]

        for (r in 0 until cache.rows) {
            for (c in 0 until cache.cols) {
                if (cache.cellLayouts[r][c].rect.contains(lx, ly)) {
                    return Pair(r, c)
                }
            }
        }
        return null
    }

    private fun getTableDividerAt(element: CanvasElement, canvasX: Float, canvasY: Float): Pair<Boolean, Int>? {
        val tableData = element.tableData ?: return null
        val totalW = element.logicalContentWidth.takeIf { it > 0f } ?: (canvasWidth * 0.8f)
        val totalH = element.logicalContentHeight.takeIf { it > 0f } ?: 300f

        val cache = (element.tableLayoutCache as? com.webscare.urducanvas.common.canvas.cache.TableLayoutCache)
            ?.takeIf { it.width == totalW && it.height == totalH && it.rows == tableData.rows && it.cols == tableData.cols }
            ?: com.webscare.urducanvas.common.canvas.cache.TableLayoutCache.build(
                data = tableData,
                totalW = totalW,
                totalH = totalH,
                fontLookup = { null }
            )

        val forwardMatrix = Matrix().apply {
            val fx = if (element.isFlippedX) -1f else 1f
            val fy = if (element.isFlippedY) -1f else 1f
            postScale(element.scale * fx, element.scale * fy)
            postRotate(element.rotation)
            postTranslate(element.x, element.y)
        }
        val inverseMatrix = Matrix()
        if (!forwardMatrix.invert(inverseMatrix)) {
            return null
        }
        val pts = floatArrayOf(canvasX, canvasY)
        inverseMatrix.mapPoints(pts)
        val lx = pts[0]
        val ly = pts[1]

        val left = -totalW / 2f
        val top = -totalH / 2f
        val right = totalW / 2f
        val bottom = totalH / 2f

        val hitThreshold = 32f * resources.displayMetrics.density
        val centerY = (top + bottom) / 2f
        val centerX = (left + right) / 2f
        val badgeHitRadius = 24f * resources.displayMetrics.density

        // 1. Check direct badge taps first
        for (c in 1 until cache.cols) {
            val divX = left + cache.colWidthsPx.take(c).sum()
            val dist = kotlin.math.hypot((lx - divX).toDouble(), (ly - centerY).toDouble()).toFloat()
            if (dist <= badgeHitRadius) {
                return Pair(true, c)
            }
        }
        for (r in 1 until cache.rows) {
            val divY = top + cache.rowHeightsPx.take(r).sum()
            val dist = kotlin.math.hypot((lx - centerX).toDouble(), (ly - divY).toDouble()).toFloat()
            if (dist <= badgeHitRadius) {
                return Pair(false, r)
            }
        }

        // 2. Check full line divider borders
        for (c in 1 until cache.cols) {
            val divX = left + cache.colWidthsPx.take(c).sum()
            if (Math.abs(lx - divX) <= hitThreshold && ly >= top - hitThreshold && ly <= bottom + hitThreshold) {
                return Pair(true, c)
            }
        }

        for (r in 1 until cache.rows) {
            val divY = top + cache.rowHeightsPx.take(r).sum()
            if (Math.abs(ly - divY) <= hitThreshold && lx >= left - hitThreshold && lx <= right + hitThreshold) {
                return Pair(false, r)
            }
        }

        return null
    }

    private fun drawColorPickerOverlay(canvas: Canvas) {
        val halfIcon = desiredPickerIconSizePx
        val bmp = colorPickerBitmap
        val (screenX, screenY) = canvasToScreen(pickerX, pickerY)

        if (bmp != null && !bmp.isRecycled) {
            val scaleX = bmp.width.toFloat() / canvasWidth.toFloat()
            val scaleY = bmp.height.toFloat() / canvasHeight.toFloat()
            val px = (pickerX * scaleX).roundToInt().coerceIn(0, bmp.width - 1)
            val py = (pickerY * scaleY).roundToInt().coerceIn(0, bmp.height - 1)
            val pixelColor = bmp.getPixel(px, py)
            val dark = isColorDark(pixelColor)

            // Outer shadow bubble ring
            canvas.drawCircle(
                screenX, screenY - halfIcon * 3, halfIcon + 22f, Paint().apply {
                    color = Color.argb(100, 0, 0, 0)
                    style = Paint.Style.FILL
                    isAntiAlias = true
                })

            // Magnifying color bubble
            canvas.drawCircle(
                screenX, screenY - halfIcon * 3, halfIcon + 20f, Paint().apply {
                    color = pixelColor
                    style = Paint.Style.FILL
                    isAntiAlias = true
                })

            // Magnifying bubble border
            canvas.drawCircle(
                screenX, screenY - halfIcon * 3, halfIcon + 20f, Paint().apply {
                    color = if (dark) Color.WHITE else Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = 5f
                    isAntiAlias = true
                })

            // Crosshair outer contrast circle
            canvas.drawCircle(
                screenX, screenY, halfIcon / 3 + 2f, Paint().apply {
                    color = if (dark) Color.WHITE else Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                    isAntiAlias = true
                })

            // Crosshair inner fill circle
            canvas.drawCircle(
                screenX, screenY, halfIcon / 3, Paint().apply {
                    color = pixelColor
                    style = Paint.Style.FILL
                    isAntiAlias = true
                })
        } else {
            // Crosshair cursor drawn while bitmap is preparing
            canvas.drawCircle(
                screenX, screenY, halfIcon / 3, Paint().apply {
                    color = Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                    isAntiAlias = true
                })
        }
    }

    private fun drawElementOverlays(canvas: Canvas, showOverlays: Boolean = true) {

        if (showOverlays && selectedElements.isNotEmpty()) {
            val desiredScreenStrokeWidth = 2f
            val dashLengthOnScreen = 10f
            val gapLengthOnScreen = 10f

            val localSpaceStrokeWidth = desiredScreenStrokeWidth / (scale * overallScale)
            val localDashLength = dashLengthOnScreen / (scale * overallScale)
            val localGapLength = gapLengthOnScreen / (scale * overallScale)

            reusableBoxPaint.color = Color.GRAY
            reusableBoxPaint.style = Paint.Style.STROKE
            reusableBoxPaint.pathEffect =
                DashPathEffect(floatArrayOf(localDashLength, localGapLength), 0f)
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
                            element.scale * if (element.isFlippedY) -1f else 1f
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
                    cy + textBounds.height() / 2f + padding
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
                        corners[2], corners[3]
                    )

                    iconMap["edit"] = Pair(
                        corners[0], corners[1]
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
                        corners[4], corners[5]
                    )
                    if (element.type == ElementType.SHAPE || element.type == ElementType.TEXT || element.type == ElementType.TABLE) {
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
                            position.second + localIconDrawHeight / 2f
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
                                            element.scale * if (element.isFlippedY) -1f else 1f
                                        )
                                        postRotate(element.rotation)
                                        postTranslate(element.x, element.y)
                                    }

                                    val topCenter = floatArrayOf(bounds.centerX(), bounds.top)
                                    val fixedHandleLengthPx = 80f
                                    val rotateIcon = floatArrayOf(
                                        bounds.centerX(),
                                        bounds.top - (fixedHandleLengthPx / (scale * overallScale))
                                    )

                                    matrix.mapPoints(topCenter)
                                    matrix.mapPoints(rotateIcon)

                                    topCenter to rotateIcon

                                } else {

                                    // ===== SNAP CLEANLY TO SCREEN TOP =====
                                    val corners = element.getRotatedCorners()

                                    val yValues = listOf(
                                        corners[1], corners[3], corners[5], corners[7]
                                    )
                                    val topY = yValues.minOrNull() ?: 0f

                                    val xValues = listOf(
                                        corners[0], corners[2], corners[4], corners[6]
                                    )
                                    val leftX = xValues.minOrNull() ?: 0f
                                    val rightX = xValues.maxOrNull() ?: 0f
                                    val centerX = (leftX + rightX) / 2f

                                    val topCenter = floatArrayOf(centerX, topY)

                                    val fixedHandleLengthPx = 80f
                                    val rotateIcon = floatArrayOf(
                                        centerX,
                                        topY - (fixedHandleLengthPx / (scale * overallScale))
                                    )

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
                                val rotateIcon = floatArrayOf(
                                    pivotX, topY - (fixedHandleLengthPx / (scale * overallScale))
                                )

                                topCenter to rotateIcon
                            }

                            rotateLinePaint.strokeWidth = 4f / (scale * overallScale)
                            rotateLinePaint.pathEffect = DashPathEffect(
                                floatArrayOf(
                                    10f / (scale * overallScale), 10f / (scale * overallScale)
                                ), 0f
                            )
                            val linePaint = rotateLinePaint

                            canvas.drawLine(
                                localTopCenter[0],
                                localTopCenter[1],
                                localRotateIcon[0],
                                localRotateIcon[1],
                                linePaint
                            )

                            dstRect = RectF(
                                localRotateIcon[0] - localIconDrawWidth / 2f,
                                localRotateIcon[1] - localIconDrawHeight / 2f,
                                localRotateIcon[0] + localIconDrawWidth / 2f,
                                localRotateIcon[1] + localIconDrawHeight / 2f
                            )
                        }

                        lastDrawnIconRect[iconName] = dstRect
                        bmp.setBounds(
                            dstRect.left.toInt(),
                            dstRect.top.toInt(),
                            dstRect.right.toInt(),
                            dstRect.bottom.toInt()
                        )
                        bmp.draw(canvas)
                    }
                }
            }
        }

    }

    private fun drawShapeElement(canvas: Canvas, element: CanvasElement) {

        val localHalfW = element.logicalContentWidth / 2f
        val localHalfH = element.logicalContentHeight / 2f
        val localRect = RectF(-localHalfW, -localHalfH, localHalfW, localHalfH)

        val shapeType = element.shapeType ?: ShapeType.RECTANGLE
        val cornerRadius = if (element.shapeHasCorner) {
            element.shapeCornerRadius
        } else {
            0f
        }

        // Path is built WITHOUT corner radius baked in (except for RECT/ROUNDED_RECT).
        // Rounding for all other shapes is applied at draw-time via CornerPathEffect
        // inside ShapeRenderUtils.withCornerEffect().
        val path = ShapeRenderUtils.buildShapePath(shapeType, localRect, cornerRadius)

        // -------------------------------------------------
        // 1️⃣ SHADOW (DRAW FIRST - BEHIND EVERYTHING)
        // -------------------------------------------------

        if (element.hasShadow && element.shadowOpacity > 0) {

            val shadowColor = Color.argb(
                element.shadowOpacity,
                Color.red(element.shadowColor),
                Color.green(element.shadowColor),
                Color.blue(element.shadowColor)
            )

            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = shadowColor
                maskFilter = BlurMaskFilter(
                    element.shadowRadius.coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL
                )
            }

            canvas.save()
            canvas.translate(element.shadowDx, element.shadowDy)
            ShapeRenderUtils.withCornerEffect(shadowPaint, cornerRadius, shapeType) {
                canvas.drawPath(path, shadowPaint)
            }
            canvas.restore()
        }

        // -------------------------------------------------
        // 2️⃣ SHAPE FILL
        // -------------------------------------------------

        if (element.shapeHasFill) {

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {

                style = Paint.Style.FILL

                if (element.shapeFillGradient != null) {

                    shader = createGradientShader(
                        element.shapeFillGradient!!, localRect.width(), localRect.height()
                    )

                } else {

                    color = element.shapeFillColor ?: Color.TRANSPARENT
                }

                alpha = element.paintAlpha
            }

            ShapeRenderUtils.withCornerEffect(fillPaint, cornerRadius, shapeType) {
                canvas.drawPath(path, fillPaint)
            }
        }

        // -------------------------------------------------
        // 3️⃣ IMAGE + PERFECT MASK
        // -------------------------------------------------

        element.bitmap?.let { bmp ->

            if (bmp.isRecycled) return

            canvas.withSave {

                val finalBitmap: Bitmap =
                    if (element.hasLight || element.hasColor || element.hasDetail || element.hasBlur) {
                        resolveAdjustedBitmapAsync(element, bmp)
                    } else {
                        bmp
                    }

                val srcW = finalBitmap.width.toFloat()
                val srcH = finalBitmap.height.toFloat()

                val scaleX = localRect.width() / srcW
                val scaleY = localRect.height() / srcH

                val baseScale = when (element.imageFitMode) {
                    "contain" -> minOf(scaleX, scaleY)
                    "stretch" -> scaleX
                    else -> maxOf(scaleX, scaleY)
                }

                val finalScale = baseScale * (element.imageScale.takeIf { it != 0f } ?: 1f)

                val drawW = srcW * finalScale
                val drawH = srcH * finalScale

                val dx = localRect.left + (localRect.width() - drawW) / 2f + element.imagePanX
                val dy = localRect.top + (localRect.height() - drawH) / 2f + element.imagePanY

                val matrix = Matrix().apply {
                    postScale(finalScale, finalScale)
                    postTranslate(dx, dy)
                }

                // ---------- MASK LAYER ----------
                canvas.saveLayer(localRect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    alpha = element.paintAlpha  // opacity applies to the entire masked image
                })

                if (element.shapeHasFill) {
                    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        if (element.shapeFillGradient != null) {
                            shader = createGradientShader(
                                element.shapeFillGradient!!, localRect.width(), localRect.height()
                            )
                        } else {
                            color = element.shapeFillColor ?: Color.TRANSPARENT
                        }
                    }
                    ShapeRenderUtils.withCornerEffect(fillPaint, cornerRadius, shapeType) {
                        canvas.drawPath(path, fillPaint)
                    }
                } else {
                    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = Color.WHITE
                    }

                    // Apply corner rounding to the mask so the image is clipped with rounded corners too
                    ShapeRenderUtils.withCornerEffect(maskPaint, cornerRadius, shapeType) {
                        canvas.drawPath(path, maskPaint)
                    }
                }

                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = colorFilterFor(element.imageFilter, element.filterIntensity)
                    isFilterBitmap = true
                    // No alpha here — handled by saveLayer above
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                }

                // ---------- IMAGE + FILTER ----------
                when (element.imageFilter) {

                    ImageFilter.SoftBlur -> {

                        paint.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)

                        canvas.drawBitmap(finalBitmap, matrix, paint)
                    }

                    ImageFilter.Glow -> {

                        canvas.drawBitmap(finalBitmap, matrix, paint)

                        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.argb(180, 255, 255, 200)
                            maskFilter = BlurMaskFilter(
                                25f, BlurMaskFilter.Blur.OUTER
                            )
                        }

                        canvas.drawBitmap(finalBitmap, matrix, glowPaint)
                    }

                    else -> {
                        canvas.drawBitmap(finalBitmap, matrix, paint)
                    }
                }

                paint.xfermode = null

                // ---------- OVERLAY ----------
                if (element.hasOverlay && element.overlayOpacity > 0) {

                    val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {

                        alpha = element.overlayOpacity.coerceIn(0, 255)

                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)

                        if (element.overlayGradient != null) {

                            shader = createGradientShader(
                                element.overlayGradient!!, localRect.width(), localRect.height()
                            )

                        } else {

                            color = element.overlayColor
                        }
                    }

                    canvas.drawRect(
                        localRect.left,
                        localRect.top,
                        localRect.right,
                        localRect.bottom,
                        overlayPaint
                    )

                    overlayPaint.xfermode = null
                }

                if (element.hasFeather && element.featherRadius > 0f && element.shapeHasStroke) {
                    val scaleSafe = element.scale.takeIf { it > 0f } ?: 1f
                    val visualStrokeWidth = (element.shapeStrokeWidth ?: 1f) / scaleSafe

                    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = visualStrokeWidth

                        if (element.shapeStrokeGradient != null) {
                            shader = createGradientShader(
                                element.shapeStrokeGradient!!, localRect.width(), localRect.height()
                            )
                        } else {
                            color = element.shapeStrokeColor ?: Color.BLACK
                        }

                        alpha = element.paintAlpha
                        strokeJoin = Paint.Join.ROUND
                        strokeCap = Paint.Cap.ROUND
                    }

                    ShapeRenderUtils.withCornerEffect(strokePaint, cornerRadius, shapeType) {
                        canvas.drawPath(path, strokePaint)
                    }
                }

                // ── Feather: soft edge fade drawn on canvas — instant, no pixel loops ─
                if (element.hasFeather && element.featherRadius > 0f) {
                    drawFeatherMask(
                        canvas,
                        element.id,
                        localRect.left,
                        localRect.top,
                        localRect.right,
                        localRect.bottom,
                        element.featherRadius,
                        element.featherWidth,
                        element.featherDirection ?: FeatherDirection.ALL,
                        element.featherBiasX,
                        element.featherBiasY
                    )
                }

                canvas.restore()
            }
        }

        // -------------------------------------------------
        // 4️⃣ STROKE (TOP MOST)
        // -------------------------------------------------

        if (element.shapeHasStroke && !(element.hasFeather && element.featherRadius > 0f)) {

            val scaleSafe = element.scale.takeIf { it > 0f } ?: 1f
            val visualStrokeWidth = (element.shapeStrokeWidth ?: 1f) / scaleSafe

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {

                style = Paint.Style.STROKE
                strokeWidth = visualStrokeWidth

                if (element.shapeStrokeGradient != null) {
                    shader = createGradientShader(
                        element.shapeStrokeGradient!!, localRect.width(), localRect.height()
                    )
                } else {
                    color = element.shapeStrokeColor ?: Color.BLACK
                }

                alpha = element.paintAlpha
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }

            ShapeRenderUtils.withCornerEffect(strokePaint, cornerRadius, shapeType) {
                canvas.drawPath(path, strokePaint)
            }
        }
    }

    private fun drawDrawElement(
        canvas: Canvas, element: CanvasElement
    ) {
        element.drawStrokes?.forEach { stroke ->
            com.webscare.urducanvas.common.utils.BrushRenderUtils.drawSingleStroke(
                canvas, stroke, element.paintAlpha
            )
        }
    }

    private fun drawBackgroundElement(
        canvas: Canvas, e: CanvasElement
    ) {
        val w = canvasWidth.toFloat()
        val h = canvasHeight.toFloat()

        reusableBgPaint.reset()
        reusableBgPaint.alpha = e.paintAlpha
        reusableBgPaint.style = Paint.Style.FILL
        reusableBgPaint.isAntiAlias = true

        ensureElementHydrated(e)

        if (e.bitmap == null || e.bitmap?.isRecycled == true) {
            Log.w("CanvasView", "drawBackgroundElement: background bitmap is null or recycled for element ${e.id}")
        }

        e.bitmap?.let { bmp ->
            if (bmp.isRecycled) return@let

            val baseScale = max(w / bmp.width, h / bmp.height)
            val totalScale = baseScale * e.scale

            val sw = bmp.width * totalScale
            val sh = bmp.height * totalScale

            if (!allowFreeDrag) {
                val theta = Math.toRadians(e.rotation.toDouble())
                val cosA = abs(cos(theta))
                val sinA = abs(sin(theta))

                val halfW = (sw / 2) * cosA + (sh / 2) * sinA
                val halfH = (sw / 2) * sinA + (sh / 2) * cosA

                val xMin = halfW
                val xMax = w - halfW
                val yMax = h - halfH

                if (xMax >= xMin && yMax >= halfH) {
                    e.x = e.x.coerceIn(xMin.toFloat(), xMax.toFloat())
                    e.y = e.y.coerceIn(halfH.toFloat(), yMax.toFloat())
                } else {
                    allowFreeDrag = true
                }
            }

            val left = e.x - sw / 2f
            val top = e.y - sh / 2f

            // ── Async adjustment: never block onDraw with full-res processing ──
            // If dirty, schedule a background job and draw the raw bitmap this frame.
            val adjustedBackground: Bitmap = resolveAdjustedBitmapAsync(e, bmp)

            // ── Display-resolution downscale ─────────────────────────────────
            // The source bitmap may be 12 MP. We only need pixels for the actual
            // on-screen footprint — downscale once, cache, and reuse every frame.
            // Full resolution is always preserved in element.bitmap for export.
            val onScreenW =
                (sw * scale * overallScale).toInt().coerceIn(1, adjustedBackground.width)
            val onScreenH =
                (sh * scale * overallScale).toInt().coerceIn(1, adjustedBackground.height)
            val displayBmp =
                getOrBuildDisplayBitmap(e.id + "_bg", adjustedBackground, onScreenW, onScreenH)

            canvas.withTranslation(left, top) {
                scale(totalScale, totalScale)
                rotate(e.rotation, bmp.width / 2f, bmp.height / 2f)

                if (bmp.isRecycled) return@withTranslation

                reusableBgPaint.colorFilter = colorFilterFor(e.imageFilter, e.filterIntensity)
                reusableBgPaint.maskFilter = null

                // We draw displayBmp but we must draw it at the source bitmap's
                // coordinate space (because the canvas is already scaled by totalScale).
                // So map displayBmp back to the full-size drawing rect.
                val dstRect =
                    reusableRectF.also { it.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()) }

                when (e.imageFilter) {
                    ImageFilter.SoftBlur -> {
                        reusableBgPaint.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
                        if (!displayBmp.isRecycled) drawBitmap(
                            displayBmp, null, dstRect, reusableBgPaint
                        )
                    }

                    ImageFilter.Glow -> {
                        if (!displayBmp.isRecycled) drawBitmap(
                            displayBmp, null, dstRect, reusableBgPaint
                        )
                        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.argb(180, 255, 255, 200)
                            maskFilter = BlurMaskFilter(25f, BlurMaskFilter.Blur.OUTER)
                        }
                        if (!displayBmp.isRecycled) drawBitmap(displayBmp, null, dstRect, glowPaint)
                    }

                    else -> {
                        if (!displayBmp.isRecycled) drawBitmap(
                            displayBmp, null, dstRect, reusableBgPaint
                        )
                    }
                }
                if (e.hasOverlay && e.overlayOpacity > 0) {
                    val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        alpha = e.overlayOpacity.coerceIn(0, 255)
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                    }

                    if (e.overlayGradient != null) {
                        overlayPaint.shader = createGradientShader(
                            e.overlayGradient!!, bmp.width.toFloat(), bmp.height.toFloat()
                        )
                    } else {
                        overlayPaint.color = e.overlayColor
                    }
                    drawRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), overlayPaint)
                }
                if (isElementProcessing(e.id)) {
                    drawShimmerOverlay(this, dstRect)
                }
            }
            reusableBgPaint.xfermode = drawWithBlend(e)
            return
        }

        val left = e.x - w / 2f
        val top = e.y - h / 2f
        val pivotX = w / 2f
        val pivotY = h / 2f

        // 2) else if there's a gradient -> stretch it across the full canvas
        if (e.hasOverlay) {
            canvas.withTranslation(left, top) {
                scale(e.scale, e.scale, pivotX, pivotY)
                rotate(e.rotation, pivotX, pivotY)

                val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    alpha = e.overlayOpacity.coerceIn(0, 255)
                }
                if (e.overlayGradient != null) {
                    overlayPaint.shader = createGradientShader(e.overlayGradient!!, w, h)
                } else {
                    overlayPaint.color = e.overlayColor
                }
                reusableBgPaint.alpha = e.paintAlpha
                drawRect(0f, 0f, w, h, overlayPaint)
                reusableBgPaint.shader = null
            }
            return
        } else {
            canvas.withTranslation(left, top) {
                scale(e.scale, e.scale, pivotX, pivotY)
                rotate(e.rotation, pivotX, pivotY)

                if (e.fillGradient != null) {
                    reusableBgPaint.shader = createBackgroundGradientShader(
                        e.fillGradient!!, w, h
                    )
                } else {
                    reusableBgPaint.shader = null
                    reusableBgPaint.color = e.backgroundColor
                }

                reusableBgPaint.alpha = e.paintAlpha
                drawRect(0f, 0f, w, h, reusableBgPaint)
            }
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
    private fun drawFeatherMask(
        canvas: Canvas,
        elementId: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        featherRadius: Float,
        featherWidth: Float,
        direction: FeatherDirection = FeatherDirection.ALL,
        biasX: Float = 0f,
        biasY: Float = 0f
    ) {
        if (featherRadius <= 0f) return
        val w = right - left
        val h = bottom - top
        if (w <= 0f || h <= 0f) return

        val maskW = 128
        val maskH = 128

        val featherFp = Objects.hash(elementId, featherRadius, featherWidth, direction, (biasX * 1000).toInt(), (biasY * 1000).toInt())
        val cached = featherBitmapCache[elementId]
        val maskBmp: Bitmap =
            if (cached != null && cached.fingerprint == featherFp && !cached.bitmap.isRecycled) {
                cached.bitmap
            } else {
                cached?.bitmap?.recycle()

                val fraction = sqrt((featherRadius / 100.0)).toFloat().coerceIn(0f, 1f)
                val baseBandX = (maskW / 2f) * fraction
                val baseBandY = (maskH / 2f) * fraction
                val exponent = 1.0 + ((100f - featherWidth) / 100.0) * 7.0

                val bx = biasX.coerceIn(-1f, 1f)
                val by = biasY.coerceIn(-1f, 1f)
                val isBiasActive = (bx != 0f || by != 0f)

                // Per-edge band widths according to continuous 2D bias vector:
                // When snapped to cardinal axis -> 1 side ONLY.
                // When off-axis / diagonal -> AT LEAST 2 sides fade.
                val (bandTop, bandBottom, bandLeft, bandRight) = if (isBiasActive) {
                    val isPureTop = (bx == 0f && by < 0f)
                    val isPureBottom = (bx == 0f && by > 0f)
                    val isPureLeft = (bx < 0f && by == 0f)
                    val isPureRight = (bx > 0f && by == 0f)

                    when {
                        isPureTop -> {
                            val mag = kotlin.math.abs(by)
                            listOf(baseBandY * (mag * 1.5f + 0.5f), 0f, 0f, 0f)
                        }
                        isPureBottom -> {
                            val mag = kotlin.math.abs(by)
                            listOf(0f, baseBandY * (mag * 1.5f + 0.5f), 0f, 0f)
                        }
                        isPureLeft -> {
                            val mag = kotlin.math.abs(bx)
                            listOf(0f, 0f, baseBandX * (mag * 1.5f + 0.5f), 0f)
                        }
                        isPureRight -> {
                            val mag = kotlin.math.abs(bx)
                            listOf(0f, 0f, 0f, baseBandX * (mag * 1.5f + 0.5f))
                        }
                        else -> {
                            val wTop = if (by < 0f) baseBandY * (kotlin.math.abs(by) * 1.5f + 0.3f) else 0f
                            val wBottom = if (by > 0f) baseBandY * (kotlin.math.abs(by) * 1.5f + 0.3f) else 0f
                            val wLeft = if (bx < 0f) baseBandX * (kotlin.math.abs(bx) * 1.5f + 0.3f) else 0f
                            val wRight = if (bx > 0f) baseBandX * (kotlin.math.abs(bx) * 1.5f + 0.3f) else 0f
                            listOf(wTop, wBottom, wLeft, wRight)
                        }
                    }
                } else {
                    listOf(baseBandY, baseBandY, baseBandX, baseBandX)
                }

                // Which edges are active (require band width > 0.5f to fade, otherwise edge stays 100% solid)
                val doTop = (direction == FeatherDirection.ALL || direction == FeatherDirection.TOP || isBiasActive) && bandTop > 0.5f
                val doBottom = (direction == FeatherDirection.ALL || direction == FeatherDirection.BOTTOM || isBiasActive) && bandBottom > 0.5f
                val doLeft = (direction == FeatherDirection.ALL || direction == FeatherDirection.LEFT || isBiasActive) && bandLeft > 0.5f
                val doRight = (direction == FeatherDirection.ALL || direction == FeatherDirection.RIGHT || isBiasActive) && bandRight > 0.5f

                val pixels = IntArray(maskW * maskH)
                for (py in 0 until maskH) {
                    val topRamp = if (doTop) smoothStep((py / bandTop).coerceIn(0f, 1f), exponent) else 1f
                    val botRamp = if (doBottom) smoothStep(((maskH - 1 - py) / bandBottom).coerceIn(0f, 1f), exponent) else 1f
                    val vRamp = topRamp * botRamp

                    for (px in 0 until maskW) {
                        val leftRamp = if (doLeft) smoothStep((px / bandLeft).coerceIn(0f, 1f), exponent) else 1f
                        val rightRamp = if (doRight) smoothStep(((maskW - 1 - px) / bandRight).coerceIn(0f, 1f), exponent) else 1f
                        val alpha = (vRamp * leftRamp * rightRamp * 255f).toInt().coerceIn(0, 255)
                        pixels[py * maskW + px] = Color.argb(alpha, 0, 0, 0)
                    }
                }

                val newBmp = Bitmap.createBitmap(maskW, maskH, Bitmap.Config.ARGB_8888)
                newBmp.setPixels(pixels, 0, maskW, 0, 0, maskW, maskH)
                FeatherCacheEntry(newBmp, featherFp).also {
                    featherBitmapCache[elementId] = it
                }.bitmap
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

    private fun drawShimmerOverlay(canvas: Canvas, rectF: RectF) {
        val width = rectF.width()
        val height = rectF.height()
        if (width <= 0f || height <= 0f) return

        val time = System.currentTimeMillis() % 1600L
        val progress = time / 1600f

        val startX = rectF.left + (progress * 2.2f - 0.6f) * width
        val startY = rectF.top
        val endX = startX + width * 0.45f
        val endY = rectF.bottom

        shimmerPaint.shader = LinearGradient(
            startX, startY, endX, endY,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(95, 255, 255, 255),
                Color.argb(45, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.35f, 0.65f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rectF, shimmerPaint)
        shimmerPaint.shader = null
        postInvalidateOnAnimation()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: resolve the adjusted bitmap for display, scheduling async work
    // when the adjustment cache is dirty instead of blocking onDraw.
    // On the frame the adjustment is requested we fall back to the raw bitmap
    // (or the stale cache) — a one-frame visual glitch is far better than jank.
    // ─────────────────────────────────────────────────────────────────────────
    private fun resolveAdjustedBitmapAsync(element: CanvasElement, rawBitmap: Bitmap): Bitmap {
        val hasAnyAdjustment =
            element.hasLight || element.hasColor || element.hasDetail || element.hasBlur
        if (!hasAnyAdjustment) {
            // No adjustments needed — raw bitmap is the final bitmap
            return rawBitmap
        }

        val cached = element.cachedAdjustedBitmap
        if (!element.isAdjustmentDirty && cached != null && !cached.isRecycled) {
            return cached  // ✅ clean cache hit — zero work this frame
        }

        // Dirty or missing — schedule background processing (once per element)
        val existing = pendingAdjustmentJobs[element.id]
        if (existing == null || !existing.isActive) {
            onProcessingStateChanged?.invoke(true)
            val targetAdjustments = element.adjustments.copy()
            val job = adjustmentScope.launch {
                // Prefer the element's context; fall back to the View's context
                // so adjustments (especially RenderScript blur) never silently skip.
                val ctx = element.context ?: context ?: return@launch
                val result = ImageAdjustmentHelper.applyAllAdjustments(
                    ctx, rawBitmap, element
                )
                withContext(Dispatchers.Main) {
                    element.cachedAdjustedBitmap = result
                    // Only mark clean if adjustments haven't changed while we were processing
                    if (element.adjustments == targetAdjustments) {
                        element.isAdjustmentDirty = false
                    }
                    // Invalidate display cache so next frame resamples from the fresh adjusted bitmap
                    displayBitmapCache.remove(element.id)
                    displayBitmapCache.remove(element.id + "_bg")
                    pendingAdjustmentJobs.remove(element.id)
                    if (pendingAdjustmentJobs.isEmpty()) {
                        onProcessingStateChanged?.invoke(false)
                    }
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
    private fun getOrBuildDisplayBitmap(
        cacheKey: String, source: Bitmap, targetW: Int, targetH: Int
    ): Bitmap {
        if (isExportRendering) return source

        // If source IS already at or below display size, use it directly (no copy needed)
        if (source.width <= targetW && source.height <= targetH) return source

        val cached = displayBitmapCache[cacheKey]
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

            if (cached.srcWidth == source.width && cached.srcHeight == source.height && cached.dstWidth == discreteW && cached.dstHeight == discreteH) {
                return cached.bitmap  // ✅ cache hit
            }
        }

        // For the first frame of gesture or when no cache exists, use rounded/discretized size.
        val discreteW = (((targetW + 127) / 128) * 128).coerceAtMost(source.width).coerceAtLeast(1)
        val discreteH = (((targetH + 127) / 128) * 128).coerceAtMost(source.height).coerceAtLeast(1)

        // Build a high-quality downscale using FILTER_BITMAP_FLAG (bilinear)
        val scaled = Bitmap.createScaledBitmap(source, discreteW, discreteH, true)
        if (scaled === source) return source

        // Do NOT recycle the old cached bitmap immediately — the export pipeline runs on a
        // background thread and may hold a reference to it mid-draw. Let it become unreachable
        // and be GC'd rather than risk a "Canvas: trying to use a recycled bitmap" crash.
        displayBitmapCache[cacheKey] = DisplayCacheEntry(
            bitmap = scaled,
            srcWidth = source.width,
            srcHeight = source.height,
            dstWidth = discreteW,
            dstHeight = discreteH
        )
        return scaled
    }

    private fun createGradientShader(
        gradientItem: GradientItem,
        width: Float,
        height: Float,
        translateX: Float = 0f,
        translateY: Float = 0f
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
                    -dx, -dy, dx, dy, colors, positions, Shader.TileMode.CLAMP
                )
            }

            GradientType.RADIAL -> {
                val radius =
                    min(width, height) / 2f * gradientItem.radialRadiusFactor * gradientItem.scale

                rawShader = RadialGradient(
                    0f, 0f, radius, colors, positions, Shader.TileMode.CLAMP
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

    private fun drawTextElement(
        canvas: Canvas, element: CanvasElement
    ) {
        if (element.paintAlpha == 0) return

        val maxCanvasW = if (canvasWidth > 0) canvasWidth * 0.85f else 0f
        val lines = element.getVisualLines(maxCanvasWidth = maxCanvasW)
        val fm = try {
            element.paint.fontMetrics
        } catch (e: Exception) {
            Paint.FontMetrics() // safe default: all zeros, text won't draw but won't crash
        }
        val lineHeight = (fm.descent - fm.ascent) * element.lineSpacing
        val totalHeight = lineHeight * lines.size

        // ----- DRAW LABEL -----
        if (element.hasLabel) {
            val maxLineWidth = try {
                lines.maxOf { element.paint.measureText(it) }
            } catch (e: Exception) {
                0f
            }

            val labelPaddingY = element.getLabelPaddingY()
            val labelPaddingX = element.getLabelPaddingX()

            val left = -maxLineWidth / 2f - labelPaddingX
            val top = -totalHeight / 2f - labelPaddingY
            val right = maxLineWidth / 2f + labelPaddingX
            val bottom = totalHeight / 2f + labelPaddingY

            val labelRect = RectF(left, top, right, bottom)
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            if (element.labelGradient != null) {
                val rectW = labelRect.width()
                val rectH = labelRect.height()
                labelPaint.shader = createGradientShader(
                    gradientItem = element.labelGradient!!, width = rectW, height = rectH
                )
            } else {
                labelPaint.shader = null
                labelPaint.color = element.labelColor
            }

            val prevAlpha = labelPaint.alpha
            labelPaint.alpha = element.paintAlpha

            // ── COMPOSITE LAYER 1: Folded Dark Ribbon Flaps (Drawn Behind Main Banner) ──
            if (element.hasFoldedRibbonFlaps && element.labelSecondaryColor != null) {
                val flapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = element.labelSecondaryColor!!
                    style = Paint.Style.FILL
                }
                val flapWidth = labelRect.height() * 0.35f
                val flapPath = Path().apply {
                    // Left flap fold triangle
                    moveTo(labelRect.left, labelRect.bottom)
                    lineTo(labelRect.left - flapWidth, labelRect.bottom + flapWidth * 0.5f)
                    lineTo(labelRect.left + flapWidth, labelRect.bottom)
                    close()
                    // Right flap fold triangle
                    moveTo(labelRect.right, labelRect.top)
                    lineTo(labelRect.right + flapWidth, labelRect.top - flapWidth * 0.5f)
                    lineTo(labelRect.right - flapWidth, labelRect.top)
                    close()
                }
                canvas.drawPath(flapPath, flapPaint)
            }

            // ── COMPOSITE LAYER 2: Main Shape Fill / Stroke ──
            when (element.labelShape) {
                LabelShape.RECTANGLE_FILL -> canvas.drawRect(
                    labelRect, labelPaint
                )

                LabelShape.RECTANGLE_STROKE -> {
                    labelPaint.style = Paint.Style.STROKE
                    labelPaint.strokeWidth = 4f
                    canvas.drawRect(labelRect, labelPaint)
                }

                LabelShape.OVAL_FILL -> canvas.drawOval(
                    labelRect, labelPaint
                )

                LabelShape.OVAL_STROKE -> {
                    labelPaint.style = Paint.Style.STROKE
                    labelPaint.strokeWidth = 4f
                    canvas.drawOval(labelRect, labelPaint)
                }

                LabelShape.CIRCLE_FILL -> {
                    val r = min(labelRect.width(), labelRect.height()) / 2f
                    canvas.drawCircle(labelRect.centerX(), labelRect.centerY(), r, labelPaint)
                }

                LabelShape.CIRCLE_STROKE -> {
                    labelPaint.style = Paint.Style.STROKE
                    labelPaint.strokeWidth = 4f
                    val r = min(labelRect.width(), labelRect.height()) / 2f
                    canvas.drawCircle(labelRect.centerX(), labelRect.centerY(), r, labelPaint)
                }

                LabelShape.ROUNDED_RECTANGLE_FILL -> {
                    canvas.drawRoundRect(labelRect, 24f, 24f, labelPaint)
                }

                LabelShape.ROUNDED_RECTANGLE_STROKE -> {
                    labelPaint.style = Paint.Style.STROKE
                    labelPaint.strokeWidth = 4f
                    canvas.drawRoundRect(labelRect, 24f, 24f, labelPaint)
                }

                LabelShape.CAPSULE_FILL -> {
                    val pillRadius = min(labelRect.width(), labelRect.height()) / 2f
                    canvas.drawRoundRect(labelRect, pillRadius, pillRadius, labelPaint)
                }

                LabelShape.CAPSULE_STROKE -> {
                    labelPaint.style = Paint.Style.STROKE
                    labelPaint.strokeWidth = 4f
                    val pillRadius = min(labelRect.width(), labelRect.height()) / 2f
                    canvas.drawRoundRect(labelRect, pillRadius, pillRadius, labelPaint)
                }

                LabelShape.TAG_FILL -> {
                    val arrowWidth = labelRect.height() * 0.4f
                    val tagPath = Path().apply {
                        moveTo(labelRect.left, labelRect.top)
                        lineTo(labelRect.right - arrowWidth, labelRect.top)
                        lineTo(labelRect.right, labelRect.centerY())
                        lineTo(labelRect.right - arrowWidth, labelRect.bottom)
                        lineTo(labelRect.left, labelRect.bottom)
                        close()
                    }
                    canvas.drawPath(tagPath, labelPaint)
                }

                LabelShape.TAG_STROKE -> {
                    labelPaint.style = Paint.Style.STROKE
                    labelPaint.strokeWidth = 4f
                    val arrowWidth = labelRect.height() * 0.4f
                    val tagPath = Path().apply {
                        moveTo(labelRect.left, labelRect.top)
                        lineTo(labelRect.right - arrowWidth, labelRect.top)
                        lineTo(labelRect.right, labelRect.centerY())
                        lineTo(labelRect.right - arrowWidth, labelRect.bottom)
                        lineTo(labelRect.left, labelRect.bottom)
                        close()
                    }
                    canvas.drawPath(tagPath, labelPaint)
                }

                LabelShape.REVERSE_TAG_FILL -> {
                    val arrowWidth = labelRect.height() * 0.4f
                    val revTagPath = Path().apply {
                        moveTo(labelRect.left + arrowWidth, labelRect.top)
                        lineTo(labelRect.right, labelRect.top)
                        lineTo(labelRect.right, labelRect.bottom)
                        lineTo(labelRect.left + arrowWidth, labelRect.bottom)
                        lineTo(labelRect.left, labelRect.centerY())
                        close()
                    }
                    canvas.drawPath(revTagPath, labelPaint)
                }

                LabelShape.REVERSE_TAG_STROKE -> {
                    labelPaint.style = Paint.Style.STROKE
                    labelPaint.strokeWidth = 4f
                    val arrowWidth = labelRect.height() * 0.4f
                    val revTagPath = Path().apply {
                        moveTo(labelRect.left + arrowWidth, labelRect.top)
                        lineTo(labelRect.right, labelRect.top)
                        lineTo(labelRect.right, labelRect.bottom)
                        lineTo(labelRect.left + arrowWidth, labelRect.bottom)
                        lineTo(labelRect.left, labelRect.centerY())
                        close()
                    }
                    canvas.drawPath(revTagPath, labelPaint)
                }

                LabelShape.RIBBON_FILL -> {
                    val arrowWidth = labelRect.height() * 0.4f
                    val ribbonPath = Path().apply {
                        moveTo(labelRect.left, labelRect.top)
                        lineTo(labelRect.left + arrowWidth, labelRect.centerY())
                        lineTo(labelRect.left, labelRect.bottom)
                        lineTo(labelRect.right - arrowWidth, labelRect.bottom)
                        lineTo(labelRect.right, labelRect.centerY())
                        lineTo(labelRect.right - arrowWidth, labelRect.top)
                        close()
                    }
                    canvas.drawPath(ribbonPath, labelPaint)
                }

                LabelShape.RIBBON_STROKE -> {
                    labelPaint.style = Paint.Style.STROKE
                    labelPaint.strokeWidth = 4f
                    val arrowWidth = labelRect.height() * 0.4f
                    val ribbonPath = Path().apply {
                        moveTo(labelRect.left, labelRect.top)
                        lineTo(labelRect.left + arrowWidth, labelRect.centerY())
                        lineTo(labelRect.left, labelRect.bottom)
                        lineTo(labelRect.right - arrowWidth, labelRect.bottom)
                        lineTo(labelRect.right, labelRect.centerY())
                        lineTo(labelRect.right - arrowWidth, labelRect.top)
                        close()
                    }
                    canvas.drawPath(ribbonPath, labelPaint)
                }

                LabelShape.SLANTED_FILL -> {
                    val slant = labelRect.height() * 0.35f
                    val slantedPath = Path().apply {
                        moveTo(labelRect.left + slant, labelRect.top)
                        lineTo(labelRect.right, labelRect.top)
                        lineTo(labelRect.right - slant, labelRect.bottom)
                        lineTo(labelRect.left, labelRect.bottom)
                        close()
                    }
                    canvas.drawPath(slantedPath, labelPaint)
                }

                LabelShape.SLANTED_STROKE -> {
                    labelPaint.style = Paint.Style.STROKE
                    labelPaint.strokeWidth = 4f
                    val slant = labelRect.height() * 0.35f
                    val slantedPath = Path().apply {
                        moveTo(labelRect.left + slant, labelRect.top)
                        lineTo(labelRect.right, labelRect.top)
                        lineTo(labelRect.right - slant, labelRect.bottom)
                        lineTo(labelRect.left, labelRect.bottom)
                        close()
                    }
                    canvas.drawPath(slantedPath, labelPaint)
                }

                LabelShape.BADGE_FILL -> {
                    val chamfer = min(labelRect.width(), labelRect.height()) * 0.25f
                    val badgePath = Path().apply {
                        moveTo(labelRect.left + chamfer, labelRect.top)
                        lineTo(labelRect.right - chamfer, labelRect.top)
                        lineTo(labelRect.right, labelRect.top + chamfer)
                        lineTo(labelRect.right, labelRect.bottom - chamfer)
                        lineTo(labelRect.right - chamfer, labelRect.bottom)
                        lineTo(labelRect.left + chamfer, labelRect.bottom)
                        lineTo(labelRect.left, labelRect.bottom - chamfer)
                        lineTo(labelRect.left, labelRect.top + chamfer)
                        close()
                    }
                    canvas.drawPath(badgePath, labelPaint)
                }

                LabelShape.BADGE_STROKE -> {
                    labelPaint.style = Paint.Style.STROKE
                    labelPaint.strokeWidth = 4f
                    val chamfer = min(labelRect.width(), labelRect.height()) * 0.25f
                    val badgePath = Path().apply {
                        moveTo(labelRect.left + chamfer, labelRect.top)
                        lineTo(labelRect.right - chamfer, labelRect.top)
                        lineTo(labelRect.right, labelRect.top + chamfer)
                        lineTo(labelRect.right, labelRect.bottom - chamfer)
                        lineTo(labelRect.right - chamfer, labelRect.bottom)
                        lineTo(labelRect.left + chamfer, labelRect.bottom)
                        lineTo(labelRect.left, labelRect.bottom - chamfer)
                        lineTo(labelRect.left, labelRect.top + chamfer)
                        close()
                    }
                    canvas.drawPath(badgePath, labelPaint)
                }

                LabelShape.HEXAGON_BADGE_FILL -> {
                    val hexIndent = labelRect.height() * 0.3f
                    val hexPath = Path().apply {
                        moveTo(labelRect.left + hexIndent, labelRect.top)
                        lineTo(labelRect.right - hexIndent, labelRect.top)
                        lineTo(labelRect.right, labelRect.centerY())
                        lineTo(labelRect.right - hexIndent, labelRect.bottom)
                        lineTo(labelRect.left + hexIndent, labelRect.bottom)
                        lineTo(labelRect.left, labelRect.centerY())
                        close()
                    }
                    canvas.drawPath(hexPath, labelPaint)
                }

                LabelShape.HEXAGON_BADGE_STROKE -> {
                    labelPaint.style = Paint.Style.STROKE
                    labelPaint.strokeWidth = 4f
                    val hexIndent = labelRect.height() * 0.3f
                    val hexPath = Path().apply {
                        moveTo(labelRect.left + hexIndent, labelRect.top)
                        lineTo(labelRect.right - hexIndent, labelRect.top)
                        lineTo(labelRect.right, labelRect.centerY())
                        lineTo(labelRect.right - hexIndent, labelRect.bottom)
                        lineTo(labelRect.left + hexIndent, labelRect.bottom)
                        lineTo(labelRect.left, labelRect.centerY())
                        close()
                    }
                    canvas.drawPath(hexPath, labelPaint)
                }

                LabelShape.DIAMOND_SHIELD_FILL -> {
                    val shieldPath = Path().apply {
                        moveTo(labelRect.centerX(), labelRect.top)
                        lineTo(labelRect.right, labelRect.top + labelRect.height() * 0.25f)
                        lineTo(labelRect.centerX(), labelRect.bottom)
                        lineTo(labelRect.left, labelRect.top + labelRect.height() * 0.25f)
                        close()
                    }
                    canvas.drawPath(shieldPath, labelPaint)
                }

                LabelShape.DIAMOND_SHIELD_STROKE -> {
                    labelPaint.style = Paint.Style.STROKE
                    labelPaint.strokeWidth = 4f
                    val shieldPath = Path().apply {
                        moveTo(labelRect.centerX(), labelRect.top)
                        lineTo(labelRect.right, labelRect.top + labelRect.height() * 0.25f)
                        lineTo(labelRect.centerX(), labelRect.bottom)
                        lineTo(labelRect.left, labelRect.top + labelRect.height() * 0.25f)
                        close()
                    }
                    canvas.drawPath(shieldPath, labelPaint)
                }

                LabelShape.UNDERLINE_BAR_FILL, LabelShape.UNDERLINE_BAR_STROKE -> {
                    val barHeight = 8f
                    val barRect = RectF(labelRect.left, labelRect.bottom - barHeight, labelRect.right, labelRect.bottom)
                    canvas.drawRoundRect(barRect, 4f, 4f, labelPaint)
                }

                LabelShape.SPEECH_BUBBLE_FILL -> {
                    val bubblePath = Path().apply {
                        val rx = 20f
                        addRoundRect(RectF(labelRect.left, labelRect.top, labelRect.right, labelRect.bottom - 10f), rx, rx, Path.Direction.CW)
                        moveTo(labelRect.left + 30f, labelRect.bottom - 10f)
                        lineTo(labelRect.left + 20f, labelRect.bottom)
                        lineTo(labelRect.left + 45f, labelRect.bottom - 10f)
                    }
                    canvas.drawPath(bubblePath, labelPaint)
                }

                LabelShape.SPEECH_BUBBLE_STROKE -> {
                    labelPaint.style = Paint.Style.STROKE
                    labelPaint.strokeWidth = 4f
                    val bubblePath = Path().apply {
                        val rx = 20f
                        addRoundRect(RectF(labelRect.left, labelRect.top, labelRect.right, labelRect.bottom - 10f), rx, rx, Path.Direction.CW)
                        moveTo(labelRect.left + 30f, labelRect.bottom - 10f)
                        lineTo(labelRect.left + 20f, labelRect.bottom)
                        lineTo(labelRect.left + 45f, labelRect.bottom - 10f)
                    }
                    canvas.drawPath(bubblePath, labelPaint)
                }
            }

            // ── COMPOSITE LAYER 3: White / Inner Stroke Line ──
            if (element.labelStrokeColor != null && element.labelStrokeWidth > 0f) {
                val innerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = element.labelStrokeColor!!
                    style = Paint.Style.STROKE
                    strokeWidth = element.labelStrokeWidth
                }
                val inset = element.labelStrokeWidth * 2f
                val insetRect = RectF(labelRect.left + inset, labelRect.top + inset, labelRect.right - inset, labelRect.bottom - inset)
                canvas.drawRoundRect(insetRect, 16f, 16f, innerStrokePaint)
            }

            // ── COMPOSITE LAYER 4: 3D Glossy Shine Arc Overlay ──
            if (element.hasGlossHighlight) {
                val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        labelRect.left, labelRect.top,
                        labelRect.left, labelRect.centerY(),
                        Color.argb(120, 255, 255, 255),
                        Color.argb(10, 255, 255, 255),
                        Shader.TileMode.CLAMP
                    )
                    style = Paint.Style.FILL
                }
                val glossRect = RectF(labelRect.left + 2f, labelRect.top + 2f, labelRect.right - 2f, labelRect.centerY())
                canvas.drawRoundRect(glossRect, 16f, 16f, glossPaint)
            }

            labelPaint.alpha = prevAlpha
        }

        // ----- DRAW TEXT -----
        // Font correction for baseline alignment
        val baselineShift = (fm.ascent + fm.descent) / 2f
        var yOffset = -((lines.size - 1) * lineHeight / 2f) - baselineShift

        lines.forEachIndexed { i, rawLine ->

            val fillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = element.paintColor
                textSize = element.paintTextSize
                letterSpacing = element.letterSpacing
                style = Paint.Style.FILL
                isAntiAlias = true

                // Bold / Italic / Underline
                isUnderlineText = TextDecoration.UNDERLINE in element.textDecoration
                val baseTf = element.paint.typeface ?: Typeface.DEFAULT
                val bold = TextDecoration.BOLD in element.textDecoration
                val italic = TextDecoration.ITALIC in element.textDecoration
                val style = when {
                    bold && italic -> Typeface.BOLD_ITALIC
                    bold -> Typeface.BOLD
                    italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                typeface = Typeface.create(baseTf, style)
            }
            // Apply text formatting
            val text = when (element.listStyle) {
                ListStyle.BULLETED -> "• $rawLine"
                ListStyle.NUMBERED -> "${i + 1}. $rawLine"
                else -> rawLine
            }

            val displayText = when (element.letterCasing) {
                LetterCasing.ALL_CAPS -> text.uppercase()
                LetterCasing.LOWER_CASE -> text.lowercase()
                LetterCasing.TITLE_CASE -> text.split(" ")
                    .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

                else -> text
            }

            val alignment = when (element.alignment) {
                TextAlignment.LEFT -> Paint.Align.LEFT
                TextAlignment.CENTER -> Paint.Align.CENTER
                TextAlignment.RIGHT -> Paint.Align.RIGHT
                TextAlignment.JUSTIFY -> Paint.Align.LEFT
                else -> Paint.Align.LEFT
            }
            fillPaint.textAlign = alignment

            val indentOffset = if (i == 0) element.currentIndent else 0f
            val xPos = when (alignment) {
                Paint.Align.LEFT -> -element.getLocalContentWidth() / 2f + indentOffset
                Paint.Align.CENTER -> 0f
                Paint.Align.RIGHT -> element.getLocalContentWidth() / 2f + indentOffset
            }

            // Gradient Fill
            if (element.fillGradient != null) {
                val w = fillPaint.measureText(displayText)
                fillPaint.shader =
                    createGradientShader(element.fillGradient!!, w, fillPaint.textSize)
            }

            // Blur and Blend
            if (element.hasBlur) fillPaint.maskFilter =
                BlurMaskFilter(element.blurValue, BlurMaskFilter.Blur.NORMAL)
            fillPaint.xfermode = drawWithBlend(element)

            // ── LAYER 1a: Double Step 2 Extrusion (Style #17) ──
            if (element.hasDoubleExtrude && element.extrudeStep2Depth > 0f) {
                val step2Paint = TextPaint(fillPaint).apply {
                    shader = null
                    color = element.extrudeStep2Color
                    xfermode = null
                    maskFilter = null
                    style = Paint.Style.FILL
                }
                val steps = (element.extrudeStep2Depth.toInt()).coerceIn(1, 16)
                for (step in 1..steps) {
                    val stepFrac = step.toFloat() / steps
                    val ex = xPos + element.extrudeStep2Dx * stepFrac
                    val ey = yOffset + element.extrudeStep2Dy * stepFrac
                    canvas.drawText(displayText, ex, ey, step2Paint)
                }
            }

            // ── LAYER 1b: 3D Block Extrusion / Hard Offset Layer (as in Image 1 & 3) ──
            if (element.has3dExtrude) {
                val extrudePaint = TextPaint(fillPaint).apply {
                    shader = null
                    color = element.extrudeColor
                    xfermode = null
                    maskFilter = null
                    style = Paint.Style.FILL
                }
                val depth = if (element.extrudeDepth > 0f) element.extrudeDepth else kotlin.math.hypot(element.extrudeDx, element.extrudeDy)
                val steps = (depth.toInt()).coerceIn(1, 16)
                for (step in 1..steps) {
                    val stepFrac = step.toFloat() / steps
                    val ex = xPos + element.extrudeDx * stepFrac
                    val ey = yOffset + element.extrudeDy * stepFrac
                    canvas.drawText(displayText, ex, ey, extrudePaint)
                }
            }

            // ── LAYER 2: Shadow / Soft Glow / Hard Stamp Drop ──
            if (element.hasShadow && element.shadowOpacity > 0) {
                val baseAlpha = Color.alpha(element.shadowColor).takeIf { it > 0 } ?: 255
                val effectiveAlpha = ((element.shadowOpacity.coerceIn(0, 255) / 255f) * baseAlpha).toInt()
                val sc = (element.shadowColor and 0x00FFFFFF) or (effectiveAlpha shl 24)
                val sp = TextPaint(fillPaint).apply {
                    shader = null
                    color = sc
                    xfermode = null
                    maskFilter = if (element.shadowRadius > 0.5f) BlurMaskFilter(element.shadowRadius, BlurMaskFilter.Blur.NORMAL) else null
                }
                canvas.drawText(
                    displayText, xPos + element.shadowDx, yOffset + element.shadowDy, sp
                )
            }

            // ── LAYER 2b: Outer Glow ──
            if (element.hasOuterGlow && element.outerGlowRadius > 0f && element.outerGlowOpacity > 0) {
                val baseAlpha = Color.alpha(element.outerGlowColor).takeIf { it > 0 } ?: 255
                val effectiveAlpha = ((element.outerGlowOpacity.coerceIn(0, 255) / 255f) * baseAlpha).toInt()
                val glowCol = (element.outerGlowColor and 0x00FFFFFF) or (effectiveAlpha shl 24)
                val glowPaint = TextPaint(fillPaint).apply {
                    shader = null
                    color = glowCol
                    xfermode = null
                    maskFilter = BlurMaskFilter(element.outerGlowRadius.coerceAtLeast(0.5f), BlurMaskFilter.Blur.OUTER)
                }
                canvas.drawText(displayText, xPos, yOffset, glowPaint)
            }

            // ── LAYER 3: Outer Under-Stroke / Secondary Contour / Gilded Border (Images 1, 3, 4) ──
            if (element.hasUnderStroke && element.underStrokeWidth > 0f) {
                val underStrokePaint = TextPaint(fillPaint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = element.underStrokeWidth
                    color = element.underStrokeColor
                    shader = null
                    maskFilter = null
                    xfermode = null
                }
                canvas.drawText(displayText, xPos, yOffset, underStrokePaint)
            }

            // ── LAYER 4: Inner Stroke / Primary Stroke ──
            if (element.hasStroke && element.strokeWidth > 0f) {
                val strokePaint = TextPaint(fillPaint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = element.strokeWidth
                    maskFilter = null
                }
                if (element.strokeGradient != null) {
                    val w = fillPaint.measureText(displayText)
                    strokePaint.shader =
                        createGradientShader(element.strokeGradient!!, w, fillPaint.textSize)
                } else {
                    strokePaint.color = element.strokeColor
                }
                val old = strokePaint.alpha
                strokePaint.alpha = element.paintAlpha
                canvas.drawText(displayText, xPos, yOffset, strokePaint)
                strokePaint.alpha = old
            }

            // ── LAYER 5a: Anaglyph 3D Stereoscopic Red-Cyan Split ──
            if (element.hasAnaglyph && element.anaglyphOffset > 0f) {
                val anaglyphPaint1 = TextPaint(fillPaint).apply {
                    shader = null
                    color = element.anaglyphColor1
                    maskFilter = null
                }
                val anaglyphPaint2 = TextPaint(fillPaint).apply {
                    shader = null
                    color = element.anaglyphColor2
                    maskFilter = null
                }
                canvas.drawText(displayText, xPos - element.anaglyphOffset, yOffset, anaglyphPaint1)
                canvas.drawText(displayText, xPos + element.anaglyphOffset, yOffset, anaglyphPaint2)
            }

            // ── LAYER 5b: 3D Chisel Bevel (Highlights & Shadows) ──
            if (element.hasBevel && element.bevelDepth > 0f) {
                val bevelShadowPaint = TextPaint(fillPaint).apply {
                    shader = null
                    color = element.bevelShadowColor
                    maskFilter = null
                }
                val bevelHighlightPaint = TextPaint(fillPaint).apply {
                    shader = null
                    color = element.bevelHighlightColor
                    maskFilter = null
                }
                canvas.drawText(displayText, xPos + element.bevelDepth, yOffset + element.bevelDepth, bevelShadowPaint)
                canvas.drawText(displayText, xPos - element.bevelDepth, yOffset - element.bevelDepth, bevelHighlightPaint)
            }

            // ── LAYER 5c: 3D Emboss & Deboss (Letterpress / Carved / Sunken) ──
            if (element.hasEmboss && element.embossDepth > 0f) {
                val highlightPaint = TextPaint(fillPaint).apply {
                    shader = null
                    color = element.embossHighlightColor
                    maskFilter = null
                }
                val shadowPaint = TextPaint(fillPaint).apply {
                    shader = null
                    color = element.embossShadowColor
                    maskFilter = null
                }
                if (element.isDebossed) {
                    canvas.drawText(displayText, xPos - element.embossDepth, yOffset - element.embossDepth, shadowPaint)
                    canvas.drawText(displayText, xPos + element.embossDepth, yOffset + element.embossDepth, highlightPaint)
                } else {
                    canvas.drawText(displayText, xPos - element.embossDepth, yOffset - element.embossDepth, highlightPaint)
                    canvas.drawText(displayText, xPos + element.embossDepth, yOffset + element.embossDepth, shadowPaint)
                }
            }

            // ── LAYER 5d: Main Fill ──
            if (element.alignment == TextAlignment.JUSTIFY) {
                element.paint = fillPaint
                justifyText(canvas, displayText, yOffset, element)
            } else {
                val oldFillAlpha = fillPaint.alpha
                fillPaint.alpha = element.paintAlpha
                canvas.drawText(displayText, xPos, yOffset, fillPaint)
                fillPaint.alpha = oldFillAlpha
            }

            // ── LAYER 5e: Inner Glow ──
            if (element.hasInnerGlow && element.innerGlowRadius > 0f && element.innerGlowOpacity > 0) {
                val baseAlpha = Color.alpha(element.innerGlowColor).takeIf { it > 0 } ?: 255
                val effectiveAlpha = ((element.innerGlowOpacity.coerceIn(0, 255) / 255f) * baseAlpha).toInt()
                val glowCol = (element.innerGlowColor and 0x00FFFFFF) or (effectiveAlpha shl 24)
                val innerGlowPaint = TextPaint(fillPaint).apply {
                    shader = null
                    color = glowCol
                    xfermode = null
                    maskFilter = BlurMaskFilter(element.innerGlowRadius.coerceAtLeast(0.5f), BlurMaskFilter.Blur.INNER)
                }
                canvas.drawText(displayText, xPos, yOffset, innerGlowPaint)
            }

            yOffset += lineHeight
        }
    }

    private fun drawWithBlend(element: CanvasElement): Xfermode? {
        return when (element.blendType) {
            BlendType.SRC -> PorterDuffXfermode(
                PorterDuff.Mode.SRC
            )

            BlendType.NORMAL -> null
            BlendType.DARKEN -> PorterDuffXfermode(
                PorterDuff.Mode.DARKEN
            )

            BlendType.LIGHTEN -> PorterDuffXfermode(
                PorterDuff.Mode.LIGHTEN
            )

            BlendType.MULTIPLY -> PorterDuffXfermode(
                PorterDuff.Mode.MULTIPLY
            )

            BlendType.SCREEN -> PorterDuffXfermode(
                PorterDuff.Mode.SCREEN
            )

            else -> {}
        } as Xfermode?
    }

    private fun isRTL(text: String): Boolean {
        return text.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.ARABIC }
    }

    private fun justifyText(
        canvas: Canvas, text: String, yOffset: Float, element: CanvasElement
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
        } else null

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
    private fun getPinchDistance(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return hypot(x.toDouble(), y.toDouble()).toFloat()
    }

    private fun getPinchAngle(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
    }

    fun CanvasElement.containsPoint(
        px: Float, py: Float
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
            bounds.bottom
        )
        val m = Matrix().apply {
            postScale(
                scale * if (isFlippedX) -1f else 1f, scale * if (isFlippedY) -1f else 1f
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

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {

            if (isPanMode) {
                stepZoomOverall()
                return true
            }

            val (x, y) = screenToCanvas(e.x, e.y)

            val touchedElement =
                canvasElements.filter { !it.isLocked && it.type != ElementType.GROUP }
                    .sortedByDescending { it.zIndex }.firstOrNull { element ->
                        val matrix = Matrix()
                        matrix.postTranslate(-element.x, -element.y)
                        matrix.postRotate(-element.rotation)
                        matrix.postScale(1f / element.scale, 1f / element.scale)

                        val touchPoint = floatArrayOf(x, y)
                        matrix.mapPoints(touchPoint)

                        val tightBounds = element.getTightTextBounds()
                        tightBounds.contains(touchPoint[0], touchPoint[1])
                    }

            if (touchedElement != null && currentMode == Mode.GROUP_EDIT && touchedElement.groupId == activeGroupId && touchedElement.type != ElementType.BACKGROUND) {

                canvasElements.forEach { it.isSelected = false }
                selectedElements.clear()
                touchedElement.isSelected = true
                selectedElements.add(touchedElement)
                onElementSelected?.invoke(selectedElements)
                onEditTextRequested?.invoke(touchedElement)
                invalidate()
                return true
            }
            if (touchedElement?.groupId != null) {
                activeGroupId = touchedElement.groupId
                // Enter GROUP_EDIT and immediately select the tapped child --
                // don't select all children, just the one that was double-tapped.
                canvasElements.forEach { it.isSelected = false }
                selectedElements.clear()
                touchedElement.isSelected = true
                selectedElements.add(touchedElement)
                currentMode = Mode.GROUP_EDIT
                onElementSelected?.invoke(selectedElements)
                onEditTextRequested?.invoke(touchedElement)
                invalidate()
                return true
            } else if (touchedElement != null) {
                if (touchedElement.type == ElementType.TABLE) {
                    val cellPair = getTableCellAt(touchedElement, x, y)
                    if (cellPair != null) {
                        val (r, c) = cellPair
                        val pair = Pair(r, c)
                        touchedElement.tableData?.let { data ->
                            data.selectedCells.clear()
                            data.selectedCells.add(pair)
                        }
                        onTableCellSelected?.invoke(r, c)
                    }
                }
                canvasElements.forEach { it.isSelected = false }
                selectedElements.clear()
                touchedElement.isSelected = true
                selectedElements.add(touchedElement)
                onElementSelected?.invoke(selectedElements)
                onEditTextRequested?.invoke(touchedElement)
                invalidate()
                return true
            }

            stepZoomOverall()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val (x, y) = screenToCanvas(e.x, e.y)

            // 1. In Table Edit Mode: Long press on a cell triggers Table Selection Mode
            if (isTableEditMode) {
                val activeTable = selectedElements.firstOrNull { it.type == ElementType.TABLE }
                    ?: canvasElements.firstOrNull { it.isSelected && it.type == ElementType.TABLE }
                    ?: canvasElements.firstOrNull { it.type == ElementType.TABLE }
                if (activeTable != null) {
                    val cellPair = getTableCellAt(activeTable, x, y)
                    if (cellPair != null) {
                        val (r, c) = cellPair
                        val pair = Pair(r, c)
                        val data = activeTable.tableData
                        if (data != null) {
                            if (!isTableMultiSelectMode) {
                                data.selectedCells.clear()
                                data.selectedCells.add(pair)
                                isTableMultiSelectMode = true
                                performHapticFeedback(
                                    android.view.HapticFeedbackConstants.LONG_PRESS,
                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                )
                                onTableMultiSelectChanged?.invoke(true)
                                onTableCellSelected?.invoke(r, c)
                            } else {
                                if (data.selectedCells.contains(pair)) {
                                    data.selectedCells.remove(pair)
                                    if (data.selectedCells.isEmpty()) {
                                        isTableMultiSelectMode = false
                                        onTableMultiSelectChanged?.invoke(false)
                                    }
                                } else {
                                    data.selectedCells.add(pair)
                                }
                                onTableCellToggleSelected?.invoke(r, c)
                            }
                        }
                        vibrateSoft()
                        invalidate()
                        return
                    }
                }
                return
            }

            val touchedElement = canvasElements.filter { !it.isLocked } // ignore locked
                .sortedByDescending { it.zIndex }.firstOrNull { it.containsPoint(x, y) }

            if (touchedElement != null && touchedElement.type != ElementType.BACKGROUND) {
                // Resolve grouped child -> its children for canvas bounds,
                // but mark the sentinel selected so ViewModel counts it as 1.
                val groupId = touchedElement.groupId
                val sentinel =
                    if (groupId != null) canvasElements.firstOrNull { it.type == ElementType.GROUP && it.id == groupId }
                    else null
                val canvasItems: List<CanvasElement> =
                    if (sentinel != null) canvasElements.filter { it.groupId == groupId }  // children for bounds
                    else listOf(touchedElement)

                if (!inSelectionMode) {
                    inSelectionMode = true
                    clearSelection()
                    canvasItems.forEach { it.isSelected = true; selectedElements.add(it) }
                    sentinel?.isSelected = true   // mark sentinel for ViewModel only
                    vibrateSoft()
                    onRequestOpenLayers?.invoke()
                } else {
                    val alreadySelected = canvasItems.all { it.isSelected }
                    if (alreadySelected) {
                        canvasItems.forEach { it.isSelected = false; selectedElements.remove(it) }
                        sentinel?.isSelected = false
                        if (selectedElements.isEmpty()) {
                            inSelectionMode = false
                            onExitSelectionMode?.invoke()
                        }
                    } else {
                        canvasItems.forEach {
                            if (!it.isSelected) {
                                it.isSelected = true; selectedElements.add(it)
                            }
                        }
                        sentinel?.isSelected = true
                    }
                }
                // Report sentinel (or real element) to ViewModel so count = 1 per group
                val reportList =
                    if (sentinel != null) listOf(sentinel) else selectedElements.toList()
                onElementSelected?.invoke(reportList)
                invalidate()
            } else {
                // Long press away from any art-board element → canvas options popup.
                val isOutsideArtboard = x < 0f || y < 0f || x > canvasWidth || y > canvasHeight
                if (isOutsideArtboard) {
                    vibrateSoft()
                    onCanvasLongPressed?.invoke(e.rawX, e.rawY)
                }
            }
        }
    }

    private fun stepZoomOverall() {
        // 50% → 100% → 200% → 300% → 50% cycle
        val next = when {
            overallScale < 0.9f -> 1.0f   // 50%  → 100%
            overallScale < 1.5f -> 2.0f   // 100% → 200%
            overallScale < 2.5f -> 3.0f   // 200% → 300%
            else -> 0.5f   // 300% → 50%
        }
        animateOverallZoom(next)
        onZoomChanged?.invoke(next)         // popup label update karo
    }

    private fun animateOverallZoom(toScale: Float) {
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

    private fun screenToCanvas(sx: Float, sy: Float): Pair<Float, Float> {
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

    private fun canvasToScreen(cx: Float, cy: Float): Pair<Float, Float> {
        val pt = floatArrayOf(cx, cy)
        val transform = Matrix().apply {
            postScale(scale, scale)
            postTranslate(offsetX, offsetY)
            postScale(overallScale, overallScale, width / 2f, height / 2f)
            postTranslate(overallOffsetX, overallOffsetY)
        }
        transform.mapPoints(pt)
        return pt[0] to pt[1]
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Only forward single-pointer events to GestureDetector.
        // Forwarding ACTION_POINTER_DOWN/UP causes TouchTarget double-recycle
        // on Android 14 (SDK 34), triggering IllegalStateException: already recycled once.
        val maskedAction = event.actionMasked
        if (maskedAction == MotionEvent.ACTION_DOWN || maskedAction == MotionEvent.ACTION_MOVE || maskedAction == MotionEvent.ACTION_UP || maskedAction == MotionEvent.ACTION_CANCEL) {
            gestureDetector.onTouchEvent(event)
        }

        val (x, y) = screenToCanvas(event.x, event.y)

        if (isColorPickerMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    pickerX = x.coerceIn(0f, canvasWidth.toFloat())
                    pickerY = y.coerceIn(0f, canvasHeight.toFloat())
                    isDraggingPicker = true
                    invalidate()
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingPicker) {
                        val bmp = colorPickerBitmap
                        if (bmp != null && !bmp.isRecycled) {
                            val scaleX = bmp.width.toFloat() / canvasWidth.toFloat()
                            val scaleY = bmp.height.toFloat() / canvasHeight.toFloat()
                            val px = (pickerX * scaleX).roundToInt().coerceIn(0, bmp.width - 1)
                            val py = (pickerY * scaleY).roundToInt().coerceIn(0, bmp.height - 1)
                            val color = bmp.getPixel(px, py)
                            onColorPicked?.invoke(color)
                        }
                        isDraggingPicker = false
                        invalidate()
                    }
                    return true
                }
            }
        }

        if (isDrawing) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    currentStrokePath = Path().apply {
                        moveTo(x, y)
                        lineTo(x + 0.01f, y + 0.01f)
                    }
                    currentStrokePoints.clear()
                    currentStrokePoints.add(x to y)

                    // Paint for live preview (scaled thickness)
                    currentStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = currentBrushColor
                        strokeWidth = currentBrushThickness   // ✅ scale-aware preview
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        alpha = (currentBrushHardness * 255).toInt()

                        if (currentBrushStyle == BrushStyle.BRUSH) {
                            val blurRadius = max(0.1f, (1f - currentBrushHardness) * 25f)
                            maskFilter = try {
                                BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        currentBrushGradient?.let {
                            shader = createBackgroundGradientShader(
                                it, width.toFloat(), height.toFloat()
                            )
                        }
                    }

                    invalidate()
                }

                MotionEvent.ACTION_MOVE -> {
                    val clampedX = x.coerceIn(0f, canvasWidth.toFloat())
                    val clampedY = y.coerceIn(0f, canvasHeight.toFloat())

                    currentStrokePath?.lineTo(clampedX, clampedY)
                    currentStrokePoints.add(clampedX to clampedY)
                    invalidate()
                }

                MotionEvent.ACTION_UP -> {
                    currentStrokePath?.lineTo(x, y)

                    val path = currentStrokePath
                    if (path != null && activeSessionElement != null) {
                        // Store path in ABSOLUTE canvas coordinates — no normalization
                        val strokeData = StrokeData(
                            path = Path(path),
                            color = currentBrushColor,
                            thickness = currentBrushThickness,
                            hardness = currentBrushHardness,
                            style = currentBrushStyle,
                            gradient = currentBrushGradient
                        )
                        activeSessionElement!!.drawStrokes?.add(strokeData)
                        onStrokeCompleted?.invoke(strokeData)
                    }

                    // Cleanup live preview
                    currentStrokePath = null
                    currentStrokePaint = null
                    currentStrokePoints.clear()
                    invalidate()
                }
            }

            return true
        }

        if (rulerState != com.webscare.urducanvas.common.canvas.enums.RulerState.OFF) {
            if (handleRulerTouch(event, x, y)) {
                return true
            }
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            if (gestureStartZoom == null) {
                gestureStartZoom = overallScale
                gestureStartPanX = overallOffsetX
                gestureStartPanY = overallOffsetY
            }
        }

        when (event.actionMasked) {

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    initialPinchDistance = getPinchDistance(event)
                    initialPinchAngle = getPinchAngle(event)
                    initialOverallScale = overallScale

                    // Capture the midpoint between the two fingers in screen coords.
                    // All subsequent CANVAS_PAN pinch-zoom frames use this as the
                    // fixed pivot so the canvas content under the fingers never moves.
                    pinchFocusX = (event.getX(0) + event.getX(1)) / 2f
                    pinchFocusY = (event.getY(0) + event.getY(1)) / 2f
                    initialOffsetXAtPinch = overallOffsetX
                    initialOffsetYAtPinch = overallOffsetY

                    when {
                        // Table element selected → canvas zoom & pan
                        selectedElements.any { it.type == ElementType.TABLE } -> {
                            currentMode = Mode.CANVAS_PAN
                        }
                        // Elements selected → element scale/rotate (pan mode OFF only)
                        selectedElements.isNotEmpty() && !isPanMode -> {
                            currentMode = Mode.MULTI_TOUCH
                            initialScale = selectedElements.firstOrNull()?.scale ?: 1f
                            initialRotation = selectedElements.firstOrNull()?.rotation ?: 0f
                        }
                        // Pan locked — block two-finger zoom/pan
                        isCanvasPanLocked -> { /* consume but do nothing */
                        }
                        // Empty canvas (ya pan mode ON) → overall canvas zoom
                        else -> {
                            currentMode = Mode.CANVAS_PAN
                        }
                    }
                }
            }

            MotionEvent.ACTION_DOWN -> {
                iconTouched = null
                lastTouchedElement = null
                showVerticalGuide = false
                showHorizontalGuide = false
                showRotationVerticalGuide = false
                showRotationHorizontalGuide = false

                if (currentMode == Mode.GROUP_EDIT && activeGroupId != null) {
                    // Check if the tap is within the combined bounds of the group
                    val groupChildren = canvasElements.filter { it.groupId == activeGroupId }
                    val groupBounds = run {
                        var minX = Float.MAX_VALUE;
                        var minY = Float.MAX_VALUE
                        var maxX = -Float.MAX_VALUE;
                        var maxY = -Float.MAX_VALUE
                        groupChildren.forEach { el ->
                            el.getRotatedCorners().toList().chunked(2).forEach { (cx, cy) ->
                                if (cx < minX) minX = cx; if (cx > maxX) maxX = cx
                                if (cy < minY) minY = cy; if (cy > maxY) maxY = cy
                            }
                        }
                        RectF(minX, minY, maxX, maxY)
                    }

                    val tapInsideGroup = groupBounds.contains(x, y)

                    if (!tapInsideGroup) {
                        // Tap outside group bounds -- exit GROUP_EDIT completely
                        currentMode = Mode.NONE
                        activeGroupId = null
                        canvasElements.forEach { it.isSelected = false }
                        selectedElements.clear()
                        onElementSelected?.invoke(selectedElements)
                        // Don't return -- let the tap fall through to select whatever is there
                    } else {
                        // ── Icon check FIRST — rotate/resize/delete/edit handles ─────────
                        // If a child is already selected, its handles are drawn and must
                        // respond to touch before we do any hit-test on children below.
                        if (selectedElements.isNotEmpty()) {
                            val touchedIconEntry =
                                lastDrawnIconRect.entries.firstOrNull { (_, rect) ->
                                        rect.contains(
                                            x,
                                            y
                                        )
                                    }
                            if (touchedIconEntry != null) {
                                iconTouched = touchedIconEntry.key
                                when (iconTouched) {
                                    "delete" -> {
                                        removeSelectedElement()
                                        return true
                                    }

                                    "rotate" -> {
                                        currentMode = Mode.ROTATE
                                        touchStartX = x; touchStartY = y
                                        isRotating = true
                                        initialElementRotations.clear()
                                        initialElementPositionsRelativeToGroupPivot.clear()
                                        val bounds = getCombinedSelectedBounds()
                                        initialGroupPivotX = bounds.centerX()
                                        initialGroupPivotY = bounds.centerY()
                                        selectedElements.forEach { el ->
                                            initialElementRotations[el.id] = el.rotation
                                            initialElementPositionsRelativeToGroupPivot[el.id] =
                                                Pair(
                                                    el.x - initialGroupPivotX,
                                                    el.y - initialGroupPivotY
                                                )
                                        }
                                        initialAngle = atan2(
                                            touchStartY - initialGroupPivotY,
                                            touchStartX - initialGroupPivotX
                                        )
                                        selectedElements.firstOrNull()?.let {
                                            onStartBatchUpdate?.invoke(it.id, "rotate")
                                        }
                                        return true
                                    }

                                    "resize" -> {
                                        currentMode = Mode.RESIZE
                                        touchStartX = x; touchStartY = y
                                        val combined = getCombinedSelectedBounds()
                                        val pivotX = combined.centerX()
                                        val pivotY = combined.centerY()
                                        resizeStartDist = hypot(x - pivotX, y - pivotY)
                                        selectedElements.forEach { el ->
                                            resizeLastSignX[el.id] = (touchStartX - pivotX).sign
                                            resizeLastSignY[el.id] = (touchStartY - pivotY).sign
                                            resizeInitialScales[el.id] = el.scale
                                            onStartBatchUpdate?.invoke(el.id, "resize")
                                        }
                                        return true
                                    }

                                    "edit" -> {
                                        if (selectedElements.size == 1) onEditTextRequested?.invoke(
                                            selectedElements.first()
                                        )
                                        return true
                                    }

                                    "transform" -> {
                                        currentMode = Mode.TRANSFORM
                                        touchStartX = x; touchStartY = y
                                        selectedElements.forEach { el ->
                                            val startW = if (el.type == ElementType.TEXT) {
                                                el.boxWidth ?: el.getNaturalUnwrappedWidth()
                                            } else {
                                                el.logicalContentWidth
                                            }
                                            val startH = if (el.type == ElementType.TEXT) {
                                                el.boxHeight ?: el.getNaturalContentHeight(startW)
                                            } else {
                                                el.logicalContentHeight
                                            }
                                            initialElementSizes[el.id] = Pair(
                                                startW, startH
                                            )
                                            if (el.type == ElementType.TEXT) {
                                                initialTextSizes[el.id] = el.paintTextSize
                                                initialUnwrappedWidths[el.id] =
                                                    el.getNaturalUnwrappedWidth()
                                                initialMinWordWidths[el.id] = el.getMinWordWidth()
                                            }
                                            onStartBatchUpdate?.invoke(el.id, "transform")
                                        }
                                        return true
                                    }
                                }
                            }
                        }

                        // ── No icon hit — do child hit-test ──────────────────────────────
                        val hitChild =
                            groupChildren.filter { !it.isLocked }.sortedByDescending { it.zIndex }
                                .firstOrNull { element ->
                                    val matrix = Matrix().apply {
                                        postTranslate(-element.x, -element.y)
                                        postRotate(-element.rotation)
                                        postScale(1f / element.scale, 1f / element.scale)
                                    }
                                    val pt = floatArrayOf(x, y).also { matrix.mapPoints(it) }
                                    element.getTightTextBounds().contains(pt[0], pt[1])
                                }

                        if (hitChild != null) {
                            // Already selected same child — start drag immediately
                            // Otherwise switch selection to the new child
                            if (selectedElements.size != 1 || selectedElements.first().id != hitChild.id) {
                                canvasElements.forEach { it.isSelected = false }
                                selectedElements.clear()
                                hitChild.isSelected = true
                                selectedElements.add(hitChild)
                                onElementSelected?.invoke(selectedElements)
                            }
                            lastTouchedElement = hitChild
                            currentMode = Mode.DRAG
                            touchStartX = x
                            touchStartY = y
                            onStartBatchUpdate?.invoke(hitChild.id, "drag")
                            invalidate()
                            return true
                        } else {
                            // Tapped inside group area but missed all children --
                            // select all children for bounds, mark sentinel for ViewModel
                            val sentinel = canvasElements.firstOrNull {
                                it.type == ElementType.GROUP && it.id == activeGroupId
                            }
                            canvasElements.forEach { it.isSelected = false }
                            selectedElements.clear()
                            groupChildren.forEach { it.isSelected = true; selectedElements.add(it) }
                            sentinel?.isSelected = true
                            currentMode = Mode.DRAG
                            touchStartX = x
                            touchStartY = y
                            val report =
                                if (sentinel != null) listOf(sentinel) else selectedElements.toList()
                            onElementSelected?.invoke(report)
                            invalidate()
                            return true
                        }
                    }
                }

                if (selectedElements.isNotEmpty()) {
                    val touchedIconEntry =
                        lastDrawnIconRect.entries.firstOrNull { (iconName, rect) ->
                            Log.d(
                                "IconTouch",
                                "Touch region icon=$iconName Rect(${rect.left}, ${rect.top}, ${rect.right}, ${rect.bottom})"
                            )
                            rect.contains(x, y)
                        }

                    if (touchedIconEntry != null) {
                        Log.d(
                            "IconHit", "User tapped inside icon=${touchedIconEntry.key} at ($x,$y)"
                        )
                        iconTouched = touchedIconEntry.key
                        when (iconTouched) {
                            "delete" -> {
                                removeSelectedElement() // Handles removing all selected
                                return true // Consume the event immediately
                            }

                            "rotate" -> {
                                currentMode = Mode.ROTATE
                                touchStartX = x
                                touchStartY = y

                                isRotating = true
                                initialElementRotations.clear()
                                initialElementPositionsRelativeToGroupPivot.clear() // Clear previous initial positions
                                val combinedBoundsAtStart =
                                    getCombinedSelectedBounds() // Get bounds at start of interaction
                                initialGroupPivotX = combinedBoundsAtStart.centerX()
                                initialGroupPivotY = combinedBoundsAtStart.centerY()

                                selectedElements.forEach { element ->
                                    initialElementRotations[element.id] = element.rotation
                                    // Store initial position relative to the group's center
                                    initialElementPositionsRelativeToGroupPivot[element.id] = Pair(
                                        element.x - initialGroupPivotX,
                                        element.y - initialGroupPivotY
                                    )
                                }
                                initialAngle = atan2(
                                    touchStartY - initialGroupPivotY,
                                    touchStartX - initialGroupPivotX
                                )
                                selectedElements.firstOrNull()?.let { element ->
                                    onStartBatchUpdate?.invoke(element.id, "rotate")
                                }
                                return true
                            }

                            "resize" -> {
                                currentMode = Mode.RESIZE
                                touchStartX = x
                                touchStartY = y
                                val combined = getCombinedSelectedBounds()
                                val pivotX = combined.centerX()
                                val pivotY = combined.centerY()
                                // Capture the distance from finger to pivot at gesture start.
                                // MOVE frames compute newScale = initialScale * (currentDist / startDist)
                                // — absolute math, same as MULTI_TOUCH pinch, so zoom levels match.
                                resizeStartDist = hypot(x - pivotX, y - pivotY)
                                selectedElements.forEach { element ->
                                    resizeLastSignX[element.id] = (touchStartX - pivotX).sign
                                    resizeLastSignY[element.id] = (touchStartY - pivotY).sign
                                    resizeInitialScales[element.id] = element.scale
                                    onStartBatchUpdate?.invoke(element.id, "resize")
                                }
                                return true
                            }

                            "edit" -> {
                                if (selectedElements.size == 1) {
                                    onEditTextRequested?.invoke(selectedElements.first())
                                }
                                return true
                            }

                            "transform" -> {
                                currentMode = Mode.TRANSFORM
                                touchStartX = x
                                touchStartY = y

                                // Store initial logical sizes for direct geometry resize
                                selectedElements.forEach { element ->
                                    val startW = if (element.type == ElementType.TEXT) {
                                        element.boxWidth ?: element.getLocalContentWidth()
                                    } else {
                                        element.logicalContentWidth
                                    }
                                    val startH = if (element.type == ElementType.TEXT) {
                                        element.boxHeight ?: element.getLocalContentHeight()
                                    } else {
                                        element.logicalContentHeight
                                    }
                                    initialElementSizes[element.id] = Pair(
                                        startW, startH
                                    )
                                    if (element.type == ElementType.TEXT) {
                                        initialTextSizes[element.id] = element.paintTextSize
                                        initialUnwrappedWidths[element.id] =
                                            element.getNaturalUnwrappedWidth()
                                        initialMinWordWidths[element.id] = element.getMinWordWidth()
                                    }
                                    onStartBatchUpdate?.invoke(element.id, "transform")
                                }
                                return true
                            }

                        }
                    }
                }

                // 2. If no icon was touched, check for element touch (single or multi-selection)
                val touchedElement =
                    canvasElements.filter { !it.isLocked && it.type != ElementType.GROUP }
                        .sortedByDescending { it.zIndex }.firstOrNull { element ->
                            val matrix = Matrix()
                            matrix.postTranslate(-element.x, -element.y)
                            matrix.postRotate(-element.rotation)
                            matrix.postScale(1f / element.scale, 1f / element.scale)

                            val touchPoint = floatArrayOf(x, y)
                            matrix.mapPoints(touchPoint)

                            val tightBounds = element.getTightTextBounds()
                            tightBounds.contains(touchPoint[0], touchPoint[1])
                        }

                if (isTableEditMode) {
                    val activeTable = selectedElements.firstOrNull { it.type == ElementType.TABLE }
                        ?: canvasElements.firstOrNull { it.isSelected && it.type == ElementType.TABLE }
                        ?: canvasElements.firstOrNull { it.type == ElementType.TABLE }
                    if (activeTable != null) {
                        if (isTableResizeMode) {
                            val div = getTableDividerAt(activeTable, x, y)
                            if (div != null) {
                                val (isCol, idx) = div
                                if (isCol) {
                                    draggedColDividerIndex = idx
                                    draggedRowDividerIndex = -1
                                } else {
                                    draggedRowDividerIndex = idx
                                    draggedColDividerIndex = -1
                                }
                                currentMode = Mode.TABLE_RESIZE
                                touchStartX = x
                                touchStartY = y
                                invalidate()
                                return true
                            }
                        }

                        val cellPair = getTableCellAt(activeTable, x, y)
                        if (cellPair != null) {
                            val (r, c) = cellPair
                            val pair = Pair(r, c)
                            val data = activeTable.tableData
                            if (data != null) {
                                if (isTableMultiSelectMode) {
                                    if (data.selectedCells.contains(pair)) {
                                        data.selectedCells.remove(pair)
                                        if (data.selectedCells.isEmpty()) {
                                            isTableMultiSelectMode = false
                                            onTableMultiSelectChanged?.invoke(false)
                                        }
                                    } else {
                                        data.selectedCells.add(pair)
                                    }
                                    onTableCellToggleSelected?.invoke(r, c)
                                } else {
                                    data.selectedCells.clear()
                                    data.selectedCells.add(pair)
                                    onTableCellSelected?.invoke(r, c)
                                }
                            }
                            vibrateSoft()
                            invalidate()
                            return true
                        } else {
                            if (isTableMultiSelectMode) {
                                activeTable.tableData?.selectedCells?.clear()
                                isTableMultiSelectMode = false
                                onTableMultiSelectChanged?.invoke(false)
                                vibrateSoft()
                                invalidate()
                                return true
                            }
                        }
                    }
                    // Canvas is completely frozen for non-table actions in edit mode
                    return true
                }

                if (touchedElement != null && !isPanMode) {

                    if (touchedElement.groupId != null) {
                        val gid = touchedElement.groupId!!

                        // Is this child already individually selected (e.g. from layers panel)?
                        // Also treat GROUP_EDIT mode as "child already entered".
                        // Also treat it as child entered if the group is currently selected,
                        // so clicking a child of the selected group selects only that child.
                        val isGroupAlreadySelected =
                            selectedElements.size > 1 && selectedElements.all { it.groupId == gid || it.type == ElementType.GROUP }

                        val isChildAlreadySelectedAlone =
                            (selectedElements.size == 1 && selectedElements.first().id == touchedElement.id) || (currentMode == Mode.GROUP_EDIT && activeGroupId == gid) || isGroupAlreadySelected

                        if (isChildAlreadySelectedAlone) {
                            // ── Group-edit mode: drag just this child ────────────────────────
                            // Enter GROUP_EDIT if not already in it, so tapping outside exits.
                            if (currentMode != Mode.GROUP_EDIT) {
                                activeGroupId = gid
                                currentMode = Mode.GROUP_EDIT
                            }
                            // Ensure only this child is selected
                            canvasElements.forEach { it.isSelected = false }
                            selectedElements.clear()
                            touchedElement.isSelected = true
                            selectedElements.add(touchedElement)
                            lastTouchedElement = touchedElement
                            touchStartX = x
                            touchStartY = y
                            currentMode = Mode.DRAG  // drag takes over until ACTION_UP
                            onStartBatchUpdate?.invoke(touchedElement.id, "drag")
                            // Report just the child — NOT the sentinel — so ViewModel keeps
                            // the child individually selected and doesn't collapse to whole group.
                            onElementSelected?.invoke(selectedElements.toList())
                            invalidate()
                            return true
                        } else {
                            // Fresh tap on a grouped child → select whole group as one unit
                            val groupMembers = canvasElements.filter { it.groupId == gid }
                            val sentinel = canvasElements.firstOrNull {
                                it.type == ElementType.GROUP && it.id == gid
                            }
                            canvasElements.forEach { it.isSelected = false }
                            selectedElements.clear()
                            groupMembers.forEach { element ->
                                element.isSelected = true
                                selectedElements.add(element)
                            }
                            sentinel?.isSelected = true
                            touchStartX = x
                            touchStartY = y
                            currentMode = Mode.DRAG
                            vibrateSoft()
                        }
                    } else {
                        if (inSelectionMode) {
                            if (touchedElement.isSelected) {
                                touchedDownElement = touchedElement
                                isDragCandidate = true
                                touchStartX = x
                                touchStartY = y
                                currentMode = Mode.NONE
                            } else {
                                touchedElement.isSelected = true
                                selectedElements.add(touchedElement)
                                onElementSelected?.invoke(selectedElements)
                                vibrateSoft()
                            }
                        } else {
                            if (touchedElement.type == ElementType.TABLE) {
                                canvasElements.forEach { it.isSelected = false }
                                selectedElements.clear()
                                touchedElement.isSelected = true
                                selectedElements.add(touchedElement)
                                lastTouchedElement = touchedElement

                                if (isTableEditMode) {
                                    if (isTableResizeMode) {
                                        val div = getTableDividerAt(touchedElement, x, y)
                                        if (div != null) {
                                            val (isCol, idx) = div
                                            if (isCol) {
                                                draggedColDividerIndex = idx
                                                draggedRowDividerIndex = -1
                                            } else {
                                                draggedRowDividerIndex = idx
                                                draggedColDividerIndex = -1
                                            }
                                            currentMode = Mode.TABLE_RESIZE
                                            touchStartX = x
                                            touchStartY = y
                                            invalidate()
                                            return true
                                        }
                                    }

                                    val cellPair = getTableCellAt(touchedElement, x, y)
                                    if (cellPair != null) {
                                        val (r, c) = cellPair
                                        val pair = Pair(r, c)
                                        val data = touchedElement.tableData
                                        if (data != null) {
                                            if (isTableMultiSelectMode) {
                                                if (data.selectedCells.contains(pair)) {
                                                    data.selectedCells.remove(pair)
                                                    if (data.selectedCells.isEmpty()) {
                                                        isTableMultiSelectMode = false
                                                        onTableMultiSelectChanged?.invoke(false)
                                                    }
                                                } else {
                                                    data.selectedCells.add(pair)
                                                }
                                                onTableCellToggleSelected?.invoke(r, c)
                                            } else {
                                                data.selectedCells.clear()
                                                data.selectedCells.add(pair)
                                                onTableCellSelected?.invoke(r, c)
                                            }
                                        }
                                    }
                                    currentMode = Mode.NONE
                                } else {
                                    lastTouchedElement = touchedElement
                                    currentMode = Mode.DRAG
                                    touchStartX = x
                                    touchStartY = y
                                }
                            } else if (touchedElement.isSelected) {
                                lastTouchedElement = touchedElement
                                currentMode = Mode.DRAG
                                touchStartX = x
                                touchStartY = y
                            } else {
                                canvasElements.forEach { it.isSelected = false }
                                selectedElements.clear()
                                touchedElement.isSelected = true
                                selectedElements.add(touchedElement)
                                lastTouchedElement = touchedElement
                                currentMode = Mode.DRAG
                                touchStartX = x
                                touchStartY = y
                            }
                            vibrateSoft()
                        }
                    }
                    onStartBatchUpdate?.invoke(touchedElement.id, "drag")
                    // Report sentinel to ViewModel when a group is selected,
                    // so it sees 1 unit not N children.
                    val reportForSelection = if (touchedElement.groupId != null) {
                        val sent = canvasElements.firstOrNull {
                            it.type == ElementType.GROUP && it.id == touchedElement.groupId
                        }
                        if (sent != null) listOf(sent) else selectedElements.toList()
                    } else selectedElements.toList()
                    onElementSelected?.invoke(reportForSelection)
                    invalidate()
                    return true
                } else {
                    // Check if we touched inside the bounds of an already selected group (empty space drag support)
                    val isGroupSelected =
                        selectedElements.size > 1 && selectedElements.all { it.groupId != null || it.type == ElementType.GROUP } && selectedElements.mapNotNull { it.groupId }
                            .distinct().size == 1
                    val isTouchInsideGroupBounds = if (isGroupSelected) {
                        val groupBounds = getCombinedSelectedBounds()
                        groupBounds.contains(x, y)
                    } else false

                    if (isTouchInsideGroupBounds && !isPanMode) {
                        // Touch on empty space inside the already selected group -> drag the group as a unit
                        currentMode = Mode.DRAG
                        touchStartX = x
                        touchStartY = y
                        vibrateSoft()
                        selectedElements.firstOrNull()?.let {
                            onStartBatchUpdate?.invoke(it.id, "drag")
                        }
                        invalidate()
                        return true
                    }

                    // isPanMode ON hai, ya empty canvas tap — pan mode set karo
                    val bg =
                        canvasElements.firstOrNull { it.type == ElementType.BACKGROUND && !it.isLocked }
                    if (!isPanMode && bg?.bitmap != null) {
                        canvasElements.forEach { it.isSelected = false }
                        selectedElements.clear()
                        bg.isSelected = true
                        selectedElements.add(bg)
                        onElementSelected?.invoke(selectedElements)
                        currentMode = Mode.DRAG
                        touchStartX = x
                        touchStartY = y
                        invalidate()
                        return true
                    }
                    if (selectedElements.isNotEmpty() && !isPanMode) {
                        canvasElements.forEach { it.isSelected = false }
                        selectedElements.clear()
                        inSelectionMode = false
                        onExitSelectionMode?.invoke()
                        onElementSelected?.invoke(selectedElements)
                        invalidate()
                    } else {
                        // Pan mode ON, ya zoomed in — canvas pan karo
                        if (isCanvasPanLocked) {
                            currentMode = Mode.NONE
                            return true
                        }
                        currentMode = Mode.CANVAS_PAN
                        touchStartX = event.x
                        touchStartY = event.y
                        return true
                    }
                    currentMode = Mode.NONE
                    return true
                }

            }

            MotionEvent.ACTION_MOVE -> {
                // Determine which elements to modify based on current mode and touch context
                val elementsToModify = selectedElements.filter {
                    !it.isLocked
                }
                if (elementsToModify.isEmpty()) {
                    // allow overall canvas pan/zoom
                    when (currentMode) {
                        Mode.CANVAS_PAN -> {
                            if (!isCanvasPanLocked) {
                                if (event.pointerCount == 2) {
                                    // Empty canvas ya pan mode → overall zoom
                                    val newDist = getPinchDistance(event)
                                    val factor = newDist / initialPinchDistance
                                    var newScale =
                                        (initialOverallScale * factor).coerceIn(0.5f, 3.0f)
                                    // Snap to 50%, 100%, 150%, 200%, 250%, 300%
                                    val snapTargets = listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f)
                                    val snapThreshold = 0.03f
                                    val snappedTarget =
                                        snapTargets.firstOrNull { abs(newScale - it) <= snapThreshold }
                                    if (snappedTarget != null) {
                                        if (overallScale != snappedTarget) vibrateSoft()
                                        newScale = snappedTarget
                                    }

                                    // Keep the canvas point under the finger midpoint fixed.
                                    // Derived from onDraw transform:
                                    //   screenPos = overallScale*(p - pivot) + pivot + overallOffset
                                    // Setting screenPos equal before/after scale change gives:
                                    //   newOffset = initialOffset + (focus - pivot) * (1 - newScale/initialScale)
                                    val pivotX = width / 2f
                                    val pivotY = height / 2f
                                    val scaleFactor = newScale / initialOverallScale
                                    overallOffsetX =
                                        initialOffsetXAtPinch + (pinchFocusX - pivotX) * (1f - scaleFactor)
                                    overallOffsetY =
                                        initialOffsetYAtPinch + (pinchFocusY - pivotY) * (1f - scaleFactor)

                                    overallScale = newScale
                                    clampOverallPan()
                                    suppressZoomCallback = true
                                    onZoomChanged?.invoke(overallScale)
                                    suppressZoomCallback = false
                                    invalidate()
                                } else if (event.pointerCount == 1) {
                                    val dx = event.x - touchStartX
                                    val dy = event.y - touchStartY
                                    overallOffsetX += dx
                                    overallOffsetY += dy
                                    checkCanvasPanSnap()
                                    clampOverallPan()
                                    touchStartX = event.x
                                    touchStartY = event.y
                                    invalidate()
                                }
                            }
                        }

                        else -> return true
                    }
                    return true
                }

                if (isDragCandidate && touchedDownElement != null) {
                    val dx = abs(x - touchStartX)
                    val dy = abs(y - touchStartY)
                    if (dx > touchSlop || dy > touchSlop) {
                        // start drag instead of deselect
                        lastTouchedElement = touchedDownElement
                        currentMode = Mode.DRAG
                        isDragCandidate = false
                        touchedDownElement = null
                    }
                }

                when (currentMode) {
                    Mode.DRAG -> {
                        val dx = x - touchStartX
                        val dy = y - touchStartY

                        elementsToModify.forEach { element ->
                            if (element.type == ElementType.BACKGROUND && element.bitmap != null) {
                                val (xRange, yRange) = computeBackgroundPanBounds(element)
                                val newX = element.x + dx
                                val newY = element.y + dy

                                if (!allowFreeDrag) {
                                    // are we still within “no-blank” pan?
                                    if (newX in xRange && newY in yRange) {
                                        element.x = newX.coerceIn(xRange)
                                        element.y = newY.coerceIn(yRange)
                                    } else {
                                        // user pushed past the edge: switch to free-drag from now on
                                        allowFreeDrag = true
                                        element.x = newX
                                        element.y = newY
                                    }
                                } else {
                                    // already in free-drag, just move like normal
                                    element.x = newX
                                    element.y = newY
                                }
                            } else {
                                // regular drag for all elements including tables
                                element.x += dx
                                element.y += dy
                            }
                            onElementChanged?.invoke(element)
                        }

                        // Check alignment for the first selected element (if only one is selected for single drag)
                        if (selectedElements.isNotEmpty()) {
                            checkDragSnap()
                        } else {
                            showVerticalGuide = false
                            showHorizontalGuide = false
                        }

                        touchStartX = x // Update touch start for continuous drag
                        touchStartY = y
                        invalidate()
                    }

                    Mode.MULTI_TOUCH -> {
                        if (event.pointerCount >= 2) {
                            val newPinchDistance = getPinchDistance(event)
                            val newPinchAngle = getPinchAngle(event)

                            // Scale
                            if (initialPinchDistance > 0) {
                                val scaleFactor = newPinchDistance / initialPinchDistance
                                selectedElements.filter { !it.isLocked }.forEach { element ->
                                    // ── Dynamic minimum scale (matches RESIZE handle) ─────────
                                    val minOnScreenPx = 20f * resources.displayMetrics.density
                                    val logicalW =
                                        element.getLocalContentWidth().takeIf { it > 0 } ?: 1f
                                    val minScale =
                                        (minOnScreenPx / (logicalW * scale * overallScale)).coerceAtMost(
                                                0.01f
                                            )

                                    val newScale =
                                        (initialScale * scaleFactor).coerceIn(minScale, 100f)
                                    element.scale = newScale
                                    onElementChanged?.invoke(element)
                                }
                            }

                            // Rotate
                            val rotationDelta = newPinchAngle - initialPinchAngle
                            selectedElements.filter { !it.isLocked }.forEach { element ->
                                element.rotation = (initialRotation + rotationDelta) % 360
                                onElementChanged?.invoke(element)
                            }

                            checkDragSnap()

                            if (selectedElements.size == 1) {
                                checkRotationAlignment(selectedElements.first())
                            } else {
                                checkGroupRotationAlignment()
                            }
                            invalidate()
                        }
                    }

                    Mode.ROTATE -> {
                        if (selectedElements.isEmpty()) return true

                        isRotating = true
                        val currentAngle = atan2(
                            y - initialGroupPivotY, x - initialGroupPivotX
                        ) // Calculate angle relative to initial group pivot
                        val deltaAngle =
                            Math.toDegrees((currentAngle - initialAngle).toDouble()).toFloat()

                        elementsToModify.forEach { element ->
                            val initialRotation =
                                initialElementRotations[element.id] ?: element.rotation
                            element.rotation =
                                (initialRotation + deltaAngle) % 360 // Update element's own rotation

                            // Rotate element's initial position relative to the group pivot
                            val initialRelativeX =
                                initialElementPositionsRelativeToGroupPivot[element.id]?.first ?: 0f
                            val initialRelativeY =
                                initialElementPositionsRelativeToGroupPivot[element.id]?.second
                                    ?: 0f

                            val rotatedRelativeX =
                                (initialRelativeX * cos(Math.toRadians(deltaAngle.toDouble()))) - (initialRelativeY * sin(
                                    Math.toRadians(deltaAngle.toDouble())
                                ))
                            val rotatedRelativeY =
                                (initialRelativeX * sin(Math.toRadians(deltaAngle.toDouble()))) + (initialRelativeY * cos(
                                    Math.toRadians(deltaAngle.toDouble())
                                ))

                            // Update element's position based on the rotated relative position from the *initial* group pivot
                            element.x = initialGroupPivotX + rotatedRelativeX.toFloat()
                            element.y = initialGroupPivotY + rotatedRelativeY.toFloat()

                            onElementChanged?.invoke(element)
                        }

                        // After rotating, re-calculate the combined bounds to check for clamping
                        val newCombinedBounds = getCombinedSelectedBounds()

                        // Clamp the rotated group back into the canvas if it went out
                        var translationX = 0f
                        var translationY = 0f

                        if (newCombinedBounds.left < 0) {
                            translationX = -newCombinedBounds.left
                        } else if (newCombinedBounds.right > canvasWidth) {
                            translationX = canvasWidth - newCombinedBounds.right
                        }

                        if (newCombinedBounds.top < 0) {
                            translationY = -newCombinedBounds.top
                        } else if (newCombinedBounds.bottom > canvasHeight) {
                            translationY = canvasHeight - newCombinedBounds.bottom
                        }

                        if (translationX != 0f || translationY != 0f) {
                            elementsToModify.forEach { element ->
                                element.x += translationX
                                element.y += translationY
                                onElementChanged?.invoke(element)
                            }
                            // Also adjust the initialGroupPivotX and Y to reflect the new clamped position
                            initialGroupPivotX += translationX
                            initialGroupPivotY += translationY
                        }

                        // Check rotation alignment for the first selected element
                        if (selectedElements.size == 1) {
                            checkRotationAlignment(selectedElements.first())
                        } else if (selectedElements.size > 1) {
                            checkGroupRotationAlignment()
                        }

                        invalidate()
                    }


                    Mode.RESIZE -> {
                        if (selectedElements.isEmpty()) return true

                        val combined = getCombinedSelectedBounds()
                        val pivotX = combined.centerX()
                        val pivotY = combined.centerY()

                        val currentDist = hypot(x - pivotX, y - pivotY)

                        // ── Absolute scale math (matches MULTI_TOUCH pinch) ───────────
                        // OLD incremental: newScale = element.scale * (currentDist/startDist)
                        //   — resets startDist each frame via touchStartX=x, causing drift
                        //     and different zoom sensitivity than pinch.
                        // NEW absolute:   newScale = initialScale * (currentDist/resizeStartDist)
                        //   — same formula as MULTI_TOUCH scaleFactor = newPinchDist/initialPinchDist
                        //   — identical zoom level for the same physical finger movement.
                        if (resizeStartDist > 0) {
                            val scaleFactor = currentDist / resizeStartDist
                            elementsToModify.forEach { element ->
                                val initialScale = resizeInitialScales[element.id] ?: element.scale

                                // ── Dynamic minimum scale ─────────────────────────────────
                                // Hard-coding 0.1f as min breaks large bitmaps: a 4000px image
                                // at scale=0.1 is still 400 canvas units wide — too large to
                                // call "minimum". Compute min from a 20dp on-screen threshold.
                                val minOnScreenPx = 20f * resources.displayMetrics.density
                                val logicalW =
                                    element.getLocalContentWidth().takeIf { it > 0 } ?: 1f
                                val minScale =
                                    (minOnScreenPx / (logicalW * scale * overallScale)).coerceAtMost(
                                            0.01f
                                        )  // never go above 0.01 as floor

                                val newScale = (initialScale * scaleFactor).coerceIn(minScale, 100f)
                                element.scale = newScale

                                val lastSignX = resizeLastSignX[element.id] ?: 0f
                                val currSignX = (x - pivotX).sign
                                if (currSignX != 0f && currSignX != lastSignX) {
                                    element.isFlippedX = !element.isFlippedX
                                    onElementChanged?.invoke(element)
                                    resizeLastSignX[element.id] = currSignX
                                }

                                val lastSignY = resizeLastSignY[element.id] ?: 0f
                                val currSignY = (y - pivotY).sign
                                if (currSignY != 0f && currSignY != lastSignY) {
                                    element.isFlippedY = !element.isFlippedY
                                    onElementChanged?.invoke(element)
                                    resizeLastSignY[element.id] = currSignY
                                }

                                onElementChanged?.invoke(element)
                            }
                        }

                        // NOTE: touchStartX/Y are NOT reset here — absolute math doesn't need it.
                        invalidate()
                    }

                    Mode.TABLE_RESIZE -> {
                        val activeTable = selectedElements.firstOrNull { it.type == ElementType.TABLE }
                        val data = activeTable?.tableData
                        if (activeTable != null && data != null) {
                            val totalW = activeTable.logicalContentWidth.takeIf { it > 0f } ?: (canvasWidth * 0.8f)
                            val totalH = activeTable.logicalContentHeight.takeIf { it > 0f } ?: 300f

                            val rad = Math.toRadians((-activeTable.rotation).toDouble())
                            val cosA = kotlin.math.cos(rad).toFloat()
                            val sinA = kotlin.math.sin(rad).toFloat()
                            val dxRaw = (x - touchStartX) / (activeTable.scale.takeIf { it > 0f } ?: 1f)
                            val dyRaw = (y - touchStartY) / (activeTable.scale.takeIf { it > 0f } ?: 1f)
                            val localDx = (dxRaw * cosA - dyRaw * sinA)
                            val localDy = (dxRaw * sinA + dyRaw * cosA)

                            if (draggedColDividerIndex in 1 until data.cols) {
                                val cCount = data.cols
                                if (data.colWidthRatios == null || data.colWidthRatios?.size != cCount) {
                                    val currentCache = activeTable.tableLayoutCache as? com.webscare.urducanvas.common.canvas.cache.TableLayoutCache
                                    if (currentCache != null && currentCache.colWidthsPx.size == cCount) {
                                        val sum = currentCache.colWidthsPx.sum().takeIf { it > 0f } ?: totalW
                                        data.colWidthRatios = currentCache.colWidthsPx.map { it / sum }.toMutableList()
                                    } else {
                                        data.colWidthRatios = MutableList(cCount) { 1f / cCount }
                                    }
                                }
                                data.contentWrap = false
                                val ratios = data.colWidthRatios!!
                                val rawDelta = localDx / totalW
                                val leftIdx = draggedColDividerIndex - 1
                                val rightIdx = draggedColDividerIndex
                                val minRatio = 0.05f

                                val maxNegativeDelta = -(ratios[leftIdx] - minRatio)
                                val maxPositiveDelta = ratios[rightIdx] - minRatio
                                val clampedDelta = rawDelta.coerceIn(maxNegativeDelta, maxPositiveDelta)

                                if (clampedDelta != 0f) {
                                    ratios[leftIdx] += clampedDelta
                                    ratios[rightIdx] -= clampedDelta
                                    activeTable.tableLayoutCache = null
                                    invalidate()
                                }
                                touchStartX = x
                                touchStartY = y
                            } else if (draggedRowDividerIndex in 1 until data.rows) {
                                val rCount = data.rows
                                if (data.rowHeightRatios == null || data.rowHeightRatios?.size != rCount) {
                                    val currentCache = activeTable.tableLayoutCache as? com.webscare.urducanvas.common.canvas.cache.TableLayoutCache
                                    if (currentCache != null && currentCache.rowHeightsPx.size == rCount) {
                                        val sum = currentCache.rowHeightsPx.sum().takeIf { it > 0f } ?: totalH
                                        data.rowHeightRatios = currentCache.rowHeightsPx.map { it / sum }.toMutableList()
                                    } else {
                                        data.rowHeightRatios = MutableList(rCount) { 1f / rCount }
                                    }
                                }
                                data.contentWrap = false
                                val ratios = data.rowHeightRatios!!
                                val rawDelta = localDy / totalH
                                val topIdx = draggedRowDividerIndex - 1
                                val botIdx = draggedRowDividerIndex
                                val minRatio = 0.05f

                                val maxNegativeDelta = -(ratios[topIdx] - minRatio)
                                val maxPositiveDelta = ratios[botIdx] - minRatio
                                val clampedDelta = rawDelta.coerceIn(maxNegativeDelta, maxPositiveDelta)

                                if (clampedDelta != 0f) {
                                    ratios[topIdx] += clampedDelta
                                    ratios[botIdx] -= clampedDelta
                                    activeTable.tableLayoutCache = null
                                    invalidate()
                                }
                                touchStartX = x
                                touchStartY = y
                            }
                        }
                    }

                    Mode.NONE -> {
                        // This block handles potential tap-and-hold to drag if not immediately picking up an icon/element
                    }

                    Mode.GROUP_EDIT -> {
                        // This block handles potential tap-and-hold to drag if not immediately picking up an icon/element
                    }

                    Mode.CANVAS_PAN -> {
                        if (!isCanvasPanLocked) {
                            if (event.pointerCount == 2) {
                                val newDist = getPinchDistance(event)
                                val factor = newDist / initialPinchDistance
                                var newScale = (initialOverallScale * factor).coerceIn(0.5f, 3.0f)
                                // Snap to 50%, 100%, 150%, 200%, 250%, 300%
                                val snapTargets = listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f)
                                val snapThreshold = 0.03f
                                val snappedTarget =
                                    snapTargets.firstOrNull { abs(newScale - it) <= snapThreshold }
                                if (snappedTarget != null) {
                                    if (overallScale != snappedTarget) vibrateSoft()
                                    newScale = snappedTarget
                                }

                                // Keep the canvas point under the finger midpoint fixed.
                                // Derived from onDraw transform:
                                //   screenPos = overallScale*(p - pivot) + pivot + overallOffset
                                // Setting screenPos equal before/after scale change gives:
                                //   newOffset = initialOffset + (focus - pivot) * (1 - newScale/initialScale)
                                val pivotX = width / 2f
                                val pivotY = height / 2f
                                val scaleFactor = newScale / initialOverallScale
                                overallOffsetX =
                                    initialOffsetXAtPinch + (pinchFocusX - pivotX) * (1f - scaleFactor)
                                overallOffsetY =
                                    initialOffsetYAtPinch + (pinchFocusY - pivotY) * (1f - scaleFactor)

                                overallScale = newScale
                                clampOverallPan()
                                suppressZoomCallback = true
                                onZoomChanged?.invoke(overallScale)
                                suppressZoomCallback = false
                                invalidate()
                            } else if (event.pointerCount == 1) {
                                val dx = event.x - touchStartX
                                val dy = event.y - touchStartY
                                overallOffsetX += dx
                                overallOffsetY += dy
                                checkCanvasPanSnap()
                                clampOverallPan()
                                touchStartX = event.x
                                touchStartY = event.y
                                invalidate()
                            }
                        }
                    }

                    Mode.TRANSFORM -> {
                        if (selectedElements.isEmpty()) return true

                        val dx = x - touchStartX
                        val dy = y - touchStartY

                        selectedElements.forEach { element ->
                            val (initialW, initialH) = initialElementSizes[element.id]
                                ?: return@forEach

                            val rad = Math.toRadians(element.rotation.toDouble())
                            val cosA = kotlin.math.cos(rad).toFloat()
                            val sinA = kotlin.math.sin(rad).toFloat()

                            val elemScale = if (element.scale > 0f) element.scale else 1f
                            val localDx = (dx * cosA + dy * sinA) / elemScale
                            val localDy = (-dx * sinA + dy * cosA) / elemScale

                            if (element.type == ElementType.TEXT) {
                                val initTextSize =
                                    initialTextSizes[element.id] ?: element.paintTextSize
                                val initUnwrappedW = initialUnwrappedWidths[element.id]
                                    ?: element.getNaturalUnwrappedWidth()
                                val initMinWordW =
                                    initialMinWordWidths[element.id] ?: element.getMinWordWidth()

                                val reqW = initialW - localDx

                                if (reqW > initUnwrappedW && initUnwrappedW > 0f) {
                                    val scaleFactor = reqW / initUnwrappedW
                                    val newTextSize =
                                        (initTextSize * scaleFactor).coerceIn(12f, 500f)
                                    element.paintTextSize = newTextSize
                                    element.updatePaintProperties()

                                    val fitW = element.getNaturalUnwrappedWidth()
                                    element.boxWidth = fitW
                                    element.boxHeight = element.getNaturalContentHeight(fitW)
                                } else if (reqW < initMinWordW && initMinWordW > 0f) {
                                    val scaleFactor = (reqW / initMinWordW).coerceAtLeast(0.1f)
                                    val newTextSize =
                                        (initTextSize * scaleFactor).coerceIn(12f, 500f)
                                    element.paintTextSize = newTextSize
                                    element.updatePaintProperties()

                                    val fitW = element.getMinWordWidth()
                                    element.boxWidth = fitW
                                    element.boxHeight = element.getNaturalContentHeight(fitW)
                                } else {
                                    element.paintTextSize = initTextSize
                                    element.updatePaintProperties()

                                    val targetW = reqW.coerceIn(initMinWordW, initUnwrappedW)
                                    element.boxWidth = targetW
                                    element.boxHeight = element.getNaturalContentHeight(targetW)
                                }

                                element.logicalContentWidth = element.boxWidth ?: 0f
                                element.logicalContentHeight = element.boxHeight ?: 0f
                            } else {
                                val newW = (initialW - localDx).coerceAtLeast(10f)
                                val newH = (initialH + localDy).coerceAtLeast(10f)

                                element.logicalContentWidth = newW
                                element.logicalContentHeight = newH
                            }
                            onElementChanged?.invoke(element)
                        }

                        invalidate()
                        return true
                    }

                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // A second (or later) finger lifted — only reset pinch state.
                // Do NOT run the full ACTION_UP logic here; doing so causes the
                // framework to recycle the same TouchTarget twice, which throws
                // IllegalStateException: already recycled once on Android 15.
                if (currentMode == Mode.CANVAS_PAN || currentMode == Mode.MULTI_TOUCH) {
                    initialPinchDistance = 0f
                    initialPinchAngle = 0f
                    initialOverallScale = overallScale
                    pinchFocusX = 0f
                    pinchFocusY = 0f
                    initialOffsetXAtPinch = overallOffsetX
                    initialOffsetYAtPinch = overallOffsetY

                    // Update touchStart points to the remaining finger's coordinates
                    // if we were in CANVAS_PAN mode to prevent a jump/snap when the remaining finger moves.
                    if (currentMode == Mode.CANVAS_PAN && event.pointerCount == 2) {
                        val remainingIndex = if (event.actionIndex == 0) 1 else 0
                        touchStartX = event.getX(remainingIndex)
                        touchStartY = event.getY(remainingIndex)
                    }
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (gestureStartZoom != null && gestureStartPanX != null && gestureStartPanY != null) {
                    val startZoom = gestureStartZoom!!
                    val startPanX = gestureStartPanX!!
                    val startPanY = gestureStartPanY!!
                    gestureStartZoom = null
                    gestureStartPanX = null
                    gestureStartPanY = null

                    if (!isRestoringTransformFromUndoRedo) {
                        val zoomChanged = abs(overallScale - startZoom) > 0.01f
                        val panXChanged = abs(overallOffsetX - startPanX) > 2f
                        val panYChanged = abs(overallOffsetY - startPanY) > 2f
                        if (zoomChanged || panXChanged || panYChanged) {
                            onTransformChanged?.invoke(
                                startZoom,
                                startPanX,
                                startPanY,
                                overallScale,
                                overallOffsetX,
                                overallOffsetY
                            )
                        }
                    }
                }
                showVerticalGuide = false
                showHorizontalGuide = false
                activeSnapLines.clear()
                showRotationVerticalGuide = false // Reset rotation guides on ACTION_UP
                showRotationHorizontalGuide = false // Reset rotation guides on ACTION_UP
                showCanvasCenterVerticalSnap = false
                showCanvasCenterHorizontalSnap = false
                if (currentMode == Mode.CANVAS_PAN) {
                    currentMode = Mode.NONE
                }

                if (currentMode == Mode.TRANSFORM) {
                    selectedElements.forEach {
                        onElementChanged?.invoke(it)
                        onEndBatchUpdate?.invoke(it.id)
                    }
                }

                if (currentMode == Mode.DRAG || currentMode == Mode.ROTATE || currentMode == Mode.RESIZE || currentMode == Mode.TABLE_RESIZE) {
                    selectedElements.filter { !it.isLocked }.forEach {
                        onElementChanged?.invoke(it)
                        onEndBatchUpdate?.invoke(it.id)
                    }
                    draggedColDividerIndex = -1
                    draggedRowDividerIndex = -1
                }
                if (isDragCandidate && touchedDownElement != null) {
                    val element = touchedDownElement!!
                    element.isSelected = false
                    selectedElements.remove(element)
                    if (selectedElements.isEmpty()) {
                        inSelectionMode = false
                        onExitSelectionMode?.invoke()
                    }
                    onElementSelected?.invoke(selectedElements)
                    invalidate()
                }
                isDragCandidate = false
                touchedDownElement = null

                iconTouched = null
                initialPinchDistance = 0f
                initialPinchAngle = 0f
                initialScale = 1f
                initialRotation = 0f
                pinchFocusX = 0f
                pinchFocusY = 0f
                initialOffsetXAtPinch = 0f
                initialOffsetYAtPinch = 0f
                resizeInitialScales.clear()
                resizeStartDist = 0f
                initialElementRotations.clear()
                initialElementPositionsRelativeToGroupPivot.clear() // Clear initial positions on action up
                initialAngle = 0f
                initialGroupPivotX = 0f
                initialGroupPivotY = 0f
                if (currentMode != Mode.GROUP_EDIT) {
                    lastTouchedElement = null
                    // If we just finished dragging a single group child, restore GROUP_EDIT
                    // so the user stays "inside" the group (Photoshop behaviour).
                    // Tap outside will exit GROUP_EDIT via the existing bounds check.
                    if (activeGroupId != null) {
                        currentMode = Mode.GROUP_EDIT
                    } else {
                        currentMode = Mode.NONE
                    }
                }
                clampOverallPan()
                isRotating = false
                invalidate()
                return true
            }
        }
        // This view handles all touch events — never fall through to super,
        // as that can re-enter the parent's touch dispatch chain and contribute
        // to the TouchTarget double-recycle crash on Android 14.
        return true
    }

    /**
     * Called during CANVAS_PAN single-finger drag.
     * Snaps overallOffsetX/Y to zero (canvas centered) when the canvas center
     * comes within [canvasSnapThresholdPx] of the view center, and shows the
     * dashed cyan guide lines.  Vibrates once when snapping occurs.
     */
    private fun checkCanvasPanSnap() {
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

    private fun clampOverallPan() {

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

    private fun canvasToView(cx: Float, cy: Float): Pair<Float, Float> {
        val scaledWidth = canvasWidth * scale
        val scaledHeight = canvasHeight * scale
        val ox = (width - scaledWidth) / 2f   // offsetX
        val oy = (height - scaledHeight) / 2f   // offsetY
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

        val roundedTop = Math.round(top).toFloat()
        val roundedBottom = Math.round(bottom).toFloat()
        val roundedLeft = Math.round(left).toFloat()
        val roundedRight = Math.round(right).toFloat()

        var x = left
        while (x <= right + 0.5f) {
            val rx = Math.round(x).toFloat()
            canvas.drawLine(rx, roundedTop, rx, roundedBottom, gridPaint)
            x += stepPx
        }

        var y = top
        while (y <= bottom + 0.5f) {
            val ry = Math.round(y).toFloat()
            canvas.drawLine(roundedLeft, ry, roundedRight, ry, gridPaint)
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

    private fun drawCanvasShadow(canvas: Canvas) {

        val rect = RectF(
            0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat()
        )

        val spread = 40f // increase spread for softness

        val shadowRect = RectF(
            rect.left - spread, rect.top - spread, rect.right + spread, rect.bottom + spread
        )

        val isDark = isCanvasBgDark()
        canvasShadowPaint.color = if (isDark) Color.WHITE else Color.BLACK
        canvasShadowPaint.alpha = if (isDark) 50 else 30

        canvas.drawRoundRect(
            shadowRect, 30f, 30f, canvasShadowPaint
        )
    }

    private fun handleRulerTouch(event: MotionEvent, x: Float, y: Float): Boolean {
        if (rulerState == com.webscare.urducanvas.common.canvas.enums.RulerState.OFF) return false

        val hitMarginPx = 24f.dpToPx()
        val hitMarginCanvas = (hitMarginPx / (scale * overallScale)).coerceAtLeast(12f)

        val isTwoSides =
            rulerState == com.webscare.urducanvas.common.canvas.enums.RulerState.TWO_SIDES
        val isFourSides =
            rulerState == com.webscare.urducanvas.common.canvas.enums.RulerState.FOUR_SIDES

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                var touchedRuler = DraggingRuler.NONE
                var minDistance = Float.MAX_VALUE

                if (isTwoSides || isFourSides) {
                    val distLeft = abs(x - leftRulerX)
                    if (distLeft <= hitMarginCanvas && distLeft < minDistance) {
                        minDistance = distLeft
                        touchedRuler = DraggingRuler.LEFT
                    }

                    val distTop = abs(y - topRulerY)
                    if (distTop <= hitMarginCanvas && distTop < minDistance) {
                        minDistance = distTop
                        touchedRuler = DraggingRuler.TOP
                    }
                }

                if (isFourSides) {
                    val distRight = abs(x - rightRulerX)
                    if (distRight <= hitMarginCanvas && distRight < minDistance) {
                        minDistance = distRight
                        touchedRuler = DraggingRuler.RIGHT
                    }

                    val distBottom = abs(y - bottomRulerY)
                    if (distBottom <= hitMarginCanvas && distBottom < minDistance) {
                        minDistance = distBottom
                        touchedRuler = DraggingRuler.BOTTOM
                    }
                }

                if (touchedRuler != DraggingRuler.NONE) {
                    activeDraggingRuler = touchedRuler
                    vibrateSoft()
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeDraggingRuler != DraggingRuler.NONE) {
                    val snapThresholdPx = 8f / (scale * overallScale)
                    when (activeDraggingRuler) {
                        DraggingRuler.LEFT -> {
                            var targetX = x.coerceIn(0f, canvasWidth.toFloat())
                            val candidates =
                                mutableListOf(0f, canvasWidth / 2f, canvasWidth.toFloat())
                            if (showGrid) {
                                var gx = 0f; while (gx <= canvasWidth) {
                                    candidates.add(gx); gx += 50f
                                }
                            }
                            val snapMatch =
                                candidates.firstOrNull { abs(targetX - it) <= snapThresholdPx }
                            if (snapMatch != null) {
                                if (leftRulerX != snapMatch) vibrateSoft()
                                targetX = snapMatch
                            }
                            leftRulerX = targetX
                        }

                        DraggingRuler.RIGHT -> {
                            var targetX = x.coerceIn(0f, canvasWidth.toFloat())
                            val candidates =
                                mutableListOf(0f, canvasWidth / 2f, canvasWidth.toFloat())
                            if (showGrid) {
                                var gx = 0f; while (gx <= canvasWidth) {
                                    candidates.add(gx); gx += 50f
                                }
                            }
                            val snapMatch =
                                candidates.firstOrNull { abs(targetX - it) <= snapThresholdPx }
                            if (snapMatch != null) {
                                if (rightRulerX != snapMatch) vibrateSoft()
                                targetX = snapMatch
                            }
                            rightRulerX = targetX
                        }

                        DraggingRuler.TOP -> {
                            var targetY = y.coerceIn(0f, canvasHeight.toFloat())
                            val candidates =
                                mutableListOf(0f, canvasHeight / 2f, canvasHeight.toFloat())
                            if (showGrid) {
                                var gy = 0f; while (gy <= canvasHeight) {
                                    candidates.add(gy); gy += 50f
                                }
                            }
                            val snapMatch =
                                candidates.firstOrNull { abs(targetY - it) <= snapThresholdPx }
                            if (snapMatch != null) {
                                if (topRulerY != snapMatch) vibrateSoft()
                                targetY = snapMatch
                            }
                            topRulerY = targetY
                        }

                        DraggingRuler.BOTTOM -> {
                            var targetY = y.coerceIn(0f, canvasHeight.toFloat())
                            val candidates =
                                mutableListOf(0f, canvasHeight / 2f, canvasHeight.toFloat())
                            if (showGrid) {
                                var gy = 0f; while (gy <= canvasHeight) {
                                    candidates.add(gy); gy += 50f
                                }
                            }
                            val snapMatch =
                                candidates.firstOrNull { abs(targetY - it) <= snapThresholdPx }
                            if (snapMatch != null) {
                                if (bottomRulerY != snapMatch) vibrateSoft()
                                targetY = snapMatch
                            }
                            bottomRulerY = targetY
                        }

                        else -> {}
                    }
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeDraggingRuler != DraggingRuler.NONE) {
                    activeDraggingRuler = DraggingRuler.NONE
                    invalidate()
                    return true
                }
            }
        }
        return false
    }

    private fun drawRuler(canvas: Canvas) {
        if (rulerState == com.webscare.urducanvas.common.canvas.enums.RulerState.OFF) return

        val rulerThicknessPx = 16f.dpToPx()
        val majorTickLen = rulerThicknessPx * 0.6f
        val minorTickLen = rulerThicknessPx * 0.3f

        // Canvas boundaries in view space
        val (canvasLeft, canvasTop) = canvasToView(0f, 0f)
        val (canvasRight, canvasBottom) = canvasToView(
            canvasWidth.toFloat(), canvasHeight.toFloat()
        )

        val rawSpacing = canvasWidth / 8f
        val tickSpacing = niceNumber(rawSpacing)
        val stepViewPx = tickSpacing * scale * overallScale

        if (stepViewPx < 4f) return

        val isTwoSides =
            rulerState == com.webscare.urducanvas.common.canvas.enums.RulerState.TWO_SIDES
        val isFourSides =
            rulerState == com.webscare.urducanvas.common.canvas.enums.RulerState.FOUR_SIDES

        if (isTwoSides || isFourSides) {
            drawHorizontalRulerStrip(
                canvas,
                topRulerY,
                canvasLeft,
                canvasRight,
                tickSpacing,
                stepViewPx,
                majorTickLen,
                minorTickLen,
                rulerThicknessPx,
                isBottom = false
            )
            drawVerticalRulerStrip(
                canvas,
                leftRulerX,
                canvasTop,
                canvasBottom,
                tickSpacing,
                stepViewPx,
                majorTickLen,
                minorTickLen,
                rulerThicknessPx,
                isRight = false
            )
        }

        if (isFourSides) {
            drawHorizontalRulerStrip(
                canvas,
                bottomRulerY,
                canvasLeft,
                canvasRight,
                tickSpacing,
                stepViewPx,
                majorTickLen,
                minorTickLen,
                rulerThicknessPx,
                isBottom = true
            )
            drawVerticalRulerStrip(
                canvas,
                rightRulerX,
                canvasTop,
                canvasBottom,
                tickSpacing,
                stepViewPx,
                majorTickLen,
                minorTickLen,
                rulerThicknessPx,
                isRight = true
            )
        }
    }

    private fun drawHorizontalRulerStrip(
        canvas: Canvas,
        rulerCanvasY: Float,
        canvasLeft: Float,
        canvasRight: Float,
        tickSpacing: Float,
        stepViewPx: Float,
        majorTickLen: Float,
        minorTickLen: Float,
        rulerThicknessPx: Float,
        isBottom: Boolean
    ) {
        val (_, viewY) = canvasToView(0f, rulerCanvasY)

        val topPx = if (isBottom) viewY - rulerThicknessPx else viewY
        val bottomPx = if (isBottom) viewY else viewY + rulerThicknessPx

        canvas.drawRect(canvasLeft, topPx, canvasRight, bottomPx, rulerBgPaint)
        canvas.drawLine(canvasLeft, viewY, canvasRight, viewY, rulerPaint)

        var tickIndex = 0
        var x = canvasLeft
        val tickBaselineY = if (isBottom) topPx else bottomPx
        val tickDir = if (isBottom) 1f else -1f

        while (x <= canvasRight + 0.5f) {
            val isMajor = tickIndex % 5 == 0
            val tickLen = if (isMajor) majorTickLen else minorTickLen
            val tickEnd = tickBaselineY + (tickLen * tickDir)

            canvas.drawLine(x, tickBaselineY, x, tickEnd, rulerPaint)

            if (isMajor) {
                val textY = if (isBottom) {
                    tickEnd + rulerTextPaint.textSize
                } else {
                    tickEnd - 2f
                }
                canvas.drawText(
                    "${(tickIndex * tickSpacing).toInt()}", x, textY, rulerTextPaint
                )
            }
            x += stepViewPx
            tickIndex++
        }

        // ── Active Selection Value Indicator Lines on Horizontal Scale ──
        if (selectedElements.isNotEmpty()) {
            val selBounds = if (selectedElements.size == 1) {
                getElementAxisAlignedBounds(selectedElements.first())
            } else {
                getCombinedSelectedBounds()
            }
            val (vLeft, _) = canvasToView(selBounds.left, 0f)
            val (vCenter, _) = canvasToView(selBounds.centerX(), 0f)
            val (vRight, _) = canvasToView(selBounds.right, 0f)

            listOf(vLeft to false, vCenter to true, vRight to false).forEach { (vx, isCenter) ->
                if (vx in (canvasLeft - 1f)..(canvasRight + 1f)) {
                    if (isCenter) {
                        rulerIndicatorPaint.pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
                    } else {
                        rulerIndicatorPaint.pathEffect = null
                    }
                    canvas.drawLine(vx, topPx, vx, bottomPx, rulerIndicatorPaint)
                }
            }
        }

        // ── Active Guideline Pointer & Badge ──
        val activeGuidelineX = when (activeDraggingRuler) {
            DraggingRuler.LEFT -> leftRulerX
            DraggingRuler.RIGHT -> rightRulerX
            else -> null
        }
        if (activeGuidelineX != null) {
            val (vGuideX, _) = canvasToView(activeGuidelineX, 0f)
            if (vGuideX in (canvasLeft - 1f)..(canvasRight + 1f)) {
                canvas.drawLine(vGuideX, topPx, vGuideX, bottomPx, rulerIndicatorPaint)
                val label = "X: ${activeGuidelineX.toInt()}"
                val textWidth = rulerBadgeTextPaint.measureText(label)
                val badgeWidth = textWidth + 8f.dpToPx()
                val badgeHeight = rulerThicknessPx * 0.85f
                val minLeft = canvasLeft
                val maxLeft = (canvasRight - badgeWidth).coerceAtLeast(minLeft)
                val badgeLeft = (vGuideX - badgeWidth / 2f).coerceIn(minLeft, maxLeft)
                val badgeTop = topPx + (rulerThicknessPx - badgeHeight) / 2f
                val rect =
                    RectF(badgeLeft, badgeTop, badgeLeft + badgeWidth, badgeTop + badgeHeight)
                canvas.drawRoundRect(rect, 4f.dpToPx(), 4f.dpToPx(), rulerBadgeBgPaint)
                canvas.drawText(
                    label,
                    rect.centerX(),
                    rect.centerY() + rulerBadgeTextPaint.textSize / 3f,
                    rulerBadgeTextPaint
                )
            }
        }
    }

    private fun drawVerticalRulerStrip(
        canvas: Canvas,
        rulerCanvasX: Float,
        canvasTop: Float,
        canvasBottom: Float,
        tickSpacing: Float,
        stepViewPx: Float,
        majorTickLen: Float,
        minorTickLen: Float,
        rulerThicknessPx: Float,
        isRight: Boolean
    ) {
        val (viewX, _) = canvasToView(rulerCanvasX, 0f)

        val leftPx = if (isRight) viewX - rulerThicknessPx else viewX
        val rightPx = if (isRight) viewX else viewX + rulerThicknessPx

        canvas.drawRect(leftPx, canvasTop, rightPx, canvasBottom, rulerBgPaint)
        canvas.drawLine(viewX, canvasTop, viewX, canvasBottom, rulerPaint)

        var tickIndex = 0
        var y = canvasTop
        val tickBaselineX = if (isRight) leftPx else rightPx
        val tickDir = if (isRight) 1f else -1f

        while (y <= canvasBottom + 0.5f) {
            val isMajor = tickIndex % 5 == 0
            val tickLen = if (isMajor) majorTickLen else minorTickLen
            val tickEnd = tickBaselineX + (tickLen * tickDir)

            canvas.drawLine(tickBaselineX, y, tickEnd, y, rulerPaint)

            if (isMajor && tickIndex > 0) {
                canvas.withSave {
                    val labelX = if (isRight) tickEnd + 2f else tickEnd - 2f
                    rotate(-90f, labelX, y)
                    canvas.drawText(
                        "${(tickIndex * tickSpacing).toInt()}",
                        labelX,
                        y + rulerTextPaint.textSize / 3f,
                        rulerTextPaint
                    )
                }
            }
            y += stepViewPx
            tickIndex++
        }

        // ── Active Selection Value Indicator Lines on Vertical Scale ──
        if (selectedElements.isNotEmpty()) {
            val selBounds = if (selectedElements.size == 1) {
                getElementAxisAlignedBounds(selectedElements.first())
            } else {
                getCombinedSelectedBounds()
            }
            val (_, vTop) = canvasToView(0f, selBounds.top)
            val (_, vCenter) = canvasToView(0f, selBounds.centerY())
            val (_, vBottom) = canvasToView(0f, selBounds.bottom)

            listOf(vTop to false, vCenter to true, vBottom to false).forEach { (vy, isCenter) ->
                if (vy in (canvasTop - 1f)..(canvasBottom + 1f)) {
                    if (isCenter) {
                        rulerIndicatorPaint.pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
                    } else {
                        rulerIndicatorPaint.pathEffect = null
                    }
                    canvas.drawLine(leftPx, vy, rightPx, vy, rulerIndicatorPaint)
                }
            }
        }

        // ── Active Guideline Pointer & Badge ──
        val activeGuidelineY = when (activeDraggingRuler) {
            DraggingRuler.TOP -> topRulerY
            DraggingRuler.BOTTOM -> bottomRulerY
            else -> null
        }
        if (activeGuidelineY != null) {
            val (_, vGuideY) = canvasToView(0f, activeGuidelineY)
            if (vGuideY in (canvasTop - 1f)..(canvasBottom + 1f)) {
                canvas.drawLine(leftPx, vGuideY, rightPx, vGuideY, rulerIndicatorPaint)
                val label = "Y: ${activeGuidelineY.toInt()}"
                val textWidth = rulerBadgeTextPaint.measureText(label)
                val badgeWidth = textWidth + 8f.dpToPx()
                val badgeHeight = rulerThicknessPx * 0.85f
                val minTop = canvasTop
                val maxTop = (canvasBottom - badgeHeight).coerceAtLeast(minTop)
                val badgeTop = (vGuideY - badgeHeight / 2f).coerceIn(minTop, maxTop)
                val badgeLeft = leftPx + (rulerThicknessPx - badgeWidth) / 2f
                val rect =
                    RectF(badgeLeft, badgeTop, badgeLeft + badgeWidth, badgeTop + badgeHeight)
                canvas.drawRoundRect(rect, 4f.dpToPx(), 4f.dpToPx(), rulerBadgeBgPaint)
                canvas.drawText(
                    label,
                    rect.centerX(),
                    rect.centerY() + rulerBadgeTextPaint.textSize / 3f,
                    rulerBadgeTextPaint
                )
            }
        }
    }

    fun setRulerState(state: com.webscare.urducanvas.common.canvas.enums.RulerState) {
        rulerState = state
        if (state != com.webscare.urducanvas.common.canvas.enums.RulerState.OFF) {
            if (bottomRulerY == 0f || bottomRulerY > canvasHeight.toFloat()) {
                bottomRulerY = canvasHeight.toFloat()
            }
            if (rightRulerX == 0f || rightRulerX > canvasWidth.toFloat()) {
                rightRulerX = canvasWidth.toFloat()
            }
        }
        invalidate()
    }

    fun setGridEnabled(enabled: Boolean) {
        showGrid = enabled
        invalidate()
    }

    fun setRulerEnabled(enabled: Boolean) {
        setRulerState(if (enabled) com.webscare.urducanvas.common.canvas.enums.RulerState.TWO_SIDES else com.webscare.urducanvas.common.canvas.enums.RulerState.OFF)
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

    var onTransformChanged: ((oldZoom: Float, oldPanX: Float, oldPanY: Float, newZoom: Float, newPanX: Float, newPanY: Float) -> Unit)? =
        null
    private var gestureStartZoom: Float? = null
    private var gestureStartPanX: Float? = null
    private var gestureStartPanY: Float? = null
    var isRestoringTransformFromUndoRedo = false

    fun setZoomAndPan(zoom: Float, panX: Float, panY: Float) {
        isRestoringTransformFromUndoRedo = true
        overallScale = zoom.coerceIn(0.5f, 3.0f)
        overallOffsetX = panX
        overallOffsetY = panY
        clampOverallPan()
        invalidate()
        isRestoringTransformFromUndoRedo = false
    }

    fun resetZoomAndPan() {
        if (isRestoringTransformFromUndoRedo) return
        val oldZoom = overallScale
        val oldPanX = overallOffsetX
        val oldPanY = overallOffsetY
        overallScale = 1f
        overallOffsetX = 0f
        overallOffsetY = 0f
        clampOverallPan()
        invalidate()
        if (oldZoom != 1f || oldPanX != 0f || oldPanY != 0f) {
            onTransformChanged?.invoke(oldZoom, oldPanX, oldPanY, 1f, 0f, 0f)
        }
    }

    fun setZoomLevel(zoom: Float) {
        if (suppressZoomCallback || isRestoringTransformFromUndoRedo) return
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
    fun getCurrentPanX(): Float = overallOffsetX
    fun getCurrentPanY(): Float = overallOffsetY

    /**
     * Returns true if the canvas is currently centered in both axes
     * (overallOffset within snap threshold on both axes).
     * Callers can use this to show a "Centered" badge in the toolbar.
     */
    fun isCanvasCentered(): Boolean =
        abs(overallOffsetX) <= canvasSnapThresholdPx && abs(overallOffsetY) <= canvasSnapThresholdPx

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
        // Cancel pending async adjustment child jobs to prevent leaks without destroying scope
        adjustmentScope.coroutineContext[Job]?.cancelChildren()
        pendingAdjustmentJobs.values.forEach { it.cancel() }
        pendingAdjustmentJobs.clear()
        // Release display-proxy bitmaps that are not owned by active elements
        val owned = canvasElements.mapNotNullTo(HashSet<Bitmap>()) { it.bitmap }
            .also { set -> canvasElements.forEach { e -> e.cachedAdjustedBitmap?.let(set::add) } }
        displayBitmapCache.values.forEach { entry ->
            if (entry.bitmap !in owned && !entry.bitmap.isRecycled) {
                entry.bitmap.recycle()
            }
        }
        displayBitmapCache.clear()
    }
}