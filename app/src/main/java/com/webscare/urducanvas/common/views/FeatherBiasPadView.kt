package com.webscare.urducanvas.common.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import com.webscare.urducanvas.R
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Bias(val x: Float, val y: Float)

class FeatherBiasPadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { BIAS, OFFSET }

    var mode: Mode = Mode.BIAS
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var maxDistance: Float = 24f

    var bias: Bias = Bias(0f, 0f)
        set(value) {
            val clampedX = value.x.coerceIn(-1f, 1f)
            val clampedY = value.y.coerceIn(-1f, 1f)
            val newBias = Bias(clampedX, clampedY)
            if (field != newBias) {
                field = newBias
                invalidate()
                updateAccessibility()
            }
        }

    var handleColor: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    var onBiasChanged: ((Bias) -> Unit)? = null
    var onOffsetChanged: ((angle: Float, distance: Float) -> Unit)? = null
    var onDragStateChanged: ((isDragging: Boolean) -> Unit)? = null

    private var isDragging = false
    private var lastTouchTime = 0L
    private var lastSnappedAngle: Float? = null

    // Paints
    private val padWellBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#F3F3F3".toColorInt()
    }

    private val padBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = "#E3E9E5".toColorInt()
        strokeWidth = dpToPx(1f)
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = "#E7E7E7".toColorInt()
        strokeWidth = dpToPx(1f)
    }

    private val dashedRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = "#DDDDDD".toColorInt()
        strokeWidth = dpToPx(1f)
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(4f), dpToPx(4f)), 0f)
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#DCDCDC".toColorInt()
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#AFBBB4".toColorInt()
    }

    private val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = "#CFE1D6".toColorInt()
        strokeWidth = dpToPx(2f)
    }

    private val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = "#9CC4AB".toColorInt()
        strokeWidth = dpToPx(2f)
    }

    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#005D28".toColorInt()
    }

    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = dpToPx(2f)
    }

    private val titleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = spToPx(11f)
        color = "#005D28".toColorInt()
        try {
            typeface = ResourcesCompat.getFont(context, R.font.bold) ?: Typeface.DEFAULT_BOLD
        } catch (e: Exception) {
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private val hintTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = spToPx(10f)
        color = "#8E94A2".toColorInt()
        try {
            typeface = ResourcesCompat.getFont(context, R.font.regular) ?: Typeface.DEFAULT
        } catch (e: Exception) {
            typeface = Typeface.DEFAULT
        }
    }

    private val padRect = RectF()
    private val tickRect = RectF()

    init {
        isFocusable = true
        isClickable = true
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        updateAccessibility()
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
    private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1.0f else 0.42f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val padSize = dpToPx(88f).toInt()
        val defaultWidth = if (mode == Mode.OFFSET) padSize else dpToPx(300f).toInt()
        val w = resolveSize(defaultWidth, widthMeasureSpec)
        val h = resolveSize(padSize, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val padSize = 88f * density
        val cx = padSize / 2f
        val cy = padSize / 2f

        val cornerRadius = 18f * density
        val ringRadius = 26f * density // 52dp diameter guide ring
        val handleRadius = 13f * density
        // Max travel allows handle to go farther till pad ends (leaving 3dp margin inside pad well)
        val maxTravelPx = cx - handleRadius - dpToPx(2.5f)

        val crosshairInset = 12f * density // 12dp inset from edge

        padRect.set(0f, 0f, padSize, padSize)

        // 1. Draw Pad Background (#F3F3F3 well) & Border (#E3E9E5)
        canvas.drawRoundRect(padRect, cornerRadius, cornerRadius, padWellBgPaint)
        canvas.drawRoundRect(padRect, cornerRadius, cornerRadius, padBorderPaint)

        // 2. Crosshair (1dp #E7E7E7 lines, inset 12dp from edge)
        canvas.drawLine(crosshairInset, cy, padSize - crosshairInset, cy, crosshairPaint)
        canvas.drawLine(cx, crosshairInset, cx, padSize - crosshairInset, crosshairPaint)

        // 3. Dashed Guide Ring (52dp diameter = 26dp radius)
        canvas.drawCircle(cx, cy, ringRadius, dashedRingPaint)

        // 4. Edge Ticks (2x8dp ticks)
        val tickLength = 8f * density
        val tickThickness = 2f * density

        // Top tick
        tickRect.set(cx - tickThickness / 2f, crosshairInset / 2f - tickLength / 2f, cx + tickThickness / 2f, crosshairInset / 2f + tickLength / 2f)
        canvas.drawRoundRect(tickRect, 1f * density, 1f * density, tickPaint)
        // Bottom tick
        tickRect.set(cx - tickThickness / 2f, padSize - crosshairInset / 2f - tickLength / 2f, cx + tickThickness / 2f, padSize - crosshairInset / 2f + tickLength / 2f)
        canvas.drawRoundRect(tickRect, 1f * density, 1f * density, tickPaint)
        // Left tick
        tickRect.set(crosshairInset / 2f - tickLength / 2f, cy - tickThickness / 2f, crosshairInset / 2f + tickLength / 2f, cy + tickThickness / 2f)
        canvas.drawRoundRect(tickRect, 1f * density, 1f * density, tickPaint)
        // Right tick
        tickRect.set(padSize - crosshairInset / 2f - tickLength / 2f, cy - tickThickness / 2f, padSize - crosshairInset / 2f + tickLength / 2f, cy + tickThickness / 2f)
        canvas.drawRoundRect(tickRect, 1f * density, 1f * density, tickPaint)

        // 5. Centre Dot
        canvas.drawCircle(cx, cy, 2.5f * density, centerDotPaint)

        // Calculate Handle Position
        val puckX = cx + (bias.x * maxTravelPx)
        val puckY = cy + (bias.y * maxTravelPx)

        // 6. Arm / Stem
        if (bias.x != 0f || bias.y != 0f) {
            val linePaint = if (mode == Mode.OFFSET) armPaint else stemPaint
            canvas.drawLine(cx, cy, puckX, puckY, linePaint)
        }

        // 7. Handle (26dp painted diameter = 13dp radius)
        val appColor = androidx.core.content.ContextCompat.getColor(context, R.color.appColor)
        val fillCol = when {
            mode == Mode.OFFSET && handleColor != null && handleColor != Color.TRANSPARENT -> handleColor!!
            mode == Mode.BIAS && isEnabled -> appColor
            else -> handleColor ?: appColor
        }
        handleFillPaint.color = fillCol

        canvas.drawCircle(puckX, puckY, handleRadius, handleFillPaint)
        canvas.drawCircle(puckX, puckY, handleRadius, handleBorderPaint)

        // 8. Text Block beside pad (Only drawn if view width is wide enough in BIAS mode)
        if (width > padSize + 20f * density) {
            val textLeft = padSize + 12f * density
            val availableWidth = (width - textLeft).coerceAtLeast(100f)

            val titleText = getBiasTitleText(bias)
            val titleY = 22f * density
            canvas.drawText(titleText, textLeft, titleY, titleTextPaint)

            val hintText = "Drag handle to choose\nwhich edges soft-fade.\nDouble-tap to reset."
            val staticLayout = StaticLayout.Builder.obtain(hintText, 0, hintText.length, hintTextPaint, availableWidth.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f)
                .setIncludePad(false)
                .build()

            canvas.save()
            canvas.translate(textLeft, 36f * density)
            staticLayout.draw(canvas)
            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        val density = resources.displayMetrics.density
        val padSize = 88f * density
        val cx = padSize / 2f
        val cy = padSize / 2f
        val handleRadius = 13f * density
        val maxTravelPx = cx - handleRadius - dpToPx(2.5f)
        val snapZonePx = 0.12f * maxTravelPx

        val touchX = event.x.coerceIn(0f, padSize)
        val touchY = event.y.coerceIn(0f, padSize)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                onDragStateChanged?.invoke(true)

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTouchTime < 300L && event.x <= padSize && event.y <= padSize) {
                    updateBias(Bias(0f, 0f))
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    lastTouchTime = 0L
                    return true
                }
                lastTouchTime = currentTime

                if (event.x <= padSize && event.y <= padSize) {
                    isDragging = true
                    processTouchPad(touchX, touchY, cx, cy, snapZonePx, maxTravelPx)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    processTouchPad(touchX, touchY, cx, cy, snapZonePx, maxTravelPx)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                onDragStateChanged?.invoke(false)

                if (isDragging) {
                    isDragging = false
                    processTouchPad(touchX, touchY, cx, cy, snapZonePx, maxTravelPx)
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun processTouchPad(
        tx: Float, ty: Float,
        cx: Float, cy: Float,
        snapZonePx: Float, maxTravelPx: Float
    ) {
        val dx = tx - cx
        val dy = ty - cy
        val dist = sqrt(dx * dx + dy * dy)

        if (mode == Mode.OFFSET) {
            // Shadow mode: pad controls ONLY angle, handle stays at outer boundary ring
            var rawAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (rawAngle < 0) rawAngle += 360f

            val snappedAngle = snapAngleIfNeeded(rawAngle)
            if (snappedAngle != rawAngle && snappedAngle != lastSnappedAngle) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                lastSnappedAngle = snappedAngle
            } else if (snappedAngle == rawAngle) {
                lastSnappedAngle = null
            }

            val rad = Math.toRadians(snappedAngle.toDouble())
            val bx = cos(rad).toFloat()
            val by = sin(rad).toFloat()
            updateBias(Bias(bx, by))
        } else {
            // Feather mode: bias pad control
            if (dist <= snapZonePx) {
                updateBias(Bias(0f, 0f))
            } else {
                val scale = if (dist > maxTravelPx) maxTravelPx / dist else 1f
                val clampedDx = dx * scale
                val clampedDy = dy * scale

                val rawBx = (clampedDx / maxTravelPx).coerceIn(-1f, 1f)
                val rawBy = (clampedDy / maxTravelPx).coerceIn(-1f, 1f)

                updateBias(Bias(rawBx, rawBy))
            }
        }
    }

    private fun snapAngleIfNeeded(rawAngle: Float): Float {
        val snapTargets = floatArrayOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f, 360f)
        val threshold = 6.0f
        for (target in snapTargets) {
            if (abs(rawAngle - target) <= threshold) {
                return if (target == 360f) 0f else target
            }
        }
        return rawAngle
    }

    private fun updateBias(newBias: Bias) {
        if (bias != newBias) {
            bias = newBias
            onBiasChanged?.invoke(bias)

            if (mode == Mode.OFFSET) {
                val (angle, distance) = computeAngleAndDistance()
                onOffsetChanged?.invoke(angle, distance)
            }

            invalidate()
            updateAccessibility()
        }
    }

    fun setOffset(angleDeg: Float, distanceDp: Float) {
        val snappedAngle = snapAngleIfNeeded(angleDeg)
        val rad = Math.toRadians(snappedAngle.toDouble())
        val bx = cos(rad).toFloat().coerceIn(-1f, 1f)
        val by = sin(rad).toFloat().coerceIn(-1f, 1f)
        bias = Bias(bx, by)
    }

    fun computeAngleAndDistance(): Pair<Float, Float> {
        val bx = bias.x
        val by = bias.y
        val rad = atan2(by.toDouble(), bx.toDouble())
        var angleDeg = Math.toDegrees(rad).toFloat()
        if (angleDeg < 0) angleDeg += 360f

        return Pair(angleDeg, maxDistance)
    }

    fun getDirectionTitle(): String {
        val (angle, _) = computeAngleAndDistance()

        return when {
            angle >= 337.5f || angle < 22.5f -> "Right"
            angle in 22.5f..67.5f -> "Down-right"
            angle in 67.5f..112.5f -> "Down"
            angle in 112.5f..157.5f -> "Down-left"
            angle in 157.5f..202.5f -> "Left"
            angle in 202.5f..247.5f -> "Up-left"
            angle in 247.5f..292.5f -> "Up"
            angle in 292.5f..337.5f -> "Up-right"
            else -> "Right"
        }
    }

    fun getBiasTitleText(b: Bias): String {
        val x = b.x
        val y = b.y
        val r = sqrt(x * x + y * y)
        if (r < 0.05f || (x == 0f && y == 0f)) {
            return "Even on all sides"
        }
        val angleRad = atan2(-y.toDouble(), x.toDouble())
        val angleDeg = Math.toDegrees(angleRad).toFloat()

        return when {
            angleDeg in 67.5f..112.5f -> "Biased to top"
            angleDeg in 22.5f..67.5f -> "Biased to top-right"
            angleDeg in -22.5f..22.5f -> "Biased to right"
            angleDeg in -67.5f..-22.5f -> "Biased to bottom-right"
            angleDeg in -112.5f..-67.5f -> "Biased to bottom"
            angleDeg in -157.5f..-112.5f -> "Biased to bottom-left"
            angleDeg in 112.5f..157.5f -> "Biased to top-left"
            else -> "Biased to left"
        }
    }

    private fun updateAccessibility() {
        contentDescription = if (mode == Mode.OFFSET) {
            val (angle, _) = computeAngleAndDistance()
            "Shadow angle pad: ${getDirectionTitle()}, angle ${angle.roundToInt()} degrees"
        } else {
            "Feather direction pad: ${getBiasTitleText(bias)}"
        }
    }
}
