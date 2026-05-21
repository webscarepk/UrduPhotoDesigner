package com.webscare.urducanvas.ui.editor

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Guideline
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs

/**
 * PanelSheetBehavior
 *
 * Drives the panel by moving centerGuide.guideBegin in pixels every frame.
 * This means the ConstraintLayout resizes the panel height in real time —
 * the panel grows/shrinks as the user drags. No translationY, no clipping.
 *
 * Collapsed : guideBegin = collapsedPx  (65% of screen height)
 * Expanded  : guideBegin = expandedPx   (just below editor header)
 *
 * [onSlide]        — 0f..1f every frame, used for header crossfade etc.
 * [onStateSettled] — true/false when spring finishes snapping.
 */
class PanelSheetBehavior(
    private val root: ConstraintLayout,
    private val guideline: Guideline,
    private val dragHandleView: View,
    private val collapsedPx: Int,   // guideBegin when collapsed
    private val expandedPx: Int,    // guideBegin when expanded (smaller number = higher up)
    private val onSlide: (Float) -> Unit,
    private val onStateSettled: (expanded: Boolean) -> Unit
) {
    // ── State ─────────────────────────────────────────────────────────────────

    private var isExpanded = false
    private var springAnim: SpringAnimation? = null
    private var velocityTracker: VelocityTracker? = null
    private var dragStartRawY = 0f
    private var dragStartGuideBegin = 0
    private var isDragging = false

    // ── Guideline access ──────────────────────────────────────────────────────

    private var currentGuideBegin: Int
        get() {
            val lp = guideline.layoutParams as ConstraintLayout.LayoutParams
            return if (lp.guideBegin >= 0) lp.guideBegin else collapsedPx
        }
        set(value) {
            val lp = guideline.layoutParams as ConstraintLayout.LayoutParams
            lp.guideBegin  = value
            lp.guidePercent = -1f   // switch to absolute mode, disable percent
            lp.guideEnd    = -1
            guideline.layoutParams = lp
            // ConstraintLayout will re-measure panel height automatically
            emitSlide(value)
        }

    private fun emitSlide(guideBegin: Int) {
        val travel = (collapsedPx - expandedPx).toFloat()
        val offset = if (travel > 0f)
            ((collapsedPx - guideBegin) / travel).coerceIn(0f, 1f)
        else 0f
        onSlide(offset)
    }

    // ── Spring ────────────────────────────────────────────────────────────────

    private fun springTo(targetPx: Int) {
        springAnim?.cancel()
        val startPx = currentGuideBegin
        if (startPx == targetPx) {
            isExpanded = targetPx == expandedPx
            onStateSettled(isExpanded)
            return
        }

        val holder = FloatValueHolder(startPx.toFloat())
        springAnim = SpringAnimation(holder).apply {
            setStartValue(startPx.toFloat())
            // Velocity: finger moving up (negative rawY delta) = guideBegin decreasing
            // VelocityTracker gives px/sec in screen coords — invert for guide direction
            velocityTracker?.computeCurrentVelocity(1000)
            val vy = velocityTracker?.yVelocity ?: 0f
            setStartVelocity(vy)   // positive vy = drag down = guide increases = collapse

            spring = SpringForce(targetPx.toFloat()).apply {
                stiffness    = SpringForce.STIFFNESS_MEDIUM        // 300 — snappy
                dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY // slight overshoot
            }
            addUpdateListener { _, value, _ ->
                currentGuideBegin = value.toInt().coerceIn(expandedPx, collapsedPx)
            }
            addEndListener { _, _, _, _ ->
                currentGuideBegin = targetPx   // land exactly
                isExpanded = targetPx == expandedPx
                onStateSettled(isExpanded)
                releaseVelocityTracker()
            }
            start()
        }
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        dragHandleView.setOnTouchListener { _, event -> onTouch(event) }
    }

    private fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                springAnim?.cancel()
                dragStartRawY       = event.rawY
                dragStartGuideBegin = currentGuideBegin
                isDragging          = false
                acquireVelocityTracker(event)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dy = event.rawY - dragStartRawY   // positive = finger moving down

                if (!isDragging && abs(dy) < 8f) return true
                isDragging = true

                // Dragging up (dy < 0) → guideBegin decreases → panel top moves up → panel grows
                val raw = dragStartGuideBegin + dy.toInt()
                currentGuideBegin = raw.coerceIn(expandedPx, collapsedPx)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.computeCurrentVelocity(1000)
                val vy = velocityTracker?.yVelocity ?: 0f   // positive = moving down

                val snapExpanded = when {
                    !isDragging           -> !isExpanded          // tap = toggle
                    vy < -600f            -> true                 // fast fling up
                    vy >  600f            -> false                // fast fling down
                    else                  -> currentGuideBegin < (collapsedPx + expandedPx) / 2
                }

                springTo(if (snapExpanded) expandedPx else collapsedPx)
                isDragging = false
                return true
            }
        }
        return false
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun snapTo(expanded: Boolean, immediate: Boolean = false) {
        val target = if (expanded) expandedPx else collapsedPx
        if (immediate) {
            springAnim?.cancel()
            currentGuideBegin = target
            isExpanded = expanded
            onStateSettled(expanded)
        } else {
            springTo(target)
        }
    }

    fun isCurrentlyExpanded() = isExpanded

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun acquireVelocityTracker(event: MotionEvent) {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        else velocityTracker!!.clear()
        velocityTracker!!.addMovement(event)
    }

    private fun releaseVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }
}