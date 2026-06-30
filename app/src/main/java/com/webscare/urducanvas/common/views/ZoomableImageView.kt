package com.webscare.urducanvas.common.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val matrixScale = Matrix()
    private val savedMatrix = Matrix()
    private val matrixValues = FloatArray(9)

    private val scaleDetector: ScaleGestureDetector

    private val last = PointF()
    private var mode = NONE

    private var minScale = 1f
    private var maxScale = 5f

    private val rubberBandMinScale get() = minScale * 0.55f
    private var isScaling = false

    // ─── Manual double-tap ────────────────────────────────────────────────────
    private val doubleTapHandler = Handler(Looper.getMainLooper())
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var awaitingSecondTap = false
    private val doubleTapMaxDelay = 250L
    private val doubleTapSlopPx = 80f
    private val zoomSteps = floatArrayOf(2f, 4f, 1f)
    private var doubleTapZoomStep = 0

    // ─── Swipe-down dismiss ───────────────────────────────────────────────────

    /**
     * Fired every move frame during a dismiss drag.
     * [progress] 0.0 → 1.0. Use it to fade a dedicated scrim view (NOT root):
     *
     *   zoomableImageView.onDismissProgress = { p -> binding.scrim.alpha = 1f - p }
     */
    var onDismissProgress: ((progress: Float) -> Unit)? = null

    /**
     * Fired when the user releases past the dismiss threshold.
     *
     *   zoomableImageView.onDismiss = { requireActivity().onBackPressed() }
     */
    var onDismiss: (() -> Unit)? = null

    /**
     * Programmatically trigger the same dismiss animation that a swipe-down fires.
     * Call from a back button or the system back press handler so the user always
     * gets the morph-out animation instead of a hard navigate.
     */
    fun triggerDismiss() {
        if (!isDismissing) animateDismiss()
    }

    // 35% of view height to commit dismiss
    private val dismissThreshold get() = height * 0.1f

    // True once the finger has moved enough downward to enter dismiss mode
    private var isDismissing = false

    // Accumulated drag values — only used while isDismissing
    private var dismissDragY = 0f
    private var dismissDragX = 0f

    init {
        super.setClickable(true)
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
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

    // ─── Touch ───────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(matrixScale)
                last.set(event.x, event.y)
                mode = DRAG
                isScaling = false
                // Reset dismiss state on every new touch
                isDismissing = false
                dismissDragY = 0f
                dismissDragX = 0f
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (isDismissing) cancelDismissDrag(animated = false)
                isScaling = true
                mode = ZOOM
            }

            MotionEvent.ACTION_MOVE -> {
                handleMove(event)
                // Only update imageMatrix when NOT in dismiss mode
                // During dismiss the view is moved via translationX/Y, not the matrix
                if (!isDismissing) imageMatrix = matrixScale
                return true  // early return — skip the bottom imageMatrix assign
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (getCurrentScale() < minScale) {
                    animateZoom(getCurrentScale(), minScale, width / 2f, height / 2f)
                }
                isScaling = false
                mode = NONE
            }

            MotionEvent.ACTION_UP -> {
                if (isDismissing) {
                    if (dismissDragY >= dismissThreshold) animateDismiss()
                    else cancelDismissDrag(animated = true)
                } else if (!isScaling) {
                    handleTap(event)
                }
                mode = NONE
                isScaling = false
            }

            MotionEvent.ACTION_CANCEL -> {
                if (isDismissing) cancelDismissDrag(animated = true)
                mode = NONE
                isScaling = false
            }
        }

        // Reached only by DOWN / POINTER_UP / UP / CANCEL — never during MOVE
        if (!isDismissing) imageMatrix = matrixScale
        return true
    }

    private fun handleMove(event: MotionEvent) {
        if (mode != DRAG || isScaling) return

        val dx = event.x - last.x
        val dy = event.y - last.y

        when {
            // ── Already in dismiss drag ──────────────────────────────────────
            isDismissing -> {
                dismissDragY = (dismissDragY + dy).coerceAtLeast(0f)
                dismissDragX += dx * 0.4f
                applyDismissTransform()
                last.set(event.x, event.y)
            }

            // ── Candidate for dismiss: not zoomed, moving downward ───────────
            isAtMinScale() && dy > abs(dx) * 1.2f -> {
                // Accumulate silently until we cross the slop threshold (8px).
                // Once crossed, commit to dismiss mode and stop touching the matrix.
                dismissDragY += dy
                if (dismissDragY > 3f) {
                    isDismissing = true
                    // Hard-freeze the matrix so it never gets reassigned again
                    // during this gesture — this is what eliminates the flicker.
                    imageMatrix = matrixScale
                    applyDismissTransform()
                }
                // Do NOT update `last` here on the pre-commit frames — we don't
                // want any pan delta leaking into the matrix before we commit.
                // Once committed last will be updated next frame via the top branch.
                last.set(event.x, event.y)
            }

            // ── Normal pan ───────────────────────────────────────────────────
            else -> {
                matrixScale.postTranslate(dx, dy)
                fixTranslation()
                last.set(event.x, event.y)
            }
        }
    }

    // ─── Dismiss helpers ──────────────────────────────────────────────────────

    private fun isAtMinScale() = getCurrentScale() <= minScale * 1.08f

    private fun applyDismissTransform() {
        val progress = (dismissDragY / dismissThreshold).coerceIn(0f, 1.2f)
        val viewScale = 1f - (progress.coerceAtMost(1f) * 0.22f)
        scaleX = viewScale
        scaleY = viewScale
        translationY = dismissDragY
        translationX = dismissDragX
        onDismissProgress?.invoke(progress.coerceAtMost(1f))
    }

    private fun cancelDismissDrag(animated: Boolean) {
        isDismissing = false
        if (animated) {
            val fromY = dismissDragY
            val fromX = dismissDragX
            val fromScale = scaleX
            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 280
                interpolator = DecelerateInterpolator(2f)
                addUpdateListener { va ->
                    val t = va.animatedValue as Float   // 1 → 0
                    translationY = fromY * t
                    translationX = fromX * t
                    val s = fromScale + (1f - fromScale) * (1f - t)
                    scaleX = s
                    scaleY = s
                    onDismissProgress?.invoke((fromY / dismissThreshold * t).coerceIn(0f, 1f))
                }
                start()
            }
        } else {
            translationY = 0f
            translationX = 0f
            scaleX = 1f
            scaleY = 1f
            onDismissProgress?.invoke(0f)
        }
        dismissDragY = 0f
        dismissDragX = 0f
    }

    private fun animateDismiss() {
        val fromY = translationY
        val fromX = translationX
        val fromScale = scaleX
        val toY = height.toFloat() * 1.5f
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = DecelerateInterpolator(1.2f)
            addUpdateListener { va ->
                val t = va.animatedValue as Float
                translationY = fromY + (toY - fromY) * t
                translationX = fromX
                val s = fromScale * (1f - t * 0.15f)
                scaleX = s
                scaleY = s
                onDismissProgress?.invoke((1f - t).coerceIn(0f, 1f))
            }
            doOnEnd {
                isDismissing = false
                onDismiss?.invoke()
            }
            start()
        }
    }

    private fun ValueAnimator.doOnEnd(block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = block()
        })
    }

    // ─── Double-tap ───────────────────────────────────────────────────────────

    private fun handleTap(event: MotionEvent) {
        if (awaitingSecondTap) {
            val dx = event.x - lastTapX
            val dy = event.y - lastTapY
            if (dx * dx + dy * dy <= doubleTapSlopPx * doubleTapSlopPx) {
                doubleTapHandler.removeCallbacksAndMessages(null)
                awaitingSecondTap = false
                onDoubleTap(event.x, event.y)
            } else {
                armFirstTap(event)
            }
        } else {
            armFirstTap(event)
        }
    }

    private fun armFirstTap(event: MotionEvent) {
        doubleTapHandler.removeCallbacksAndMessages(null)
        awaitingSecondTap = true
        lastTapX = event.x
        lastTapY = event.y
        doubleTapHandler.postDelayed({ awaitingSecondTap = false }, doubleTapMaxDelay)
    }

    private fun onDoubleTap(x: Float, y: Float) {
        val currentScale = getCurrentScale()
        val targetScale = zoomSteps[doubleTapZoomStep] * minScale
        animateZoom(currentScale, targetScale, x, y)
        doubleTapZoomStep = (doubleTapZoomStep + 1) % zoomSteps.size
    }

    // ─── Fit / translate / scale helpers ─────────────────────────────────────

    private fun fitToScreen() {
        drawable?.let { d ->
            val vw = width.toFloat()
            val vh = height.toFloat()
            if (vw == 0f || vh == 0f) return
            val scale = min(vw / d.intrinsicWidth, vh / d.intrinsicHeight)
            minScale = scale.coerceAtMost(maxScale)
            matrixScale.setScale(scale, scale)
            matrixScale.postTranslate(
                (vw - scale * d.intrinsicWidth) / 2f,
                (vh - scale * d.intrinsicHeight) / 2f
            )
            imageMatrix = matrixScale
        }
    }

    private fun fixTranslation() {
        val rect = getMatrixRect()
        matrixScale.postTranslate(
            getFixTranslation(rect.left, rect.right, width.toFloat()),
            getFixTranslation(rect.top, rect.bottom, height.toFloat())
        )
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
            minEdge > 0f -> -minEdge
            maxEdge < viewSize -> viewSize - maxEdge
            else -> 0f
        }
    }

    private fun getCurrentScale(): Float {
        matrixScale.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    // ─── Pinch-to-zoom ────────────────────────────────────────────────────────

    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val cur = getCurrentScale()
            val safeMin = rubberBandMinScale.coerceAtMost(maxScale)
            val next = (cur * detector.scaleFactor).coerceIn(safeMin, maxScale)
            matrixScale.postScale(next / cur, next / cur, detector.focusX, detector.focusY)
            fixTranslation()
            imageMatrix = matrixScale
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            val cur = getCurrentScale()
            if (cur < minScale) animateZoom(cur, minScale, width / 2f, height / 2f, 250)
        }
    }

    // ─── Zoom animation ───────────────────────────────────────────────────────

    private var currentAnimator: ValueAnimator? = null

    private fun animateZoom(
        from: Float, to: Float,
        focusX: Float, focusY: Float,
        duration: Long = 280
    ) {
        currentAnimator?.cancel()
        currentAnimator = ValueAnimator.ofFloat(from, to).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                val s = it.animatedValue as Float
                val factor = s / getCurrentScale()
                matrixScale.postScale(factor, factor, focusX, focusY)
                fixTranslation()
                imageMatrix = matrixScale
            }
            start()
        }
    }

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}