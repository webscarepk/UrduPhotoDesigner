package com.example.urduphotodesigner.common.views

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.Xfermode
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.enums.BlendType
import com.example.urduphotodesigner.common.canvas.enums.ElementType
import com.example.urduphotodesigner.common.canvas.enums.GradientType
import com.example.urduphotodesigner.common.canvas.enums.HAlign
import com.example.urduphotodesigner.common.canvas.enums.LabelShape
import com.example.urduphotodesigner.common.canvas.enums.LetterCasing
import com.example.urduphotodesigner.common.canvas.enums.ListStyle
import com.example.urduphotodesigner.common.canvas.enums.Mode
import com.example.urduphotodesigner.common.canvas.enums.MultiAlignMode
import com.example.urduphotodesigner.common.canvas.enums.TextAlignment
import com.example.urduphotodesigner.common.canvas.enums.TextDecoration
import com.example.urduphotodesigner.common.canvas.enums.VAlign
import com.example.urduphotodesigner.common.canvas.model.CanvasElement
import com.example.urduphotodesigner.common.canvas.model.ExportFormat
import com.example.urduphotodesigner.common.canvas.model.ExportOptions
import com.example.urduphotodesigner.common.canvas.model.ExportQuality
import com.example.urduphotodesigner.common.canvas.model.ExportResolution
import com.example.urduphotodesigner.common.canvas.model.GradientItem
import com.example.urduphotodesigner.common.canvas.sealed.ImageFilter
import com.example.urduphotodesigner.common.utils.ImageProcessor
import com.example.urduphotodesigner.data.model.FontEntity
import com.google.gson.Gson
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
    var onColorPicked: ((Int) -> Unit)? = null
) : View(context, attrs) {

    private var gestureDetector: GestureDetector

    private var isColorPickerMode = false
    private var pickerX = 0f
    private var pickerY = 0f
    private var isDraggingPicker = false
    private val desiredPickerIconSizePx = 64f

    private var desiredIconScreenSizePx = 36f
    private var iconTouched: String? = null
    private var allowFreeDrag: Boolean = false
    private val checkerSize = 20
    private val light = Color.parseColor("#F5F5F5")
    private val dark = Color.parseColor("#DDDDDD")

    private var activeGroupId: String? = null

    private val checkerShader: BitmapShader by lazy {
        // create a 2×2 tile
        val bmp = Bitmap.createBitmap(checkerSize * 2, checkerSize * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()

        // top-left & bottom-right = light
        p.color = light
        c.drawRect(0f, 0f, checkerSize.toFloat(), checkerSize.toFloat(), p)
        c.drawRect(
            checkerSize.toFloat(), checkerSize.toFloat(),
            (checkerSize * 2).toFloat(), (checkerSize * 2).toFloat(),
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

    init {
        gestureDetector = GestureDetector(context, GestureListener())
    }

    private val alignmentPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 1f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private var showVerticalGuide = false
    private var showHorizontalGuide = false
    private var showRotationVerticalGuide = false
    private var showRotationHorizontalGuide = false

    private val removeIcon: Bitmap by lazy {
        drawableToBitmap(AppCompatResources.getDrawable(context, R.drawable.ic_cross))
    }
    private val resizeIcon: Bitmap by lazy {
        drawableToBitmap(AppCompatResources.getDrawable(context, R.drawable.ic_resize))
    }
    private val rotateIcon: Bitmap by lazy {
        drawableToBitmap(AppCompatResources.getDrawable(context, R.drawable.ic_rotate))
    }
    private val editIcon: Bitmap by lazy {
        drawableToBitmap(AppCompatResources.getDrawable(context, R.drawable.ic_edit_text))
    }

    private var selectedElements: CopyOnWriteArrayList<CanvasElement> = CopyOnWriteArrayList()
    private var lastTouchedElement: CanvasElement? =
        null

    private fun drawableToBitmap(drawable: Drawable?): Bitmap {
        if (drawable == null) {
            return createBitmap(1, 1)
        }
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val bmp = createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1)
        )
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }

    private fun Float.dpToPx(): Float =
        this * resources.displayMetrics.density

    fun enableColorPicker() {
        isColorPickerMode = true

        val marginPx = 100f.dpToPx()
        pickerX = marginPx
        pickerY = marginPx

        invalidate()
    }

    fun disableColorPicker() {
        isColorPickerMode = false
        isDraggingPicker = false
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        canvasElements
            .firstOrNull { it.type == ElementType.BACKGROUND }
            ?.apply {
                logicalContentWidth = canvasWidth.toFloat()
                logicalContentHeight = canvasHeight.toFloat()
            }

        val existingBg = canvasElements.firstOrNull { it.type == ElementType.BACKGROUND }
        if (existingBg != null) {
            backgroundElement = canvasElements
                .firstOrNull { it.type == ElementType.BACKGROUND }!!
        }

        ensureBackgroundElement()
    }

    /**
     * If there isn’t already a background element, create one,
     * lock it, fill its fields, and insert it at index 0.
     */
    private fun ensureBackgroundElement() {
        // only add once
        if (canvasElements.any { it.type == ElementType.BACKGROUND }) return

        // insert at bottom of draw order
        canvasElements.add(0, backgroundElement)
        onElementChanged?.invoke(canvasElements.first())
        invalidate()  // trigger a redraw so you’ll see it immediately
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
        mode: MultiAlignMode = MultiAlignMode.CANVAS
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
                    elem.x = targetX.coerceIn(xRange.start, xRange.endInclusive)
                    onElementChanged?.invoke(elem)
                    invalidate()
                    return
                }

                // --- your existing logic for non-background elements ---
                val halfW = elem.getLocalContentWidth() * elem.scale / 2f
                val rawX = when (align) {
                    HAlign.LEFT -> halfW
                    HAlign.CENTER -> canvasWidth / 2f
                    HAlign.RIGHT -> canvasWidth - halfW
                }
                elem.x = rawX.coerceIn(halfW, canvasWidth - halfW)
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
        mode: MultiAlignMode = MultiAlignMode.CANVAS
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
                newcomer.isSelected = true
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
    private fun getCombinedSelectedBounds(): RectF {
        if (selectedElements.isEmpty()) {
            return RectF()
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        val transformedPoints = mutableListOf<Float>()

        selectedElements.forEach { element ->
            val elementBounds = RectF(
                -element.getLocalContentWidth() / 2f,
                -element.getLocalContentHeight() / 2f,
                element.getLocalContentWidth() / 2f,
                element.getLocalContentHeight() / 2f
            )

            val matrix = Matrix()
            matrix.postRotate(element.rotation)
            matrix.postScale(element.scale, element.scale)
            matrix.postTranslate(element.x, element.y)

            val corners = floatArrayOf(
                elementBounds.left, elementBounds.top,
                elementBounds.right, elementBounds.top,
                elementBounds.right, elementBounds.bottom,
                elementBounds.left, elementBounds.bottom
            )
            matrix.mapPoints(corners)
            transformedPoints.addAll(corners.toList())
        }

        // Find min/max from all transformed corner points
        minX = transformedPoints.filterIndexed { index, _ -> index % 2 == 0 }.minOrNull() ?: minX
        minY = transformedPoints.filterIndexed { index, _ -> index % 2 != 0 }.minOrNull() ?: minY
        maxX = transformedPoints.filterIndexed { index, _ -> index % 2 == 0 }.maxOrNull() ?: maxX
        maxY = transformedPoints.filterIndexed { index, _ -> index % 2 != 0 }.maxOrNull() ?: maxY

        return RectF(minX, minY, maxX, maxY)
    }

    private fun removeSelectedElement() {
        // Remove all selected elements
        val elementsToRemove =
            selectedElements.toList()
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
                element.imageFilter = filter
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

    private fun createBackgroundGradientShader(
        gradientItem: GradientItem,
        width: Float,
        height: Float
    ): Shader {
        val colors = gradientItem.colors.toIntArray()
        val positions = gradientItem.positions.toFloatArray()

        // compute actual center from relative values
        val cx = width * gradientItem.centerX
        val cy = height * gradientItem.centerY

        val baseShader = when (gradientItem.type) {
            GradientType.LINEAR -> {
                // angle in radians
                val theta = Math.toRadians(gradientItem.angle.toDouble())
                // full hypotenuse scaled, half on each side
                val halfLen = (hypot(width, height) * gradientItem.scale / 2f)
                val dx = (cos(theta) * halfLen).toFloat()
                val dy = (sin(theta) * halfLen).toFloat()

                LinearGradient(
                    cx - dx, cy - dy,
                    cx + dx, cy + dy,
                    colors, positions,
                    Shader.TileMode.CLAMP
                )
            }

            GradientType.RADIAL -> {
                // radius based on the smaller dimension
                val radius =
                    min(width, height) / 2f * gradientItem.radialRadiusFactor * gradientItem.scale
                RadialGradient(
                    cx, cy,
                    radius,
                    colors, positions,
                    Shader.TileMode.CLAMP
                )
            }

            GradientType.SWEEP -> {
                SweepGradient(cx, cy, colors, positions).apply {
                    // rotate start angle around the chosen center
                    val m = Matrix().apply {
                        postRotate(gradientItem.sweepStartAngle, cx, cy)
                    }
                    setLocalMatrix(m)
                }
            }
        }

        return baseShader
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
        canvasElements
            .first { it.type == ElementType.BACKGROUND }
            .apply {
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

    fun exportCanvas(
        options: ExportOptions,
        onProgress: ((percent: Int, stage: String) -> Unit)? = null
    ): Pair<Bitmap, String> {
        val contentWidth = this.canvasWidth
        val contentHeight = this.canvasHeight

        val scaleFactor = options.resolution.scaleFactor.takeIf { it > 0f } ?: 1f
        val outputWidth = (contentWidth * scaleFactor).roundToInt()
        val outputHeight = (contentHeight * scaleFactor).roundToInt()

        onProgress?.invoke(10, "Preparing canvas")

        val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        onProgress?.invoke(30, "Rendering design")
        onProgress?.invoke(40, "Just a few moments")

        renderCanvasTo(canvas, scaleFactor)
        onProgress?.invoke(50, "Please wait")

        onProgress?.invoke(70, "Encoding image data")

        canvasElements.forEach { element ->
            element.bitmap?.let {
                element.bitmapData = ImageProcessor.bitmapToFilePath(context, it)
            }
        }

        onProgress?.invoke(90, "Finishing up")
        val json = Gson().toJson(canvasElements)
        onProgress?.invoke(100, "Done")

        return Pair(bitmap, json)
    }

    suspend fun exportCanvasJson(): String {
        return withContext(Dispatchers.IO) {
            val canvasElementsCopy = ArrayList(canvasElements)
            canvasElementsCopy.forEach { element ->
                element.bitmap?.let {
                    element.bitmapData = ImageProcessor.bitmapToFilePath(context, it)
                }
            }
            Gson().toJson(canvasElementsCopy)
        }
    }

    private fun checkAlignment(element: CanvasElement) {
        val centerThreshold = 5f
        showVerticalGuide = abs(element.x - canvasWidth / 2f) < centerThreshold
        showHorizontalGuide = abs(element.y - canvasHeight / 2f) < centerThreshold
    }

    /**
     * Checks if the element's rotation is close to 0, 90, 180, or 270 degrees
     * and sets the rotation alignment guide flags accordingly.
     */
    private fun checkRotationAlignment(element: CanvasElement) {
        val rotationThreshold = 5f
        val normalizedRotation = (element.rotation % 360 + 360) % 360
        showRotationVerticalGuide = false
        showRotationHorizontalGuide = false
        if (abs(normalizedRotation) < rotationThreshold || abs(normalizedRotation - 180) < rotationThreshold) {
            showRotationVerticalGuide = true
        }
        if (abs(normalizedRotation - 90) < rotationThreshold || abs(normalizedRotation - 270) < rotationThreshold) {
            showRotationHorizontalGuide = true
        }
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

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val scaledWidth = canvasWidth * scale
        val scaledHeight = canvasHeight * scale
        offsetX = (width - scaledWidth) / 2f
        offsetY = (height - scaledHeight) / 2f

        canvas.withTranslation(offsetX, offsetY) {
            scale(scale, scale)

            drawCanvasElements(canvas)
        }

        canvas.restore()

        if (showVerticalGuide) {
            canvas.drawLine(
                width / 2f, 0f,
                width / 2f, height.toFloat(),
                alignmentPaint
            )
        }

        if (showHorizontalGuide) {
            canvas.drawLine(
                0f, height / 2f,
                width.toFloat(), height / 2f,
                alignmentPaint
            )
        }

        // Draw rotation alignment guides
        if (showRotationVerticalGuide) {
            // Draw a vertical line through the center of the canvas
            canvas.drawLine(
                width / 2f, 0f,
                width / 2f, height.toFloat(),
                alignmentPaint
            )
        }

        if (showRotationHorizontalGuide) {
            // Draw a horizontal line through the center of the canvas
            canvas.drawLine(
                0f, height / 2f,
                width.toFloat(), height / 2f,
                alignmentPaint
            )
        }
    }

    private fun drawCanvasElements(
        canvas: Canvas,
        showOverlays: Boolean = true,
        showCheckerboard: Boolean = true
    ) {
        canvas.save()
        canvas.clipRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat())

        if (showCheckerboard) {
            val checkerPaint = Paint().apply { shader = checkerShader }
            canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), checkerPaint)
        }

        canvasElements
            .firstOrNull { it.type == ElementType.BACKGROUND }
            ?.let { drawBackgroundElement(canvas, it) }

        // Draw all elements
        canvasElements.filter { it.type != ElementType.BACKGROUND }.sortedBy { it.zIndex }
            .forEach { element ->
                if (!element.isVisible) return@forEach
                canvas.withTranslation(element.x, element.y) {
                    canvas.rotate(element.rotation)
                    val fx = if (element.isFlippedX) -1f else 1f
                    val fy = if (element.isFlippedY) -1f else 1f
                    canvas.scale(element.scale * fx, element.scale * fy)

                    when (element.type) {
                        ElementType.TEXT -> {
                            drawTextElement(canvas, element)
                        }

                        ElementType.BACKGROUND -> {}

                        else -> {
                            element.paint.colorFilter = when (element.imageFilter) {
                                ImageFilter.Grayscale -> ColorMatrixColorFilter(ColorMatrix().apply {
                                    setSaturation(0f)
                                })

                                ImageFilter.Sepia -> ColorMatrixColorFilter(ColorMatrix().apply {
                                    set(
                                        floatArrayOf(
                                            0.393f, 0.769f, 0.189f, 0f, 0f,
                                            0.349f, 0.686f, 0.168f, 0f, 0f,
                                            0.272f, 0.534f, 0.131f, 0f, 0f,
                                            0f, 0f, 0f, 1f, 0f
                                        )
                                    )
                                })

                                ImageFilter.Invert -> ColorMatrixColorFilter(ColorMatrix().apply {
                                    set(
                                        floatArrayOf(
                                            -1f, 0f, 0f, 0f, 255f,
                                            0f, -1f, 0f, 0f, 255f,
                                            0f, 0f, -1f, 0f, 255f,
                                            0f, 0f, 0f, 1f, 0f
                                        )
                                    )
                                })

                                ImageFilter.CoolTint -> ColorMatrixColorFilter(ColorMatrix().apply {
                                    set(
                                        floatArrayOf(
                                            1.1f, 0f, 0f, 0f, -20f,  // Red decrease
                                            0f, 1f, 0f, 0f, 0f,      // Green
                                            0f, 0f, 1.3f, 0f, 20f,   // Blue boost
                                            0f, 0f, 0f, 1f, 0f
                                        )
                                    )
                                })

                                ImageFilter.WarmTint -> ColorMatrixColorFilter(ColorMatrix().apply {
                                    set(
                                        floatArrayOf(
                                            1.3f, 0f, 0f, 0f, 30f,   // Red boost
                                            0f, 1f, 0f, 0f, 0f,      // Green
                                            0f, 0f, 0.8f, 0f, -20f,  // Blue reduce
                                            0f, 0f, 0f, 1f, 0f
                                        )
                                    )
                                })

                                ImageFilter.Vintage -> ColorMatrixColorFilter(ColorMatrix().apply {
                                    set(
                                        floatArrayOf(
                                            0.9f, 0.3f, 0.1f, 0f, 5f,
                                            0.2f, 0.8f, 0.2f, 0f, 5f,
                                            0.1f, 0.2f, 0.7f, 0f, -10f,
                                            0f, 0f, 0f, 1f, 0f
                                        )
                                    )
                                })

                                ImageFilter.Film -> ColorMatrixColorFilter(ColorMatrix().apply {
                                    // High red + green, faded blue for a film-like tone
                                    set(
                                        floatArrayOf(
                                            1.2f, 0.1f, 0.1f, 0f, 15f,
                                            0.1f, 1.2f, 0.1f, 0f, 10f,
                                            0.1f, 0.1f, 0.9f, 0f, -10f,
                                            0f, 0f, 0f, 1f, 0f
                                        )
                                    )
                                })

                                ImageFilter.TealOrange -> ColorMatrixColorFilter(ColorMatrix().apply {
                                    // Teal shadows, orange highlights – a Hollywood-style grade
                                    set(
                                        floatArrayOf(
                                            1.2f, 0f, 0f, 0f, 20f,
                                            0f, 1.0f, 0f, 0f, 0f,
                                            0f, 0f, 0.8f, 0f, -10f,
                                            0f, 0f, 0f, 1f, 0f
                                        )
                                    )
                                })

                                ImageFilter.HighContrast -> ColorMatrixColorFilter(ColorMatrix().apply {
                                    set(
                                        floatArrayOf(
                                            1.5f, 0f, 0f, 0f, -50f,
                                            0f, 1.5f, 0f, 0f, -50f,
                                            0f, 0f, 1.5f, 0f, -50f,
                                            0f, 0f, 0f, 1f, 0f
                                        )
                                    )
                                })

                                ImageFilter.BlackWhite -> ColorMatrixColorFilter(ColorMatrix().apply {
                                    setSaturation(0f)
                                    val contrast = ColorMatrix().apply {
                                        set(
                                            floatArrayOf(
                                                1.4f, 0f, 0f, 0f, -50f,
                                                0f, 1.4f, 0f, 0f, -50f,
                                                0f, 0f, 1.4f, 0f, -50f,
                                                0f, 0f, 0f, 1f, 0f
                                            )
                                        )
                                    }
                                    postConcat(contrast)
                                })

                                else -> null
                            }

                            element.bitmap?.let {
                                canvas.drawBitmap(
                                    it,
                                    -it.width / 2f,
                                    -it.height / 2f,
                                    element.paint
                                )
                            }
                        }
                    }
                }
            }

        // --- Draw combined bounding box and icons based on selection state ---
        if (showOverlays && selectedElements.isNotEmpty()) {
            val combinedBounds = getCombinedSelectedBounds()

            val desiredScreenPadding = 10f
            val localSpacePadding =
                desiredScreenPadding / scale // Scale padding based on canvas scale

            val desiredScreenStrokeWidth = 2f
            val localSpaceStrokeWidth = desiredScreenStrokeWidth / scale // Scale stroke width

            val dashLengthOnScreen = 10f
            val gapLengthOnScreen = 10f
            val localDashLength = dashLengthOnScreen / scale
            val localGapLength = gapLengthOnScreen / scale

            val boxPaint = Paint().apply {
                color = Color.GRAY
                style = Paint.Style.STROKE
                pathEffect =
                    DashPathEffect(floatArrayOf(localDashLength, localGapLength), 0f)
                strokeWidth = localSpaceStrokeWidth
            }

            // Draw the single combined bounding box
            canvas.drawRect(
                combinedBounds.left - localSpacePadding,
                combinedBounds.top - localSpacePadding,
                combinedBounds.right + localSpacePadding,
                combinedBounds.bottom + localSpacePadding,
                boxPaint
            )

            // Draw icons if elements are selected and not locked
            if (selectedElements.any { !it.isLocked }) { // Draw icons if at least one selected element is not locked
                val localIconDrawWidth = desiredIconScreenSizePx / scale
                val localIconDrawHeight = desiredIconScreenSizePx / scale

                // --- Icon positions for multi-selection or single element selection ---
                val iconMap = mutableMapOf<String, Pair<Float, Float>>()

                if (selectedElements.size > 1) { // Multi-selection icons
                    // Remove icon (top-right)
                    iconMap["delete"] = Pair(
                        combinedBounds.right + localSpacePadding,
                        combinedBounds.top - localSpacePadding
                    )
                    // Resize icon (bottom-left)
                    iconMap["rotate"] = Pair(
                        combinedBounds.left - localSpacePadding,
                        combinedBounds.bottom + localSpacePadding
                    )
                    // Rotate icon (bottom-right)
                    iconMap["resize"] = Pair(
                        combinedBounds.right + localSpacePadding,
                        combinedBounds.bottom + localSpacePadding
                    )
                    // Edit icon should not be there for multi-selection
                } else if (selectedElements.size == 1) { // Single element selection icons
                    val element = selectedElements.first()
                    val isBg = element.type == ElementType.BACKGROUND

                    element.getIconPositions()
                        // drop the edit icon if it’s the background
                        .filterKeys { name -> !(isBg && name == "edit") }
                        .forEach { (iconName, position) ->
                            // same matrix math you had
                            val matrix = Matrix().apply {
                                postRotate(element.rotation)
                                postScale(element.scale, element.scale)
                                postTranslate(element.x, element.y)
                            }

                            val cords = floatArrayOf(position.x, position.y)
                            matrix.mapPoints(cords)
                            iconMap[iconName] = cords[0] to cords[1]
                        }
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
                        val dstRect = RectF(
                            position.first - localIconDrawWidth / 2f,
                            position.second - localIconDrawHeight / 2f,
                            position.first + localIconDrawWidth / 2f,
                            position.second + localIconDrawHeight / 2f
                        )
                        canvas.drawBitmap(bmp, null, dstRect, null)
                    }
                }
            }
        }

        if (showOverlays && isColorPickerMode) {
            val halfIcon = desiredPickerIconSizePx / 2f

            val (bmp, _) = exportCanvas(
                ExportOptions(
                    resolution = ExportResolution("picker", canvasWidth, canvasHeight, 1f),
                    quality = ExportQuality("", 100, "", 0),
                    format = ExportFormat("", Bitmap.CompressFormat.PNG, "", emptyList())
                )
            )
            val px = pickerX.roundToInt().coerceIn(0, bmp.width - 1)
            val py = pickerY.roundToInt().coerceIn(0, bmp.height - 1)
            val pixelColor = bmp.getPixel(px, py)
            val dark = isColorDark(pixelColor)

            canvas.drawCircle(
                pickerX,
                pickerY - halfIcon * 3,
                halfIcon + 10f,
                Paint().apply {
                    color = pixelColor
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
            )

            canvas.drawCircle(
                pickerX,
                pickerY - halfIcon * 3,
                halfIcon + 10f,
                Paint().apply {
                    color = if (dark) Color.WHITE else Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                }
            )

            canvas.drawCircle(
                pickerX,
                pickerY,
                halfIcon / 2,
                Paint().apply {
                    color = Color.BLACK
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
            )
        }
    }

    private fun drawBackgroundElement(canvas: Canvas, e: CanvasElement) {
        val w = canvasWidth.toFloat()
        val h = canvasHeight.toFloat()

        val backgroundPaint = Paint().apply {
            alpha = e.paintAlpha
            style = Paint.Style.FILL
            isAntiAlias = true
            xfermode = drawWithBlend(e)
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

                // half-width/height of the axis-aligned box after rotation
                val halfW = (sw / 2) * cosA + (sh / 2) * sinA
                val halfH = (sw / 2) * sinA + (sh / 2) * cosA

                // valid center range so that the rotated box stays fully on-canvas
                val xMin = halfW
                val xMax = w - halfW
                val yMax = h - halfH

                if (xMax >= xMin && yMax >= halfH) {
                    e.x = e.x.coerceIn(xMin.toFloat(), xMax.toFloat())
                    e.y = e.y.coerceIn(halfH.toFloat(), yMax.toFloat())
                } else {
                    // image too big to ever fully fit when rotated → switch to free-drag
                    allowFreeDrag = true
                }
            }
            // otherwise: leave e.x/e.y exactly as the user dragged them

            val left = e.x - sw / 2f
            val top = e.y - sh / 2f


            canvas.save()
            canvas.translate(left, top)
            canvas.scale(totalScale, totalScale)
            canvas.rotate(e.rotation, bmp.width / 2f, bmp.height / 2f)
            canvas.drawBitmap(bmp, 0f, 0f, backgroundPaint)
            canvas.restore()
            return
        }

        val left = e.x - w / 2f
        val top = e.y - h / 2f
        val pivotX = w / 2f
        val pivotY = h / 2f

        // 2) else if there's a gradient -> stretch it across the full canvas
        e.fillGradient?.let { grad ->
            canvas.save()
            canvas.translate(left, top)
            canvas.scale(e.scale, e.scale, pivotX, pivotY)
            canvas.rotate(e.rotation, pivotX, pivotY)

            backgroundPaint.shader = createBackgroundGradientShader(grad, w, h)
            canvas.drawRect(0f, 0f, w, h, backgroundPaint)
            backgroundPaint.shader = null
            canvas.restore()
            return
        }

        // 3) else -> solid color
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(e.scale, e.scale, pivotX, pivotY)
        canvas.rotate(e.rotation, pivotX, pivotY)

        backgroundPaint.shader = null
        backgroundPaint.color = e.backgroundColor
        canvas.drawRect(0f, 0f, w, h, backgroundPaint)
        canvas.restore()
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
                    -dx, -dy,
                    dx, dy,
                    colors, positions,
                    Shader.TileMode.CLAMP
                )
            }

            GradientType.RADIAL -> {
                val radius =
                    min(width, height) / 2f * gradientItem.radialRadiusFactor * gradientItem.scale

                rawShader = RadialGradient(
                    0f, 0f,
                    radius,
                    colors, positions,
                    Shader.TileMode.CLAMP
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
                    width = rectW,
                    height = rectH
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
                        labelRect,
                        20f,
                        20f,
                        labelPaint
                    ) // Adjust corner radius as needed
                }

                LabelShape.ROUNDED_RECTANGLE_STROKE -> {
                    labelPaint.style = Paint.Style.STROKE
                    labelPaint.strokeWidth = 4f // Adjust stroke width as needed
                    canvas.drawRoundRect(
                        labelRect,
                        20f,
                        20f,
                        labelPaint
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
                TextAlignment.JUSTIFY -> Paint.Align.LEFT // needed for justify, but handled separately
            }

            val indentOffset = if (i == 0) element.currentIndent else 0f

            fillPaint.textAlign = alignment
            val xPosition = when (alignment) {
                Paint.Align.LEFT -> -element.getLocalContentWidth() / 2f + indentOffset
                Paint.Align.CENTER -> 0f
                Paint.Align.RIGHT -> element.getLocalContentWidth() / 2f + indentOffset
                else -> 0f
            }

            element.fillGradient?.let { gradient ->
                val textWidth = fillPaint.measureText(displayText)
                val textHeight = fillPaint.textSize
                fillPaint.shader = createGradientShader(
                    gradientItem = gradient,
                    width = textWidth,
                    height = textHeight
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
                                gradientItem = gradient,
                                width = textWidth,
                                height = textHeight
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

    private fun drawWithBlend(element: CanvasElement): Xfermode {
        val fillPaint = Paint()
        when (element.blendType) {
            BlendType.MULTIPLY -> fillPaint.xfermode =
                PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)

            BlendType.SRC_OVER -> fillPaint.xfermode =
                PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)

            BlendType.SCREEN -> fillPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            BlendType.ADD -> fillPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            BlendType.LIGHTEN -> fillPaint.xfermode =
                PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)

            BlendType.DARKEN -> fillPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
            BlendType.SRC -> fillPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
            BlendType.DST -> fillPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST)
            BlendType.DST_OVER -> fillPaint.xfermode =
                PorterDuffXfermode(PorterDuff.Mode.DST_OVER)

            BlendType.SRC_IN -> fillPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            BlendType.DST_IN -> fillPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            BlendType.SRC_OUT -> fillPaint.xfermode =
                PorterDuffXfermode(PorterDuff.Mode.SRC_OUT)

            BlendType.DST_OUT -> fillPaint.xfermode =
                PorterDuffXfermode(PorterDuff.Mode.DST_OUT)

            BlendType.SRC_ATOP -> fillPaint.xfermode =
                PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)

            BlendType.DST_ATOP -> fillPaint.xfermode =
                PorterDuffXfermode(PorterDuff.Mode.DST_ATOP)

            BlendType.XOR -> fillPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.XOR)
        }
        return fillPaint.xfermode
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

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val x = (e.x - offsetX) / scale
            val y = (e.y - offsetY) / scale

            val touchedElement =
                canvasElements.filter { !it.isLocked }.sortedByDescending { it.zIndex }
                    .firstOrNull { element ->
                        val matrix = Matrix()
                        matrix.postTranslate(-element.x, -element.y)
                        matrix.postRotate(-element.rotation)
                        matrix.postScale(1f / element.scale, 1f / element.scale)

                        val touchPoint = floatArrayOf(x, y)
                        matrix.mapPoints(touchPoint)

                        val bounds = RectF(
                            -element.getLocalContentWidth() / 2f,
                            -element.getLocalContentHeight() / 2f,
                            element.getLocalContentWidth() / 2f,
                            element.getLocalContentHeight() / 2f
                        )
                        bounds.contains(touchPoint[0], touchPoint[1])
                    }

            if (touchedElement != null && currentMode == Mode.GROUP_EDIT
                && touchedElement.groupId == activeGroupId
                && touchedElement.type != ElementType.BACKGROUND
            ) {

                canvasElements.forEach { it.isSelected = false }
                selectedElements.clear() // Clear internal selected list as well
                touchedElement.isSelected = true // Select the new element
                selectedElements.add(touchedElement) // Add to internal selected list
                onElementSelected?.invoke(selectedElements) // Notify ViewModel of the new single selection
                onEditTextRequested?.invoke(touchedElement)
                invalidate()
                return true
            }
            if (touchedElement?.groupId != null) {
                activeGroupId = touchedElement.groupId

                // select all members of that group
                canvasElements.forEach {
                    it.isSelected = (it.groupId == activeGroupId)
                }
                selectedElements.clear()
                selectedElements.addAll(canvasElements.filter { it.isSelected })

                currentMode = Mode.GROUP_EDIT
                onElementSelected?.invoke(selectedElements)
                invalidate()
                return true
            } else if (touchedElement != null && touchedElement.type != ElementType.BACKGROUND) {
                // Deselect all existing elements before selecting the new one
                canvasElements.forEach { it.isSelected = false }
                selectedElements.clear() // Clear internal selected list as well
                touchedElement.isSelected = true // Select the new element
                selectedElements.add(touchedElement) // Add to internal selected list
                onElementSelected?.invoke(selectedElements) // Notify ViewModel of the new single selection
                onEditTextRequested?.invoke(touchedElement)
                invalidate()
                return true
            } else if (touchedElement != null && touchedElement.type == ElementType.BACKGROUND) {
                val bg = canvasElements
                    .firstOrNull { it.type == ElementType.BACKGROUND && !it.isLocked }
                if (bg?.bitmap != null) {
                    stepZoom(bg)
                    selectOnly(bg)
                    onElementChanged?.invoke(bg)
                    invalidate()
                    return true
                }
            }

            return false
        }
    }

    private fun animateZoom(elem: CanvasElement, toScale: Float) {
        val fromScale = elem.scale
        ValueAnimator.ofFloat(fromScale, toScale).apply {
            duration = 500L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                elem.scale = animation.animatedValue as Float
                onElementChanged?.invoke(elem)
                invalidate()
            }
            start()
        }
    }

    private fun stepZoom(elem: CanvasElement) {
        val next = when (elem.scale) {
            1f -> 2f
            2f -> 4f
            else -> 1f
        }
        animateZoom(elem, next)
    }

    // Helper: clear selection & select only this one
    private fun selectOnly(elem: CanvasElement) {
        canvasElements.forEach { it.isSelected = false }
        selectedElements.clear()
        elem.isSelected = true
        selectedElements.add(elem)
        onElementSelected?.invoke(selectedElements)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        val x = (event.x - offsetX) / scale
        val y = (event.y - offsetY) / scale

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
                        // sample color:
                        val (bmp, _) = exportCanvas(
                            ExportOptions(
                                resolution = ExportResolution(
                                    "picker",
                                    canvasWidth,
                                    canvasHeight,
                                    1f
                                ),
                                quality = ExportQuality("", 100, "", 0),
                                format = ExportFormat(
                                    "",
                                    Bitmap.CompressFormat.PNG,
                                    "",
                                    emptyList()
                                )
                            )
                        )
                        val px = pickerX.roundToInt().coerceIn(0, bmp.width - 1)
                        val py = pickerY.roundToInt().coerceIn(0, bmp.height - 1)
                        val color = bmp.getPixel(px, py)
                        onColorPicked?.invoke(color)
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
                    initialScale = selectedElements.firstOrNull()?.scale
                        ?: 1f // Get initial scale of the first selected element
                    initialRotation =
                        selectedElements.firstOrNull()?.rotation ?: 0f // Get initial rotation
                }
            }

            MotionEvent.ACTION_DOWN -> {
                iconTouched = null // Reset touched icon
                lastTouchedElement = null // Reset last touched element
                showVerticalGuide = false
                showHorizontalGuide = false
                showRotationVerticalGuide = false // Reset rotation guides
                showRotationHorizontalGuide = false // Reset rotation guides

                if (currentMode == Mode.GROUP_EDIT && activeGroupId != null) {
                    // see if we tapped on one of the group's members
                    val hitChild = canvasElements
                        .filter { it.groupId == activeGroupId && !it.isLocked }
                        .sortedByDescending { it.zIndex }
                        .firstOrNull { element ->
                            // reuse your hit‑test transform
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

                        // 2) Deselect everyone, select only the child
                        canvasElements.forEach { it.isSelected = false }
                        selectedElements.clear()
                        hitChild.isSelected = true
                        selectedElements.add(hitChild)
                        onElementSelected?.invoke(selectedElements)

                        // 3) Start drag
                        touchStartX = x
                        touchStartY = y
                        invalidate()
                        return true
                    } else {
                        // exit group‑edit if tapped outside
                        currentMode = Mode.NONE
                        activeGroupId = null
                        canvasElements.forEach { it.isSelected = false }
                        selectedElements.clear()
                        onElementSelected?.invoke(selectedElements)
                    }
                }

                // 1. Check for icon touch (regardless of single or multi-selection, based on combined bounds)
                if (selectedElements.isNotEmpty()) {
                    val combinedBounds = getCombinedSelectedBounds()
                    if (selectedElements.size > 1 && combinedBounds.contains(x, y)) {
                        // Start dragging the group of selected elements
                        currentMode = Mode.DRAG
                        touchStartX = x
                        touchStartY = y
                        return true
                    }
                    val localSpacePadding = 10f / scale // Use the same padding as drawing
                    val adjustedIconHitSize = desiredIconScreenSizePx / scale * 1.5f

                    // Define "global" icon positions based on combined bounds
                    val globalIconRegions = mutableMapOf<String, RectF>()

                    if (selectedElements.size > 1) { // Multi-selection icon hit areas
                        // Delete icon (top-right of combined bounds)
                        globalIconRegions["delete"] = RectF(
                            combinedBounds.right + localSpacePadding - adjustedIconHitSize / 2f,
                            combinedBounds.top - localSpacePadding - adjustedIconHitSize / 2f,
                            combinedBounds.right + localSpacePadding + adjustedIconHitSize / 2f,
                            combinedBounds.top - localSpacePadding + adjustedIconHitSize / 2f
                        )
                        // Resize icon (bottom-left of combined bounds)
                        globalIconRegions["rotate"] = RectF(
                            combinedBounds.left - localSpacePadding - adjustedIconHitSize / 2f,
                            combinedBounds.bottom + localSpacePadding - adjustedIconHitSize / 2f,
                            combinedBounds.left - localSpacePadding + adjustedIconHitSize / 2f,
                            combinedBounds.bottom + localSpacePadding + adjustedIconHitSize / 2f
                        )
                        // Rotate icon (bottom-right of combined bounds)
                        globalIconRegions["resize"] = RectF(
                            combinedBounds.right + localSpacePadding - adjustedIconHitSize / 2f,
                            combinedBounds.bottom + localSpacePadding - adjustedIconHitSize / 2f,
                            combinedBounds.right + localSpacePadding + adjustedIconHitSize / 2f,
                            combinedBounds.bottom + localSpacePadding + adjustedIconHitSize / 2f
                        )
                        // No edit icon for multi-selection
                    } else { // Single element selection icon hit areas
                        val element = selectedElements.first()
                        val elementIconPositions =
                            element.getIconPositions() // These are element-local positions

                        // Convert element-local icon positions to canvas coordinates for hit testing
                        val elementMatrix = Matrix()
                        elementMatrix.postRotate(element.rotation)
                        elementMatrix.postScale(element.scale, element.scale)
                        elementMatrix.postTranslate(element.x, element.y)

                        elementIconPositions.forEach { (iconName, position) ->
                            val iconCenterInCanvasCords = floatArrayOf(position.x, position.y)
                            elementMatrix.mapPoints(iconCenterInCanvasCords)

                            globalIconRegions[iconName] = RectF(
                                iconCenterInCanvasCords[0] - adjustedIconHitSize / 2f,
                                iconCenterInCanvasCords[1] - adjustedIconHitSize / 2f,
                                iconCenterInCanvasCords[0] + adjustedIconHitSize / 2f,
                                iconCenterInCanvasCords[1] + adjustedIconHitSize / 2f
                            )
                        }
                    }

                    val touchedIconEntry = globalIconRegions.entries.firstOrNull { (_, rect) ->
                        rect.contains(x, y)
                    }

                    if (touchedIconEntry != null) {
                        iconTouched = touchedIconEntry.key
                        // For icon interaction, we often operate on the entire selection or a specific element related to the icon
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
                                    initialElementPositionsRelativeToGroupPivot[element.id] =
                                        Pair(
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

                            val bounds = RectF(
                                -element.getLocalContentWidth() / 2f,
                                -element.getLocalContentHeight() / 2f,
                                element.getLocalContentWidth() / 2f,
                                element.getLocalContentHeight() / 2f
                            )
                            bounds.contains(touchPoint[0], touchPoint[1])
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
                    } else {
                        if (touchedElement.isSelected) {
                            // If an already selected element is tapped, assume user wants to drag the group
                            // Do NOT deselect others.
                            lastTouchedElement =
                                touchedElement // Set the one that initiated the drag
                            currentMode = Mode.DRAG
                            touchStartX = x
                            touchStartY = y

                        } else {
                            // If an unselected element is tapped, deselect all others and select only this one
                            canvasElements.forEach { it.isSelected = false } // Deselect all
                            selectedElements.clear() // Clear internal selected list
                            touchedElement.isSelected = true // Select the new element
                            selectedElements.add(touchedElement) // Add to internal selected list
                            lastTouchedElement = touchedElement // Set for drag
                            currentMode = Mode.DRAG
                            touchStartX = x
                            touchStartY = y
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
                        onElementSelected?.invoke(selectedElements) // Notify ViewModel of empty selection
                        invalidate()
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

                if (elementsToModify.isEmpty()) return true // No elements to modify

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
                            // If just one, check its own center
                            if (selectedElements.size == 1) {
                                checkAlignment(selectedElements.first())
                            } else {
                                // Multiple: compute combined bounds center
                                val combined = getCombinedSelectedBounds()
                                val centerX = combined.left + combined.width() / 2f
                                val centerY = combined.top + combined.height() / 2f
                                val threshold = 5f

                                showVerticalGuide = abs(centerX - canvasWidth / 2f) < threshold
                                showHorizontalGuide = abs(centerY - canvasHeight / 2f) < threshold
                            }
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
                                        0.1f,
                                        5f
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
                            invalidate()
                        }
                    }

                    Mode.ROTATE -> {
                        if (selectedElements.isEmpty()) return true

                        val currentAngle = atan2(
                            y - initialGroupPivotY,
                            x - initialGroupPivotX
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
                        } else {
                            showRotationVerticalGuide = false
                            showRotationHorizontalGuide = false
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

                                val newScale = (element.scale * scaleChange).coerceIn(0.1f, 5f)
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
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                showVerticalGuide = false
                showHorizontalGuide = false
                showRotationVerticalGuide = false // Reset rotation guides on ACTION_UP
                showRotationHorizontalGuide = false // Reset rotation guides on ACTION_UP


                if (currentMode == Mode.DRAG || currentMode == Mode.ROTATE || currentMode == Mode.RESIZE) {
                    selectedElements.filter { !it.isLocked }.forEach {
                        onElementChanged?.invoke(it)
                        onEndBatchUpdate?.invoke(it.id)
                    }
                }

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
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
}