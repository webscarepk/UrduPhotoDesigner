package com.webscare.urducanvas.common.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.webscare.urducanvas.R
import kotlin.math.atan2
import kotlin.math.sqrt

data class Bias(val x: Float, val y: Float)

class FeatherBiasPadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

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

    var onBiasChanged: ((Bias) -> Unit)? = null

    private var isDragging = false
    private var lastTouchTime = 0L

    // Paints
    private val padBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#E3E9E5")
        strokeWidth = dpToPx(1f)
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#DCE3DE")
        strokeWidth = dpToPx(1f)
    }

    private val dashedRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#C4CFC8")
        strokeWidth = dpToPx(1f)
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(4f), dpToPx(4f)), 0f)
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#C4CFC8")
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#AFBBB4")
    }

    private val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#9CC4AB")
        strokeWidth = dpToPx(2f)
    }

    private val puckFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#005D28")
        setShadowLayer(dpToPx(4f), 0f, dpToPx(2f), Color.parseColor("#40000000"))
    }

    private val puckBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = dpToPx(2f)
    }

    private val titleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = spToPx(11f)
        color = Color.parseColor("#005D28")
        try {
            typeface = ResourcesCompat.getFont(context, R.font.bold) ?: Typeface.DEFAULT_BOLD
        } catch (e: Exception) {
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private val hintTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = spToPx(10f)
        color = Color.parseColor("#8E94A2")
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
        val desiredHeight = dpToPx(96f).toInt()
        val defaultWidth = dpToPx(300f).toInt()
        val w = resolveSize(defaultWidth, widthMeasureSpec)
        val h = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val padSize = 96f * density
        val cx = padSize / 2f
        val cy = padSize / 2f
        val deadzoneRadius = 22f * density // 44dp diameter
        val maxPuckOffset = cx - 11f * density // 37dp max radius to keep puck inside pad

        padRect.set(0f, 0f, padSize, padSize)

        // 1. Draw Pad Background (Radial Gradient)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                cx, cy, padSize / 2f,
                intArrayOf(
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#F2F5F3"),
                    Color.parseColor("#E8EDEA")
                ),
                floatArrayOf(0.0f, 0.7f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(padRect, 12f * density, 12f * density, bgPaint)
        canvas.drawRoundRect(padRect, 12f * density, 12f * density, padBorderPaint)

        // 2. Crosshair (1dp lines)
        canvas.drawLine(0f, cy, padSize, cy, crosshairPaint)
        canvas.drawLine(cx, 0f, cx, padSize, crosshairPaint)

        // 3. Dashed Ring (44dp diameter = 22dp radius)
        canvas.drawCircle(cx, cy, deadzoneRadius, dashedRingPaint)

        // 4. Edge Ticks (12 x 3dp, 5dp in from edge)
        // Top tick
        tickRect.set(cx - 6f * density, 5f * density, cx + 6f * density, 8f * density)
        canvas.drawRoundRect(tickRect, 1.5f * density, 1.5f * density, tickPaint)
        // Bottom tick
        tickRect.set(cx - 6f * density, padSize - 8f * density, cx + 6f * density, padSize - 5f * density)
        canvas.drawRoundRect(tickRect, 1.5f * density, 1.5f * density, tickPaint)
        // Left tick
        tickRect.set(5f * density, cy - 6f * density, 8f * density, cy + 6f * density)
        canvas.drawRoundRect(tickRect, 1.5f * density, 1.5f * density, tickPaint)
        // Right tick
        tickRect.set(padSize - 8f * density, cy - 6f * density, padSize - 5f * density, cy + 6f * density)
        canvas.drawRoundRect(tickRect, 1.5f * density, 1.5f * density, tickPaint)

        // 5. Centre Dot (5dp diameter = 2.5dp radius)
        canvas.drawCircle(cx, cy, 2.5f * density, centerDotPaint)

        // Calculate Puck Center (x, y mapped from bias)
        val puckX = cx + (bias.x * maxPuckOffset)
        val puckY = cy + (bias.y * maxPuckOffset)

        // 6. Stem (Line from center to puck)
        if (bias.x != 0f || bias.y != 0f) {
            canvas.drawLine(cx, cy, puckX, puckY, stemPaint)
        }

        // 7. Puck (22dp circle resting, 26dp dragging, 2dp white border)
        val puckRadius = if (isDragging) 13f * density else 11f * density
        canvas.drawCircle(puckX, puckY, puckRadius, puckFillPaint)
        canvas.drawCircle(puckX, puckY, puckRadius, puckBorderPaint)

        // 8. Text Block (Right of pad, 12dp gap)
        val textLeft = padSize + 12f * density
        val availableWidth = (width - textLeft).coerceAtLeast(100f)

        // Line 1: Bias Title
        val titleText = getBiasTitleText(bias)
        val titleY = 22f * density
        canvas.drawText(titleText, textLeft, titleY, titleTextPaint)

        // Line 2: Friendly Hint Text
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        val density = resources.displayMetrics.density
        val padSize = 96f * density
        val cx = padSize / 2f
        val cy = padSize / 2f
        val deadzoneRadius = 22f * density
        val maxPuckOffset = cx - 11f * density

        val touchX = event.x
        val touchY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTouchTime < 300L && touchX <= padSize && touchY <= padSize) {
                    // Double-tap to recentre
                    updateBias(Bias(0f, 0f))
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    lastTouchTime = 0L
                    return true
                }
                lastTouchTime = currentTime

                if (touchX <= padSize && touchY <= padSize) {
                    isDragging = true
                    processTouchPad(touchX, touchY, cx, cy, deadzoneRadius, maxPuckOffset)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    processTouchPad(touchX, touchY, cx, cy, deadzoneRadius, maxPuckOffset)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    processTouchPad(touchX, touchY, cx, cy, deadzoneRadius, maxPuckOffset)
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private var isSnappedAxis = false

    private fun processTouchPad(
        tx: Float, ty: Float,
        cx: Float, cy: Float,
        deadzoneRadius: Float, maxPuckOffset: Float
    ) {
        val dx = tx - cx
        val dy = ty - cy
        val dist = sqrt(dx * dx + dy * dy)

        val isWasDeadzone = isInsideDeadzone(bias)

        if (dist <= deadzoneRadius) {
            // Inside deadzone -> snap to (0,0)
            if (!isWasDeadzone) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            isSnappedAxis = false
            updateBias(Bias(0f, 0f))
        } else {
            // Outside deadzone -> calculate clamped circular bias (-1..1)
            if (isWasDeadzone) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            val scale = if (dist > maxPuckOffset) maxPuckOffset / dist else 1f
            val clampedDx = dx * scale
            val clampedDy = dy * scale

            var rawBx = (clampedDx / maxPuckOffset).coerceIn(-1f, 1f)
            var rawBy = (clampedDy / maxPuckOffset).coerceIn(-1f, 1f)

            val magnitude = sqrt(rawBx * rawBx + rawBy * rawBy).coerceIn(0f, 1f)

            // Calculate angle in degrees: -dy is Up (90°), +dy is Down (-90°), +dx is Right (0°), -dx is Left (180°/-180°)
            val angleRad = atan2(-rawBy, rawBx)
            val angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()

            // 5-degree angular snapping around cardinal axes
            val isNearTop = kotlin.math.abs(angleDeg - 90f) <= 5f
            val isNearBottom = kotlin.math.abs(angleDeg - (-90f)) <= 5f
            val isNearRight = kotlin.math.abs(angleDeg) <= 5f
            val isNearLeft = kotlin.math.abs(angleDeg - 180f) <= 5f || kotlin.math.abs(angleDeg - (-180f)) <= 5f

            var nowSnapped = false

            if (isNearTop) {
                rawBx = 0f
                rawBy = -magnitude
                nowSnapped = true
            } else if (isNearBottom) {
                rawBx = 0f
                rawBy = magnitude
                nowSnapped = true
            } else if (isNearRight) {
                rawBx = magnitude
                rawBy = 0f
                nowSnapped = true
            } else if (isNearLeft) {
                rawBx = -magnitude
                rawBy = 0f
                nowSnapped = true
            }

            if (nowSnapped && !isSnappedAxis) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            isSnappedAxis = nowSnapped

            updateBias(Bias(rawBx, rawBy))
        }
    }

    private fun isInsideDeadzone(b: Bias): Boolean {
        return b.x == 0f && b.y == 0f
    }

    private fun updateBias(newBias: Bias) {
        if (bias != newBias) {
            bias = newBias
            onBiasChanged?.invoke(bias)
            invalidate()
            updateAccessibility()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!isEnabled) return super.onKeyDown(keyCode, event)
        val step = 0.05f
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                updateBias(Bias(bias.x, (bias.y - step).coerceIn(-1f, 1f)))
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                updateBias(Bias(bias.x, (bias.y + step).coerceIn(-1f, 1f)))
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                updateBias(Bias((bias.x - step).coerceIn(-1f, 1f), bias.y))
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                updateBias(Bias((bias.x + step).coerceIn(-1f, 1f), bias.y))
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    fun getBiasTitleText(b: Bias): String {
        val x = b.x
        val y = b.y
        val r = sqrt(x * x + y * y)
        if (r < 0.05f || (x == 0f && y == 0f)) {
            return "Even on all sides"
        }
        val angleRad = atan2(-y, x)
        val angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()

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
        contentDescription = "Feather direction pad: ${getBiasTitleText(bias)}"
    }
}
