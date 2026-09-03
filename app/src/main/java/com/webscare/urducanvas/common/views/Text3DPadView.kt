package com.webscare.urducanvas.common.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.toColorInt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class Text3DPadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { ANGLE, SNAP_9 }

    var mode: Mode = Mode.ANGLE
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var onDragStateChanged: ((isDragging: Boolean) -> Unit)? = null
    var onAngleChanged: ((angle: Float) -> Unit)? = null
    var onDirectionChanged: ((direction: String) -> Unit)? = null

    // Angle mode state: 0..359° (0° up)
    var angle: Float = 0f
        set(value) {
            val normalized = (value % 360f + 360f) % 360f
            if (field != normalized) {
                field = normalized
                invalidate()
            }
        }

    // Snap mode state: direction name
    var direction: String = "bottom-right"
        set(value) {
            if (field != value) {
                field = value
                animateHandleToTarget(getGridCoordForDir(value), animate = isAttachedToWindow)
                invalidate()
            }
        }

    private var isDragging = false

    // Snap animation coordinates (0f..2f grid space)
    private var currentGridX: Float = 2f
    private var currentGridY: Float = 2f
    private var targetGridX: Float = 2f
    private var targetGridY: Float = 2f
    private var snapAnimator: ValueAnimator? = null

    // Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#F3F3F3".toColorInt()
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = "#D8D8D8".toColorInt()
        strokeWidth = dp(1f)
        pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(3f)), 0f)
    }

    private val dashedCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = "#D8D8D8".toColorInt()
        strokeWidth = dp(1f)
        pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(3f)), 0f)
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#B4B9C2".toColorInt()
    }

    // Angle mode paints
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = "#C9CDD2".toColorInt()
        strokeWidth = dp(1f)
    }

    private val angleHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#9A9A9A".toColorInt()
    }

    // Snap mode paints
    private val gridDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#D6DAE0".toColorInt()
    }

    private val snapHandleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val snapHandleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = "#005D28".toColorInt()
        strokeWidth = dp(2f)
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#20000000".toColorInt()
    }

    private val bgRect = RectF()
    private val crosshairPath = Path()

    init {
        val initialCoord = getGridCoordForDir(direction)
        currentGridX = initialCoord.first.toFloat()
        currentGridY = initialCoord.second.toFloat()
        targetGridX = currentGridX
        targetGridY = currentGridY
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val defaultSize = dp(84f).toInt()
        val width = resolveSize(defaultSize, widthMeasureSpec)
        val height = resolveSize(defaultSize, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val cornerRadius = dp(18f)

        // 1. Draw rounded background (#F3F3F3)
        bgRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

        // 2. Draw dashed crosshair through centre
        crosshairPath.reset()
        crosshairPath.moveTo(cx, dp(6f))
        crosshairPath.lineTo(cx, h - dp(6f))
        crosshairPath.moveTo(dp(6f), cy)
        crosshairPath.lineTo(w - dp(6f), cy)
        canvas.drawPath(crosshairPath, crosshairPaint)

        // 3. Draw dashed circle (56dp diameter => 28dp radius)
        val circleRadius = dp(28f)
        canvas.drawCircle(cx, cy, circleRadius, dashedCirclePaint)

        // 4. Centre dot (5dp diameter => 2.5dp radius, #B4B9C2)
        canvas.drawCircle(cx, cy, dp(2.5f), centerDotPaint)

        when (mode) {
            Mode.ANGLE -> drawAngleMode(canvas, cx, cy)
            Mode.SNAP_9 -> drawSnapMode(canvas, w, h)
        }
    }

    private fun drawAngleMode(canvas: Canvas, cx: Float, cy: Float) {
        val orbitRadius = dp(24f)
        // 0° is up (-90° in standard Cartesian coordinates)
        val rad = Math.toRadians((angle - 90.0)).toFloat()
        val hx = cx + cos(rad) * orbitRadius
        val hy = cy + sin(rad) * orbitRadius

        // 1dp ray behind handle from centre to handle
        canvas.drawLine(cx, cy, hx, hy, rayPaint)

        // 26dp grey circle handle with soft inner shadow
        val handleRadius = dp(13f)
        canvas.drawCircle(hx, hy + dp(1f), handleRadius, shadowPaint)
        canvas.drawCircle(hx, hy, handleRadius, angleHandlePaint)
    }

    private fun drawSnapMode(canvas: Canvas, w: Float, h: Float) {
        // Grid points at 14dp, 42dp, 70dp relative to 84dp (1/6, 3/6, 5/6 of dimension)
        val stepX = w / 3f
        val stepY = h / 3f

        val selectedCoord = getGridCoordForDir(direction)

        // Draw 9 grid dots (hiding the currently active dot)
        val dotRadius = dp(2.5f)
        for (gx in 0..2) {
            for (gy in 0..2) {
                if (gx == selectedCoord.first && gy == selectedCoord.second) {
                    continue // Hide dot under the active handle
                }
                val dotX = (gx + 0.5f) * stepX
                val dotY = (gy + 0.5f) * stepY
                canvas.drawCircle(dotX, dotY, dotRadius, gridDotPaint)
            }
        }

        // Draw active snap handle (17dp diameter => 8.5dp radius)
        val hx = (currentGridX + 0.5f) * stepX
        val hy = (currentGridY + 0.5f) * stepY
        val handleRadius = dp(8.5f)

        canvas.drawCircle(hx, hy + dp(1f), handleRadius, shadowPaint)
        canvas.drawCircle(hx, hy, handleRadius, snapHandleFillPaint)
        canvas.drawCircle(hx, hy, handleRadius, snapHandleBorderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                onDragStateChanged?.invoke(true)
                handleTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    handleTouch(event.x, event.y)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    onDragStateChanged?.invoke(false)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouch(touchX: Float, touchY: Float) {
        val cx = width / 2f
        val cy = height / 2f

        when (mode) {
            Mode.ANGLE -> {
                val dx = touchX - cx
                val dy = touchY - cy
                var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                deg = (deg % 360f + 360f) % 360f
                angle = deg
                onAngleChanged?.invoke(deg)
            }
            Mode.SNAP_9 -> {
                val gx = (touchX / width * 3f).toInt().coerceIn(0, 2)
                val gy = (touchY / height * 3f).toInt().coerceIn(0, 2)
                val newDir = getDirForGridCoord(gx, gy)
                if (newDir != direction) {
                    direction = newDir
                    onDirectionChanged?.invoke(newDir)
                }
            }
        }
    }

    private fun animateHandleToTarget(target: Pair<Int, Int>, animate: Boolean) {
        targetGridX = target.first.toFloat()
        targetGridY = target.second.toFloat()

        if (!animate) {
            currentGridX = targetGridX
            currentGridY = targetGridY
            invalidate()
            return
        }

        snapAnimator?.cancel()
        val startX = currentGridX
        val startY = currentGridY
        snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 140L
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val frac = va.animatedFraction
                currentGridX = startX + (targetGridX - startX) * frac
                currentGridY = startY + (targetGridY - startY) * frac
                invalidate()
            }
            start()
        }
    }

    private fun getGridCoordForDir(dir: String): Pair<Int, Int> {
        return when (dir) {
            "top-left" -> Pair(0, 0)
            "top" -> Pair(1, 0)
            "top-right" -> Pair(2, 0)
            "left" -> Pair(0, 1)
            "center" -> Pair(1, 1)
            "right" -> Pair(2, 1)
            "bottom-left" -> Pair(0, 2)
            "bottom" -> Pair(1, 2)
            "bottom-right" -> Pair(2, 2)
            else -> Pair(2, 2)
        }
    }

    private fun getDirForGridCoord(gx: Int, gy: Int): String {
        return when {
            gx == 0 && gy == 0 -> "top-left"
            gx == 1 && gy == 0 -> "top"
            gx == 2 && gy == 0 -> "top-right"
            gx == 0 && gy == 1 -> "left"
            gx == 1 && gy == 1 -> "center"
            gx == 2 && gy == 1 -> "right"
            gx == 0 && gy == 2 -> "bottom-left"
            gx == 1 && gy == 2 -> "bottom"
            gx == 2 && gy == 2 -> "bottom-right"
            else -> "bottom-right"
        }
    }

    override fun onDetachedFromWindow() {
        snapAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
