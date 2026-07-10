package com.webscare.urducanvas.common.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
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
import android.graphics.Xfermode
import android.graphics.drawable.Drawable
import android.text.TextPaint
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.BlendType
import com.webscare.urducanvas.common.canvas.enums.BrushStyle
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.FeatherDirection
import com.webscare.urducanvas.common.canvas.enums.GradientType
import com.webscare.urducanvas.common.canvas.enums.Mode
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.canvas.model.GradientItem
import com.webscare.urducanvas.common.canvas.model.StrokeData
import com.webscare.urducanvas.common.utils.BrushRenderUtils
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Suppress("LargeClass")
class CanvasRenderer(private val view: CanvasView) {

    companion object {
        private const val ICON_HANDLE_OFFSET_DP = 80f
        private const val ROTATE_LINE_DASH_FACTOR = 10f
        private const val MASK_SIZE = 128
        private const val ALPHA_MAX = 255

        // Color picker UI constants
        private const val COLOR_PICKER_PREVIEW_OFFSET = 3
        private const val COLOR_PICKER_STROKE_WIDTH = 4f
        private const val COLOR_PICKER_RING_OFFSET = 20f

        // Rendering color constants
        private const val SHADOW_ALPHA_DARK = 50
        private const val SHADOW_ALPHA_LIGHT = 30
        private const val GROUP_OVERLAY_ALPHA = 140
        private const val LUMINANCE_DARK_THRESHOLD = 128f

        // Icon key names
        private const val ICON_ROTATE_KEY = "rotate"

        // Grid/ruler constants
        private const val GRID_MIN_STEP_PX = 2f
        private const val GRID_STROKE_CLAMP_MIN = 0.8f
        private const val GRID_STROKE_CLAMP_MAX = 1.5f
        private const val GRID_SPACING_DP = 50f
        private const val RULER_DEFAULT_TICK = 100f
        private const val RULER_ROTATION_DEGREES = -90f
    }
    private val backgroundRenderer = BackgroundRenderer(view)
    private val textRenderer = TextElementRenderer(view)
    private val shapeRenderer = ShapeElementRenderer(view)
    private val stickerRenderer = StickerElementRenderer(view)

    private val context: Context get() = view.context

    // Paints moved from CanvasView
    private val checkerSize = 20
    private val light = "#F5F5F5".toColorInt()
    private val dark = "#DDDDDD".toColorInt()

    private val checkerShader: BitmapShader by lazy {
        val bmp = createBitmap(checkerSize * 2, checkerSize * 2)
        val c = Canvas(bmp)
        val p = Paint()
        p.color = light
        c.drawRect(0f, 0f, checkerSize.toFloat(), checkerSize.toFloat(), p)
        c.drawRect(
            checkerSize.toFloat(), checkerSize.toFloat(),
            (checkerSize * 2).toFloat(), (checkerSize * 2).toFloat(), p
        )
        p.color = dark
        c.drawRect(checkerSize.toFloat(), 0f, (checkerSize * 2).toFloat(), checkerSize.toFloat(), p)
        c.drawRect(0f, checkerSize.toFloat(), checkerSize.toFloat(), (checkerSize * 2).toFloat(), p)
        BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    val checkerPaint = Paint().apply { shader = checkerShader }

    private val rotateLinePaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
        isAntiAlias = true
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
        alpha = 30
        maskFilter = BlurMaskFilter(100f, BlurMaskFilter.Blur.NORMAL)
    }

    val drawingModeOverlayPaint = Paint().apply {
        color = Color.argb(120, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val canvasSnapPaint = Paint().apply {
        color = Color.parseColor("#00BCD4")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(14f, 6f), 0f)
    }

    private val gridPaint = Paint().apply {
        color = Color.argb(60, 0, 0, 0)
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = false
    }

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

    val removeIcon: Drawable by lazy { AppCompatResources.getDrawable(context, R.drawable.ic_cross)!! }
    val resizeIcon: Drawable by lazy { AppCompatResources.getDrawable(context, R.drawable.ic_resize)!! }
    val rotateIcon: Drawable by lazy { AppCompatResources.getDrawable(context, R.drawable.ic_rotate)!! }
    val editIcon: Drawable by lazy { AppCompatResources.getDrawable(context, R.drawable.ic_edit_text)!! }
    val transformIcon: Drawable by lazy { AppCompatResources.getDrawable(context, R.drawable.ic_transform)!! }

    private val reusableBoxPaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
    }

    private fun Float.dpToPx(): Float = this * context.resources.displayMetrics.density

