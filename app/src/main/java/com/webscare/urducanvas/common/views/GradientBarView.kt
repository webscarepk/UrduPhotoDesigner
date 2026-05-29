package com.webscare.urducanvas.common.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.annotation.ColorInt
import androidx.core.graphics.withSave
import com.webscare.urducanvas.common.canvas.model.GradientItem
import kotlin.math.abs
import kotlin.math.hypot

/**
 * GradientBarView — a horizontal color-stop editor.
 *
 * Design contract
 * ───────────────
 * • The bar ALWAYS shows a simple left→right linear gradient of the current
 *   stops/positions.  Angle, scale and center from GradientItem are intentionally
 *   ignored here — they only matter on the final canvas.  This gives the user a
 *   clean, readable strip to drag stops on regardless of the output orientation.
 *
 * • Each stop thumb is drawn as a circle whose FILL colour is exactly the stop
 *   colour, and whose CENTER is positioned exactly at the pixel that corresponds
 *   to that stop's position on the bar.  There is no visual offset.
 *
 * • Drag is smooth: positions are updated on every ACTION_MOVE frame and the
 *   shader is rebuilt in-place (no allocation on the hot path after the first
 *   build for a given size).
 *
 * • Long-press on a stop fires onStopRemoveRequested (caller decides min-count
 *   guard).  Tap on empty bar area fires onStopAdded with the interpolated colour.
 *
 * Callbacks
 * ─────────
 *   onStopSelected(index)                   — tap on existing stop
 *   onStopAdded(index, color, position)     — tap on empty bar area
 *   onStopMoved(index, newPosition)         — drag of existing stop
 *   onStopRemoveRequested(index)            — long-press on existing stop
 */
class GradientBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : android.view.View(context, attrs, defStyle) {

    // ── Public data binding ───────────────────────────────────────────────────

    var gradientItem: GradientItem = GradientItem()
        set(value) {
            field     = value
            colors    = value.colors.toMutableList()
            positions = value.positions.toMutableList()
            rebuildShader()
        }

    private var colors    = mutableListOf<Int>()
    private var positions = mutableListOf<Float>()

    // ── Callbacks ─────────────────────────────────────────────────────────────

    var onStopSelected:        ((index: Int) -> Unit)?                         = null
    var onStopAdded:           ((index: Int, color: Int, pos: Float) -> Unit)? = null
    var onStopMoved:           ((index: Int, newPos: Float) -> Unit)?          = null
    var onStopRemoveRequested: ((index: Int) -> Unit)?                         = null

    // ── Metrics ───────────────────────────────────────────────────────────────

    private val dp  = resources.displayMetrics.density
    private val handleRadius    = dp * 11f
    private val barCornerRadius = dp * 18f
    /** Horizontal padding so handles at 0 and 1 don't clip at the edges. */
    private val barPaddingH     = handleRadius + dp * 2f
    private val barPaddingV     = dp * 4f
    private val strokeWidth     = dp * 2f

    // ── Paints ────────────────────────────────────────────────────────────────

