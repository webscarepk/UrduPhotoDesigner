package com.example.urduphotodesigner.common.views

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.Xfermode
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
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.enums.BlendType
import com.example.urduphotodesigner.common.canvas.enums.BrushStyle
import com.example.urduphotodesigner.common.canvas.enums.ElementType
import com.example.urduphotodesigner.common.canvas.enums.GradientType
import com.example.urduphotodesigner.common.canvas.enums.HAlign
import com.example.urduphotodesigner.common.canvas.enums.LabelShape
import com.example.urduphotodesigner.common.canvas.enums.LetterCasing
import com.example.urduphotodesigner.common.canvas.enums.ListStyle
import com.example.urduphotodesigner.common.canvas.enums.Mode
import com.example.urduphotodesigner.common.canvas.enums.MultiAlignMode
import com.example.urduphotodesigner.common.canvas.enums.ShapeType
import com.example.urduphotodesigner.common.canvas.enums.TextAlignment
import com.example.urduphotodesigner.common.canvas.enums.TextDecoration
import com.example.urduphotodesigner.common.canvas.enums.VAlign
import com.example.urduphotodesigner.common.canvas.model.BrushSettings
import com.example.urduphotodesigner.common.canvas.model.CanvasElement
import com.example.urduphotodesigner.common.canvas.model.ExportFormat
import com.example.urduphotodesigner.common.canvas.model.ExportOptions
import com.example.urduphotodesigner.common.canvas.model.ExportQuality
import com.example.urduphotodesigner.common.canvas.model.ExportResolution
import com.example.urduphotodesigner.common.canvas.model.GradientItem
import com.example.urduphotodesigner.common.canvas.model.StrokeData
import com.example.urduphotodesigner.common.canvas.sealed.ImageFilter
import com.example.urduphotodesigner.common.utils.BrushRenderUtils
import com.example.urduphotodesigner.common.utils.BrushRenderUtils.drawBrushStroke
import com.example.urduphotodesigner.common.utils.BrushRenderUtils.drawTaperedPenStroke
import com.example.urduphotodesigner.common.utils.BrushRenderUtils.makeStrokePaint
import com.example.urduphotodesigner.common.utils.ImageAdjustmentHelper
import com.example.urduphotodesigner.common.utils.ImageProcessor
import com.example.urduphotodesigner.common.utils.ShapeRenderUtils.drawShape
import com.example.urduphotodesigner.common.utils.Utils.vibrateSoft
import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.di.GsonEntryPoint
import com.google.gson.Gson
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.core.graphics.withRotation

