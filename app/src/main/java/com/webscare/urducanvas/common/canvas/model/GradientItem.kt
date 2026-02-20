package com.webscare.urducanvas.common.canvas.model

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.webscare.urducanvas.common.canvas.enums.GradientType
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class GradientItem(
    var id: Long = 0,
    var colors: List<Int> = listOf(Color.BLACK, Color.GRAY),
    var positions: List<Float> = listOf(0f, 1f),
    val angle: Float = 0f,
    val scale: Float = 1f,
    val type: GradientType = GradientType.LINEAR,
    val radialRadiusFactor: Float = 0.5f,
    val sweepStartAngle: Float = 0f,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    var isSelected: Boolean = false
) {
    init {
        require(colors.size == positions.size) {
            "colors and positions must have the same length"
        }
        require(positions.all { it in 0f..1f }) {
            "positions must be within 0f…1f"
        }
        require(radialRadiusFactor in 0f..1f) {
            "radialRadiusFactor must be within 0f…1f"
        }
        require(centerX in 0f..1f && centerY in 0f..1f) {
            "centerX/centerY must be within 0f…1f"
        }
    }

    /** Return a new item with stops reversed (colors & positions inverted). */
    fun swapped(): GradientItem {
        val invPos = positions.map { 1f - it }.reversed()
        return copy(
            colors = colors.reversed(),
            positions = invPos
        )
    }

    /** Helper for sweep start-angle. */
    fun withSweepStart(angle: Float): GradientItem =
        copy(sweepStartAngle = angle)

    /** Helper for radial center. */
    fun withRadialCenter(x: Float, y: Float): GradientItem =
        copy(centerX = x.coerceIn(0f, 1f), centerY = y.coerceIn(0f, 1f))

    fun createGradientPreviewDrawable(
        gradient: GradientItem,
        width: Int,
        height: Int
    ): Drawable {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val colors = gradient.colors.toIntArray()
        val positions = gradient.positions.toFloatArray()

        when (gradient.type) {
            GradientType.LINEAR -> {
                val angleRad = Math.toRadians(gradient.angle.toDouble())

                val scaledWidth = width * gradient.scale
                val scaledHeight = height * gradient.scale

                val dx = (cos(angleRad) * scaledWidth).toFloat()
                val dy = (sin(angleRad) * scaledHeight).toFloat()

                val cx = width / 2f
                val cy = height / 2f

                val x0 = cx - dx / 2f
                val y0 = cy - dy / 2f
                val x1 = cx + dx / 2f
                val y1 = cy + dy / 2f

                paint.shader = LinearGradient(
                    x0, y0, x1, y1,
                    colors, positions,
                    Shader.TileMode.CLAMP
                )
            }

            GradientType.RADIAL -> {
                val cx = width * gradient.centerX
                val cy = height * gradient.centerY

                val maxRadius = min(width, height) / 2f
                val radius = maxRadius * gradient.radialRadiusFactor * gradient.scale

                paint.shader = RadialGradient(
                    cx, cy, radius,
                    colors, positions,
                    Shader.TileMode.CLAMP
                )
            }

            GradientType.SWEEP -> {
                val cx = width * gradient.centerX
                val cy = height * gradient.centerY

                val sweepGradient = SweepGradient(cx, cy, colors, positions)

                // Apply sweep start angle as rotation matrix
                val matrix = Matrix().apply {
                    setRotate(gradient.sweepStartAngle, cx, cy)
                }
                sweepGradient.setLocalMatrix(matrix)

                paint.shader = sweepGradient
            }
        }

        // Draw the full gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        return BitmapDrawable(Resources.getSystem(), bitmap)
    }

}
