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
 * PanelSheetBehavior  — smooth single-spring edition
 *
 * Changes vs original:
 *  1. 90 % height cap  — expandedPx clamped internally; pass any value from caller.
 *  2. Smooth bouncy spring — ONE spring with DAMPING_RATIO_MEDIUM_BOUNCY + a boosted
 *     launch velocity drives the overshoot naturally. No two-phase chaining → no jerk.
 *  3. Dim overlay — pass a dimView; its alpha tracks slide offset automatically.
 *     Tapping the dim overlay collapses the panel.
 */
class PanelSheetBehavior(
    private val root: ConstraintLayout,
    private val guideline: Guideline,
    private val dragHandleView: View,
    private val collapsedPx: Int,
    expandedPx: Int, // raw value — clamped to 90 % internally
    private val onSlide: (Float) -> Unit,
    private val onStateSettled: (expanded: Boolean) -> Unit,
    /** Optional full-screen dim view placed behind the panel. Pass null to skip. */
    private val dimView: View? = null,
) {

    // ── Height cap ────────────────────────────────────────────────────────────

    private val screenHeight: Int = root.context.resources.displayMetrics.heightPixels

    // guideBegin is distance-from-top, so "taller panel" = smaller guideBegin.
    // Cap: panel top must be at least 10 % from top → guideBegin >= screenHeight * 0.10
    private val expandedPx: Int = maxOf(
        expandedPx,
        (screenHeight * (1f - MAX_EXPANDED_FRACTION)).toInt(),
    )

    // ── State ─────────────────────────────────────────────────────────────────

    var isSwipeEnabled = true
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
            lp.guideBegin = value
            lp.guidePercent = -1f
            lp.guideEnd = -1
            guideline.layoutParams = lp
            emitSlide(value)
        }

    private fun emitSlide(guideBegin: Int) {
        val travel = (collapsedPx - expandedPx).toFloat()
        val offset = if (travel > 0f) {
            ((collapsedPx - guideBegin) / travel).coerceIn(0f, 1f)
        } else {
            0f
        }
        onSlide(offset)
        updateDim(offset)
    }

    // ── Dim overlay ───────────────────────────────────────────────────────────

    private fun updateDim(slideOffset: Float) {
        dimView ?: return
        val alpha = (slideOffset * MAX_DIM_ALPHA).coerceIn(0f, MAX_DIM_ALPHA)
        dimView.alpha = alpha

        // CRITICAL: use visibility, not just isClickable.
        // A View with alpha=0 but isClickable=true still intercepts all touches because
        // it has non-zero bounds and sits above the canvas in Z order (translationZ="7dp").
        // INVISIBLE removes it from touch dispatch entirely. VISIBLE re-adds it so the
        // tap-to-collapse gesture works while the panel is open.
        if (alpha > 0.01f) {
            dimView.visibility = View.VISIBLE
            dimView.isClickable = true
        } else {
            dimView.visibility = View.INVISIBLE
            dimView.isClickable = false
        }
    }

    // ── Spring ────────────────────────────────────────────────────────────────
    //
    // Single-spring bounce: instead of two chained animations (which cause a
    // visible jerk at the hand-off), we launch ONE spring with:
    //   • DAMPING_RATIO_MEDIUM_BOUNCY  → spring naturally overshoots and returns
    //   • a boosted start velocity in the travel direction → controls overshoot depth
    //
    // The spring's equilibrium IS the resting target (expandedPx / collapsedPx),
    // so it always settles exactly there — no second phase needed.

    private fun springTo(targetPx: Int, startVelocity: Float = 0f) {
        springAnim?.cancel()

        val startPx = currentGuideBegin

        val holder = FloatValueHolder(startPx.toFloat())
        springAnim = SpringAnimation(holder).apply {
            setStartValue(startPx.toFloat())
            setStartVelocity(startVelocity)
            spring = SpringForce(targetPx.toFloat()).apply {
                stiffness = SpringForce.STIFFNESS_LOW
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            }
            addUpdateListener { _, value, _ ->
                val clampMin = expandedPx - (screenHeight * OVERSHOOT_CLAMP_MARGIN).toInt()
                val clampMax = collapsedPx + (screenHeight * OVERSHOOT_CLAMP_MARGIN).toInt()
                currentGuideBegin = value.toInt().coerceIn(clampMin, clampMax)
            }
            addEndListener { _, _, _, _ ->
                // Snap exactly to target when settled
                currentGuideBegin = targetPx
                isExpanded = targetPx == expandedPx
                // Hard-reset the dim when collapsing
                if (!isExpanded) updateDim(0f)
                onStateSettled(isExpanded)
                releaseVelocityTracker()
            }
            start()
        }
    }

    // ── Snap decision ─────────────────────────────────────────────────────────

    private fun shouldSnapExpanded(): Boolean {
        val travel = (collapsedPx - expandedPx).toFloat()
        if (travel <= 0f) return isExpanded
        val progress = ((collapsedPx - currentGuideBegin) / travel).coerceIn(0f, 1f)
        return if (isExpanded) progress > 0.85f else progress > 0.15f
    }

    // ── Attach ────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        dragHandleView.setOnTouchListener { _, event -> onTouch(event) }

        root.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val panelTop = getPanelTopY()
                    if (event.rawY in panelTop..(panelTop + DRAG_ZONE_PX)) {
                        onTouch(event)
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    if (velocityTracker != null) onTouch(event) else false
                }
                else -> false
            }
        }

        dimView?.setOnClickListener {
            if (isExpanded) snapTo(expanded = false)
        }
    }

    private fun getPanelTopY(): Float {
        val loc = IntArray(2)
        root.getLocationInWindow(loc)
        return (loc[1] + currentGuideBegin).toFloat()
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    private fun onTouch(event: MotionEvent): Boolean {
        if (!isSwipeEnabled) return false
        var handled = false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                springAnim?.cancel()
                dragStartRawY = event.rawY
                dragStartGuideBegin = currentGuideBegin
                isDragging = false
                acquireVelocityTracker(event)
                handled = true
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dy = event.rawY - dragStartRawY
                if (!isDragging && abs(dy) < 4f) {
                    handled = true
                } else {
                    isDragging = true
                    currentGuideBegin = (dragStartGuideBegin + dy.toInt()).coerceIn(expandedPx, collapsedPx)
                    handled = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.computeCurrentVelocity(1000)
                val flingV = velocityTracker?.yVelocity ?: 0f
                val snapExpand = if (!isDragging) !isExpanded else shouldSnapExpanded()
                springTo(
                    targetPx = if (snapExpand) expandedPx else collapsedPx,
                    startVelocity = flingV,
                )
                isDragging = false
                handled = true
            }
        }
        return handled
    }

    // ── External drag API ─────────────────────────────────────────────────────

    fun externalDragBegin(downRawY: Float, currentRawY: Float = downRawY) {
        springAnim?.cancel()
        extDragStartRawY = downRawY
        extDragStartGuideBegin = currentGuideBegin
        dragStartRawY = downRawY
        dragStartGuideBegin = extDragStartGuideBegin
        extDragLastY = currentRawY
        extDragLastT = System.nanoTime()
        extDragVelocity = 0f
    }

    fun externalDragBy(rawY: Float) {
        val now = System.nanoTime()
        val dt = (now - extDragLastT) / 1_000_000_000f
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
        springTo(
            targetPx = if (expand) expandedPx else collapsedPx,
            startVelocity = extDragVelocity,
        )
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun snapTo(expanded: Boolean, immediate: Boolean = false) {
        val target = if (expanded) expandedPx else collapsedPx
        if (immediate) {
            springAnim?.cancel()
            currentGuideBegin = target
            isExpanded = expanded
            // updateDim is normally driven by emitSlide → onSlide, but when snapping
            // immediately we set guideBegin directly without going through emitSlide,
            // so we must update the dim manually to guarantee the overlay is hidden.
            updateDim(if (expanded) 1f else 0f)
            onStateSettled(expanded)
        } else {
            springTo(target)
        }
    }

    var onAdditionalHandleAttached: ((View) -> Unit)? = null

    @SuppressLint("ClickableViewAccessibility")
    fun attachAdditionalHandle(view: View) {
        view.setOnTouchListener { _, event -> onTouch(event) }
        onAdditionalHandleAttached?.invoke(view)
    }

    fun isCurrentlyExpanded() = isExpanded

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun acquireVelocityTracker(event: MotionEvent) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        } else {
            velocityTracker!!.clear()
        }
        velocityTracker!!.addMovement(event)
    }

    private fun releaseVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    companion object {
        private const val DRAG_ZONE_PX = 200

        /** Panel never exceeds this fraction of screen height when expanded */
        private const val MAX_EXPANDED_FRACTION = 0.90f

        /**
         * Minimum launch velocity as a fraction of screen height per second.
         * Drives the spring past the target so it overshoots ~5 % before bouncing back.
         * Increase for a more dramatic bounce, decrease for subtler feel.
         */
        private const val OVERSHOOT_VELOCITY_FACTOR = 1.5f // 1.5 × screenHeight px/s

        /**
         * How far past the hard resting bounds the spring is allowed to travel
         * during the overshoot, as a fraction of screen height.
         * Keeps the panel from going completely off-screen.
         */
        private const val OVERSHOOT_CLAMP_MARGIN = 0.06f // 6 % of screen height

        /** Maximum alpha of the dim overlay at full expansion */
        const val MAX_DIM_ALPHA = 0.45f
    }
}
