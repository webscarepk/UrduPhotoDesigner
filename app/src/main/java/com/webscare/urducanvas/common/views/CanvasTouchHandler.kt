package com.webscare.urducanvas.common.views

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.BlurMaskFilter
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.webscare.urducanvas.common.canvas.enums.BrushStyle
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.Mode
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.canvas.model.StrokeData
import com.webscare.urducanvas.common.utils.Utils.vibrateSoft
import java.util.Objects
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class CanvasTouchHandler(private val view: CanvasView) {

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var initialElementRotations = mutableMapOf<String, Float>()
    private var initialElementPositionsRelativeToGroupPivot = mutableMapOf<String, Pair<Float, Float>>()
    private var initialAngle = 0f
    private var initialGroupPivotX = 0f
    private var initialGroupPivotY = 0f
    private var initialPinchDistance = 0f
    private var initialPinchAngle = 0f
    private var initialOverallScale = 1f
    private var pinchFocusX = 0f
    private var pinchFocusY = 0f
    private var initialOffsetXAtPinch = 0f
    private var initialOffsetYAtPinch = 0f
    private val resizeLastSignX = mutableMapOf<String, Float>()
    private val resizeLastSignY = mutableMapOf<String, Float>()
    private val resizeInitialScales = mutableMapOf<String, Float>()
    private var resizeStartDist = 0f
    private var touchedDownElement: CanvasElement? = null
    private var isDragCandidate = false
    private var suppressZoomCallback = false
    private var iconTouched: String? = null
    
    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
    private val gestureDetector = GestureDetector(view.context, GestureListener())

    private fun Float.dpToPx(): Float = this * view.resources.displayMetrics.density

    private fun getPinchDistance(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return hypot(x.toDouble(), y.toDouble()).toFloat()
    }

    private fun getPinchAngle(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
    }

    inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (isPanMode) {
                view.stepZoomOverall()
                return true
            }

            val (x, y) = view.screenToCanvas(e.x, e.y)

            val touchedElement =
                view.canvasElements.filter { !it.isLocked && it.type != ElementType.GROUP }
                    .sortedByDescending { it.zIndex }
                    .firstOrNull { element ->
                        val matrix = Matrix()
                        matrix.postTranslate(-element.x, -element.y)
                        matrix.postRotate(-element.rotation)
                        matrix.postScale(1f / element.view.scale, 1f / element.view.scale)

                        val touchPoint = floatArrayOf(x, y)
                        matrix.mapPoints(touchPoint)

                        val tightBounds = element.getTightTextBounds()
                        tightBounds.contains(touchPoint[0], touchPoint[1])
                    }

            if (touchedElement != null && view.currentMode == Mode.GROUP_EDIT && touchedElement.groupId == view.activeGroupId && touchedElement.type != ElementType.BACKGROUND) {
                view.canvasElements.forEach { it.isSelected = false }
                view.selectedElements.clear()
                touchedElement.isSelected = true
                view.selectedElements.add(touchedElement)
                view.onElementSelected?.invoke(view.selectedElements)
                view.onEditTextRequested?.invoke(touchedElement)
                view.invalidate()
                return true
            }
            if (touchedElement?.groupId != null) {
                view.activeGroupId = touchedElement.groupId
                // Enter GROUP_EDIT and immediately select the tapped child --
                // don't select all children, just the one that was double-tapped.
                view.canvasElements.forEach { it.isSelected = false }
                view.selectedElements.clear()
                touchedElement.isSelected = true
                view.selectedElements.add(touchedElement)
                view.currentMode = Mode.GROUP_EDIT
                view.onElementSelected?.invoke(view.selectedElements)
                view.onEditTextRequested?.invoke(touchedElement)
                view.invalidate()
                return true
            } else if (touchedElement != null) {
                view.canvasElements.forEach { it.isSelected = false }
                view.selectedElements.clear()
                touchedElement.isSelected = true
                view.selectedElements.add(touchedElement)
                view.onElementSelected?.invoke(view.selectedElements)
                view.onEditTextRequested?.invoke(touchedElement)
                view.invalidate()
                return true
            }

            view.stepZoomOverall()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val (x, y) = view.screenToCanvas(e.x, e.y)

            val touchedElement = view.canvasElements.filter { !it.isLocked } // ignore locked
                .sortedByDescending { it.zIndex }.firstOrNull { it.containsPoint(x, y) }

            if (touchedElement != null && touchedElement.type != ElementType.BACKGROUND) {
                // Resolve grouped child -> its children for canvas bounds,
                // but mark the sentinel selected so ViewModel counts it as 1.
                val groupId = touchedElement.groupId
                val sentinel = if (groupId != null) {
                    view.canvasElements.firstOrNull { it.type == ElementType.GROUP && it.id == groupId }
                } else {
                    null
                }
                val canvasItems: List<CanvasElement> = if (sentinel != null) {
                    view.canvasElements.filter { it.groupId == groupId } // children for bounds
                } else {
                    listOf(touchedElement)
                }

                if (!view.inSelectionMode) {
                    view.inSelectionMode = true
                    view.clearSelection()
                    canvasItems.forEach {
                        it.isSelected = true
                        view.selectedElements.add(it)
                    }
                    sentinel?.isSelected = true // mark sentinel for ViewModel only
                    vibrateSoft()
                    view.onRequestOpenLayers?.invoke()
                } else {
                    val alreadySelected = canvasItems.all { it.isSelected }
                    if (alreadySelected) {
                        canvasItems.forEach {
                            it.isSelected = false
                            view.selectedElements.remove(it)
                        }
                        sentinel?.isSelected = false
                        if (view.selectedElements.isEmpty()) {
                            view.inSelectionMode = false
                            view.onExitSelectionMode?.invoke()
                        }
                    } else {
                        canvasItems.forEach {
                            if (!it.isSelected) {
                                it.isSelected = true
                                view.selectedElements.add(it)
                            }
                        }
                        sentinel?.isSelected = true
                    }
                }
                // Report sentinel (or real element) to ViewModel so count = 1 per group
                val reportList = if (sentinel != null) listOf(sentinel) else view.selectedElements.toList()
                view.onElementSelected?.invoke(reportList)
                view.invalidate()
            } else {
                // Long press away from any art-board element → canvas options popup.
                val isOutsideArtboard = x < 0f || y < 0f || x > view.canvasWidth || y > view.canvasHeight
                if (isOutsideArtboard) {
                    vibrateSoft()
                    view.onCanvasLongPressed?.invoke(e.rawX, e.rawY)
                }
            }
        }
    }

    

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Only forward single-pointer events to GestureDetector.
        // Forwarding ACTION_POINTER_DOWN/UP causes TouchTarget double-recycle
        // on Android 14 (SDK 34), triggering IllegalStateException: already recycled once.
        val maskedAction = event.actionMasked
        if (maskedAction == MotionEvent.ACTION_DOWN ||
            maskedAction == MotionEvent.ACTION_MOVE ||
            maskedAction == MotionEvent.ACTION_UP ||
            maskedAction == MotionEvent.ACTION_CANCEL
        ) {
            gestureDetector.onTouchEvent(event)
        }

        val (x, y) = view.screenToCanvas(event.x, event.y)

        if (view.isDrawing) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.currentStrokePath = Path().apply {
                        moveTo(x, y)
                    }
                    view.currentStrokePoints.clear()
                    view.currentStrokePoints.add(x to y)

                    // Paint for live preview (scaled thickness)
                    view.currentStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = view.currentBrushColor
                        strokeWidth = view.currentBrushThickness // ✅ view.scale-aware preview
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        alpha = (view.currentBrushHardness * 255).toInt()

                        if (view.currentBrushStyle == BrushStyle.BRUSH) {
                            val blurRadius = max(0.1f, (1f - view.currentBrushHardness) * 25f)
                            maskFilter = try {
                                BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
                            } catch (e: IllegalArgumentException) {
                                Log.e("CanvasView", "Invalid blur radius: $blurRadius", e)
                                null
                            }
                        }

                        view.currentBrushGradient?.let {
                            shader =
                                createBackgroundGradientShader(
                                    it,
                                    width.toFloat(),
                                    height.toFloat(),
                                )
                        }
                    }

                    view.invalidate()
                }

                MotionEvent.ACTION_MOVE -> {
                    val clampedX = x.coerceIn(0f, view.canvasWidth.toFloat())
                    val clampedY = y.coerceIn(0f, view.canvasHeight.toFloat())

                    view.currentStrokePath?.lineTo(clampedX, clampedY)
                    view.currentStrokePoints.add(clampedX to clampedY)
                    view.invalidate()
                }

                MotionEvent.ACTION_UP -> {
                    view.currentStrokePath?.lineTo(x, y)

                    val path = view.currentStrokePath
                    if (path != null && view.activeSessionElement != null) {
                        // Store path in ABSOLUTE canvas coordinates — no normalization
                        val strokeData = StrokeData(
                            path = Path(path),
                            color = view.currentBrushColor,
                            thickness = view.currentBrushThickness,
                            hardness = view.currentBrushHardness,
                            style = view.currentBrushStyle,
                            gradient = view.currentBrushGradient,
                        )
                        view.activeSessionElement!!.drawStrokes?.add(strokeData)
                        view.onStrokeCompleted?.invoke(strokeData)
                    }

                    // Cleanup live preview
                    view.currentStrokePath = null
                    view.currentStrokePaint = null
                    view.currentStrokePoints.clear()
                    view.invalidate()
                }
            }

            return true
        }

        if (isColorPickerMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    view.pickerX = x.coerceIn(0f, view.canvasWidth.toFloat())
                    view.pickerY = y.coerceIn(0f, view.canvasHeight.toFloat())
                    view.isDraggingPicker = true
                    view.invalidate()
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (view.isDraggingPicker) {
                        val bmp = view.colorPickerBitmap
                        if (bmp != null && !bmp.isRecycled) {
                            val px = view.pickerX.roundToInt().coerceIn(0, bmp.width - 1)
                            val py = view.pickerY.roundToInt().coerceIn(0, bmp.height - 1)
                            val color = bmp.getPixel(px, py)
                            view.onColorPicked?.invoke(color)
                        }
                        view.isDraggingPicker = false
                        view.invalidate()
                    }
                    return true
                }
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    initialPinchDistance = getPinchDistance(event)
                    initialPinchAngle = getPinchAngle(event)
                    initialOverallScale = view.overallScale

                    // Capture the midpoint between the two fingers in screen coords.
                    // All subsequent CANVAS_PAN pinch-zoom frames use this as the
                    // fixed pivot so the canvas content under the fingers never moves.
                    pinchFocusX = (event.getX(0) + event.getX(1)) / 2f
                    pinchFocusY = (event.getY(0) + event.getY(1)) / 2f
                    initialOffsetXAtPinch = view.overallOffsetX
                    initialOffsetYAtPinch = view.overallOffsetY

                    when {
                        // Elements selected → element view.scale/rotate (pan mode OFF only)
                        view.selectedElements.isNotEmpty() && !isPanMode -> {
                            view.currentMode = Mode.MULTI_TOUCH
                            initialScale = view.selectedElements.firstOrNull()?.view.scale ?: 1f
                            initialRotation = view.selectedElements.firstOrNull()?.rotation ?: 0f
                        }
                        // Pan locked — block two-finger zoom/pan
                        isCanvasPanLocked -> { /* consume but do nothing */ }
                        // Empty canvas (ya pan mode ON) → overall canvas zoom
                        else -> {
                            view.currentMode = Mode.CANVAS_PAN
                        }
                    }
                }
            }

            MotionEvent.ACTION_DOWN -> {
                view.iconTouched = null
                lastTouchedElement = null
                showVerticalGuide = false
                showHorizontalGuide = false
                showRotationVerticalGuide = false
                showRotationHorizontalGuide = false

                if (view.currentMode == Mode.GROUP_EDIT && view.activeGroupId != null) {
                    // Check if the tap is within the combined bounds of the group
                    val groupChildren = view.canvasElements.filter { it.groupId == view.activeGroupId }
                    val groupBounds = run {
                        var minX = Float.MAX_VALUE
                        var minY = Float.MAX_VALUE
                        var maxX = -Float.MAX_VALUE
                        var maxY = -Float.MAX_VALUE
                        groupChildren.forEach { el ->
                            el.getRotatedCorners().toList().chunked(2).forEach { (cx, cy) ->
                                if (cx < minX) minX = cx
                                if (cx > maxX) maxX = cx
                                if (cy < minY) minY = cy
                                if (cy > maxY) maxY = cy
                            }
                        }
                        RectF(minX, minY, maxX, maxY)
                    }

                    val tapInsideGroup = groupBounds.contains(x, y)

                    if (!tapInsideGroup) {
                        // Tap outside group bounds -- exit GROUP_EDIT completely
                        view.currentMode = Mode.NONE
                        view.activeGroupId = null
                        view.canvasElements.forEach { it.isSelected = false }
                        view.selectedElements.clear()
                        view.onElementSelected?.invoke(view.selectedElements)
                        // Don't return -- let the tap fall through to select whatever is there
                    } else {
                        // ── Icon check FIRST — rotate/resize/delete/edit handles ─────────
                        // If a child is already selected, its handles are drawn and must
                        // respond to touch before we do any hit-test on children below.
                        if (view.selectedElements.isNotEmpty()) {
                            val touchedIconEntry = view.lastDrawnIconRect.entries
                                .firstOrNull { (_, rect) -> rect.contains(x, y) }
                            if (touchedIconEntry != null) {
                                view.iconTouched = touchedIconEntry.key
                                when (view.iconTouched) {
                                    "delete" -> {
                                        view.removeSelectedElement()
                                        return true
                                    }
                                    "rotate" -> {
                                        view.currentMode = Mode.ROTATE
                                        touchStartX = x
                                        touchStartY = y
                                        view.isRotating = true
                                        initialElementRotations.clear()
                                        initialElementPositionsRelativeToGroupPivot.clear()
                                        val bounds = getCombinedSelectedBounds()
                                        initialGroupPivotX = bounds.centerX()
                                        initialGroupPivotY = bounds.centerY()
                                        view.selectedElements.forEach { el ->
                                            initialElementRotations[el.id] = el.rotation
                                            initialElementPositionsRelativeToGroupPivot[el.id] =
                                                Pair(el.x - initialGroupPivotX, el.y - initialGroupPivotY)
                                        }
                                        initialAngle = atan2(
                                            touchStartY - initialGroupPivotY,
                                            touchStartX - initialGroupPivotX,
                                        )
                                        view.selectedElements.firstOrNull()?.let {
                                            onStartBatchUpdate?.invoke(it.id, "rotate")
                                        }
                                        return true
                                    }
                                    "resize" -> {
                                        view.currentMode = Mode.RESIZE
                                        touchStartX = x
                                        touchStartY = y
                                        val combined = getCombinedSelectedBounds()
                                        val pivotX = combined.centerX()
                                        val pivotY = combined.centerY()
                                        resizeStartDist = hypot(x - pivotX, y - pivotY)
                                        view.selectedElements.forEach { el ->
                                            resizeLastSignX[el.id] = (touchStartX - pivotX).sign
                                            resizeLastSignY[el.id] = (touchStartY - pivotY).sign
                                            resizeInitialScales[el.id] = el.view.scale
                                            onStartBatchUpdate?.invoke(el.id, "resize")
                                        }
                                        return true
                                    }
                                    "edit" -> {
                                        if (view.selectedElements.size == 1) {
                                            view.onEditTextRequested?.invoke(view.selectedElements.first())
                                        }
                                        return true
                                    }
                                    "transform" -> {
                                        view.currentMode = Mode.TRANSFORM
                                        touchStartX = x
                                        touchStartY = y
                                        view.selectedElements.forEach { el ->
                                            initialElementSizes[el.id] = Pair(
                                                el.logicalContentWidth,
                                                el.logicalContentHeight,
                                            )
                                            onStartBatchUpdate?.invoke(el.id, "transform")
                                        }
                                        return true
                                    }
                                }
                            }
                        }

                        // ── No icon hit — do child hit-test ──────────────────────────────
                        val hitChild = groupChildren.filter { !it.isLocked }
                            .sortedByDescending { it.zIndex }.firstOrNull { element ->
                                val matrix = Matrix().apply {
                                    postTranslate(-element.x, -element.y)
                                    postRotate(-element.rotation)
                                    postScale(1f / element.view.scale, 1f / element.view.scale)
                                }
                                val pt = floatArrayOf(x, y).also { matrix.mapPoints(it) }
                                element.getTightTextBounds().contains(pt[0], pt[1])
                            }

                        if (hitChild != null) {
                            // Already selected same child — start drag immediately
                            // Otherwise switch selection to the new child
                            if (view.selectedElements.size != 1 || view.selectedElements.first().id != hitChild.id) {
                                view.canvasElements.forEach { it.isSelected = false }
                                view.selectedElements.clear()
                                hitChild.isSelected = true
                                view.selectedElements.add(hitChild)
                                view.onElementSelected?.invoke(view.selectedElements)
                            }
                            lastTouchedElement = hitChild
                            view.currentMode = Mode.DRAG
                            touchStartX = x
                            touchStartY = y
                            onStartBatchUpdate?.invoke(hitChild.id, "drag")
                            view.invalidate()
                            return true
                        } else {
                            // Tapped inside group area but missed all children --
                            // select all children for bounds, mark sentinel for ViewModel
                            val sentinel = view.canvasElements.firstOrNull {
                                it.type == ElementType.GROUP && it.id == view.activeGroupId
                            }
                            view.canvasElements.forEach { it.isSelected = false }
                            view.selectedElements.clear()
                            groupChildren.forEach {
                                it.isSelected = true
                                view.selectedElements.add(it)
                            }
                            sentinel?.isSelected = true
                            view.currentMode = Mode.DRAG
                            touchStartX = x
                            touchStartY = y
                            val report = if (sentinel != null) listOf(sentinel) else view.selectedElements.toList()
                            view.onElementSelected?.invoke(report)
                            view.invalidate()
                            return true
                        }
                    }
                }

                if (view.selectedElements.isNotEmpty()) {
                    val touchedIconEntry =
                        view.lastDrawnIconRect.entries.firstOrNull { (iconName, rect) ->
                            Log.d(
                                "IconTouch",
                                "Touch region icon=$iconName Rect(${rect.left}, ${rect.top}, ${rect.right}, ${rect.bottom})",
                            )
                            rect.contains(x, y)
                        }

                    if (touchedIconEntry != null) {
                        Log.d(
                            "IconHit",
                            "User tapped inside icon=${touchedIconEntry.key} at ($x,$y)",
                        )
                        view.iconTouched = touchedIconEntry.key
                        when (view.iconTouched) {
                            "delete" -> {
                                view.removeSelectedElement() // Handles removing all selected
                                return true // Consume the event immediately
                            }

                            "rotate" -> {
                                view.currentMode = Mode.ROTATE
                                touchStartX = x
                                touchStartY = y

                                view.isRotating = true
                                initialElementRotations.clear()
                                initialElementPositionsRelativeToGroupPivot.clear() // Clear previous initial positions
                                val combinedBoundsAtStart =
                                    getCombinedSelectedBounds() // Get bounds at start of interaction
                                initialGroupPivotX = combinedBoundsAtStart.centerX()
                                initialGroupPivotY = combinedBoundsAtStart.centerY()

                                view.selectedElements.forEach { element ->
                                    initialElementRotations[element.id] = element.rotation
                                    // Store initial position relative to the group's center
                                    initialElementPositionsRelativeToGroupPivot[element.id] = Pair(
                                        element.x - initialGroupPivotX,
                                        element.y - initialGroupPivotY,
                                    )
                                }
                                initialAngle = atan2(
                                    touchStartY - initialGroupPivotY,
                                    touchStartX - initialGroupPivotX,
                                )
                                view.selectedElements.firstOrNull()?.let { element ->
                                    onStartBatchUpdate?.invoke(element.id, "rotate")
                                }
                                return true
                            }

                            "resize" -> {
                                view.currentMode = Mode.RESIZE
                                touchStartX = x
                                touchStartY = y
                                val combined = getCombinedSelectedBounds()
                                val pivotX = combined.centerX()
                                val pivotY = combined.centerY()
                                // Capture the distance from finger to pivot at gesture start.
                                // MOVE frames compute newScale = initialScale * (currentDist / startDist)
                                // — absolute math, same as MULTI_TOUCH pinch, so zoom levels match.
                                resizeStartDist = hypot(x - pivotX, y - pivotY)
                                view.selectedElements.forEach { element ->
                                    resizeLastSignX[element.id] = (touchStartX - pivotX).sign
                                    resizeLastSignY[element.id] = (touchStartY - pivotY).sign
                                    resizeInitialScales[element.id] = element.view.scale
                                    onStartBatchUpdate?.invoke(element.id, "resize")
                                }
                                return true
                            }

                            "edit" -> {
                                if (view.selectedElements.size == 1) {
                                    view.onEditTextRequested?.invoke(view.selectedElements.first())
                                }
                                return true
                            }

                            "transform" -> {
                                view.currentMode = Mode.TRANSFORM
                                touchStartX = x
                                touchStartY = y

                                // Store initial logical sizes for direct geometry resize
                                view.selectedElements.forEach { element ->
                                    initialElementSizes[element.id] = Pair(
                                        element.logicalContentWidth,
                                        element.logicalContentHeight,
                                    )
                                    onStartBatchUpdate?.invoke(element.id, "transform")
                                }
                                return true
                            }
                        }
                    }
                }

                // 2. If no icon was touched, check for element touch (single or multi-selection)
                val touchedElement =
                    view.canvasElements.filter { !it.isLocked && it.type != ElementType.GROUP }
                        .sortedByDescending { it.zIndex }
                        .firstOrNull { element ->
                            val matrix = Matrix()
                            matrix.postTranslate(-element.x, -element.y)
                            matrix.postRotate(-element.rotation)
                            matrix.postScale(1f / element.view.scale, 1f / element.view.scale)

                            val touchPoint = floatArrayOf(x, y)
                            matrix.mapPoints(touchPoint)

                            val tightBounds = element.getTightTextBounds()
                            tightBounds.contains(touchPoint[0], touchPoint[1])
                        }

                if (touchedElement != null && !isPanMode) {
                    if (touchedElement.groupId != null) {
                        val gid = touchedElement.groupId!!

                        // Is this child already individually selected (e.g. from layers panel)?
                        // Also treat GROUP_EDIT mode as "child already entered".
                        val isChildAlreadySelectedAlone =
                            (view.selectedElements.size == 1 && view.selectedElements.first().id == touchedElement.id) ||
                                (view.currentMode == Mode.GROUP_EDIT && view.activeGroupId == gid)

                        if (isChildAlreadySelectedAlone) {
                            // ── Group-edit mode: drag just this child ────────────────────────
                            // Enter GROUP_EDIT if not already in it, so tapping outside exits.
                            if (view.currentMode != Mode.GROUP_EDIT) {
                                view.activeGroupId = gid
                                view.currentMode = Mode.GROUP_EDIT
                            }
                            // Ensure only this child is selected
                            view.canvasElements.forEach { it.isSelected = false }
                            view.selectedElements.clear()
                            touchedElement.isSelected = true
                            view.selectedElements.add(touchedElement)
                            lastTouchedElement = touchedElement
                            touchStartX = x
                            touchStartY = y
                            view.currentMode = Mode.DRAG // drag takes over until ACTION_UP
                            onStartBatchUpdate?.invoke(touchedElement.id, "drag")
                            // Report just the child — NOT the sentinel — so ViewModel keeps
                            // the child individually selected and doesn't collapse to whole group.
                            view.onElementSelected?.invoke(view.selectedElements.toList())
                            view.invalidate()
                            return true
                        } else {
                            // Fresh tap on a grouped child → select whole group as one unit
                            val groupMembers = view.canvasElements.filter { it.groupId == gid }
                            val sentinel = view.canvasElements.firstOrNull {
                                it.type == ElementType.GROUP && it.id == gid
                            }
                            view.canvasElements.forEach { it.isSelected = false }
                            view.selectedElements.clear()
                            groupMembers.forEach { element ->
                                element.isSelected = true
                                view.selectedElements.add(element)
                            }
                            sentinel?.isSelected = true
                            touchStartX = x
                            touchStartY = y
                            view.currentMode = Mode.DRAG
                            vibrateSoft()
                        }
                    } else {
                        if (view.inSelectionMode) {
                            if (touchedElement.isSelected) {
                                touchedDownElement = touchedElement
                                isDragCandidate = true
                                touchStartX = x
                                touchStartY = y
                                view.currentMode = Mode.NONE
                            } else {
                                touchedElement.isSelected = true
                                view.selectedElements.add(touchedElement)
                                view.onElementSelected?.invoke(view.selectedElements)
                                vibrateSoft()
                            }
                        } else {
                            if (touchedElement.isSelected) {
                                lastTouchedElement = touchedElement
                                view.currentMode = Mode.DRAG
                                touchStartX = x
                                touchStartY = y
                            } else {
                                view.canvasElements.forEach { it.isSelected = false }
                                view.selectedElements.clear()
                                touchedElement.isSelected = true
                                view.selectedElements.add(touchedElement)
                                lastTouchedElement = touchedElement
                                view.currentMode = Mode.DRAG
                                touchStartX = x
                                touchStartY = y
                            }
                            vibrateSoft()
                        }
                    }
                    onStartBatchUpdate?.invoke(touchedElement.id, "drag")
                    // Report sentinel to ViewModel when a group is selected,
                    // so it sees 1 unit not N children.
                    val reportForSelection = if (touchedElement.groupId != null) {
                        val sent = view.canvasElements.firstOrNull {
                            it.type == ElementType.GROUP && it.id == touchedElement.groupId
                        }
                        if (sent != null) listOf(sent) else view.selectedElements.toList()
                    } else {
                        view.selectedElements.toList()
                    }
                    view.onElementSelected?.invoke(reportForSelection)
                    view.invalidate()
                    return true
                } else {
                    // isPanMode ON hai, ya empty canvas tap — pan mode set karo
                    val bg =
                        view.canvasElements.firstOrNull { it.type == ElementType.BACKGROUND && !it.isLocked }
                    if (!isPanMode && bg?.bitmap != null) {
                        view.canvasElements.forEach { it.isSelected = false }
                        view.selectedElements.clear()
                        bg.isSelected = true
                        view.selectedElements.add(bg)
                        view.onElementSelected?.invoke(view.selectedElements)
                        view.currentMode = Mode.DRAG
                        touchStartX = x
                        touchStartY = y
                        view.invalidate()
                        return true
                    }
                    if (view.selectedElements.isNotEmpty() && !isPanMode) {
                        view.canvasElements.forEach { it.isSelected = false }
                        view.selectedElements.clear()
                        view.inSelectionMode = false
                        view.onExitSelectionMode?.invoke()
                        view.onElementSelected?.invoke(view.selectedElements)
                        view.invalidate()
                    } else {
                        // Pan mode ON, ya zoomed in — canvas pan karo
                        if (isCanvasPanLocked) {
                            view.currentMode = Mode.NONE
                            return true
                        }
                        view.currentMode = Mode.CANVAS_PAN
                        touchStartX = event.x
                        touchStartY = event.y
                        return true
                    }
                    view.currentMode = Mode.NONE
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // Determine which elements to modify based on current mode and touch context
                val elementsToModify = view.selectedElements.filter {
                    !it.isLocked
                }
                if (elementsToModify.isEmpty()) {
                    // allow overall canvas pan/zoom
                    when (view.currentMode) {
                        Mode.CANVAS_PAN -> {
                            if (!isCanvasPanLocked) {
                                if (event.pointerCount == 2) {
                                    // Empty canvas ya pan mode → overall zoom
                                    val newDist = getPinchDistance(event)
                                    val factor = newDist / initialPinchDistance
                                    var newScale = (initialOverallScale * factor).coerceIn(0.5f, 3.0f)
                                    // Snap to 50%, 100%, 150%, 200%, 250%, 300%
                                    val snapTargets = listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f)
                                    val snapThreshold = 0.03f
                                    val snappedTarget =
                                        snapTargets.firstOrNull { abs(newScale - it) <= snapThreshold }
                                    if (snappedTarget != null) {
                                        if (view.overallScale != snappedTarget) vibrateSoft()
                                        newScale = snappedTarget
                                    }

                                    // Keep the canvas point under the finger midpoint fixed.
                                    // Derived from onDraw transform:
                                    //   screenPos = view.overallScale*(p - pivot) + pivot + overallOffset
                                    // Setting screenPos equal before/after view.scale change gives:
                                    //   newOffset = initialOffset + (focus - pivot) * (1 - newScale/initialScale)
                                    val pivotX = width / 2f
                                    val pivotY = height / 2f
                                    val scaleFactor = newScale / initialOverallScale
                                    view.overallOffsetX = initialOffsetXAtPinch + (pinchFocusX - pivotX) * (1f - scaleFactor)
                                    view.overallOffsetY = initialOffsetYAtPinch + (pinchFocusY - pivotY) * (1f - scaleFactor)

                                    view.overallScale = newScale
                                    view.clampOverallPan()
                                    suppressZoomCallback = true
                                    view.onZoomChanged?.invoke(view.overallScale)
                                    suppressZoomCallback = false
                                    view.invalidate()
                                } else if (event.pointerCount == 1) {
                                    val dx = event.x - touchStartX
                                    val dy = event.y - touchStartY
                                    view.overallOffsetX += dx
                                    view.overallOffsetY += dy
                                    view.checkCanvasPanSnap()
                                    view.clampOverallPan()
                                    touchStartX = event.x
                                    touchStartY = event.y
                                    view.invalidate()
                                }
                            }
                        }

                        else -> return true
                    }
                    return true
                }

                if (isDragCandidate && touchedDownElement != null) {
                    val dx = abs(x - touchStartX)
                    val dy = abs(y - touchStartY)
                    if (dx > touchSlop || dy > touchSlop) {
                        // start drag instead of deselect
                        lastTouchedElement = touchedDownElement
                        view.currentMode = Mode.DRAG
                        isDragCandidate = false
                        touchedDownElement = null
                    }
                }

                when (view.currentMode) {
                    Mode.DRAG -> {
                        val dx = x - touchStartX
                        val dy = y - touchStartY

                        elementsToModify.forEach { element ->
                            if (element.type == ElementType.BACKGROUND && element.bitmap != null) {
                                val (xRange, yRange) = computeBackgroundPanBounds(element)
                                val newX = element.x + dx
                                val newY = element.y + dy

                                if (!view.allowFreeDrag) {
                                    // are we still within “no-blank” pan?
                                    if (newX in xRange && newY in yRange) {
                                        element.x = newX.coerceIn(xRange)
                                        element.y = newY.coerceIn(yRange)
                                    } else {
                                        // user pushed past the edge: switch to free-drag from now on
                                        view.allowFreeDrag = true
                                        element.x = newX
                                        element.y = newY
                                    }
                                } else {
                                    // already in free-drag, just move like normal
                                    element.x = newX
                                    element.y = newY
                                }
                            } else {
                                // non-background elements: regular drag
                                element.x += dx
                                element.y += dy
                            }
                            onElementChanged?.invoke(element)
                        }

                        // Check alignment for the first selected element (if only one is selected for single drag)
                        if (view.selectedElements.isNotEmpty()) {
                            checkDragSnap()
                        } else {
                            showVerticalGuide = false
                            showHorizontalGuide = false
                        }

                        touchStartX = x // Update touch start for continuous drag
                        touchStartY = y
                        view.invalidate()
                    }

                    Mode.MULTI_TOUCH -> {
                        if (event.pointerCount >= 2) {
                            val newPinchDistance = getPinchDistance(event)
                            val newPinchAngle = getPinchAngle(event)

                            // Scale
                            if (initialPinchDistance > 0) {
                                val scaleFactor = newPinchDistance / initialPinchDistance
                                view.selectedElements.filter { !it.isLocked }.forEach { element ->
                                    // ── Dynamic minimum view.scale (matches RESIZE handle) ─────────
                                    val minOnScreenPx = 20f * resources.displayMetrics.density
                                    val logicalW = element.getLocalContentWidth().takeIf { it > 0 } ?: 1f
                                    val minScale = (minOnScreenPx / (logicalW * view.scale * view.overallScale))
                                        .coerceAtMost(0.01f)

                                    val newScale = (initialScale * scaleFactor).coerceIn(minScale, 100f)
                                    element.view.scale = newScale
                                    onElementChanged?.invoke(element)
                                }
                            }

                            // Rotate
                            val rotationDelta = newPinchAngle - initialPinchAngle
                            view.selectedElements.filter { !it.isLocked }.forEach { element ->
                                element.rotation = (initialRotation + rotationDelta) % 360
                                onElementChanged?.invoke(element)
                            }

                            checkDragSnap()

                            if (view.selectedElements.size == 1) {
                                checkRotationAlignment(view.selectedElements.first())
                            } else {
                                checkGroupRotationAlignment()
                            }
                            view.invalidate()
                        }
                    }

                    Mode.ROTATE -> {
                        if (view.selectedElements.isEmpty()) return true

                        view.isRotating = true
                        val currentAngle = atan2(
                            y - initialGroupPivotY,
                            x - initialGroupPivotX,
                        ) // Calculate angle relative to initial group pivot
                        val deltaAngle =
                            Math.toDegrees((currentAngle - initialAngle).toDouble()).toFloat()

                        elementsToModify.forEach { element ->
                            val initialRotation =
                                initialElementRotations[element.id] ?: element.rotation
                            element.rotation =
                                (initialRotation + deltaAngle) % 360 // Update element's own rotation

                            // Rotate element's initial position relative to the group pivot
                            val initialRelativeX =
                                initialElementPositionsRelativeToGroupPivot[element.id]?.first ?: 0f
                            val initialRelativeY =
                                initialElementPositionsRelativeToGroupPivot[element.id]?.second
                                    ?: 0f

                            val rotatedRelativeX =
                                (initialRelativeX * cos(Math.toRadians(deltaAngle.toDouble()))) - (
                                    initialRelativeY * sin(
                                        Math.toRadians(deltaAngle.toDouble()),
                                    )
                                    )
                            val rotatedRelativeY =
                                (initialRelativeX * sin(Math.toRadians(deltaAngle.toDouble()))) + (
                                    initialRelativeY * cos(
                                        Math.toRadians(deltaAngle.toDouble()),
                                    )
                                    )

                            // Update element's position based on the rotated relative position from the *initial* group pivot
                            element.x = initialGroupPivotX + rotatedRelativeX.toFloat()
                            element.y = initialGroupPivotY + rotatedRelativeY.toFloat()

                            onElementChanged?.invoke(element)
                        }

                        // After rotating, re-calculate the combined bounds to check for clamping
                        val newCombinedBounds = getCombinedSelectedBounds()

                        // Clamp the rotated group back into the canvas if it went out
                        var translationX = 0f
                        var translationY = 0f

                        if (newCombinedBounds.left < 0) {
                            translationX = -newCombinedBounds.left
                        } else if (newCombinedBounds.right > view.canvasWidth) {
                            translationX = view.canvasWidth - newCombinedBounds.right
                        }

                        if (newCombinedBounds.top < 0) {
                            translationY = -newCombinedBounds.top
                        } else if (newCombinedBounds.bottom > view.canvasHeight) {
                            translationY = view.canvasHeight - newCombinedBounds.bottom
                        }

                        if (translationX != 0f || translationY != 0f) {
                            elementsToModify.forEach { element ->
                                element.x += translationX
                                element.y += translationY
                                onElementChanged?.invoke(element)
                            }
                            // Also adjust the initialGroupPivotX and Y to reflect the new clamped position
                            initialGroupPivotX += translationX
                            initialGroupPivotY += translationY
                        }

                        // Check rotation alignment for the first selected element
                        if (view.selectedElements.size == 1) {
                            checkRotationAlignment(view.selectedElements.first())
                        } else if (view.selectedElements.size > 1) {
                            checkGroupRotationAlignment()
                        }

                        view.invalidate()
                    }

                    Mode.RESIZE -> {
                        if (view.selectedElements.isEmpty()) return true

                        val combined = getCombinedSelectedBounds()
                        val pivotX = combined.centerX()
                        val pivotY = combined.centerY()

                        val currentDist = hypot(x - pivotX, y - pivotY)

                        // ── Absolute view.scale math (matches MULTI_TOUCH pinch) ───────────
                        // OLD incremental: newScale = element.view.scale * (currentDist/startDist)
                        //   — resets startDist each frame via touchStartX=x, causing drift
                        //     and different zoom sensitivity than pinch.
                        // NEW absolute:   newScale = initialScale * (currentDist/resizeStartDist)
                        //   — same formula as MULTI_TOUCH scaleFactor = newPinchDist/initialPinchDist
                        //   — identical zoom level for the same physical finger movement.
                        if (resizeStartDist > 0) {
                            val scaleFactor = currentDist / resizeStartDist
                            elementsToModify.forEach { element ->
                                val initialScale = resizeInitialScales[element.id] ?: element.view.scale

                                // ── Dynamic minimum view.scale ─────────────────────────────────
                                // Hard-coding 0.1f as min breaks large bitmaps: a 4000px image
                                // at view.scale=0.1 is still 400 canvas units wide — too large to
                                // call "minimum". Compute min from a 20dp on-screen threshold.
                                val minOnScreenPx = 20f * resources.displayMetrics.density
                                val logicalW = element.getLocalContentWidth().takeIf { it > 0 } ?: 1f
                                val minScale = (minOnScreenPx / (logicalW * view.scale * view.overallScale))
                                    .coerceAtMost(0.01f) // never go above 0.01 as floor

                                val newScale = (initialScale * scaleFactor).coerceIn(minScale, 100f)
                                element.view.scale = newScale

                                val lastSignX = resizeLastSignX[element.id] ?: 0f
                                val currSignX = (x - pivotX).sign
                                if (currSignX != 0f && currSignX != lastSignX) {
                                    element.isFlippedX = !element.isFlippedX
                                    onElementChanged?.invoke(element)
                                    resizeLastSignX[element.id] = currSignX
                                }

                                val lastSignY = resizeLastSignY[element.id] ?: 0f
                                val currSignY = (y - pivotY).sign
                                if (currSignY != 0f && currSignY != lastSignY) {
                                    element.isFlippedY = !element.isFlippedY
                                    onElementChanged?.invoke(element)
                                    resizeLastSignY[element.id] = currSignY
                                }

                                onElementChanged?.invoke(element)
                            }
                        }

                        // NOTE: touchStartX/Y are NOT reset here — absolute math doesn't need it.
                        view.invalidate()
                    }

                    Mode.NONE -> {
                        // This block handles potential tap-and-hold to drag if not immediately picking up an icon/element
                    }

                    Mode.GROUP_EDIT -> {
                        // This block handles potential tap-and-hold to drag if not immediately picking up an icon/element
                    }

                    Mode.CANVAS_PAN -> {
                        if (!isCanvasPanLocked) {
                            if (event.pointerCount == 2) {
                                val newDist = getPinchDistance(event)
                                val factor = newDist / initialPinchDistance
                                var newScale = (initialOverallScale * factor).coerceIn(0.5f, 3.0f)
                                // Snap to 50%, 100%, 150%, 200%, 250%, 300%
                                val snapTargets = listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f)
                                val snapThreshold = 0.03f
                                val snappedTarget =
                                    snapTargets.firstOrNull { abs(newScale - it) <= snapThreshold }
                                if (snappedTarget != null) {
                                    if (view.overallScale != snappedTarget) vibrateSoft()
                                    newScale = snappedTarget
                                }

                                // Keep the canvas point under the finger midpoint fixed.
                                // Derived from onDraw transform:
                                //   screenPos = view.overallScale*(p - pivot) + pivot + overallOffset
                                // Setting screenPos equal before/after view.scale change gives:
                                //   newOffset = initialOffset + (focus - pivot) * (1 - newScale/initialScale)
                                val pivotX = width / 2f
                                val pivotY = height / 2f
                                val scaleFactor = newScale / initialOverallScale
                                view.overallOffsetX = initialOffsetXAtPinch + (pinchFocusX - pivotX) * (1f - scaleFactor)
                                view.overallOffsetY = initialOffsetYAtPinch + (pinchFocusY - pivotY) * (1f - scaleFactor)

                                view.overallScale = newScale
                                view.clampOverallPan()
                                suppressZoomCallback = true
                                view.onZoomChanged?.invoke(view.overallScale)
                                suppressZoomCallback = false
                                view.invalidate()
                            } else if (event.pointerCount == 1) {
                                val dx = event.x - touchStartX
                                val dy = event.y - touchStartY
                                view.overallOffsetX += dx
                                view.overallOffsetY += dy
                                view.checkCanvasPanSnap()
                                view.clampOverallPan()
                                touchStartX = event.x
                                touchStartY = event.y
                                view.invalidate()
                            }
                        }
                    }

                    Mode.TRANSFORM -> {
                        if (view.selectedElements.isEmpty()) return true

                        val dx = x - touchStartX
                        val dy = y - touchStartY

                        view.selectedElements.forEach { element ->
                            val (initialW, initialH) = initialElementSizes[element.id]
                                ?: return@forEach

                            val newW = (initialW - dx).coerceAtLeast(10f)
                            val newH = (initialH + dy).coerceAtLeast(10f)

                            element.logicalContentWidth = newW
                            element.logicalContentHeight = newH
                            onElementChanged?.invoke(element)
                        }

                        view.invalidate()
                        return true
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // A second (or later) finger lifted — only reset pinch state.
                // Do NOT run the full ACTION_UP logic here; doing so causes the
                // framework to recycle the same TouchTarget twice, which throws
                // IllegalStateException: already recycled once on Android 15.
                if (view.currentMode == Mode.CANVAS_PAN || view.currentMode == Mode.MULTI_TOUCH) {
                    initialPinchDistance = 0f
                    initialPinchAngle = 0f
                    initialOverallScale = view.overallScale
                    pinchFocusX = 0f
                    pinchFocusY = 0f
                    initialOffsetXAtPinch = view.overallOffsetX
                    initialOffsetYAtPinch = view.overallOffsetY

                    // Update touchStart points to the remaining finger's coordinates
                    // if we were in CANVAS_PAN mode to prevent a jump/snap when the remaining finger moves.
                    if (view.currentMode == Mode.CANVAS_PAN && event.pointerCount == 2) {
                        val remainingIndex = if (event.actionIndex == 0) 1 else 0
                        touchStartX = event.getX(remainingIndex)
                        touchStartY = event.getY(remainingIndex)
                    }
                }
                view.invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                showVerticalGuide = false
                showHorizontalGuide = false
                showRotationVerticalGuide = false // Reset rotation guides on ACTION_UP
                showRotationHorizontalGuide = false // Reset rotation guides on ACTION_UP
                showCanvasCenterVerticalSnap = false
                showCanvasCenterHorizontalSnap = false
                if (view.currentMode == Mode.CANVAS_PAN) {
                    view.currentMode = Mode.NONE
                }

                if (view.currentMode == Mode.TRANSFORM) {
                    view.selectedElements.forEach {
                        onElementChanged?.invoke(it)
                        onEndBatchUpdate?.invoke(it.id)
                    }
                }

                if (view.currentMode == Mode.DRAG || view.currentMode == Mode.ROTATE || view.currentMode == Mode.RESIZE) {
                    view.selectedElements.filter { !it.isLocked }.forEach {
                        onElementChanged?.invoke(it)
                        onEndBatchUpdate?.invoke(it.id)
                    }
                }
                if (isDragCandidate && touchedDownElement != null) {
                    val element = touchedDownElement!!
                    element.isSelected = false
                    view.selectedElements.remove(element)
                    if (view.selectedElements.isEmpty()) {
                        view.inSelectionMode = false
                        view.onExitSelectionMode?.invoke()
                    }
                    view.onElementSelected?.invoke(view.selectedElements)
                    view.invalidate()
                }
                isDragCandidate = false
                touchedDownElement = null

                view.iconTouched = null
                initialPinchDistance = 0f
                initialPinchAngle = 0f
                initialScale = 1f
                initialRotation = 0f
                pinchFocusX = 0f
                pinchFocusY = 0f
                initialOffsetXAtPinch = 0f
                initialOffsetYAtPinch = 0f
                resizeInitialScales.clear()
                resizeStartDist = 0f
                initialElementRotations.clear()
                initialElementPositionsRelativeToGroupPivot.clear() // Clear initial positions on action up
                initialAngle = 0f
                initialGroupPivotX = 0f
                initialGroupPivotY = 0f
                if (view.currentMode != Mode.GROUP_EDIT) {
                    lastTouchedElement = null
                    // If we just finished dragging a single group child, restore GROUP_EDIT
                    // so the user stays "inside" the group (Photoshop behaviour).
                    // Tap outside will exit GROUP_EDIT via the existing bounds check.
                    if (view.activeGroupId != null &&
                        view.selectedElements.size == 1 &&
                        view.selectedElements.first().groupId == view.activeGroupId
                    ) {
                        view.currentMode = Mode.GROUP_EDIT
                    } else {
                        view.currentMode = Mode.NONE
                    }
                }
                view.clampOverallPan()
                view.isRotating = false
                view.invalidate()
                return true
            }
        }
        // This view handles all touch events — never fall through to super,
        // as that can re-enter the parent's touch dispatch chain and contribute
        // to the TouchTarget double-recycle crash on Android 14.
        return true
    }

    /**
     * Called during CANVAS_PAN single-finger drag.
     * Snaps view.overallOffsetX/Y to zero (canvas centered) when the canvas center
     * comes within [canvasSnapThresholdPx] of the view center, and shows the
     * dashed cyan guide lines.  Vibrates once when snapping occurs.
     */
    
}