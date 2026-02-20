package com.webscare.urducanvas.common.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.annotation.ColorInt
import com.webscare.urducanvas.common.canvas.model.GradientItem
import kotlin.math.abs

class GradientBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : android.view.View(context, attrs, defStyle) {

    var gradientItem: com.webscare.urducanvas.common.canvas.model.GradientItem =
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.GradientItem()
        set(value) {
            field = value
            colors = value.colors.toMutableList()
            positions = value.positions.toMutableList()
            invalidateShader()
        }

    private var colors = mutableListOf<Int>()
    private var positions = mutableListOf<Float>()

    var onStopSelected: ((index: Int) -> Unit)? = null
    var onStopAdded: ((index: Int, color: Int, position: Float) -> Unit)? = null
    var onStopMoved: ((index: Int, newPosition: Float) -> Unit)? = null
    var onStopRemoved: ((index: Int) -> Unit)? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2
        color = Color.WHITE
    }

    private val handleRadius = resources.displayMetrics.density * 10
    private val barCornerRadius = resources.displayMetrics.density * 18
    private val barPadding = resources.displayMetrics.density
    private val extraInset = handleRadius / 2.5f

    private var activeHandle = -1
    private var pendingHandle = -1
    private var pendingAdd = false
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        gradientItem = gradientItem
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw gradient bar background
        val barLeft = barPadding
        val barTop = barPadding
        val barRight = width - barPadding
        val barBottom = height - barPadding
        val rect = RectF(barLeft, barTop, barRight, barBottom)
        fillPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, barCornerRadius, barCornerRadius, fillPaint)

        // Handle position calculations
        val minX = barLeft + handleRadius + extraInset
        val maxX = barRight - handleRadius - extraInset
        val effWidth = maxX - minX
        val cy = height / 2f

        // Draw each stop handle
        positions.forEachIndexed { i, pos ->
            val cx = minX + (pos.coerceIn(0f, 1f) * effWidth)
            fillPaint.shader = null
            fillPaint.color = colors[i]
            canvas.drawCircle(cx, cy, handleRadius, fillPaint)
            strokePaint.color = if (isColorDark(colors[i])) {
                Color.WHITE
            } else {
                Color.BLACK
            }
            canvas.drawCircle(cx, cy, handleRadius, strokePaint)
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val rawX = ev.x
        val rawY = ev.y

        val minHandleX = barPadding + handleRadius + extraInset + handleRadius
        val maxHandleX = width - minHandleX
        val x = rawX.coerceIn(minHandleX, maxHandleX)
        val effWidth = maxHandleX - minHandleX
        val pos = (x - minHandleX) / effWidth

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = rawX
                downY = rawY

                // bar geometry
                val barTop = barPadding
                val barBottom = height - barPadding
                val cy = height / 2f

                val hitRadius = resources.displayMetrics.density * 24  // 48dp diameter
                pendingHandle = positions.indexOfFirst { pos ->
                    val handleX = minHandleX + pos * effWidth
                    val dx = rawX - handleX
                    val dy = rawY - cy
                    dx * dx + dy * dy <= hitRadius * hitRadius
                }

                if (pendingHandle >= 0) {
                    // Tapped directly on a handle
                    activeHandle = pendingHandle
                    pendingAdd = false
                } else {
                    // Allow adding only if tap is near the bar vertically
                    pendingAdd = rawY in (barTop - hitRadius)..(barBottom + hitRadius)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeHandle >= 0) {
                    if (abs(rawX - downX) > touchSlop || abs(rawY - downY) > touchSlop) {
                        // Prevent handles from crossing neighbors by enforcing a small separation
                        val lower = if (activeHandle > 0)
                            positions[activeHandle - 1]
                        else
                            0f

                        val upper = if (activeHandle < positions.lastIndex)
                            positions[activeHandle + 1]
                        else
                            1f

                        val minSeparation = 0.001f
                        val min = lower + minSeparation
                        val max = upper - minSeparation

                        val clamped = pos.coerceIn(min, max)
                        // Only update if there's a noticeable change (e.g., 0.001f threshold)
                        if (abs(clamped - positions[activeHandle]) > 0.001f) {
                            positions[activeHandle] = clamped
                            gradientItem.positions = positions.toList()
                            onStopMoved?.invoke(activeHandle, clamped)
                            invalidateShader()
                        }
                        pendingAdd = false
                        return true
                    }
                } else if (pendingAdd) {
                    if (abs(rawX - downX) > touchSlop || abs(rawY - downY) > touchSlop) {
                        pendingAdd = false
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeHandle >= 0) {
                    if (pendingHandle == activeHandle
                        && abs(rawX - downX) <= touchSlop
                        && abs(rawY - downY) <= touchSlop
                    ) {
                        onStopSelected?.invoke(activeHandle)
                        postInvalidateOnAnimation()
                    }
                } else if (pendingAdd) {
                    if (abs(rawX - downX) <= touchSlop && abs(rawY - downY) <= touchSlop) {
                        val idx =
                            positions.indexOfFirst { it > pos }.takeIf { it >= 0 } ?: positions.size
                        val sampled = sampleColorAt(pos)
                        onStopAdded?.invoke(idx, sampled, pos)
                        invalidateShader()
                    }
                    pendingAdd = false
                    activeHandle = -1
                    pendingHandle = -1
                    return true
                }
                activeHandle = -1
                pendingHandle = -1
                pendingAdd = false
            }
        }

        return super.onTouchEvent(ev)
    }

    fun invalidateShader() {
        if (width == 0 || height == 0) return
        val barLeft = barPadding + handleRadius + extraInset
        val barRight = width - barPadding - handleRadius - extraInset
        val cy = height / 2f
        fillPaint.shader = LinearGradient(
            barLeft, cy,
            barRight, cy,
            colors.toIntArray(), positions.toFloatArray(), Shader.TileMode.CLAMP
        )
        postInvalidateOnAnimation()
    }

    private fun sampleColorAt(pos: Float): Int {
        val i = positions.indexOfLast { it <= pos }.coerceAtLeast(0)
        if (i == positions.lastIndex) return colors.last()
        val t = (pos - positions[i]) / (positions[i + 1] - positions[i])
        fun lerp(a: Int, b: Int) = (a + ((b - a) * t)).toInt()
        val a = lerp(Color.alpha(colors[i]), Color.alpha(colors[i + 1]))
        val r = lerp(Color.red(colors[i]), Color.red(colors[i + 1]))
        val g = lerp(Color.green(colors[i]), Color.green(colors[i + 1]))
        val b = lerp(Color.blue(colors[i]), Color.blue(colors[i + 1]))
        return Color.argb(a, r, g, b)
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setLayerType(LAYER_TYPE_HARDWARE, null)
        invalidateShader()
    }

}