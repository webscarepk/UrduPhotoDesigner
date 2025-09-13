package com.example.urduphotodesigner.common.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val matrixScale = Matrix()
    private val savedMatrix = Matrix()
    private val matrixValues = FloatArray(9)

    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    private val last = PointF()
    private var mode = NONE

    private var minScale = 1f
    private var maxScale = 5f

    init {
        super.setClickable(true)
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
        imageMatrix = matrixScale
        scaleType = ScaleType.MATRIX
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        post { fitToScreen() }
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        post { fitToScreen() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val currentPoint = PointF(event.x, event.y)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(matrixScale)
                last.set(currentPoint)
                mode = DRAG
            }
            MotionEvent.ACTION_MOVE -> if (mode == DRAG) {
                val dx = currentPoint.x - last.x
                val dy = currentPoint.y - last.y
                matrixScale.postTranslate(dx, dy)
                fixTranslation()
                last.set(currentPoint)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
            }
        }

        imageMatrix = matrixScale
        return true
    }

    private fun fitToScreen() {
        drawable?.let { d ->
            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()
            if (viewWidth == 0f || viewHeight == 0f) return

            val drawableWidth = d.intrinsicWidth.toFloat()
            val drawableHeight = d.intrinsicHeight.toFloat()

            val scale = min(viewWidth / drawableWidth, viewHeight / drawableHeight)

            minScale = scale
            matrixScale.setScale(scale, scale)

            val redundantYSpace = (viewHeight - scale * drawableHeight) / 2f
            val redundantXSpace = (viewWidth - scale * drawableWidth) / 2f
            matrixScale.postTranslate(redundantXSpace, redundantYSpace)

            imageMatrix = matrixScale
        }
    }

    private fun fixTranslation() {
        val rect = getMatrixRect()
        val deltaX = getFixTranslation(rect.left, rect.right, width.toFloat())
        val deltaY = getFixTranslation(rect.top, rect.bottom, height.toFloat())
        matrixScale.postTranslate(deltaX, deltaY)
    }

    private fun getMatrixRect(): RectF {
        val d = drawable ?: return RectF()
        val rect = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        matrixScale.mapRect(rect)
        return rect
    }

    private fun getFixTranslation(minEdge: Float, maxEdge: Float, viewSize: Float): Float {
        return when {
            maxEdge - minEdge < viewSize -> (viewSize - (maxEdge - minEdge)) / 2f - minEdge
            minEdge > 0 -> -minEdge
            maxEdge < viewSize -> viewSize - maxEdge
            else -> 0f
        }
    }

    private fun getCurrentScale(): Float {
        matrixScale.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            var scale = getCurrentScale() * scaleFactor
            if (scale < minScale) scale = minScale
            if (scale > maxScale) scale = maxScale

            val factor = scale / getCurrentScale()
            matrixScale.postScale(factor, factor, detector.focusX, detector.focusY)
            fixTranslation()
            return true
        }
    }

    inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        private val zoomSteps = floatArrayOf(2f, 4f, 1f)
        private var currentStep = 0

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val currentScale = getCurrentScale()
            val targetScale = zoomSteps[currentStep] * minScale // base se multiply

            animateZoom(currentScale, targetScale, e.x, e.y)

            currentStep = (currentStep + 1) % zoomSteps.size
            return true
        }
    }

    private fun animateZoom(from: Float, to: Float, focusX: Float, focusY: Float) {
        val animator = ValueAnimator.ofFloat(from, to)
        animator.duration = 300
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            val scale = it.animatedValue as Float
            val factor = scale / getCurrentScale()
            matrixScale.postScale(factor, factor, focusX, focusY)
            fixTranslation()
            imageMatrix = matrixScale
        }
        animator.start()
    }

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
    }
}
