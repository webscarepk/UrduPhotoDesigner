package com.webscare.urducanvas.common.utils

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

/**
 * A GridLayoutManager that morphs between collapsed (horizontal strip, many columns) and
 * expanded (vertical grid, few columns) states without ever being swapped out.
 *
 * [applyFraction] is the single entry point called synchronously on every touch frame.
 * It updates span count and scroll orientation in-place — no RecyclerView.swapAdapter(),
 * no notifyDataSetChanged(), no layout manager replacement.
 *
 * Span count interpolation:
 *   fraction = 0.0  →  [collapsedSpan] rows (horizontal strip)
 *   fraction = 1.0  →  [expandedSpan] columns (vertical grid)
 *
 * Orientation flip: switches at [orientationFlipThreshold] to avoid a jarring jump.
 * At the threshold, the span count is momentarily equal on both sides so items keep
 * their visual size across the orientation boundary.
 */
class MorphGridLayoutManager(
    context: Context,
    var collapsedSpan: Int = 3,   // rows in horizontal strip
    var expandedSpan: Int = 3,    // columns in vertical grid
    private val orientationFlipThreshold: Float = 0.95f
) : GridLayoutManager(context, collapsedSpan, HORIZONTAL, false) {

    // Track what we last set so we skip redundant requestLayout() calls
    private var currentSpan: Int = collapsedSpan
    private var currentOrientation: Int = HORIZONTAL

    // Exposed so the fragment can read it for item-size calculations
    var fraction: Float = 0f
        private set

    /**
     * Called synchronously on every touch frame with the panel drag progress.
     * [f] must be clamped to [0f, 1f] by the caller.
     *
     * Returns true if a layout pass was requested (span or orientation changed).
     */
    fun applyFraction(recycler: RecyclerView, f: Float): Boolean {
        fraction = f

        val targetOrientation = if (f < orientationFlipThreshold) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL
        val targetSpan = computeSpan(f, targetOrientation)

        var changed = false

        if (targetOrientation != currentOrientation) {
            currentOrientation = targetOrientation
            orientation = targetOrientation
            changed = true
        }

        if (targetSpan != currentSpan) {
            currentSpan = targetSpan
            spanCount = targetSpan
            changed = true
        }

        if (changed) recycler.requestLayout()
        return changed
    }

    /**
     * Interpolate span count.
     * Horizontal layout uses collapsedSpan rows.
     * Vertical layout uses expandedSpan columns.
     * Changes orientation and span count in a single step at the flip threshold.
     */
    private fun computeSpan(f: Float, orientation: Int): Int {
        return if (orientation == HORIZONTAL) collapsedSpan else expandedSpan
    }

    private fun lerp(a: Int, b: Int, t: Float): Int =
        (a + (b - a) * t).roundToInt().coerceAtLeast(1)

    // ── Scroll direction gates ───────────────────────────────────────────────
    // RecyclerView consults these before deciding whether to intercept touch events.
    // Mirror the actual orientation so swipe gestures work correctly throughout drag.

    override fun canScrollHorizontally(): Boolean = currentOrientation == HORIZONTAL
    override fun canScrollVertically(): Boolean   = currentOrientation == VERTICAL
}