class CanvasView @JvmOverloads constructor(
    context: Context,
    private var canvasWidth: Int = 300,
    private var canvasHeight: Int = 300,
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
    var onDrawStrokeCompleted: ((CanvasElement) -> Unit)? = null
) : View(context, attrs) {

    private val gson: Gson by lazy {
        EntryPointAccessors.fromApplication(context, GsonEntryPoint::class.java).gson()
    }
    private var gestureDetector: GestureDetector

    private var colorPickerBitmap: Bitmap? = null
    private var isColorPickerMode = false

    private var currentPath: Path? = null
    private var currentPaint: Paint? = null
    private var activeDrawElement: CanvasElement? = null

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

    private val canvasElements = mutableListOf<CanvasElement>()
    private lateinit var backgroundElement: CanvasElement

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

    private val resizeLastSignX = mutableMapOf<String, Float>()
    private val resizeLastSignY = mutableMapOf<String, Float>()

    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    // Overall canvas zoom & pan
    private var overallScale = 1f
    private var overallOffsetX = 0f
    private var overallOffsetY = 0f

    private var initialOverallScale = 1f

    private val lastDrawnIconRect = mutableMapOf<String, RectF>()

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
        typeface = ResourcesCompat.getFont(context, R.font.regular)
    }

    private val rotationLabelPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val drawingModeOverlayPaint = Paint().apply {
        color = Color.argb(120, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private var showVerticalGuide = false
    private var showHorizontalGuide = false
    private var showRotationVerticalGuide = false
    private var showRotationHorizontalGuide = false

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

    private var selectedElements: CopyOnWriteArrayList<CanvasElement> = CopyOnWriteArrayList()
    private var lastTouchedElement: CanvasElement? = null

    private fun Float.dpToPx(): Float = this * resources.displayMetrics.density

    fun resizeCanvas(newWidth: Int, newHeight: Int) {
        this.canvasWidth = newWidth
        this.canvasHeight = newHeight
        requestLayout()
        invalidate()
    }

    fun setDrawingMode(enabled: Boolean) {
        isDrawing = enabled
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
        val (bmp, _) = exportCanvas(
            ExportOptions(
                resolution = ExportResolution("picker", canvasWidth, canvasHeight, 1f),
                quality = ExportQuality("", 100, "", 0),
                format = ExportFormat("", Bitmap.CompressFormat.PNG, "", emptyList())
            )
        )
        colorPickerBitmap = bmp
        invalidate()
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        canvasElements.firstOrNull { it.type == ElementType.BACKGROUND }?.apply {
            logicalContentWidth = canvasWidth.toFloat()
            logicalContentHeight = canvasHeight.toFloat()
        }

        if (canvasElements.isEmpty()) {
            ensureBackgroundElement()
        }
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

                // Normal element path
                val halfW = elem.getLocalContentWidth() * elem.scale / 2f
                if (!halfW.isFinite() || !canvasWidth.toFloat()
                        .isFinite() || canvasWidth <= 0f
                ) return

                val rawX = when (align) {
                    HAlign.LEFT -> halfW
                    HAlign.CENTER -> canvasWidth / 2f
                    HAlign.RIGHT -> canvasWidth - halfW
                }

                val minX = halfW
                val maxX = canvasWidth - halfW
                val oversized = (halfW * 2f) > canvasWidth

                elem.x = when {
                    oversized -> canvasWidth / 2f                    // element wider than canvas: center it
                    minX <= maxX -> rawX.coerceIn(minX, maxX)        // normal case
                    else -> canvasWidth / 2f                         // safety fallback
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

                val halfH = elem.getLocalContentHeight() * elem.scale / 2f
                val rawY = when (align) {
                    VAlign.TOP -> halfH
                    VAlign.MIDDLE -> canvasHeight / 2f
                    VAlign.BOTTOM -> canvasHeight - halfH
                }
                elem.y = rawY.coerceIn(halfH, canvasHeight - halfH)
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
     * Syncs the canvas elements with a new list from the ViewModel.
     * Updates the internal `selectedElements` list based on the `isSelected` flag of incoming elements.
     */
    fun syncElements(newElements: List<CanvasElement>) {
        val oldSize = canvasElements.size
        canvasElements.clear()
        canvasElements.addAll(newElements)
        selectedElements.clear()
        if (newElements.size > oldSize) {
            val newcomer = canvasElements.last()

            if (newcomer.type != ElementType.BACKGROUND) {
                canvasElements.forEach { it.isSelected = false }
                newcomer.isSelected = (newcomer.type != ElementType.DRAW)
                selectedElements.add(newcomer)
            } else {
                selectedElements.addAll(canvasElements.filter { it.isSelected })
            }
        } else {
            selectedElements.addAll(canvasElements.filter { it.isSelected })
        }
        invalidate()
    }

    /**
     * Calculates the combined bounding box for all currently selected elements.
     * Returns an empty RectF if no elements are selected.
     */
    /** Returns an axis-aligned bounding box that covers all rotated elements */
    private fun getCombinedSelectedBounds(): RectF {
        if (selectedElements.isEmpty()) return RectF()

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        selectedElements.forEach { element ->
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
        // Remove all selected elements
        val elementsToRemove = selectedElements.toList()
        elementsToRemove.forEach { element ->
            canvasElements.remove(element)
            onElementRemoved?.invoke(element) // Notify ViewModel to remove for each
        }
        selectedElements.clear() // Clear the selected elements list
        invalidate()
    }

    fun applyImageFilter(filter: ImageFilter?) {
        val elementsToFilter =
            selectedElements.toList() // Create a copy to avoid concurrent modification
        elementsToFilter.forEach { element ->
            if (element != null && element.type == ElementType.IMAGE) {
                element.imageFilter = filter!!
                onElementChanged?.invoke(element) // Notify ViewModel of change
                invalidate()
            }
        }
    }

    fun setFont(fontEntity: FontEntity) {
        selectedElements.filter { it.type == ElementType.TEXT }.forEach { element ->
            element.fontId = fontEntity.id.toString()

            // Check if the file_path is not blank before attempting to create a typeface
            if (fontEntity.file_path?.isNotBlank()!!) {
                try {
                    element.paint.typeface = Typeface.createFromFile(fontEntity.file_path)
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
        val scaledWidth = canvasWidth * scaleFactor
        val scaledHeight = canvasHeight * scaleFactor
        val offsetX = (canvas.width - scaledWidth) / 2f
        val offsetY = (canvas.height - scaledHeight) / 2f

        canvas.withTranslation(offsetX, offsetY) {
            scale(scaleFactor, scaleFactor)
            this@CanvasView.drawCanvasElements(this, showOverlays = false, showCheckerboard = false)
        }
    }

    private fun addWatermark(canvas: Canvas, width: Int, height: Int) {
        val watermarkText = "UrduCanvas"  // Watermark text

        // Load the custom font from the 'res/fonts' folder
        val watermarkTypeface = ResourcesCompat.getFont(context, R.font.regular)

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

    fun exportCanvas(
        options: ExportOptions, onProgress: ((percent: Int, stage: String) -> Unit)? = null
    ): Pair<Bitmap, String> {
        val contentWidth = this.canvasWidth
        val contentHeight = this.canvasHeight

        val scaleFactor = options.resolution.scaleFactor.takeIf { it > 0f } ?: 1f
        val outputWidth = (contentWidth * scaleFactor).roundToInt()
        val outputHeight = (contentHeight * scaleFactor).roundToInt()

        onProgress?.invoke(10, "Preparing canvas")

        val bitmap = createBitmap(outputWidth, outputHeight)
        val canvas = Canvas(bitmap)

        onProgress?.invoke(30, "Rendering design")
        onProgress?.invoke(40, "Just a few moments")

        renderCanvasTo(canvas, scaleFactor)
        addWatermark(canvas, outputWidth, outputHeight)
        onProgress?.invoke(50, "Please wait")

        val elementsWithBitmap = canvasElements.filter { it.bitmap != null }
        val total = elementsWithBitmap.size
        if (total > 0) {
            onProgress?.invoke(70, "Encoding image data")
            elementsWithBitmap.forEachIndexed { index, element ->
                element.bitmap?.let {
//                    element.bitmapData = ImageProcessor.bitmapToFilePath(context, it)
                    element.bitmapData = ImageProcessor.bitmapToBase64(it)
                }
                element.drawStrokes?.forEach { stroke ->
                    stroke.serializePath()
                }
                val progress = 70 + ((index + 1) * 20 / total)
                onProgress?.invoke(progress, "Saving ${index + 1} of $total")
            }
        } else {
            onProgress?.invoke(90, "No bitmaps to encode")
        }

        val json = gson.toJson(canvasElements)

        return Pair(bitmap, json)
    }

    fun exportCanvasThumbnail(
        maxWidth: Int = 300,
        maxHeight: Int = 300,
        onProgress: ((percent: Int, stage: String) -> Unit)? = null
    ): Pair<Bitmap, String> {
        val contentWidth = this.canvasWidth
        val contentHeight = this.canvasHeight

        // Compute target dimensions while preserving aspect ratio
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

        // Scale down content to thumbnail size
        val scaleFactorX = targetWidth.toFloat() / contentWidth.toFloat()
        val scaleFactorY = targetHeight.toFloat() / contentHeight.toFloat()
        val scaleFactor = minOf(scaleFactorX, scaleFactorY)

        onProgress?.invoke(30, "Rendering thumbnail")
        renderCanvasTo(canvas, scaleFactor)
        // Encode element bitmaps (if any)
        val elementsWithBitmap = canvasElements.filter { it.bitmap != null }
        val total = elementsWithBitmap.size
        if (total > 0) {
            onProgress?.invoke(70, "Encoding image data")
            elementsWithBitmap.forEachIndexed { index, element ->
                element.bitmap?.let {
                    element.bitmapData = ImageProcessor.bitmapToBase64(it)
                }
                element.drawStrokes?.forEach { stroke ->
                    stroke.serializePath()
                }
                val progress = 70 + ((index + 1) * 20 / total)
                onProgress?.invoke(progress, "Saving ${index + 1} of $total")
            }
        } else {
            onProgress?.invoke(90, "No bitmaps to encode")
        }

        val snapshot = canvasElements.toList()   // immutable copy
        val json = gson.toJson(snapshot)

        onProgress?.invoke(95, "Thumbnail ready")

        return Pair(bitmap, json)
    }

    suspend fun exportCanvasJson(): String = withContext(Dispatchers.IO) {
        // ✅ Step 1: Create a deep snapshot to prevent concurrent modifications
        val safeElements = canvasElements.toList().map { element ->
            element.copy(
                drawStrokes = element.drawStrokes?.toList()?.map { s ->
                    // Copy each stroke (with its own path)
                    s.copy(path = Path(s.path))
                }?.toMutableList()
            )
        }

        // ✅ Step 2: Safely iterate over the snapshot
        safeElements.forEach { element ->
            element.bitmap?.let {
                element.bitmapData = ImageProcessor.bitmapToBase64(it)
            }
            element.drawStrokes?.forEach { stroke ->
                stroke.serializePath()
            }
        }

        // ✅ Step 3: Serialize safely
        Gson().toJson(safeElements)
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
            snapped && (element.rotation == 0f || element.rotation == 180f || element.rotation == 360f)
        showRotationHorizontalGuide =
            snapped && (element.rotation == 90f || element.rotation == 270f)
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
            snapped && (snappedTarget == 0f || snappedTarget == 180f || snappedTarget == 360f)
        showRotationHorizontalGuide = snapped && (snappedTarget == 90f || snappedTarget == 270f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)

        val widthRatio = parentWidth.toFloat() / canvasWidth
        val heightRatio = parentHeight.toFloat() / canvasHeight
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
        if (currentStrokePath == null) return

        val tempStroke = StrokeData(
            path = currentStrokePath!!,
            color = currentBrushColor,
            thickness = currentBrushThickness,
            hardness = currentBrushHardness,
            style = currentBrushStyle,
            gradient = currentBrushGradient
        )

        when (currentBrushStyle) {
            BrushStyle.BRUSH -> drawBrushStroke(canvas, tempStroke)
            BrushStyle.PEN -> drawTaperedPenStroke(canvas, tempStroke)
            BrushStyle.PENCIL -> {
                val paint = makeStrokePaint(tempStroke, width, height).apply {
                    pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
                    alpha = 180
                }
                canvas.drawPath(tempStroke.path, paint)
            }

            BrushStyle.HIGHLIGHTER -> {
                val paint = makeStrokePaint(tempStroke, width, height).apply {
                    alpha = 130
                    strokeCap = Paint.Cap.BUTT
                }
                canvas.drawPath(tempStroke.path, paint)
            }

            BrushStyle.MARKER -> {
                val paint = makeStrokePaint(tempStroke, width, height).apply {
                    alpha = 240
                    strokeCap = Paint.Cap.BUTT
                }
                canvas.drawPath(tempStroke.path, paint)
            }

            else -> {
                val paint = makeStrokePaint(tempStroke, width, height)
                canvas.drawPath(tempStroke.path, paint)
            }
        }
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
                    // 🟢 Draw all non-draw elements dimmed
                    canvas.saveLayer(null, null)
                    drawCanvasElements(this) // draw all normally first
                    canvas.drawRect(
                        0f,
                        0f,
                        canvasWidth.toFloat(),
                        canvasHeight.toFloat(),
                        drawingModeOverlayPaint
                    )
                    canvas.restore()

                    // 🟢 Draw live preview path
                    if (currentStrokePath != null && currentStrokePaint != null) {
                        drawLivePreviewStroke(this)
                    }
                } else {
                    // 🔵 Normal render when not drawing
                    drawCanvasElements(this)
                }
            }
        }

        if (showVerticalGuide) {
            canvas.drawLine(
                width / 2f, 0f, width / 2f, height.toFloat(), alignmentPaint
            )
        }

        if (showHorizontalGuide) {
            canvas.drawLine(
                0f, height / 2f, width.toFloat(), height / 2f, alignmentPaint
            )
        }

        // Draw rotation alignment guides
        if (showRotationVerticalGuide) {
            // Draw a vertical line through the center of the canvas
            canvas.drawLine(
                width / 2f, 0f, width / 2f, height.toFloat(), alignmentPaint
            )
        }

        if (showRotationHorizontalGuide) {
            // Draw a horizontal line through the center of the canvas
            canvas.drawLine(
                0f, height / 2f, width.toFloat(), height / 2f, alignmentPaint
            )
        }
    }

    fun colorFilterFor(filter: ImageFilter?): ColorFilter? {
        return when (filter) {
            null, ImageFilter.None -> null

            ImageFilter.Grayscale -> ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(0f)
            })

            ImageFilter.Sepia -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        0.393f,
                        0.769f,
                        0.189f,
                        0f,
                        0f,
                        0.349f,
                        0.686f,
                        0.168f,
                        0f,
                        0f,
                        0.272f,
                        0.534f,
                        0.131f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Invert -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        -1f,
                        0f,
                        0f,
                        0f,
                        255f,
                        0f,
                        -1f,
                        0f,
                        0f,
                        255f,
                        0f,
                        0f,
                        -1f,
                        0f,
                        255f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.CoolTint -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.1f,
                        0f,
                        0f,
                        0f,
                        -20f,
                        0f,
                        1f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1.3f,
                        0f,
                        20f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.WarmTint -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.3f,
                        0f,
                        0f,
                        0f,
                        30f,
                        0f,
                        1f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0.8f,
                        0f,
                        -20f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Vintage -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        0.9f,
                        0.3f,
                        0.1f,
                        0f,
                        5f,
                        0.2f,
                        0.8f,
                        0.2f,
                        0f,
                        5f,
                        0.1f,
                        0.2f,
                        0.7f,
                        0f,
                        -10f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Film -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.2f,
                        0.1f,
                        0.1f,
                        0f,
                        15f,
                        0.1f,
                        1.2f,
                        0.1f,
                        0f,
                        10f,
                        0.1f,
                        0.1f,
                        0.9f,
                        0f,
                        -10f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.TealOrange -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.2f,
                        0f,
                        0f,
                        0f,
                        20f,
                        0f,
                        1f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0.8f,
                        0f,
                        -10f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.HighContrast -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.5f,
                        0f,
                        0f,
                        0f,
                        -50f,
                        0f,
                        1.5f,
                        0f,
                        0f,
                        -50f,
                        0f,
                        0f,
                        1.5f,
                        0f,
                        -50f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.BlackWhite -> {
                val cm = ColorMatrix().apply { setSaturation(0f) }
                val contrast = ColorMatrix().apply {
                    set(
                        floatArrayOf(
                            1.4f,
                            0f,
                            0f,
                            0f,
                            -50f,
                            0f,
                            1.4f,
                            0f,
                            0f,
                            -50f,
                            0f,
                            0f,
                            1.4f,
                            0f,
                            -50f,
                            0f,
                            0f,
                            0f,
                            1f,
                            0f
                        )
                    )
                }
                cm.postConcat(contrast)
                ColorMatrixColorFilter(cm)
            }

            ImageFilter.BrightnessBoost -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.2f,
                        0f,
                        0f,
                        0f,
                        30f,
                        0f,
                        1.2f,
                        0f,
                        0f,
                        30f,
                        0f,
                        0f,
                        1.2f,
                        0f,
                        30f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Sharpen -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        2f,
                        -1f,
                        -1f,
                        0f,
                        0f,
                        -1f,
                        2f,
                        -1f,
                        0f,
                        0f,
                        -1f,
                        -1f,
                        2f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Sketch -> ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(0f)
            })

            ImageFilter.Cartoon -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.5f,
                        0f,
                        0f,
                        0f,
                        -30f,
                        0f,
                        1.5f,
                        0f,
                        0f,
                        -30f,
                        0f,
                        0f,
                        1.5f,
                        0f,
                        -30f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.HDR -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.3f,
                        0f,
                        0f,
                        0f,
                        -20f,
                        0f,
                        1.3f,
                        0f,
                        0f,
                        -20f,
                        0f,
                        0f,
                        1.3f,
                        0f,
                        -20f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Lomo -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.2f,
                        0.2f,
                        0.1f,
                        0f,
                        10f,
                        0.1f,
                        1.0f,
                        0.1f,
                        0f,
                        5f,
                        0.1f,
                        0.1f,
                        1.2f,
                        0f,
                        -10f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Pastel -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.0f,
                        0f,
                        0f,
                        0f,
                        20f,
                        0f,
                        1.0f,
                        0f,
                        0f,
                        20f,
                        0f,
                        0f,
                        1.0f,
                        0f,
                        20f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Dramatic -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.5f,
                        0f,
                        0f,
                        0f,
                        -40f,
                        0f,
                        1.5f,
                        0f,
                        0f,
                        -40f,
                        0f,
                        0f,
                        1.5f,
                        0f,
                        -40f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.GoldenHour -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.2f,
                        0.2f,
                        0f,
                        0f,
                        30f,
                        0.1f,
                        1.1f,
                        0f,
                        0f,
                        20f,
                        0f,
                        0f,
                        0.8f,
                        0f,
                        -10f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Cyberpunk -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        0.9f,
                        0.2f,
                        0.6f,
                        0f,
                        30f,
                        0.1f,
                        0.8f,
                        0.5f,
                        0f,
                        10f,
                        0.2f,
                        0.3f,
                        1.5f,
                        0f,
                        -20f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Glow -> {
                null
            }

            ImageFilter.SoftBlur -> {
                null
            }

        }
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
        canvas: Canvas, showOverlays: Boolean = true, showCheckerboard: Boolean = true
    ) {
        canvas.save()
        canvas.clipRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat())

        if (showCheckerboard) {
            val checkerPaint = Paint().apply { shader = checkerShader }
            canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), checkerPaint)
        }

        // Draw all elements
        canvasElements.sortedBy { it.zIndex }.forEach { element ->
            if (!element.isVisible) return@forEach

            if (element.type == ElementType.BACKGROUND) {
                drawBackgroundElement(canvas, element)
            } else {
                canvas.withTranslation(element.x, element.y) {
                    canvas.rotate(element.rotation)
                    val fx = if (element.isFlippedX) -1f else 1f
                    val fy = if (element.isFlippedY) -1f else 1f
                    canvas.scale(element.scale * fx, element.scale * fy)

                    when (element.type) {
                        ElementType.DRAW -> drawDrawElement(canvas, element)
                        ElementType.SHAPE -> drawShapeElement(canvas, element)
                        ElementType.TEXT -> drawTextElement(canvas, element)
                        else -> {
                            element.bitmap?.let { bmp ->
                                var finalBitmap = bmp

                                if (finalBitmap.isRecycled) return@let

                                finalBitmap = ImageAdjustmentHelper.applyAllAdjustments(
                                    element.context!!, bmp, element.adjustments
                                )

                                element.paint.colorFilter = colorFilterFor(element.imageFilter)
                                element.paint.maskFilter = null

                                when (element.imageFilter) {
                                    ImageFilter.SoftBlur -> {
                                        element.paint.maskFilter =
                                            BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
                                        canvas.drawBitmap(
                                            finalBitmap,
                                            -finalBitmap.width / 2f,
                                            -finalBitmap.height / 2f,
                                            element.paint
                                        )
                                    }

                                    ImageFilter.Glow -> {
                                        canvas.drawBitmap(
                                            finalBitmap,
                                            -finalBitmap.width / 2f,
                                            -finalBitmap.height / 2f,
                                            element.paint
                                        )

                                        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                            color = Color.argb(180, 255, 255, 200)
                                            maskFilter =
                                                BlurMaskFilter(25f, BlurMaskFilter.Blur.OUTER)
                                        }
                                        canvas.drawBitmap(
                                            finalBitmap,
                                            -finalBitmap.width / 2f,
                                            -finalBitmap.height / 2f,
                                            glowPaint
                                        )
                                    }

                                    else -> {
                                        canvas.drawBitmap(
                                            finalBitmap,
                                            -finalBitmap.width / 2f,
                                            -finalBitmap.height / 2f,
                                            element.paint
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Draw combined bounding box and icons based on selection state ---
        if (showOverlays && selectedElements.isNotEmpty()) {
            val desiredScreenStrokeWidth = 2f
            val localSpaceStrokeWidth = desiredScreenStrokeWidth / scale // Scale stroke width

            val dashLengthOnScreen = 10f
            val gapLengthOnScreen = 10f
            val localDashLength = dashLengthOnScreen / scale
            val localGapLength = gapLengthOnScreen / scale

            val boxPaint = Paint().apply {
                color = Color.GRAY
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(localDashLength, localGapLength), 0f)
                strokeWidth = localSpaceStrokeWidth
            }

            val rotatedPath = if (selectedElements.size > 1) {
                getGroupRotatedPath()
            } else {
                getSelectionPath() // existing single-element case
            }
            if (rotatedPath != null) {
                canvas.drawPath(rotatedPath, boxPaint)
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
                val localIconDrawWidth = desiredIconScreenSizePx / scale
                val localIconDrawHeight = desiredIconScreenSizePx / scale

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
                }

                iconMap.forEach { (iconName, position) ->
                    val iconBitmap = when (iconName) {
                        "delete" -> removeIcon
                        "rotate" -> rotateIcon
                        "resize" -> resizeIcon
                        "edit" -> editIcon
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
                                val fixedHandleLengthPx = 80f  // visible fixed size on screen
                                val rotateIcon = floatArrayOf(
                                    bounds.centerX(),
                                    bounds.top - (fixedHandleLengthPx / scale) // convert screen px → canvas units
                                )

                                matrix.mapPoints(topCenter)
                                matrix.mapPoints(rotateIcon)
                                topCenter to rotateIcon
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
                                    pivotX, topY - (fixedHandleLengthPx / scale)
                                )

                                // ❌ No rotation applied here (handle stays fixed above top bound)
                                topCenter to rotateIcon
                            }

                            // --- Draw connecting dashed line ---
                            val linePaint = Paint().apply {
                                color = Color.GRAY
                                style = Paint.Style.STROKE
                                strokeWidth = 4f / scale
                                isAntiAlias = true
                                val phase = (System.currentTimeMillis() % 1000L) / 20f
                                pathEffect =
                                    DashPathEffect(floatArrayOf(10f / scale, 10f / scale), phase)
                            }

                            canvas.drawLine(
                                localTopCenter[0],
                                localTopCenter[1],
                                localRotateIcon[0],
                                localRotateIcon[1],
                                linePaint
                            )

                            // --- Update icon rect so it “sticks” at the end of line ---
                            dstRect = RectF(
                                localRotateIcon[0] - localIconDrawWidth / 2f,
                                localRotateIcon[1] - localIconDrawHeight / 2f,
                                localRotateIcon[0] + localIconDrawWidth / 2f,
                                localRotateIcon[1] + localIconDrawHeight / 2f
                            )
                        }

                        // --- Draw icon bitmap ---
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

        if (showOverlays && isColorPickerMode) {
            val halfIcon = desiredPickerIconSizePx

            val px = pickerX.roundToInt().coerceIn(0, colorPickerBitmap?.width!! - 1)
            val py = pickerY.roundToInt().coerceIn(0, colorPickerBitmap?.height!! - 1)
            val pixelColor = colorPickerBitmap?.getPixel(px, py)
            val dark = pixelColor?.let { isColorDark(it) }

            canvas.drawCircle(
                pickerX, pickerY - halfIcon * 3, halfIcon + 20f, Paint().apply {
                    color = pixelColor!!
                    style = Paint.Style.FILL
                    isAntiAlias = true
                })

            canvas.drawCircle(
                pickerX, pickerY - halfIcon * 3, halfIcon + 20f, Paint().apply {
                    color = if (dark!!) Color.WHITE else Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                })

            canvas.drawCircle(
                pickerX, pickerY, halfIcon / 4, Paint().apply {
                    color = Color.BLACK
                    style = Paint.Style.FILL
                    isAntiAlias = true
                })
        }
    }

    // In CanvasView.kt's drawShapeElement function

    private fun drawShapeElement(canvas: Canvas, element: CanvasElement) {
        val localHalfW = element.logicalContentWidth / 2f
        val localHalfH = element.logicalContentHeight / 2f

        val localRect = RectF(
            -localHalfW,
            -localHalfH,
            localHalfW,
            localHalfH
        )

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
            // Draw the fill
            drawShape(
                canvas,
                fillPaint,
                element.shapeType ?: ShapeType.RECTANGLE,
                localRect,
                element.shapeCornerRadius
            )
        }
        // --- Draw the STROKE second (if enabled) ---
        if (element.shapeHasStroke) {
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                // Must be STROKE style to ensure the fill doesn't interfere
                style = Paint.Style.STROKE
                strokeWidth = element.shapeStrokeWidth ?: 1f

                // Set stroke color or gradient
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
            drawShape(
                canvas,
                strokePaint,
                element.shapeType ?: ShapeType.RECTANGLE,
                localRect,
                element.shapeCornerRadius
            )
        }
    }

    private fun drawDrawElement(
        canvas: Canvas, element: CanvasElement
    ) {
        element.drawStrokes?.forEach { stroke ->

            when (stroke.style) {
                BrushStyle.BRUSH -> {
                    drawBrushStroke(canvas, stroke)
                }

                BrushStyle.PEN -> {
                    drawTaperedPenStroke(canvas, stroke)
                }

                BrushStyle.HIGHLIGHTER -> {
                    val paint = makeStrokePaint(stroke, width, height)
                    val offset = stroke.thickness * 0.3f
                    val path = Path(stroke.path)
                    val m = Matrix()
                    m.postTranslate(0f, offset)
                    path.transform(m)
                    canvas.drawPath(path, paint)
                }

                else -> {
                    val paint = makeStrokePaint(stroke, width, height)
                    canvas.drawPath(stroke.path, paint)
                }
            }
        }

        // 🟢 Live in-progress path
        if (element == activeDrawElement && currentPath != null && currentPaint != null) {
            canvas.drawPath(currentPath!!, currentPaint!!)
        }
    }

    private fun drawBackgroundElement(canvas: Canvas, e: CanvasElement) {
        val w = canvasWidth.toFloat()
        val h = canvasHeight.toFloat()

        val backgroundPaint = Paint().apply {
            alpha = e.paintAlpha
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        e.bitmap?.let { bmp ->

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

            canvas.withTranslation(left, top) {
                scale(totalScale, totalScale)
                rotate(e.rotation, bmp.width / 2f, bmp.height / 2f)

                var adjustedBackground = bmp
                if (adjustedBackground.isRecycled) return@withTranslation
                adjustedBackground = ImageAdjustmentHelper.applyAllAdjustments(
                    e.context!!, adjustedBackground, e.adjustments
                )

                backgroundPaint.colorFilter = colorFilterFor(e.imageFilter)
                backgroundPaint.maskFilter = null

                when (e.imageFilter) {
                    ImageFilter.SoftBlur -> {
                        backgroundPaint.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
                        drawBitmap(adjustedBackground, 0f, 0f, backgroundPaint)
                    }

                    ImageFilter.Glow -> {
                        drawBitmap(adjustedBackground, 0f, 0f, backgroundPaint)
                        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.argb(180, 255, 255, 200)
                            maskFilter = BlurMaskFilter(25f, BlurMaskFilter.Blur.OUTER)
                        }
                        drawBitmap(adjustedBackground, 0f, 0f, glowPaint)
                    }

                    else -> {
                        drawBitmap(adjustedBackground, 0f, 0f, backgroundPaint)
                    }
                }
            }
            backgroundPaint.xfermode = drawWithBlend(e)
            return
        }

        val left = e.x - w / 2f
        val top = e.y - h / 2f
        val pivotX = w / 2f
        val pivotY = h / 2f

        // 2) else if there's a gradient -> stretch it across the full canvas
        e.fillGradient?.let { grad ->
            canvas.withTranslation(left, top) {
                scale(e.scale, e.scale, pivotX, pivotY)
                rotate(e.rotation, pivotX, pivotY)

                backgroundPaint.shader = BrushRenderUtils.createBackgroundGradientShader(grad, w, h)
                drawRect(0f, 0f, w, h, backgroundPaint)
                backgroundPaint.shader = null
            }
            return
        }

        // 3) else -> solid color
        canvas.withTranslation(left, top) {
            scale(e.scale, e.scale, pivotX, pivotY)
            rotate(e.rotation, pivotX, pivotY)

            backgroundPaint.shader = null
            backgroundPaint.color = e.backgroundColor
            drawRect(0f, 0f, w, h, backgroundPaint)
        }
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
        val rawShader: Shader
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

        rawShader.setLocalMatrix(finalMatrix)
        return rawShader
    }

    private fun drawTextElement(canvas: Canvas, element: CanvasElement) {
        val lines = element.getTextWithKashida().split("\n")
        val fm = element.paint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent) * element.lineSpacing
        val totalHeight = lineHeight * lines.size

        if (element.hasLabel) {
            val maxLineWidth = lines.maxOf { element.paint.measureText(it) }
            val labelPadding = 16f
            val left = -maxLineWidth / 2f - labelPadding
            val top = -totalHeight / 2f - labelPadding
            val right = maxLineWidth / 2f + labelPadding
            val bottom = totalHeight / 2f + labelPadding

            val labelRect = RectF(left, top, right, bottom)
            val labelPaint = Paint().apply {
                color = element.labelColor
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            element.labelGradient?.let { gradient ->
                val rectW = labelRect.width()
                val rectH = labelRect.height()
                labelPaint.shader = createGradientShader(
                    gradientItem = gradient,  // assume you store gradient settings here
                    width = rectW, height = rectH
                )
            } ?: run {
                labelPaint.shader = null
                labelPaint.color = element.labelColor
            }

            when (element.labelShape) {
                LabelShape.RECTANGLE_FILL -> {
                    labelPaint.style = Paint.Style.FILL
                    canvas.drawRect(labelRect, labelPaint)
                }

                LabelShape.RECTANGLE_STROKE -> {
                    labelPaint.style = Paint.Style.STROKE
                    labelPaint.strokeWidth = 4f // You can adjust the stroke width as needed
                    canvas.drawRect(labelRect, labelPaint)
                }

                LabelShape.OVAL_FILL -> {
                    labelPaint.style = Paint.Style.FILL
                    canvas.drawOval(labelRect, labelPaint)
                }

                LabelShape.OVAL_STROKE -> {
                    labelPaint.style = Paint.Style.STROKE
                    labelPaint.strokeWidth = 4f // Adjust stroke width as needed
                    canvas.drawOval(labelRect, labelPaint)
                }

                LabelShape.CIRCLE_FILL -> {
                    labelPaint.style = Paint.Style.FILL
                    val radius = labelRect.width().coerceAtMost(labelRect.height()) / 2f
                    val centerX = labelRect.centerX()
                    val centerY = labelRect.centerY()
                    canvas.drawCircle(centerX, centerY, radius, labelPaint)
                }

                LabelShape.CIRCLE_STROKE -> {
                    labelPaint.style = Paint.Style.STROKE
                    labelPaint.strokeWidth = 4f // Adjust stroke width as needed
                    val radius = labelRect.width().coerceAtMost(labelRect.height()) / 2f
                    val centerX = labelRect.centerX()
                    val centerY = labelRect.centerY()
                    canvas.drawCircle(centerX, centerY, radius, labelPaint)
                }

                LabelShape.ROUNDED_RECTANGLE_FILL -> {
                    labelPaint.style = Paint.Style.FILL
                    canvas.drawRoundRect(
                        labelRect, 20f, 20f, labelPaint
                    ) // Adjust corner radius as needed
                }

                LabelShape.ROUNDED_RECTANGLE_STROKE -> {
                    labelPaint.style = Paint.Style.STROKE
                    labelPaint.strokeWidth = 4f // Adjust stroke width as needed
                    canvas.drawRoundRect(
                        labelRect, 20f, 20f, labelPaint
                    ) // Adjust corner radius as needed
                }
            }
        }

        // Font correction for baseline alignment
        val baselineShift = (fm.ascent + fm.descent) / 2f
        var yOffset = -((lines.size - 1) * lineHeight / 2f) - baselineShift

        lines.forEachIndexed { i, line ->

            val fillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = element.paintColor
                textSize = element.paintTextSize
                alpha = element.paintAlpha
                letterSpacing = element.letterSpacing
                isAntiAlias = true
                style = Paint.Style.FILL

                // Underline
                isUnderlineText = TextDecoration.UNDERLINE in element.textDecoration

                // Bold / Italic
                val baseTypeface = element.paint.typeface ?: Typeface.DEFAULT
                val isBold = TextDecoration.BOLD in element.textDecoration
                val isItalic = TextDecoration.ITALIC in element.textDecoration

                val style = when {
                    isBold && isItalic -> Typeface.BOLD_ITALIC
                    isBold -> Typeface.BOLD
                    isItalic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                typeface = Typeface.create(baseTypeface, style)
            }

            // Handle list styles
            val textToDraw = when (element.listStyle) {
                ListStyle.BULLETED -> "• $line"
                ListStyle.NUMBERED -> "${i + 1}. $line"
                else -> line
            }

            // Letter casing
            val displayText = when (element.letterCasing) {
                LetterCasing.ALL_CAPS -> textToDraw.uppercase()
                LetterCasing.LOWER_CASE -> textToDraw.lowercase()
                LetterCasing.TITLE_CASE -> textToDraw.split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }

                else -> textToDraw
            }

            // Alignment
            val alignment = when (element.alignment) {
                TextAlignment.LEFT -> Paint.Align.LEFT
                TextAlignment.CENTER -> Paint.Align.CENTER
                TextAlignment.RIGHT -> Paint.Align.RIGHT
                TextAlignment.JUSTIFY -> Paint.Align.LEFT
            }

            val indentOffset = if (i == 0) element.currentIndent else 0f

            fillPaint.textAlign = alignment
            val xPosition = when (alignment) {
                Paint.Align.LEFT -> -element.getLocalContentWidth() / 2f + indentOffset
                Paint.Align.CENTER -> 0f
                Paint.Align.RIGHT -> element.getLocalContentWidth() / 2f + indentOffset
            }

            element.fillGradient?.let { gradient ->
                val textWidth = fillPaint.measureText(displayText)
                val textHeight = fillPaint.textSize
                fillPaint.shader = createGradientShader(
                    gradientItem = gradient, width = textWidth, height = textHeight
                )
            } ?: run {
                fillPaint.shader = null
                fillPaint.color = element.paintColor
            }

            if (element.hasBlur) {
                val blurMaskFilter = BlurMaskFilter(element.blurValue, BlurMaskFilter.Blur.NORMAL)
                fillPaint.maskFilter = blurMaskFilter
            }

            // Apply opacity (alpha)
            fillPaint.alpha = element.paintAlpha

            // Apply layer blending (based on imageFilter)
            fillPaint.xfermode = drawWithBlend(element)

            if (element.hasShadow) {
                val shadowColorWithOpacity =
                    (element.shadowColor and 0x00FFFFFF) or (element.shadowOpacity shl 24)
                val shadowPaint = TextPaint(fillPaint).apply {
                    setShadowLayer(
                        element.shadowRadius,
                        element.shadowDx,
                        element.shadowDy,
                        shadowColorWithOpacity
                    )
                    shader = null
                }
                canvas.drawText(displayText, xPosition, yOffset, shadowPaint)
            }

            // Handle justified text separately
            if (element.alignment == TextAlignment.JUSTIFY) {
                element.paint = fillPaint
                justifyText(canvas, displayText, yOffset, element)
            } else {
                // Draw filled text
                canvas.drawText(displayText, xPosition, yOffset, fillPaint)

                // Draw border (stroke) if needed
                if (element.hasStroke && element.strokeWidth > 0f) {
                    val strokePaint = TextPaint(fillPaint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = element.strokeWidth
                        element.strokeGradient?.let { gradient ->
                            // use same width to span stroke gradient
                            val textWidth = fillPaint.measureText(displayText)
                            val textHeight = fillPaint.textSize
                            shader = createGradientShader(
                                gradientItem = gradient, width = textWidth, height = textHeight
                            )
                        } ?: run {
                            shader = null
                            color = element.strokeColor
                        }
                        textAlign = alignment
                    }
                    canvas.drawText(displayText, xPosition, yOffset, strokePaint)
                }
            }

            yOffset += lineHeight
        }
    }

    private fun drawWithBlend(element: CanvasElement): Xfermode? {
        return when (element.blendType) {
            BlendType.SRC -> PorterDuffXfermode(PorterDuff.Mode.SRC)
            BlendType.NORMAL -> null
            BlendType.DARKEN -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
            BlendType.LIGHTEN -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
            BlendType.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            BlendType.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
    }

    private fun justifyText(canvas: Canvas, text: String, yOffset: Float, element: CanvasElement) {
        if (text.length <= 1) {
            val x = -element.getLocalContentWidth() / 2f
            canvas.drawText(text, x, yOffset, element.paint)
            return
        }

        val basePaint = TextPaint(element.paint).apply {
            isAntiAlias = true
            letterSpacing = element.letterSpacing
            textAlign = Paint.Align.LEFT
        }

        // Create FILL and STROKE paints, both retaining shadow and font features
        val fillPaint = TextPaint(basePaint).apply {
            style = Paint.Style.FILL
            color = element.paintColor
        }

        val strokePaint = if (element.hasStroke && element.strokeWidth > 0f) {
            TextPaint(basePaint).apply {
                style = Paint.Style.STROKE
                strokeWidth = element.strokeWidth
                color = element.strokeColor
            }
        } else null

        val charWidths = text.map { fillPaint.measureText(it.toString()) }
        val textWidth = charWidths.sum()
        val totalAvailable = element.getLocalContentWidth()
        val extraSpace = (totalAvailable - textWidth) / (text.length - 1)

        var xOffset = -totalAvailable / 2f

        text.forEachIndexed { index, char ->
            val charStr = char.toString()
            // Draw stroke first
            strokePaint?.let { canvas.drawText(charStr, xOffset, yOffset, it) }
            // Then draw fill
            canvas.drawText(charStr, xOffset, yOffset, fillPaint)
            // Move to next char position
            xOffset += charWidths[index] + extraSpace
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

    fun CanvasElement.containsPoint(px: Float, py: Float): Boolean {
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

            val (x, y) = screenToCanvas(e.x, e.y)

            val touchedElement =
                canvasElements.filter { !it.isLocked }.sortedByDescending { it.zIndex }
                    .firstOrNull { element ->
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

                canvasElements.forEach {
                    it.isSelected = (it.groupId == activeGroupId)
                }
                selectedElements.clear()
                selectedElements.addAll(canvasElements.filter { it.isSelected })

                currentMode = Mode.GROUP_EDIT
                onElementSelected?.invoke(selectedElements)
                invalidate()
                return true
            } else if (touchedElement != null) {
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

            val touchedElement = canvasElements.filter { !it.isLocked } // ignore locked
                .sortedByDescending { it.zIndex }.firstOrNull { it.containsPoint(x, y) }

            if (touchedElement != null && touchedElement.type != ElementType.BACKGROUND) {
                if (!inSelectionMode) {
                    // 🔹 First long-press → enter selection mode
                    inSelectionMode = true
                    clearSelection()
                    touchedElement.isSelected = true
                    selectedElements.add(touchedElement)
                    if (inSelectionMode) {
                        vibrateSoft()
                    }
                    // 🔹 Tell UI to open Layers in selection mode
                    onRequestOpenLayers?.invoke()
                } else {
                    // 🔹 Already in selection mode → toggle this element
                    if (touchedElement.isSelected) {
                        touchedElement.isSelected = false
                        selectedElements.remove(touchedElement)
                        if (selectedElements.isEmpty()) {
                            inSelectionMode = false
                            onExitSelectionMode?.invoke()
                        }
                    } else {
                        touchedElement.isSelected = true
                        selectedElements.add(touchedElement)
                    }
                }

                onElementSelected?.invoke(selectedElements)
                invalidate()
            }
        }
    }

    private fun stepZoomOverall() {
        val next = when {
            overallScale < 1.5f -> 2f
            overallScale < 3.5f -> 4f
            else -> 1f
        }
        animateOverallZoom(next)
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        val (x, y) = screenToCanvas(event.x, event.y)

        if (isDrawing) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    currentStrokePath = Path().apply {
                        moveTo(x, y)
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
                            shader = BrushRenderUtils.createBackgroundGradientShader(
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
                    currentStrokePoints.add(x to y)

                    if (currentStrokePath == null) return false

                    // --- 1️⃣ Compute full stroke bounds in absolute canvas coordinates
                    val rawBounds = RectF()
                    currentStrokePath!!.computeBounds(rawBounds, true)

                    // --- 2️⃣ Determine the visual center
                    val centerX = rawBounds.centerX()
                    val centerY = rawBounds.centerY()

                    // --- 3️⃣ Translate the path so it becomes local (centered around 0,0)
                    val normalizedPath = Path(currentStrokePath!!)
                    val matrix = Matrix().apply { postTranslate(-centerX, -centerY) }
                    normalizedPath.transform(matrix)

                    // --- 4️⃣ Create the StrokeData (with the normalized path)
                    val strokeData = StrokeData(
                        path = normalizedPath,
                        color = currentBrushColor,
                        thickness = currentBrushThickness,
                        hardness = currentBrushHardness,
                        style = currentBrushStyle,
                        gradient = currentBrushGradient,
                    )

                    // --- 5️⃣ Create a centered CanvasElement positioned at the stroke center
                    val drawElement = CanvasElement(
                        type = ElementType.DRAW,
                        x = centerX,
                        y = centerY,
                        drawStrokes = mutableListOf(strokeData),
                        brushSettings = BrushSettings(
                            defaultColor = currentBrushColor,
                            defaultThickness = currentBrushThickness,
                            defaultHardness = currentBrushHardness,
                            style = currentBrushStyle,
                            gradient = currentBrushGradient
                        ),
                        allowsStrokeEditing = true,
                        isVisible = true,
                        backgroundColor = Color.TRANSPARENT
                    ).apply {
                        logicalContentWidth = rawBounds.width()
                        logicalContentHeight = rawBounds.height()
                    }

                    // --- 6️⃣ Add to canvas
                    onDrawStrokeCompleted?.invoke(drawElement)

                    // --- 7️⃣ Cleanup
                    currentStrokePath = null
                    currentStrokePaint = null
                    currentStrokePoints.clear()
                }
            }

            return true
        }

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

                        val px = pickerX.roundToInt().coerceIn(0, colorPickerBitmap?.width!! - 1)
                        val py = pickerY.roundToInt().coerceIn(0, colorPickerBitmap?.height!! - 1)
                        val color = colorPickerBitmap?.getPixel(px, py)
                        color?.let { onColorPicked?.invoke(it) }
                        isDraggingPicker = false
                        invalidate()
                    }
                    return true
                }
            }
        }

        when (event.actionMasked) {

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    currentMode = Mode.MULTI_TOUCH
                    initialPinchDistance = getPinchDistance(event)
                    initialPinchAngle = getPinchAngle(event)
                    initialScale = selectedElements.firstOrNull()?.scale ?: 1f
                    initialRotation = selectedElements.firstOrNull()?.rotation ?: 0f
                }
                if (event.pointerCount == 2 && selectedElements.isEmpty()) {
                    currentMode = Mode.CANVAS_PAN
                    initialPinchDistance = getPinchDistance(event)
                    initialOverallScale = overallScale
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
                    val hitChild =
                        canvasElements.filter { it.groupId == activeGroupId && !it.isLocked }
                            .sortedByDescending { it.zIndex }.firstOrNull { element ->
                                val matrix = Matrix().apply {
                                    postTranslate(-element.x, -element.y)
                                    postRotate(-element.rotation)
                                    postScale(1f / element.scale, 1f / element.scale)
                                }
                                val pt = floatArrayOf(x, y).also { matrix.mapPoints(it) }
                                RectF(
                                    -element.getLocalContentWidth() / 2f,
                                    -element.getLocalContentHeight() / 2f,
                                    element.getLocalContentWidth() / 2f,
                                    element.getLocalContentHeight() / 2f
                                ).contains(pt[0], pt[1])
                            }

                    if (hitChild != null) {
                        activeGroupId = null
                        currentMode = Mode.DRAG

                        canvasElements.forEach { it.isSelected = false }
                        selectedElements.clear()
                        hitChild.isSelected = true
                        selectedElements.add(hitChild)
                        onElementSelected?.invoke(selectedElements)

                        touchStartX = x
                        touchStartY = y
                        invalidate()
                        return true
                    } else {
                        currentMode = Mode.NONE
                        activeGroupId = null
                        canvasElements.forEach { it.isSelected = false }
                        selectedElements.clear()
                        onElementSelected?.invoke(selectedElements)
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
                                ) // Initial angle for rotation calculation
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
                                selectedElements.forEach { element ->
                                    resizeLastSignX[element.id] = (touchStartX - pivotX).sign
                                    resizeLastSignY[element.id] = (touchStartY - pivotY).sign
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
                        }
                    }
                }

                // 2. If no icon was touched, check for element touch (single or multi-selection)
                val touchedElement =
                    canvasElements.filter { !it.isLocked }.sortedByDescending { it.zIndex }
                        .firstOrNull { element ->
                            val matrix = Matrix()
                            matrix.postTranslate(-element.x, -element.y)
                            matrix.postRotate(-element.rotation)
                            matrix.postScale(1f / element.scale, 1f / element.scale)

                            val touchPoint = floatArrayOf(x, y)
                            matrix.mapPoints(touchPoint)

                            val tightBounds = element.getTightTextBounds()
                            tightBounds.contains(touchPoint[0], touchPoint[1])
                        }

                if (touchedElement != null) {
                    if (touchedElement.groupId != null) {
                        // Select all elements in the same group
                        val groupMembers =
                            canvasElements.filter { it.groupId == touchedElement.groupId }
                        // Clear any previously selected elements
                        canvasElements.forEach { it.isSelected = false }
                        selectedElements.clear() // Clear internal selection list

                        // Add all group members to the selection
                        groupMembers.forEach { element ->
                            element.isSelected = true
                            selectedElements.add(element)
                        }
                        touchStartX = x
                        touchStartY = y
                        currentMode = Mode.DRAG // Set to drag mode after selecting the group
                        vibrateSoft()
                    } else {
                        if (inSelectionMode) {
                            // 🔹 Multi-select toggle always runs in selection mode
                            if (touchedElement.isSelected) {
                                touchedDownElement = touchedElement
                                isDragCandidate = true
                                touchStartX = x
                                touchStartY = y
                                currentMode = Mode.NONE
                            } else {
                                // select
                                touchedElement.isSelected = true
                                selectedElements.add(touchedElement)
                                onElementSelected?.invoke(selectedElements)
                                vibrateSoft()
                            }
                        } else {
                            // 🔹 Normal mode (single select + drag)
                            if (touchedElement.isSelected) {
                                // already selected → start drag
                                lastTouchedElement = touchedElement
                                currentMode = Mode.DRAG
                                touchStartX = x
                                touchStartY = y
                            } else {
                                // fresh select
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
                    onElementSelected?.invoke(selectedElements)
                    invalidate()
                    return true
                } else {
                    val bg =
                        canvasElements.firstOrNull { it.type == ElementType.BACKGROUND && !it.isLocked }
                    if (bg?.bitmap != null) {
                        // select the background so ACTION_MOVE will pan it
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
                    // 3. Tapped on empty canvas, deselect all elements
                    if (selectedElements.isNotEmpty()) {
                        canvasElements.forEach { it.isSelected = false }
                        selectedElements.clear()
                        inSelectionMode = false
                        onExitSelectionMode?.invoke()
                        onElementSelected?.invoke(selectedElements) // Notify ViewModel of empty selection
                        invalidate()
                    } else {
                        if (overallScale > 1f) {
                            currentMode = Mode.CANVAS_PAN
                            touchStartX = event.x
                            touchStartY = event.y
                            return true
                        }
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
                            if (event.pointerCount == 2) {
                                val newDist = getPinchDistance(event)
                                val factor = newDist / initialPinchDistance
                                overallScale = (initialOverallScale * factor).coerceIn(1f, 4f)
                                clampOverallPan()
                                invalidate()
                            } else if (event.pointerCount == 1 && overallScale > 1f) {
                                val dx = event.x - touchStartX
                                val dy = event.y - touchStartY
                                overallOffsetX += dx
                                overallOffsetY += dy
                                clampOverallPan()
                                touchStartX = event.x
                                touchStartY = event.y
                                invalidate()
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
                                // non-background elements: regular drag
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
                                    val newScale = (initialScale * scaleFactor).coerceIn(
                                        0.1f, 5f
                                    ) // Apply to initial scale
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

                        val startDist = hypot(touchStartX - pivotX, touchStartY - pivotY)
                        val currentDist = hypot(x - pivotX, y - pivotY)
                        val scaleChange = currentDist / startDist
                        if (startDist > 0) {
                            elementsToModify.forEach { element ->

                                val newScale = (element.scale * scaleChange).coerceIn(0.1f, 100f)
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

                        touchStartX = x
                        touchStartY = y
                        invalidate()
                    }

                    Mode.NONE -> {
                        // This block handles potential tap-and-hold to drag if not immediately picking up an icon/element
                    }

                    Mode.GROUP_EDIT -> {
                        // This block handles potential tap-and-hold to drag if not immediately picking up an icon/element
                    }

                    Mode.CANVAS_PAN -> {
                        if (selectedElements.isEmpty()) {
                            if (event.pointerCount == 2) {
                                val newDist = getPinchDistance(event)
                                val factor = newDist / initialPinchDistance
                                overallScale =
                                    (initialOverallScale * factor).coerceIn(1f, 4f) // limit zoom
                                invalidate()
                            } else if (event.pointerCount == 1 && overallScale > 1f) {
                                val dx = event.x - touchStartX
                                val dy = event.y - touchStartY
                                overallOffsetX += dx
                                overallOffsetY += dy
                                clampOverallPan()
                                touchStartX = event.x
                                touchStartY = event.y
                                invalidate()
                            }
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                showVerticalGuide = false
                showHorizontalGuide = false
                showRotationVerticalGuide = false // Reset rotation guides on ACTION_UP
                showRotationHorizontalGuide = false // Reset rotation guides on ACTION_UP
                if (currentMode == Mode.CANVAS_PAN) {
                    currentMode = Mode.NONE
                }

                if (currentMode == Mode.DRAG || currentMode == Mode.ROTATE || currentMode == Mode.RESIZE) {
                    selectedElements.filter { !it.isLocked }.forEach {
                        onElementChanged?.invoke(it)
                        onEndBatchUpdate?.invoke(it.id)
                    }
                }
                if (isDragCandidate && touchedDownElement != null) {
                    // No drag happened → treat as deselect
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
                initialElementRotations.clear()
                initialElementPositionsRelativeToGroupPivot.clear() // Clear initial positions on action up
                initialAngle = 0f
                initialGroupPivotX = 0f
                initialGroupPivotY = 0f
                if (currentMode != Mode.GROUP_EDIT) {
                    lastTouchedElement = null
                    currentMode = Mode.NONE
                }
                clampOverallPan()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun clampOverallPan() {
        val scaledW = canvasWidth * scale * overallScale
        val scaledH = canvasHeight * scale * overallScale

        val containerW = width.toFloat()
        val containerH = height.toFloat()

        if (scaledW > containerW) {
            val maxOffsetX = (scaledW - containerW) / 2f
            overallOffsetX = overallOffsetX.coerceIn(-maxOffsetX, maxOffsetX)
        } else {
            overallOffsetX = 0f
        }

        if (scaledH > containerH) {
            val maxOffsetY = (scaledH - containerH) / 2f
            overallOffsetY = overallOffsetY.coerceIn(-maxOffsetY, maxOffsetY)
        } else {
            overallOffsetY = 0f
        }
    }

    fun clearCallbacks() {
        onEditTextRequested = null
        onElementChanged = null
        onElementRemoved = null
        onElementSelected = null
        onStartBatchUpdate = null
        onEndBatchUpdate = null
        onColorPicked = null
        onRequestOpenLayers = null
        onExitSelectionMode = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
}