    fun render(canvas: Canvas) {
        val pivotX = view.width / 2f
        val pivotY = view.height / 2f

        canvas.translate(view.overallOffsetX, view.overallOffsetY)
        canvas.scale(view.overallScale, view.overallScale, pivotX, pivotY)

        val scaledWidth = view.canvasWidth * view.scale
        val scaledHeight = view.canvasHeight * view.scale
        view.offsetX = (view.width - scaledWidth) / 2f
        view.offsetY = (view.height - scaledHeight) / 2f

        canvas.withTranslation(view.offsetX, view.offsetY) {
            scale(view.scale, view.scale)
            if (view.isDrawing) {
                renderDrawingMode(this)
            } else {
                renderEditingMode(this)
            }
        }

        drawGuides(canvas)
    }

    private fun renderDrawingMode(canvas: Canvas) {
        drawCanvasShadow(canvas)
        drawCanvasElements(canvas, showOverlays = false, showCheckerboard = false)

        canvas.drawRect(
            0f,
            0f,
            view.canvasWidth.toFloat(),
            view.canvasHeight.toFloat(),
            drawingModeOverlayPaint,
        )

        view.activeSessionElement?.let { session ->
            if (!session.drawStrokes.isNullOrEmpty()) {
                drawDrawElement(canvas, session)
            }
        }

        if (view.currentStrokePath != null && view.currentStrokePaint != null) {
            drawLivePreviewStroke(canvas)
        }
    }

    private fun renderEditingMode(canvas: Canvas) {
        drawCanvasShadow(canvas)
        if (view.currentMode == Mode.GROUP_EDIT && view.activeGroupId != null) {
            drawCanvasElements(canvas, showOverlays = false)
            canvas.drawRect(
                0f,
                0f,
                view.canvasWidth.toFloat(),
                view.canvasHeight.toFloat(),
                Paint().apply {
                    color = Color.argb(GROUP_OVERLAY_ALPHA, 255, 255, 255)
                    style = Paint.Style.FILL
                },
            )
            val groupChildIds = view.canvasElements.filter { it.groupId == view.activeGroupId }.map { it.id }.toSet()
            drawCanvasElements(canvas, showOverlays = true, showCheckerboard = false, isolatedIds = groupChildIds)
        } else {
            drawCanvasElements(canvas)
        }
    }

    internal fun drawCanvasElements(
        canvas: Canvas,
        showOverlays: Boolean = true,
        showCheckerboard: Boolean = true,
        isolatedIds: Set<String>? = null,
    ) {
        canvas.save()
        val clipRect = RectF(0f, 0f, view.canvasWidth.toFloat(), view.canvasHeight.toFloat())
        canvas.clipRect(clipRect)

        if (showCheckerboard) {
            canvas.drawRect(0f, 0f, view.canvasWidth.toFloat(), view.canvasHeight.toFloat(), checkerPaint)
        }

        view.canvasElements.forEach { element ->
            if (!element.isVisible) return@forEach
            if (element.type == ElementType.GROUP) return@forEach
            if (isolatedIds != null && element.id !in isolatedIds) return@forEach

            if (element.type == ElementType.BACKGROUND) {
                backgroundRenderer.draw(canvas, element)
                return@forEach
            }

            if (element.type == ElementType.IMAGE &&
                element.logicalContentWidth == view.canvasWidth.toFloat() &&
                element.logicalContentHeight == view.canvasHeight.toFloat() &&
                element.imageFitMode == "cover"
            ) {
                backgroundRenderer.draw(canvas, element)
                return@forEach
            }

            canvas.withTranslation(element.x, element.y) {
                rotate(element.rotation)
                val fx = if (element.isFlippedX) -1f else 1f
                val fy = if (element.isFlippedY) -1f else 1f
                scale(element.scale * fx, element.scale * fy)

                when (element.type) {
                    ElementType.DRAW -> {
                        if (element.bitmap != null) {
                            val bmp = element.bitmap!!
                            if (!bmp.isRecycled) {
                                val left = -bmp.width / 2f
                                val top = -bmp.height / 2f
                                val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    alpha = element.paintAlpha
                                    isFilterBitmap = true
                                }
                                canvas.drawBitmap(bmp, left, top, drawPaint)
                            }
                        } else {
                            drawDrawElement(canvas, element)
                        }
                    }
                    ElementType.SHAPE -> shapeRenderer.draw(canvas, element)
                    ElementType.TEXT -> textRenderer.draw(canvas, element)
                    else -> stickerRenderer.draw(canvas, element)
                }
            }
        }

        canvas.restore()

        if (showOverlays) {
            drawElementOverlays(canvas, showOverlays)
        }

