package com.webscare.urducanvas.common.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.webscare.urducanvas.R
import kotlin.math.roundToInt

/**
 * A compact vertical slider: value on top, track in the middle, caption underneath.
 *
 * Built for the docked eraser kit, which occupies the same narrow strip the alignment kit
 * does — there is room for a column, not a row, so the stock horizontal SeekBar does not
 * fit and rotating one leaves its touch target in the wrong place.
 */
class VerticalSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var minValue: Int = 1
    var maxValue: Int = 100

    /** Appended to the value readout — "%" for softness, empty for size. */
    var valueSuffix: String = ""

    var caption: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var value: Int = 50
        set(v) {
            val clamped = v.coerceIn(minValue, maxValue)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    /** Fired only for user drags, never for programmatic [value] writes. */
    var onValueChanged: ((Int) -> Unit)? = null
    var onDragStart: (() -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

    private val density = resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2f * density
        color = "#DDE1DA".toColorIntSafe()
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2.5f * density
        color = ContextCompat.getColor(context, R.color.appColor)
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.appColor)
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.black)
        textAlign = Paint.Align.CENTER
        textSize = 10f * density
        typeface = ResourcesCompat.getFont(context, R.font.medium) ?: Typeface.DEFAULT_BOLD
    }

    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gray)
        textAlign = Paint.Align.CENTER
        textSize = 7.5f * density
        letterSpacing = 0.12f
        typeface = ResourcesCompat.getFont(context, R.font.regular) ?: Typeface.DEFAULT
    }

    private val thumbRadius = 5.5f * density
    private val valueBlock = 14f * density
    private val captionBlock = 12f * density

    private fun String.toColorIntSafe(): Int =
        runCatching { Color.parseColor(this) }.getOrDefault(Color.LTGRAY)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 30f, resources.displayMetrics
        ).toInt()
        val h = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 108f, resources.displayMetrics
        ).toInt()
        setMeasuredDimension(
            resolveSize(w, widthMeasureSpec),
            resolveSize(h, heightMeasureSpec)
        )
    }

    private val trackTop get() = paddingTop + valueBlock + thumbRadius
    private val trackBottom get() = height - paddingBottom - captionBlock - thumbRadius

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val top = trackTop
        val bottom = trackBottom
        if (bottom <= top) return

        // Value reads at the top, the way the reference kit shows it.
        canvas.drawText("$value$valueSuffix", cx, paddingTop + valueBlock * 0.85f, valuePaint)

        canvas.drawLine(cx, top, cx, bottom, trackPaint)

        val fraction = (value - minValue).toFloat() / (maxValue - minValue).coerceAtLeast(1)
        // Larger values sit higher: the slider fills from the bottom up.
        val thumbY = bottom - fraction * (bottom - top)
        canvas.drawLine(cx, bottom, cx, thumbY, fillPaint)
        canvas.drawCircle(cx, thumbY, thumbRadius, thumbPaint)

        if (caption.isNotEmpty()) {
            canvas.drawText(caption, cx, height - paddingBottom - captionBlock * 0.15f, captionPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                onDragStart?.invoke()
                applyTouch(event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                applyTouch(event.y)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                onDragEnd?.invoke()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun applyTouch(y: Float) {
        val top = trackTop
        val bottom = trackBottom
        if (bottom <= top) return
        val fraction = ((bottom - y) / (bottom - top)).coerceIn(0f, 1f)
        val next = (minValue + fraction * (maxValue - minValue)).roundToInt()
        if (next != value) {
            value = next
            onValueChanged?.invoke(next)
        }
    }
}
