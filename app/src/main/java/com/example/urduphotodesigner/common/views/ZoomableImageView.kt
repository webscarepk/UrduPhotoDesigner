package com.example.urduphotodesigner.common.views

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private var matrixScale = Matrix()
    private var savedMatrix = Matrix()

    // Gesture detectors
    private var scaleDetector: ScaleGestureDetector
    private var gestureDetector: GestureDetector

    // States
    private var mode = NONE
    private val last = PointF()
    private val start = PointF()
    private var minScale = 1f
    private var maxScale = 4f
    private val m = FloatArray(9)

    init {
        super.setClickable(true)
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
        imageMatrix = matrixScale
        scaleType = ScaleType.MATRIX
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        fitToScreen()
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        fitToScreen()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val currentPoint = PointF(event.x, event.y)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(matrixScale)
                last.set(currentPoint)
                start.set(last)
                mode = DRAG
            }
            MotionEvent.ACTION_MOVE -> if (mode == DRAG) {
                matrixScale.set(savedMatrix)
                val dx = currentPoint.x - last.x
                val dy = currentPoint.y - last.y
                matrixScale.postTranslate(dx, dy)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
            }
        }

        imageMatrix = matrixScale
        fixTranslation()
        return true
    }

    private fun fitToScreen() {
        drawable?.let { d ->
            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()
            val drawableWidth = d.intrinsicWidth.toFloat()
            val drawableHeight = d.intrinsicHeight.toFloat()

            val scale = min(viewWidth / drawableWidth, viewHeight / drawableHeight)
            val redundantYSpace = (viewHeight - scale * drawableHeight) / 2f
            val redundantXSpace = (viewWidth - scale * drawableWidth) / 2f

            matrixScale.setScale(scale, scale)
            matrixScale.postTranslate(redundantXSpace, redundantYSpace)

            imageMatrix = matrixScale
        }
    }

    private fun fixTranslation() {
        matrixScale.getValues(m)
        val transX = m[Matrix.MTRANS_X]
        val transY = m[Matrix.MTRANS_Y]

        val fixTransX = getFixTranslation(transX, width.toFloat(), drawable?.intrinsicWidth?.times(m[Matrix.MSCALE_X]) ?: 0f)
        val fixTransY = getFixTranslation(transY, height.toFloat(), drawable?.intrinsicHeight?.times(m[Matrix.MSCALE_Y]) ?: 0f)

        if (fixTransX != 0f || fixTransY != 0f) {
            matrixScale.postTranslate(fixTransX, fixTransY)
        }
    }

    private fun getFixTranslation(trans: Float, viewSize: Float, contentSize: Float): Float {
        return when {
            contentSize <= viewSize -> viewSize / 2f - contentSize / 2f - trans
            trans > 0 -> -trans
            trans + contentSize < viewSize -> viewSize - (trans + contentSize)
            else -> 0f
        }
    }

    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val origScale = getMatrixScale()
            var newScale = origScale * scaleFactor

            newScale = max(minScale, min(newScale, maxScale))

            val factor = newScale / origScale
            matrixScale.postScale(factor, factor, detector.focusX, detector.focusY)
            fixTranslation()
            return true
        }
    }

    inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val currentScale = getMatrixScale()
            val targetScale = if (currentScale < maxScale) maxScale else minScale
            val factor = targetScale / currentScale
            matrixScale.postScale(factor, factor, e.x, e.y)
            fixTranslation()
            return true
        }
    }

    private fun getMatrixScale(): Float {
        matrixScale.getValues(m)
        return m[Matrix.MSCALE_X]
    }

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
    }
}