        drawColorPickerIfNeeded(canvas, showOverlays)
    }

    private fun drawColorPickerIfNeeded(canvas: Canvas, showOverlays: Boolean) {
        if (!showOverlays || !view.isColorPickerMode) return
        val halfIcon = view.desiredPickerIconSizePx
        val bmp = view.colorPickerBitmap

        if (bmp != null && !bmp.isRecycled) {
            val px = view.pickerX.toInt().coerceIn(0, bmp.width - 1)
            val py = view.pickerY.toInt().coerceIn(0, bmp.height - 1)
            val pixelColor = bmp.getPixel(px, py)
            val dark = view.isColorDark(pixelColor)

            canvas.drawCircle(
                view.pickerX,
                view.pickerY - halfIcon * COLOR_PICKER_PREVIEW_OFFSET,
                halfIcon + COLOR_PICKER_RING_OFFSET,
                Paint().apply {
                    color = pixelColor
                    style = Paint.Style.FILL
                    isAntiAlias = true
                },
            )

            canvas.drawCircle(
                view.pickerX,
                view.pickerY - halfIcon * COLOR_PICKER_PREVIEW_OFFSET,
                halfIcon + COLOR_PICKER_RING_OFFSET,
                Paint().apply {
                    color = if (dark) Color.WHITE else Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = COLOR_PICKER_STROKE_WIDTH
                },
            )
        }

        canvas.drawCircle(
            view.pickerX,
            view.pickerY,
            halfIcon / 4,
            Paint().apply {
                color = Color.BLACK
                style = Paint.Style.FILL
                isAntiAlias = true
            },
        )
    }

    internal fun drawGuides(canvas: Canvas) {
        drawAlignmentGuides(canvas)
        drawCanvasSnapGuides(canvas)

        if (view.showGrid) {
            drawGrid(canvas)
        }
        if (view.showRuler) {
            drawRuler(canvas)
        }
    }

    private fun drawAlignmentGuides(canvas: Canvas) {
        if (view.showVerticalGuide || view.showRotationVerticalGuide) {
            canvas.drawLine(view.width / 2f, 0f, view.width / 2f, view.height.toFloat(), alignmentPaint)
        }
        if (view.showHorizontalGuide || view.showRotationHorizontalGuide) {
            canvas.drawLine(0f, view.height / 2f, view.width.toFloat(), view.height / 2f, alignmentPaint)
        }
    }

    private fun drawCanvasSnapGuides(canvas: Canvas) {
        if (view.showCanvasCenterVerticalSnap || view.showCanvasCenterHorizontalSnap) {
            drawGrid(canvas)
            if (view.showCanvasCenterVerticalSnap) {
                canvas.drawLine(view.width / 2f, 0f, view.width / 2f, view.height.toFloat(), canvasSnapPaint)
            }
            if (view.showCanvasCenterHorizontalSnap) {
                canvas.drawLine(0f, view.height / 2f, view.width.toFloat(), view.height / 2f, canvasSnapPaint)
            }
        }
    }

    private fun getCanvasBgColor(): Int {
        val bgElement = view.canvasElements.firstOrNull { it.type == ElementType.BACKGROUND }
        return bgElement?.backgroundColor ?: Color.WHITE
    }

    private fun isCanvasBgDark(): Boolean {
        val color = getCanvasBgColor()
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val luminance = 0.299f * r + 0.587f * g + 0.114f * b
        return luminance < LUMINANCE_DARK_THRESHOLD
    }

    internal fun drawCanvasShadow(canvas: Canvas) {
        val rect = RectF(0f, 0f, view.canvasWidth.toFloat(), view.canvasHeight.toFloat())
        val spread = 40f
        val shadowRect = RectF(
            rect.left - spread,
            rect.top - spread,
            rect.right + spread,
            rect.bottom + spread,
        )

        val isDark = isCanvasBgDark()
        canvasShadowPaint.color = if (isDark) Color.WHITE else Color.BLACK
        canvasShadowPaint.alpha = if (isDark) SHADOW_ALPHA_DARK else SHADOW_ALPHA_LIGHT

        canvas.drawRoundRect(shadowRect, SHADOW_ALPHA_LIGHT.toFloat(), SHADOW_ALPHA_LIGHT.toFloat(), canvasShadowPaint)
    }

    internal fun drawElementOverlays(canvas: Canvas, showOverlays: Boolean = true) {
        if (!showOverlays || view.selectedElements.isEmpty()) return

        val desiredScreenStrokeWidth = 2f
        val dashLengthOnScreen = 10f
        val gapLengthOnScreen = 10f
        val localSpaceStrokeWidth = desiredScreenStrokeWidth / (view.scale * view.overallScale)
        val localDashLength = dashLengthOnScreen / (view.scale * view.overallScale)
        val localGapLength = gapLengthOnScreen / (view.scale * view.overallScale)

        reusableBoxPaint.color = Color.GRAY
        reusableBoxPaint.style = Paint.Style.STROKE
        reusableBoxPaint.pathEffect = DashPathEffect(floatArrayOf(localDashLength, localGapLength), 0f)
        reusableBoxPaint.strokeWidth = localSpaceStrokeWidth

        val rotatedPath = if (view.selectedElements.size > 1) {
            view.getGroupRotatedPath()
        } else {
            view.getSelectionPath()
        }
        if (rotatedPath != null) {
            canvas.drawPath(rotatedPath, reusableBoxPaint)
        }

        if (view.currentMode == Mode.ROTATE) {
            drawRotationOverlayIfNeeded(canvas)
        }

        if (view.selectedElements.any { !it.isLocked }) {
            drawOverlayIcons(canvas)
        }
    }

    private fun drawRotationOverlayIfNeeded(canvas: Canvas) {
        val rotationValue: String
        val cx: Float
        val cy: Float

        if (view.selectedElements.size == 1) {
            val element = view.selectedElements.first()
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
            val bounds = view.getCombinedSelectedBounds()
            cx = bounds.centerX()
            cy = bounds.centerY()
            val avgRotation = view.selectedElements.map { it.rotation }.average().toFloat()
            rotationValue = "${avgRotation.roundToInt()}°"
        }

        drawRotationLabel(canvas, cx, cy, rotationValue)
    }

    private fun drawRotationLabel(canvas: Canvas, cx: Float, cy: Float, rotationValue: String) {
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

    private fun drawOverlayIcons(canvas: Canvas) {
        val localIconDrawWidth = view.desiredIconScreenSizePx / (view.scale * view.overallScale)
        val localIconDrawHeight = view.desiredIconScreenSizePx / (view.scale * view.overallScale)

        val iconMap = mutableMapOf<String, Pair<Float, Float>>()

        if (view.selectedElements.size > 1) {
            val c = view.getGroupRotatedBounds()
            val left = c[0]
            val top = c[1]
            val right = c[2]
            val bottom = c[3]

            iconMap["delete"] = Pair(right, top)
            iconMap["resize"] = Pair(right, bottom)
            iconMap[ICON_ROTATE_KEY] = Pair(left, bottom)
        } else if (view.selectedElements.size == 1) {
            val element = view.selectedElements.first()
            val corners = element.getRotatedCorners()
            iconMap["delete"] = Pair(corners[2], corners[3])
            iconMap["edit"] = Pair(corners[0], corners[1])

            val topCenterX = (corners[0] + corners[2]) / 2f
            val topCenterY = (corners[1] + corners[3]) / 2f
            val offset = ICON_HANDLE_OFFSET_DP
            val rotateX = topCenterX
            val rotateY = topCenterY - offset

            iconMap[ICON_ROTATE_KEY] = Pair(rotateX, rotateY)
            val resizeCornerIdx = 4
            val transformCornerIdx = 6
            iconMap["resize"] = Pair(corners[resizeCornerIdx], corners[resizeCornerIdx + 1])
            if (element.type == ElementType.SHAPE) {
                iconMap["transform"] = Pair(corners[transformCornerIdx], corners[transformCornerIdx + 1])
            }
        }

        iconMap.forEach { (iconName, position) ->
            drawSingleOverlayIcon(canvas, iconName, position, localIconDrawWidth, localIconDrawHeight)
        }
    }

    private fun drawSingleOverlayIcon(
        canvas: Canvas,
        iconName: String,
        position: Pair<Float, Float>,
        localIconDrawWidth: Float,
        localIconDrawHeight: Float
    ) {
        val iconBitmap = when (iconName) {
            "delete" -> removeIcon
            "rotate" -> rotateIcon
            "resize" -> resizeIcon
            "edit" -> editIcon
            "transform" -> transformIcon
            else -> null
        } ?: return

        var dstRect = RectF(
            position.first - localIconDrawWidth / 2f,
            position.second - localIconDrawHeight / 2f,
            position.first + localIconDrawWidth / 2f,
            position.second + localIconDrawHeight / 2f,
        )

        if (iconName == ICON_ROTATE_KEY && view.selectedElements.isNotEmpty()) {
            val (localTopCenter, localRotateIcon) = getRotateHandlePoints()
            rotateLinePaint.strokeWidth = 4f / (view.scale * view.overallScale)
            val dashLen = ROTATE_LINE_DASH_FACTOR / (view.scale * view.overallScale)
            rotateLinePaint.pathEffect = DashPathEffect(floatArrayOf(dashLen, dashLen), 0f)

            canvas.drawLine(
                localTopCenter[0], localTopCenter[1],
                localRotateIcon[0], localRotateIcon[1],
                rotateLinePaint
            )

            dstRect = RectF(
                localRotateIcon[0] - localIconDrawWidth / 2f,
                localRotateIcon[1] - localIconDrawHeight / 2f,
                localRotateIcon[0] + localIconDrawWidth / 2f,
                localRotateIcon[1] + localIconDrawHeight / 2f,
            )
        }

        view.lastDrawnIconRect[iconName] = dstRect
        iconBitmap.setBounds(
            dstRect.left.toInt(),
            dstRect.top.toInt(),
            dstRect.right.toInt(),
            dstRect.bottom.toInt(),
        )
        iconBitmap.draw(canvas)
    }

    private fun getRotateHandlePoints(): Pair<FloatArray, FloatArray> {
        val fixedHandleLengthPx = ICON_HANDLE_OFFSET_DP
        return if (view.selectedElements.size == 1) {
            val element = view.selectedElements.first()
            if (view.isRotating) {
                val bounds = element.getTightTextBounds()
                val scaleX = element.scale * if (element.isFlippedX) -1f else 1f
                val scaleY = element.scale * if (element.isFlippedY) -1f else 1f
                val matrix = Matrix().apply {
                    postScale(scaleX, scaleY)
                    postRotate(element.rotation)
                    postTranslate(element.x, element.y)
                }
                val topCenter = floatArrayOf(bounds.centerX(), bounds.top)
                val handleDist = fixedHandleLengthPx / (view.scale * view.overallScale)
                val rotateIcon = floatArrayOf(bounds.centerX(), bounds.top - handleDist)
                matrix.mapPoints(topCenter)
                matrix.mapPoints(rotateIcon)
                topCenter to rotateIcon
            } else {
                val corners = element.getRotatedCorners()
                val topY = listOf(corners[1], corners[3], corners[5], corners[7]).minOrNull() ?: 0f
                val leftX = listOf(corners[0], corners[2], corners[4], corners[6]).minOrNull() ?: 0f
                val rightX = listOf(corners[0], corners[2], corners[4], corners[6]).maxOrNull() ?: 0f
                val centerX = (leftX + rightX) / 2f
                val topCenter = floatArrayOf(centerX, topY)
                val handleDist = fixedHandleLengthPx / (view.scale * view.overallScale)
                val rotateIcon = floatArrayOf(centerX, topY - handleDist)
                topCenter to rotateIcon
            }
        } else {
            val groupBounds = view.getGroupTrueBounds()
            val pivotX = (groupBounds[0] + groupBounds[2]) / 2f
            val topY = groupBounds[1]
            val topCenter = floatArrayOf(pivotX, topY)
            val handleDist = fixedHandleLengthPx / (view.scale * view.overallScale)
            val rotateIcon = floatArrayOf(pivotX, topY - handleDist)
            topCenter to rotateIcon
        }
    }

    internal fun drawDrawElement(canvas: Canvas, element: CanvasElement) {
        element.drawStrokes?.forEach { stroke ->
            when (stroke.style) {
                BrushStyle.BRUSH ->
                    BrushRenderUtils.drawBrushStroke(canvas, stroke, element.paintAlpha)
                BrushStyle.PEN ->
                    BrushRenderUtils.drawTaperedPenStroke(canvas, stroke, element.paintAlpha)
                BrushStyle.HIGHLIGHTER -> {
                    val paint = BrushRenderUtils.makeStrokePaint(stroke, view.width, view.height)
                    paint.alpha = element.paintAlpha
                    val offset = stroke.thickness * 0.3f
                    val path = Path(stroke.path)
                    val m = Matrix()
                    m.postTranslate(0f, offset)
                    path.transform(m)
                    canvas.drawPath(path, paint)
                }
                else -> {
                    val paint = BrushRenderUtils.makeStrokePaint(stroke, view.width, view.height)
                    paint.alpha = element.paintAlpha
                    canvas.drawPath(stroke.path!!, paint)
                }
            }
        }

        if (element == view.activeSessionElement &&
            view.currentStrokePath != null && view.currentStrokePaint != null
        ) {
            canvas.drawPath(view.currentStrokePath!!, view.currentStrokePaint!!)
        }
    }

    internal fun drawLivePreviewStroke(canvas: Canvas) {
        val path = view.currentStrokePath ?: return
        val tempStroke = StrokeData(
            path = path,
            color = view.currentBrushColor,
            thickness = view.currentBrushThickness,
            hardness = view.currentBrushHardness,
            style = view.currentBrushStyle,
            gradient = view.currentBrushGradient,
        )
        drawStyleSpecificStroke(canvas, tempStroke)
    }

    private fun drawStyleSpecificStroke(canvas: Canvas, tempStroke: StrokeData) {
        when (view.currentBrushStyle) {
            BrushStyle.BRUSH -> BrushRenderUtils.drawBrushStroke(canvas, tempStroke, ALPHA_MAX)
            BrushStyle.PEN -> BrushRenderUtils.drawTaperedPenStroke(canvas, tempStroke, ALPHA_MAX)
            BrushStyle.PENCIL -> drawPencilStroke(canvas, tempStroke)
            BrushStyle.HIGHLIGHTER -> drawHighlighterStroke(canvas, tempStroke)
            BrushStyle.MARKER -> drawMarkerStroke(canvas, tempStroke)
            else -> drawDefaultStroke(canvas, tempStroke)
        }
    }

    private fun drawPencilStroke(canvas: Canvas, tempStroke: StrokeData) {
        val paint = BrushRenderUtils.makeStrokePaint(tempStroke, view.width, view.height).apply {
            pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
            alpha = 180
        }
        canvas.drawPath(tempStroke.path!!, paint)
    }

    private fun drawHighlighterStroke(canvas: Canvas, tempStroke: StrokeData) {
        val paint = BrushRenderUtils.makeStrokePaint(tempStroke, view.width, view.height).apply {
            alpha = 130
            strokeCap = Paint.Cap.BUTT
        }
        canvas.drawPath(tempStroke.path!!, paint)
    }

    private fun drawMarkerStroke(canvas: Canvas, tempStroke: StrokeData) {
        val paint = BrushRenderUtils.makeStrokePaint(tempStroke, view.width, view.height).apply {
            alpha = 240
            strokeCap = Paint.Cap.BUTT
        }
        canvas.drawPath(tempStroke.path!!, paint)
    }

    private fun drawDefaultStroke(canvas: Canvas, tempStroke: StrokeData) {
        val paint = BrushRenderUtils.makeStrokePaint(tempStroke, view.width, view.height)
        canvas.drawPath(tempStroke.path!!, paint)
    }

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
        direction: FeatherDirection = FeatherDirection.ALL,
    ) {
        if (featherRadius <= 0f) return
        val w = right - left
        val h = bottom - top
        if (w <= 0f || h <= 0f) return

        val maskW = MASK_SIZE
        val maskH = MASK_SIZE

        val featherFp = listOf(elementId, featherRadius, featherWidth, direction).hashCode()
        val cached = view.cacheManager.featherBitmapCache[elementId]
        val maskBmp: Bitmap = if (cached != null && cached.fingerprint == featherFp && !cached.bitmap.isRecycled) {
            cached.bitmap
        } else {
            cached?.bitmap?.recycle()

            val fraction = sqrt((featherRadius / 100.0)).toFloat().coerceIn(0f, 1f)
            val bandX = (maskW / 2f) * fraction
            val bandY = (maskH / 2f) * fraction
            val exponent = 1.0 + ((100f - featherWidth) / 100.0) * 7.0

            val doTop = direction == FeatherDirection.ALL || direction == FeatherDirection.TOP
            val doBottom = direction == FeatherDirection.ALL || direction == FeatherDirection.BOTTOM
            val doLeft = direction == FeatherDirection.ALL || direction == FeatherDirection.LEFT
            val doRight = direction == FeatherDirection.ALL || direction == FeatherDirection.RIGHT

            val pixels = IntArray(maskW * maskH)
            for (py in 0 until maskH) {
                val topRamp = if (doTop && bandY > 0f) {
                    smoothStep((py / bandY).coerceIn(0f, 1f), exponent)
                } else { 1f }
                val botRamp = if (doBottom && bandY > 0f) {
                    smoothStep(((maskH - 1 - py) / bandY).coerceIn(0f, 1f), exponent)
                } else { 1f }
                val vRamp = topRamp * botRamp

                for (px in 0 until maskW) {
                    val leftRamp = if (doLeft && bandX > 0f) {
                        smoothStep((px / bandX).coerceIn(0f, 1f), exponent)
                    } else { 1f }
                    val rightRamp = if (doRight && bandX > 0f) {
                        smoothStep(((maskW - 1 - px) / bandX).coerceIn(0f, 1f), exponent)
                    } else { 1f }
                    val alpha = (vRamp * leftRamp * rightRamp * 255f).toInt().coerceIn(0, 255)
                    pixels[py * maskW + px] = Color.argb(alpha, 0, 0, 0)
                }
            }

            val newBmp = Bitmap.createBitmap(maskW, maskH, Bitmap.Config.ARGB_8888)
            newBmp.setPixels(pixels, 0, maskW, 0, 0, maskW, maskH)
            FeatherCacheEntry(newBmp, featherFp).also { view.cacheManager.featherBitmapCache[elementId] = it }.bitmap
        }

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(maskBmp, null, RectF(left, top, right, bottom), maskPaint)
        maskPaint.xfermode = null
    }

    private fun smoothStep(t: Float, exponent: Double): Float {
        val smooth = t * t * (3f - 2f * t)
        return Math.pow(smooth.toDouble(), exponent).toFloat().coerceIn(0f, 1f)
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

        val cxRel = width * gradientItem.centerX
        val cyRel = height * gradientItem.centerY

        var rawShader: Shader? = null
        var localMatrix: Matrix? = null

        when (gradientItem.type) {
            GradientType.LINEAR -> {
                val theta = Math.toRadians(gradientItem.angle.toDouble())
                val halfLen = (hypot(width.toDouble(), height.toDouble()).toFloat() * gradientItem.scale / 2f)
                val dx = (cos(theta) * halfLen).toFloat()
                val dy = (sin(theta) * halfLen).toFloat()

                rawShader = LinearGradient(-dx, -dy, dx, dy, colors, positions, Shader.TileMode.CLAMP)
            }
            GradientType.RADIAL -> {
                val radius = min(width, height) / 2f * gradientItem.radialRadiusFactor * gradientItem.scale
                rawShader = RadialGradient(0f, 0f, radius, colors, positions, Shader.TileMode.CLAMP)
            }
            GradientType.SWEEP -> {
                rawShader = SweepGradient(0f, 0f, colors, positions)
                localMatrix = Matrix().apply { postRotate(gradientItem.sweepStartAngle) }
            }
        }

        val finalMatrix = Matrix().apply {
            localMatrix?.let { set(it) }
            postTranslate(cxRel + translateX, cyRel + translateY)
        }

        rawShader?.setLocalMatrix(finalMatrix)
        return rawShader!!
    }

    internal fun drawWithBlend(element: CanvasElement): Xfermode? = when (element.blendType) {
        BlendType.SRC -> PorterDuffXfermode(PorterDuff.Mode.SRC)
        BlendType.NORMAL -> null
        BlendType.DARKEN -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
        BlendType.LIGHTEN -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
        BlendType.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        BlendType.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    } as? Xfermode

    private fun isRTL(text: String): Boolean =
        text.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.ARABIC }

    internal fun justifyText(canvas: Canvas, text: String, yOffset: Float, element: CanvasElement) {
        if (element.paintAlpha == 0) return

        val words = text.split(" ")
        if (words.size <= 1) {
            val x = -element.getLocalContentWidth() / 2f
            element.paint.alpha = element.paintAlpha
            canvas.drawText(text, x, yOffset, element.paint)
            return
        }

        val (fillPaint, strokePaint) = prepareJustifyPaints(element)
        drawJustifiedWords(canvas, words, yOffset, element, fillPaint, strokePaint)
    }

    private fun prepareJustifyPaints(element: CanvasElement): Pair<TextPaint, TextPaint?> {
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
        return fillPaint to strokePaint
    }

    private fun drawJustifiedWords(
        canvas: Canvas,
        words: List<String>,
        yOffset: Float,
        element: CanvasElement,
        fillPaint: TextPaint,
        strokePaint: TextPaint?
    ) {
        val isRTL = isRTL(words.joinToString(" "))
        val wordWidths = words.map { fillPaint.measureText(it) }
        val textWidth = wordWidths.sum()
        val totalWidth = element.getLocalContentWidth()
        val space = (totalWidth - textWidth) / (words.size - 1)

        var xOffset = if (isRTL) totalWidth / 2f else -totalWidth / 2f

        words.forEachIndexed { index, word ->
            val w = wordWidths[index]
            if (isRTL) xOffset -= w

            strokePaint?.let {
                val old = it.alpha
                it.alpha = element.paintAlpha
                canvas.drawText(word, xOffset, yOffset, it)
                it.alpha = old
            }

            canvas.drawText(word, xOffset, yOffset, fillPaint)

            if (!isRTL) xOffset += w
            xOffset += if (index < words.size - 1) (if (isRTL) -space else space) else 0f
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val gridSpacing = GRID_SPACING_DP

        val (left, top) = view.canvasToView(0f, 0f)
        val (right, bottom) = view.canvasToView(view.canvasWidth.toFloat(), view.canvasHeight.toFloat())

        val stepPx = gridSpacing * view.scale * view.overallScale
        if (stepPx < GRID_MIN_STEP_PX) return

        gridPaint.strokeWidth = (1f / view.overallScale).coerceIn(GRID_STROKE_CLAMP_MIN, GRID_STROKE_CLAMP_MAX)

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

    private fun drawRuler(canvas: Canvas) {
        val rulerThicknessPx = 16f.dpToPx()
        val majorTickLen = rulerThicknessPx * 0.6f
        val minorTickLen = rulerThicknessPx * 0.3f

        val (canvasLeft, canvasTop) = view.canvasToView(0f, 0f)
        val (canvasRight, canvasBottom) = view.canvasToView(view.canvasWidth.toFloat(), view.canvasHeight.toFloat())

        val rawSpacing = view.canvasWidth / 8f
        val tickSpacing = niceNumber(rawSpacing)

        val stepViewPx = tickSpacing * view.scale * view.overallScale
        if (stepViewPx < 4f) return

        drawRulerBackgrounds(canvas, canvasLeft, canvasTop, canvasRight, canvasBottom, rulerThicknessPx)
        drawTopRulerTicks(
            canvas, canvasLeft, canvasTop, canvasRight,
            rulerThicknessPx, stepViewPx, tickSpacing, majorTickLen, minorTickLen
        )
        drawLeftRulerTicks(
            canvas, canvasLeft, canvasTop, canvasBottom,
            rulerThicknessPx, stepViewPx, tickSpacing, majorTickLen, minorTickLen
        )
    }

    private fun drawRulerBackgrounds(
        canvas: Canvas,
        canvasLeft: Float,
        canvasTop: Float,
        canvasRight: Float,
        canvasBottom: Float,
        rulerThicknessPx: Float
    ) {
        canvas.drawRect(canvasLeft, canvasTop, canvasRight, canvasTop + rulerThicknessPx, rulerBgPaint)
        canvas.drawLine(
            canvasLeft, canvasTop + rulerThicknessPx,
            canvasRight, canvasTop + rulerThicknessPx,
            rulerPaint
        )

        canvas.drawRect(canvasLeft, canvasTop, canvasLeft + rulerThicknessPx, canvasBottom, rulerBgPaint)
        canvas.drawLine(
            canvasLeft + rulerThicknessPx, canvasTop,
            canvasLeft + rulerThicknessPx, canvasBottom,
            rulerPaint
        )

        canvas.drawRect(
            canvasLeft, canvasTop,
            canvasLeft + rulerThicknessPx, canvasTop + rulerThicknessPx,
            rulerBgPaint
        )
    }

    @Suppress("LongParameterList")
    private fun drawTopRulerTicks(
        canvas: Canvas,
        canvasLeft: Float,
        canvasTop: Float,
        canvasRight: Float,
        rulerThicknessPx: Float,
        stepViewPx: Float,
        tickSpacing: Float,
        majorTickLen: Float,
        minorTickLen: Float
    ) {
        var tickIndex = 0
        var x = canvasLeft
        while (x <= canvasRight + 0.5f) {
            val isMajor = tickIndex % 5 == 0
            val tickLen = if (isMajor) majorTickLen else minorTickLen
            val tickTop = canvasTop + rulerThicknessPx - tickLen

            canvas.drawLine(x, tickTop, x, canvasTop + rulerThicknessPx, rulerPaint)

            if (isMajor) {
                val labelY = canvasTop + rulerThicknessPx - majorTickLen - 2f
                canvas.drawText("${(tickIndex * tickSpacing).toInt()}", x, labelY, rulerTextPaint)
            }
            x += stepViewPx
            tickIndex++
        }
    }

    @Suppress("LongParameterList")
    private fun drawLeftRulerTicks(
        canvas: Canvas,
        canvasLeft: Float,
        canvasTop: Float,
        canvasBottom: Float,
        rulerThicknessPx: Float,
        stepViewPx: Float,
        tickSpacing: Float,
        majorTickLen: Float,
        minorTickLen: Float
    ) {
        var tickIndex = 0
        var y = canvasTop
        while (y <= canvasBottom + 0.5f) {
            val isMajor = tickIndex % 5 == 0
            val tickLen = if (isMajor) majorTickLen else minorTickLen
            val tickLeft = canvasLeft + rulerThicknessPx - tickLen

            canvas.drawLine(tickLeft, y, canvasLeft + rulerThicknessPx, y, rulerPaint)

            if (isMajor && tickIndex > 0) {
                canvas.withSave {
                    val labelX = canvasLeft + rulerThicknessPx - majorTickLen - 2f
                    val labelText = "${(tickIndex * tickSpacing).toInt()}"
                    rotate(RULER_ROTATION_DEGREES, labelX, y)
                    canvas.drawText(labelText, labelX, y + rulerTextPaint.textSize / 3f, rulerTextPaint)
                }
            }
            y += stepViewPx
            tickIndex++
        }
    }

    private fun niceNumber(raw: Float): Float {
        val candidates = listOf(10f, 20f, 25f, 50f, 100f, 200f, 250f, 500f, 1000f, 2000f)
        return candidates.minByOrNull { abs(it - raw) } ?: RULER_DEFAULT_TICK
    }
}
