package com.webscare.urducanvas.common.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.annotation.ColorInt
import androidx.core.graphics.withSave
import com.webscare.urducanvas.common.canvas.model.GradientItem
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class GradientBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : android.view.View(context, attrs, defStyle) {

    var gradientItem: GradientItem = GradientItem()
        set(value) {
            field     = value
            colors    = value.colors.toMutableList()
            positions = value.positions.toMutableList()
            rebuildShader()
        }

    private var colors    = mutableListOf<Int>()
    private var positions = mutableListOf<Float>()

    var onStopSelected: ((index: Int) -> Unit)?                         = null
    var onStopAdded:    ((index: Int, color: Int, pos: Float) -> Unit)? = null
    var onStopMoved:    ((index: Int, newPos: Float) -> Unit)?          = null
    var onStopRemoved:  ((index: Int) -> Unit)?                         = null

    // ── Paints ────────────────────────────────────────────────────────────────

    // barPaint carries the gradient shader — NEVER cleared between frames
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    // handleFillPaint is always solid — never touches barPaint
    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2
    }

    private val handleRadius    = resources.displayMetrics.density * 10
    private val barCornerRadius = resources.displayMetrics.density * 18
    private val barPadding      = resources.displayMetrics.density
    private val extraInset      = handleRadius / 2.5f

    private var activeHandle  = -1
    private var pendingHandle = -1
    private var pendingAdd    = false
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // ── Geometry ──────────────────────────────────────────────────────────────

    private val barRect    get() = RectF(barPadding, barPadding, width - barPadding, height - barPadding)
    private val minHandleX get() = barPadding + handleRadius + extraInset
    private val maxHandleX get() = width - barPadding - handleRadius - extraInset
    private val effWidth   get() = maxHandleX - minHandleX

    private fun posToX(pos: Float) = minHandleX + pos.coerceIn(0f, 1f) * effWidth
    private fun xToPos(x: Float)   = (x.coerceIn(minHandleX, maxHandleX) - minHandleX) / effWidth

    // ── Shader rebuild (never called from onDraw) ─────────────────────────────

    /**
     * Rebuilds barPaint's shader using the LINEAR gradient math from CanvasView,
     * respecting angle, scale, centerX/centerY, colors and positions.
     * Called only when data changes — NOT from onDraw.
     */
    private fun rebuildShader() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) {
            // onSizeChanged will retry once the view is measured
            return
        }

        val c = colors.toIntArray()
        val p = positions.toFloatArray()
        val item = gradientItem

        val theta   = Math.toRadians(item.angle.toDouble())
        val halfLen = hypot(w, h) * item.scale / 2f
        val dx      = (cos(theta) * halfLen).toFloat()
        val dy      = (sin(theta) * halfLen).toFloat()

        val shader = LinearGradient(-dx, -dy, dx, dy, c, p, Shader.TileMode.CLAMP)
        val matrix = Matrix().apply {
            postTranslate(w * item.centerX, h * item.centerY)
        }
        shader.setLocalMatrix(matrix)

        barPaint.shader = shader
        // Request one redraw — onDraw will NOT call rebuildShader again
        invalidate()
    }

    /** Public alias kept for callers in GradientEditorFragment. */
    fun invalidateShader() = rebuildShader()

    // ── Size change ───────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShader()
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val rect = barRect
        val cy   = height / 2f

        // 1. Draw gradient bar clipped to rounded rect — uses barPaint (has shader)
        canvas.withSave {
            clipPath(Path().apply {
                addRoundRect(rect, barCornerRadius, barCornerRadius, Path.Direction.CW)
            })
            canvas.drawRoundRect(rect, barCornerRadius, barCornerRadius, barPaint)
        }

        // 2. Draw stop handles — uses handleFillPaint / handleStrokePaint (always solid)
        positions.forEachIndexed { i, pos ->
            val cx = posToX(pos)
            handleFillPaint.color   = colors[i]
            handleStrokePaint.color = if (isColorDark(colors[i])) Color.WHITE else Color.BLACK
            canvas.drawCircle(cx, cy, handleRadius, handleFillPaint)
            canvas.drawCircle(cx, cy, handleRadius, handleStrokePaint)
        }

        // ⚠️  Do NOT call rebuildShader() here — that was the flicker cause
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val rawX = ev.x
        val rawY = ev.y
        val pos  = xToPos(rawX)
        val cy   = height / 2f

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = rawX
                downY = rawY

                val hitRadius = resources.displayMetrics.density * 24
                pendingHandle = positions.indexOfFirst { p ->
                    val hx = posToX(p)
                    val dx = rawX - hx
                    val dy = rawY - cy
                    dx * dx + dy * dy <= hitRadius * hitRadius
                }

                if (pendingHandle >= 0) {
                    activeHandle = pendingHandle
                    pendingAdd   = false
                } else {
                    val barTop    = barPadding
                    val barBottom = height - barPadding
                    pendingAdd = rawY in (barTop - hitRadius)..(barBottom + hitRadius)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeHandle >= 0) {
                    if (abs(rawX - downX) > touchSlop || abs(rawY - downY) > touchSlop) {
                        val lower = if (activeHandle > 0) positions[activeHandle - 1] else 0f
                        val upper = if (activeHandle < positions.lastIndex) positions[activeHandle + 1] else 1f
                        val clamped = pos.coerceIn(lower + 0.001f, upper - 0.001f)

                        if (abs(clamped - positions[activeHandle]) > 0.001f) {
                            positions[activeHandle] = clamped
                            gradientItem.positions   = positions.toList()
                            // Rebuild shader immediately — smooth real-time drag
                            rebuildShader()
                            onStopMoved?.invoke(activeHandle, clamped)
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
                        invalidate()
                    }
                } else if (pendingAdd) {
                    if (abs(rawX - downX) <= touchSlop && abs(rawY - downY) <= touchSlop) {
                        val idx     = positions.indexOfFirst { it > pos }.takeIf { it >= 0 } ?: positions.size
                        val sampled = sampleColorAt(pos)
                        onStopAdded?.invoke(idx, sampled, pos)
                        // ViewModel will set gradientItem → setter → rebuildShader
                    }
                    pendingAdd    = false
                    activeHandle  = -1
                    pendingHandle = -1
                    return true
                }
                activeHandle  = -1
                pendingHandle = -1
                pendingAdd    = false
            }
        }
        return super.onTouchEvent(ev)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sampleColorAt(pos: Float): Int {
        val i = positions.indexOfLast { it <= pos }.coerceAtLeast(0)
        if (i == positions.lastIndex) return colors.last()
        val t = (pos - positions[i]) / (positions[i + 1] - positions[i])
        fun lerp(a: Int, b: Int) = (a + ((b - a) * t)).toInt()
        return Color.argb(
            lerp(Color.alpha(colors[i]), Color.alpha(colors[i + 1])),
            lerp(Color.red(colors[i]),   Color.red(colors[i + 1])),
            lerp(Color.green(colors[i]), Color.green(colors[i + 1])),
            lerp(Color.blue(colors[i]),  Color.blue(colors[i + 1]))
        )
    }

    private fun isColorDark(@ColorInt color: Int): Boolean {
        val luminance = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
        return luminance < 128
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        rebuildShader()
    }
}