package com.webscare.urducanvas.common.views

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.model.CanvasElement

class CanvasElementManager(private val view: CanvasStateAccess) {

    /**
     * Calculates the combined bounding box for all currently selected elements.
     * Returns an empty RectF if no elements are selected.
     */
    fun getCombinedSelectedBounds(): RectF {
        val drawableSelected = view.selectedElements.filter { it.type != ElementType.GROUP }
        if (drawableSelected.isEmpty()) return RectF()

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        drawableSelected.forEach { element ->
            val corners = element.getRotatedCorners()
            for (i in corners.indices step 2) {
                val x = corners[i]
                val y = corners[i + 1]
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }

        return RectF(minX, minY, maxX, maxY)
    }

    /** Returns a straight rectangular path around all selected rotated elements. */
    fun getGroupRotatedBounds(): FloatArray {
        val allPoints = mutableListOf<Float>()
        view.selectedElements.forEach { el ->
            allPoints.addAll(el.getRotatedCorners().toList())
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (i in allPoints.indices step 2) {
            val x = allPoints[i]
            val y = allPoints[i + 1]
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }

        return floatArrayOf(minX, minY, maxX, maxY)
    }

    /** Returns a non-rotated rectangular path that covers all selected elements. */
    fun getGroupRotatedPath(): Path? {
        if (view.selectedElements.size <= 1) return null

        val b = getGroupRotatedBounds()
        return Path().apply {
            moveTo(b[0], b[1])
            lineTo(b[2], b[1])
            lineTo(b[2], b[3])
            lineTo(b[0], b[3])
            close()
        }
    }

    fun getSelectionPath(): Path? {
        if (view.selectedElements.isEmpty()) return null
        if (view.selectedElements.size == 1) {
            val c = view.selectedElements.first().getRotatedCorners()
            return Path().apply {
                moveTo(c[0], c[1])
                lineTo(c[2], c[3])
                lineTo(c[4], c[5])
                lineTo(c[6], c[7])
                close()
            }
        }
        // Multi-selection -> fallback to axis aligned
        val b = getCombinedSelectedBounds()
        return Path().apply {
            addRect(b, Path.Direction.CW)
        }
    }

    fun getGroupTrueBounds(): FloatArray {
        if (view.selectedElements.isEmpty()) return floatArrayOf(0f, 0f, 0f, 0f)

        val allPoints = mutableListOf<Float>()
        view.selectedElements.forEach { el ->
            allPoints.addAll(el.getRotatedCorners().toList())
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (i in allPoints.indices step 2) {
            val x = allPoints[i]
            val y = allPoints[i + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }

        return floatArrayOf(minX, minY, maxX, maxY)
    }

    fun CanvasElement.containsPoint(px: Float, py: Float): Boolean {
        val bounds = getTightTextBounds()
        val corners = floatArrayOf(
            bounds.left, bounds.top,
            bounds.right, bounds.top,
            bounds.right, bounds.bottom,
            bounds.left, bounds.bottom
        )
        val m = Matrix().apply {
            postScale(
                scale * if (isFlippedX) -1f else 1f,
                scale * if (isFlippedY) -1f else 1f
            )
            postRotate(rotation)
            postTranslate(x, y)
        }
        m.mapPoints(corners)
        return pointInPolygon(px, py, corners)
    }

    private fun pointInPolygon(px: Float, py: Float, pts: FloatArray): Boolean {
        var result = false
        var j = pts.size - 2
        for (i in pts.indices step 2) {
            val xi = pts[i]
            val yi = pts[i + 1]
            val xj = pts[j]
            val yj = pts[j + 1]
            val intersect = ((yi > py) != (yj > py)) && (px < (xj - xi) * (py - yi) / (yj - yi) + xi)
            if (intersect) result = !result
            j = i
        }
        return result
    }
}