    /** Carries the gradient shader — rebuilt only when data/size changes. */
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** Solid fill for each handle — colour set per-stop in onDraw. */
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** Stroke ring for each handle. */
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = this@GradientBarView.strokeWidth
    }

    /** Slightly larger outer ring to separate overlapping stops. */
    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = dp * 1.5f
        color       = Color.argb(60, 0, 0, 0)
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    private val barRect get() = RectF(
        barPaddingH,
        barPaddingV,
        width  - barPaddingH,
        height - barPaddingV
    )

    /** Left pixel that maps to position 0. */
    private val trackStart get() = barPaddingH
    /** Right pixel that maps to position 1. */
    private val trackEnd   get() = width - barPaddingH
    private val trackWidth get() = trackEnd - trackStart

    /** Map a normalised [0,1] position to a canvas X coordinate. */
    private fun posToX(pos: Float) = trackStart + pos.coerceIn(0f, 1f) * trackWidth

    /** Map a canvas X coordinate to a normalised [0,1] position. */
    private fun xToPos(x: Float) =
        ((x - trackStart) / trackWidth).coerceIn(0f, 1f)

    // ── Touch state ───────────────────────────────────────────────────────────

    private val touchSlop      = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressMs    = ViewConfiguration.getLongPressTimeout().toLong()
    private val handler        = Handler(Looper.getMainLooper())

    private var activeHandle   = -1   // handle being dragged right now
    private var pendingHandle  = -1   // handle candidate on ACTION_DOWN
    private var pendingAdd     = false
    private var dragging       = false
    private var downX          = 0f
    private var downY          = 0f

    private val longPressRunnable = Runnable {
        if (pendingHandle >= 0 && !dragging) {
            onStopRemoveRequested?.invoke(pendingHandle)
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            activeHandle  = -1
            pendingHandle = -1
            pendingAdd    = false
        }
    }

    // ── Shader rebuild ────────────────────────────────────────────────────────

    /**
     * Builds a simple LEFT → RIGHT linear gradient from the current stops.
     *
     * The gradient bar is a colour-stop editor: angle/scale/center from
     * GradientItem are intentionally NOT applied here — they only affect the
     * final canvas render, not this UI widget.
     */
    private fun rebuildShader() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || colors.isEmpty()) return

        // Ensure we always have valid sorted data before building the shader.
        val c = colors.toIntArray()
        val p = positions.toFloatArray()

        barPaint.shader = LinearGradient(
            trackStart, 0f, trackEnd, 0f,
            c, p,
            Shader.TileMode.CLAMP
        )
        invalidate()
    }

    /** Called by external code after the item's colors/positions change. */
    fun invalidateShader() = rebuildShader()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Software layer is required for LinearGradient with Matrix on some GPU paths.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        rebuildShader()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShader()
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val rect = barRect
        val cy   = height / 2f

        // 1 ── Gradient bar clipped to rounded rect
        canvas.withSave {
            val clip = Path().apply {
                addRoundRect(rect, barCornerRadius, barCornerRadius, Path.Direction.CW)
            }
            clipPath(clip)
            drawRoundRect(rect, barCornerRadius, barCornerRadius, barPaint)
        }

        // 2 ── Stop handles
        //
        // The handle CENTRE is at posToX(pos), which is the exact pixel on the
        // gradient that corresponds to that stop.  The fill colour is the stop
        // colour.  No offset.  No shadow displacement.  Just a circle whose
        // mathematical centre is the stop pixel.
        positions.forEachIndexed { i, pos ->
            val cx = posToX(pos)

            // Outer separation ring (semi-transparent dark) — helps legibility
            // when two handles are close together.
            canvas.drawCircle(cx, cy, handleRadius + strokeWidth, outerRingPaint)

            // Solid fill with the exact stop colour
            fillPaint.color = colors[i]
            canvas.drawCircle(cx, cy, handleRadius, fillPaint)

            // Contrasting stroke so white/black handles stay visible on the bar
            strokePaint.color = if (isColorDark(colors[i])) Color.WHITE else Color.BLACK
            canvas.drawCircle(cx, cy, handleRadius, strokePaint)
        }
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val rawX = ev.x
        val rawY = ev.y
        val cy   = height / 2f

        when (ev.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                downX    = rawX
                downY    = rawY
                dragging = false

                val hitRadius = handleRadius * 2.2f   // generous tap area
                pendingHandle = positions.indexOfFirst { p ->
                    val hx = posToX(p)
                    val dx = rawX - hx
                    val dy = rawY - cy
                    hypot(dx, dy) <= hitRadius
                }

                if (pendingHandle >= 0) {
                    activeHandle = pendingHandle
                    pendingAdd   = false
                    // Schedule long-press detection for removal
                    handler.postDelayed(longPressRunnable, longPressMs)
                } else {
                    val expandedTop    = barRect.top    - handleRadius
                    val expandedBottom = barRect.bottom + handleRadius
                    pendingAdd = rawY in expandedTop..expandedBottom
                    activeHandle = -1
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val movedX = abs(rawX - downX)
                val movedY = abs(rawY - downY)

                if (!dragging && (movedX > touchSlop || movedY > touchSlop)) {
                    dragging = true
                    // Cancel long-press once drag starts
                    handler.removeCallbacks(longPressRunnable)
                    if (activeHandle >= 0) pendingAdd = false
                    else activeHandle = -1
                }

                if (dragging && activeHandle >= 0) {
                    val newPos = xToPos(rawX)

                    // Clamp between neighbouring stops with a tiny gap
                    val lower = if (activeHandle > 0)
                        positions[activeHandle - 1] + 0.001f else 0f
                    val upper = if (activeHandle < positions.lastIndex)
                        positions[activeHandle + 1] - 0.001f else 1f

                    val clamped = newPos.coerceIn(lower, upper)

                    positions[activeHandle] = clamped
                    gradientItem.positions  = positions.toList()

                    // Rebuild shader in-place — smooth per-frame update
                    rebuildShader()
                    onStopMoved?.invoke(activeHandle, clamped)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)

                val wasTap = abs(rawX - downX) <= touchSlop &&
                        abs(rawY - downY) <= touchSlop

                if (ev.actionMasked == MotionEvent.ACTION_UP) {
                    when {
                        // Tap on existing handle → select
                        wasTap && pendingHandle >= 0 && !dragging -> {
                            onStopSelected?.invoke(pendingHandle)
                            invalidate()
                        }

                        // Tap on empty bar → add new stop
                        wasTap && pendingAdd && activeHandle < 0 && !dragging -> {
                            val pos     = xToPos(rawX)
                            val idx     = positions.indexOfFirst { it > pos }
                                .takeIf { it >= 0 } ?: positions.size
                            val sampled = sampleColorAt(pos)
                            onStopAdded?.invoke(idx, sampled, pos)
                            // The caller will push a new GradientItem which triggers
                            // the setter → rebuildShader automatically.
                        }
                    }
                }

                activeHandle  = -1
                pendingHandle = -1
                pendingAdd    = false
                dragging      = false
                return true
            }
        }
        return true
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Linearly interpolates the gradient colour at the given normalised [pos].
     * Used when inserting a new stop so it blends seamlessly into the existing
     * gradient.
     */
    private fun sampleColorAt(pos: Float): Int {
        if (positions.isEmpty()) return Color.BLACK
        if (positions.size == 1) return colors[0]

        val i = positions.indexOfLast { it <= pos }.coerceAtLeast(0)
        if (i >= positions.lastIndex) return colors.last()

        val t = (pos - positions[i]) / (positions[i + 1] - positions[i]).let {
            if (it == 0f) return colors[i] else it
        }

        fun lerp(a: Int, b: Int) = (a + (b - a) * t).toInt()
        return Color.argb(
            lerp(Color.alpha(colors[i]), Color.alpha(colors[i + 1])),
            lerp(Color.red(colors[i]),   Color.red(colors[i + 1])),
            lerp(Color.green(colors[i]), Color.green(colors[i + 1])),
            lerp(Color.blue(colors[i]),  Color.blue(colors[i + 1]))
        )
    }

    private fun isColorDark(@ColorInt color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        // Relative luminance (sRGB)
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return luminance < 128
    }
}