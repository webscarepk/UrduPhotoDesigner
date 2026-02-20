package com.webscare.urducanvas.common.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils

class ColorPickerBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : android.view.View(context, attrs, defStyle) {
    private val density = resources.displayMetrics.density

    // thumb radius
    private val thumbRadius = 10f * density
    // extra padding beyond the thumb so it never touches the view bounds
    private val barPadding = thumbRadius + 2f * density

    // track height slightly taller than thumb
    private val trackHeight = thumbRadius * 2.4f
    private val cornerRadius = trackHeight / 2f

    private var gradientColors = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
    private var gradientPositions = floatArrayOf(0f, 1f)
    private var shader: Shader? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barRect = RectF()

    private var _progress = 0f
    var progress: Float
        get() = _progress
        set(value) {
            _progress = value.coerceIn(0f, 1f)
            updateThumbColor()
            invalidate()
            onProgressChanged?.invoke(( _progress * max ).toInt())
        }

    var max: Int = 100
    var onProgressChanged: ((Int) -> Unit)? = null

    private var thumbColor: Int = gradientColors.first()

    fun setGradient(colors: IntArray, positions: FloatArray? = null) {
        gradientColors = colors
        gradientPositions = positions
            ?: FloatArray(colors.size) { it.toFloat() / (colors.size - 1) }
        updateShader()  // <-- rebuild the LinearGradient with your new colors
        invalidate()    // <-- now redraw with the updated shader
    }

    private fun updateShader() {
        shader = LinearGradient(
            barPadding, 0f,
            width - barPadding, 0f,
            gradientColors, gradientPositions,
            Shader.TileMode.CLAMP
        )
    }

    private fun updateThumbColor() {
        val p   = _progress * (gradientColors.size - 1)
        val idx = p.toInt().coerceIn(0, gradientColors.size - 2)
        val t   = p - idx
        val raw = ColorUtils.blendARGB(gradientColors[idx], gradientColors[idx + 1], t)
        // bake onto a white background so your thumb is fully opaque:
        thumbColor = Color.rgb(
            (Color.red(raw)   * Color.alpha(raw) + 255 * (255 - Color.alpha(raw))) / 255,
            (Color.green(raw) * Color.alpha(raw) + 255 * (255 - Color.alpha(raw))) / 255,
            (Color.blue(raw)  * Color.alpha(raw) + 255 * (255 - Color.alpha(raw))) / 255
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShader()
        updateThumbColor()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // draw the track inset by barPadding
        paint.shader = shader
        paint.style = Paint.Style.FILL
        barRect.set(
            0f,
            (height - trackHeight) / 2f,
            width.toFloat(),
            (height + trackHeight) / 2f
        )
        canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, paint)

        // draw the thumb
        val cx = barPadding + _progress * (width - 2 * barPadding)
        val cy = height / 2f

        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = thumbColor
        canvas.drawCircle(cx, cy, thumbRadius, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = if (isColorDark(thumbColor)) Color.WHITE else Color.BLACK
        canvas.drawCircle(cx, cy, thumbRadius, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                val x = event.x.coerceIn(barPadding, width - barPadding)
                progress = (x - barPadding) / (width - 2 * barPadding)
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
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
}
