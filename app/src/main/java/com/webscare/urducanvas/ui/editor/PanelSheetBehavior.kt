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

class PanelSheetBehavior(
    private val root: ConstraintLayout,
    private val guideline: Guideline,
    private val dragHandleView: View,
    private val collapsedPx: Int,
    private val expandedPx: Int,
    private val onSlide: (Float) -> Unit,
    private val onStateSettled: (expanded: Boolean) -> Unit
) {
    private var isExpanded = false
    private var springAnim: SpringAnimation? = null
    private var velocityTracker: VelocityTracker? = null
    private var dragStartRawY = 0f
    private var dragStartGuideBegin = 0
    private var isDragging = false

    private var extDragStartRawY = 0f
    private var extDragStartGuideBegin = 0
    private var extDragLastY = 0f
    private var extDragVelocity = 0f
    private var extDragLastT = 0L

    // ── Guideline ─────────────────────────────────────────────────────────────

    private var currentGuideBegin: Int
        get() {
            val lp = guideline.layoutParams as ConstraintLayout.LayoutParams
            return if (lp.guideBegin >= 0) lp.guideBegin else collapsedPx
        }
        set(value) {
            val lp = guideline.layoutParams as ConstraintLayout.LayoutParams
            lp.guideBegin   = value
            lp.guidePercent = -1f
            lp.guideEnd     = -1
            guideline.layoutParams = lp
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

    private fun springTo(targetPx: Int, startVelocity: Float = 0f) {
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
            setStartVelocity(startVelocity)
            spring = SpringForce(targetPx.toFloat()).apply {
                stiffness    = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            }
            addUpdateListener { _, value, _ ->
                currentGuideBegin = value.toInt().coerceIn(expandedPx, collapsedPx)
            }
            addEndListener { _, _, _, _ ->
                currentGuideBegin = targetPx
                isExpanded = targetPx == expandedPx
                onStateSettled(isExpanded)
                releaseVelocityTracker()
            }
            start()
        }
    }

    // ── Snap decision ─────────────────────────────────────────────────────────
    //
    // Key insight: progress = 0 means fully collapsed, 1 means fully expanded.
    //
    // When EXPANDING (isExpanded == false): snap expanded if progress > 0.15
    //   → drag only 15% of travel up = commits
    //
    // When COLLAPSING (isExpanded == true): snap collapsed if progress < 0.85
    //   → drag only 15% of travel down = commits  (NOT 85% down!)
    //
    // This makes both directions equally easy with a short drag.
    // No fling/velocity check — position only.

    private fun shouldSnapExpanded(): Boolean {
        val travel = (collapsedPx - expandedPx).toFloat()
        if (travel <= 0f) return isExpanded
        val progress = ((collapsedPx - currentGuideBegin) / travel).coerceIn(0f, 1f)
        return if (isExpanded) {
            progress > 0.85f   // already expanded: stay expanded unless dragged 15%+ down
        } else {
            progress > 0.15f   // collapsed: expand if dragged 15%+ up
        }
    }

    // ── Drag handle touch ─────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        dragHandleView.setOnTouchListener { _, event -> onTouch(event) }

        root.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val panelTop = getPanelTopY()
                    if (event.rawY in panelTop..(panelTop + DRAG_ZONE_PX)) {
                        onTouch(event)
                    } else false
                }
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (velocityTracker != null) onTouch(event) else false
                }
                else -> false
            }
        }
    }

    private fun getPanelTopY(): Float {
        val loc = IntArray(2)
        root.getLocationInWindow(loc)
        return (loc[1] + currentGuideBegin).toFloat()
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
                val dy = event.rawY - dragStartRawY
                if (!isDragging && abs(dy) < 4f) return true
                isDragging = true
                currentGuideBegin = (dragStartGuideBegin + dy.toInt()).coerceIn(expandedPx, collapsedPx)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val expand = if (!isDragging) !isExpanded else shouldSnapExpanded()
                springTo(if (expand) expandedPx else collapsedPx)
                isDragging = false
                return true
            }
        }
        return false
    }

    // ── External drag API (called from FontsListFragment RV swipe) ────────────

    // downRawY  = rawY at ACTION_DOWN — anchor so slop distance isn't wasted
    // currentRawY = rawY right now at takeover moment
    fun externalDragBegin(downRawY: Float, currentRawY: Float = downRawY) {
        springAnim?.cancel()
        extDragStartRawY       = downRawY
        extDragStartGuideBegin = currentGuideBegin
        dragStartRawY          = downRawY          // used by shouldSnapExpanded via currentGuideBegin
        dragStartGuideBegin    = extDragStartGuideBegin
        extDragLastY           = currentRawY
        extDragLastT           = System.nanoTime()
        extDragVelocity        = 0f
    }

    fun externalDragBy(rawY: Float) {
        val now = System.nanoTime()
        val dt  = (now - extDragLastT) / 1_000_000_000f
        if (dt > 0f) {
            val instant = (rawY - extDragLastY) / dt
            extDragVelocity = extDragVelocity * 0.6f + instant * 0.4f
        }
        extDragLastY = rawY
        extDragLastT = now
        val dy = rawY - extDragStartRawY
        currentGuideBegin = (extDragStartGuideBegin + dy.toInt()).coerceIn(expandedPx, collapsedPx)
    }

    fun externalDragEnd() {
        val expand = shouldSnapExpanded()
        springTo(if (expand) expandedPx else collapsedPx, startVelocity = extDragVelocity)
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

    /**
     * Register an additional view as a drag handle.
     * Useful for top-toolbars / header areas that should also drag the panel.
     * Safe to call multiple times with different views.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attachAdditionalHandle(view: View) {
        view.setOnTouchListener { _, event -> onTouch(event) }
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

    companion object {
        private const val DRAG_ZONE_PX = 200
    }
}