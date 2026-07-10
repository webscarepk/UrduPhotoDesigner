package com.webscare.urducanvas.common.views

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.BlurMaskFilter
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
import com.webscare.urducanvas.common.utils.BrushRenderUtils.createBackgroundGradientShader
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sign

@Suppress("LargeClass")
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
    private var initialScale = 1f
    private var initialRotation = 0f
    private var lastTouchedElement: CanvasElement? = null
    private var iconTouched: String? = null

    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
    private val gestureDetector = GestureDetector(view.context, GestureListener())

    private fun CanvasElement.containsPoint(px: Float, py: Float): Boolean =
        with(view.elementManager) { containsPoint(px, py) }

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
            if (view.isPanMode) {
                view.stepZoomOverall()
                return true
            }

            val (x, y) = view.screenToCanvas(e.x, e.y)
            val touchedElement = findDoubleTappedElement(x, y)

            if (touchedElement != null) {
                performElementSelectionOnDoubleTap(touchedElement)
                return true
            }

            view.stepZoomOverall()
            return true
        }

        private fun findDoubleTappedElement(x: Float, y: Float): CanvasElement? {
            return view.canvasElements.filter { !it.isLocked && it.type != ElementType.GROUP }
                .sortedByDescending { it.zIndex }
                .firstOrNull { element ->
                    val matrix = Matrix()
                    matrix.postTranslate(-element.x, -element.y)
                    matrix.postRotate(-element.rotation)
                    matrix.postScale(1f / element.scale, 1f / element.scale)

                    val touchPoint = floatArrayOf(x, y)
                    matrix.mapPoints(touchPoint)

                    val tightBounds = element.getTightTextBounds()
                    tightBounds.contains(touchPoint[0], touchPoint[1])
                }
        }

        private fun performElementSelectionOnDoubleTap(element: CanvasElement) {
            val isGroupChild = element.groupId != null
            val isInActiveGroup = view.currentMode == Mode.GROUP_EDIT &&
                element.groupId == view.activeGroupId &&
                element.type != ElementType.BACKGROUND

            if (isGroupChild && !isInActiveGroup) {
                view.activeGroupId = element.groupId
                view.currentMode = Mode.GROUP_EDIT
            }

            view.canvasElements.forEach { it.isSelected = false }
            view.selectedElements.clear()
            element.isSelected = true
            view.selectedElements.add(element)
            view.onElementSelected?.invoke(view.selectedElements)
            view.onEditTextRequested?.invoke(element)
            view.invalidate()
        }

        override fun onLongPress(e: MotionEvent) {
            val (x, y) = view.screenToCanvas(e.x, e.y)

            val touchedElement = view.canvasElements.filter { !it.isLocked } // ignore locked
                .sortedByDescending { it.zIndex }.firstOrNull { it.containsPoint(x, y) }

            if (touchedElement != null && touchedElement.type != ElementType.BACKGROUND) {
                handleElementLongPress(touchedElement)
            } else {
                val isOutsideArtboard = x < 0f || y < 0f || x > view.canvasWidth || y > view.canvasHeight
                if (isOutsideArtboard) {
                    view.vibrateSoft()
                    view.onCanvasLongPressed?.invoke(e.rawX, e.rawY)
                }
            }
        }

        private fun handleElementLongPress(touchedElement: CanvasElement) {
            val groupId = touchedElement.groupId
            val sentinel = if (groupId != null) {
                view.canvasElements.firstOrNull { it.type == ElementType.GROUP && it.id == groupId }
            } else {
                null
            }
            val canvasItems: List<CanvasElement> = if (sentinel != null) {
                view.canvasElements.filter { it.groupId == groupId }
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
                sentinel?.isSelected = true
                view.vibrateSoft()
                view.onRequestOpenLayers?.invoke()
            } else {
                handleAlreadySelectedLongPress(canvasItems, sentinel)
            }
            val reportList = if (sentinel != null) listOf(sentinel) else view.selectedElements.toList()
            view.onElementSelected?.invoke(reportList)
            view.invalidate()
        }

        private fun handleAlreadySelectedLongPress(canvasItems: List<CanvasElement>, sentinel: CanvasElement?) {
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
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
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
            return handleDrawingTouchEvent(event, x, y)
        }

        if (view.isColorPickerMode) {
            return handleColorPickerTouchEvent(event, x, y)
        }

        return when (maskedAction) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                handleActionPointerDown(event)
                true
            }
            MotionEvent.ACTION_DOWN -> {
                handleActionDown(x, y, event)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                handleActionMove(x, y, event)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handleActionUp(x, y, event)
                true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                handleActionPointerUp(event)
                true
            }
            else -> false
        }
    }

    private fun handleDrawingTouchEvent(event: MotionEvent, x: Float, y: Float): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> startDrawingStroke(x, y)
            MotionEvent.ACTION_MOVE -> moveDrawingStroke(x, y)
            MotionEvent.ACTION_UP -> finishDrawingStroke(x, y)
        }
        return true
    }

    private fun startDrawingStroke(x: Float, y: Float) {
        view.currentStrokePath = Path().apply { moveTo(x, y) }
        view.currentStrokePoints.clear()
        view.currentStrokePoints.add(x to y)

        view.currentStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = view.currentBrushColor
            strokeWidth = view.currentBrushThickness
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
                shader = createBackgroundGradientShader(it, view.width.toFloat(), view.height.toFloat())
            }
        }
        view.invalidate()
    }

    private fun moveDrawingStroke(x: Float, y: Float) {
        val clampedX = x.coerceIn(0f, view.canvasWidth.toFloat())
        val clampedY = y.coerceIn(0f, view.canvasHeight.toFloat())

        view.currentStrokePath?.lineTo(clampedX, clampedY)
        view.currentStrokePoints.add(clampedX to clampedY)
        view.invalidate()
    }

    private fun finishDrawingStroke(x: Float, y: Float) {
        view.currentStrokePath?.lineTo(x, y)

        val path = view.currentStrokePath
        if (path != null && view.activeSessionElement != null) {
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

        view.currentStrokePath = null
        view.currentStrokePaint = null
        view.currentStrokePoints.clear()
        view.invalidate()
    }

    private fun handleColorPickerTouchEvent(event: MotionEvent, x: Float, y: Float): Boolean {
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
        return false
    }

    private fun handleActionPointerDown(event: MotionEvent) {
        if (event.pointerCount == 2) {
            initialPinchDistance = getPinchDistance(event)
            initialPinchAngle = getPinchAngle(event)
            initialOverallScale = view.overallScale

            pinchFocusX = (event.getX(0) + event.getX(1)) / 2f
            pinchFocusY = (event.getY(0) + event.getY(1)) / 2f
            initialOffsetXAtPinch = view.overallOffsetX
            initialOffsetYAtPinch = view.overallOffsetY

            when {
                view.selectedElements.isNotEmpty() && !view.isPanMode -> {
                    view.currentMode = Mode.MULTI_TOUCH
                    initialScale = view.selectedElements.firstOrNull()?.scale ?: 1f
                    initialRotation = view.selectedElements.firstOrNull()?.rotation ?: 0f
                }
                view.isCanvasPanLocked -> { /* do nothing */ }
                else -> {
                    view.currentMode = Mode.CANVAS_PAN
                }
            }
        }
    }

    private fun handleActionPointerUp(event: MotionEvent) {
        if (event.pointerCount == 2) {
            val activeIndex = if (event.actionIndex == 0) 1 else 0
            if (view.currentMode == Mode.CANVAS_PAN) {
                touchStartX = event.getX(activeIndex)
                touchStartY = event.getY(activeIndex)
            } else {
                val (canvasX, canvasY) = view.screenToCanvas(event.getX(activeIndex), event.getY(activeIndex))
                touchStartX = canvasX
                touchStartY = canvasY
            }
        }
    }

    private fun handleActionDown(x: Float, y: Float, event: MotionEvent) {
        iconTouched = null
        lastTouchedElement = null
        view.showVerticalGuide = false
        view.showHorizontalGuide = false
        view.showRotationVerticalGuide = false
        view.showRotationHorizontalGuide = false

        if (view.currentMode == Mode.GROUP_EDIT && view.activeGroupId != null &&
            handleGroupEditActionDown(x, y)
        ) {
            return
        }

        val touchedIconEntry = view.selectedElements
            .takeIf { it.isNotEmpty() }
            ?.let { view.lastDrawnIconRect.entries.firstOrNull { (_, rect) -> rect.contains(x, y) } }
        if (touchedIconEntry != null && handleIconTouch(touchedIconEntry.key, x, y)) return

        if (handleElementTouch(x, y)) return

        handleEmptyOrPanTouch(event)
    }

    private fun handleGroupEditActionDown(x: Float, y: Float): Boolean {
        val groupChildren = view.canvasElements.filter { it.groupId == view.activeGroupId }
        val groupBounds = calculateGroupBounds(groupChildren)

        if (!groupBounds.contains(x, y)) {
            exitGroupEditMode()
            return false
        }

        val touchedIconEntry = view.selectedElements
            .takeIf { it.isNotEmpty() }
            ?.let { view.lastDrawnIconRect.entries.firstOrNull { (_, rect) -> rect.contains(x, y) } }
        if (touchedIconEntry != null && handleIconTouch(touchedIconEntry.key, x, y)) return true

        val hitChild = findHitGroupChild(groupChildren, x, y)

        if (hitChild != null) {
            handleGroupChildHit(hitChild, x, y)
        } else {
            handleGroupSentinelSelect(groupChildren, x, y)
        }
        return true
    }

    private fun calculateGroupBounds(groupChildren: List<CanvasElement>): RectF {
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
        return RectF(minX, minY, maxX, maxY)
    }

    private fun exitGroupEditMode() {
        view.currentMode = Mode.NONE
        view.activeGroupId = null
        view.canvasElements.forEach { it.isSelected = false }
        view.selectedElements.clear()
        view.onElementSelected?.invoke(view.selectedElements)
    }

    private fun findHitGroupChild(groupChildren: List<CanvasElement>, x: Float, y: Float): CanvasElement? {
        return groupChildren.filter { !it.isLocked }
            .sortedByDescending { it.zIndex }.firstOrNull { element ->
                val matrix = Matrix().apply {
                    postTranslate(-element.x, -element.y)
                    postRotate(-element.rotation)
                    postScale(1f / element.scale, 1f / element.scale)
                }
                val pt = floatArrayOf(x, y).also { matrix.mapPoints(it) }
                element.getTightTextBounds().contains(pt[0], pt[1])
            }
    }

    private fun handleGroupChildHit(hitChild: CanvasElement, x: Float, y: Float) {
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
        view.onStartBatchUpdate?.invoke(hitChild.id, "drag")
        view.invalidate()
    }

    private fun handleGroupSentinelSelect(groupChildren: List<CanvasElement>, x: Float, y: Float) {
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
    }

    private fun handleIconTouch(iconName: String, x: Float, y: Float): Boolean {
        iconTouched = iconName
        return when (iconName) {
            "delete" -> { view.removeSelectedElement(); true }
            "rotate" -> { startIconRotation(x, y); true }
            "resize" -> { startIconResize(x, y); true }
            "edit" -> {
                if (view.selectedElements.size == 1) {
                    view.onEditTextRequested?.invoke(view.selectedElements.first())
                }
                true
            }
            "transform" -> { startIconTransform(x, y); true }
            else -> false
        }
    }

    private fun startIconRotation(x: Float, y: Float) {
        view.currentMode = Mode.ROTATE
        touchStartX = x
        touchStartY = y
        view.isRotating = true
        initialElementRotations.clear()
        initialElementPositionsRelativeToGroupPivot.clear()
        val bounds = view.getCombinedSelectedBounds()
        initialGroupPivotX = bounds.centerX()
        initialGroupPivotY = bounds.centerY()
        view.selectedElements.forEach { el ->
            initialElementRotations[el.id] = el.rotation
            initialElementPositionsRelativeToGroupPivot[el.id] =
                Pair(el.x - initialGroupPivotX, el.y - initialGroupPivotY)
        }
        initialAngle = atan2(touchStartY - initialGroupPivotY, touchStartX - initialGroupPivotX)
        view.selectedElements.firstOrNull()?.let {
            view.onStartBatchUpdate?.invoke(it.id, "rotate")
        }
    }

    private fun startIconResize(x: Float, y: Float) {
        view.currentMode = Mode.RESIZE
        touchStartX = x
        touchStartY = y
        val combined = view.getCombinedSelectedBounds()
        val pivotX = combined.centerX()
        val pivotY = combined.centerY()
        resizeStartDist = hypot(x - pivotX, y - pivotY)
        view.selectedElements.forEach { el ->
            resizeLastSignX[el.id] = (touchStartX - pivotX).sign
            resizeLastSignY[el.id] = (touchStartY - pivotY).sign
            resizeInitialScales[el.id] = el.scale
            view.onStartBatchUpdate?.invoke(el.id, "resize")
        }
    }

    private fun startIconTransform(x: Float, y: Float) {
        view.currentMode = Mode.TRANSFORM
        touchStartX = x
        touchStartY = y
        view.selectedElements.forEach { el ->
            view.initialElementSizes[el.id] = Pair(el.logicalContentWidth, el.logicalContentHeight)
            view.onStartBatchUpdate?.invoke(el.id, "transform")
        }
    }

    private fun handleElementTouch(x: Float, y: Float): Boolean {
        val touchedElement = view.canvasElements.filter { !it.isLocked && it.type != ElementType.GROUP }
            .sortedByDescending { it.zIndex }
            .firstOrNull { element ->
                val matrix = Matrix()
                matrix.postTranslate(-element.x, -element.y)
                matrix.postRotate(-element.rotation)
                matrix.postScale(1f / element.scale, 1f / element.scale)
                val touchPoint = floatArrayOf(x, y)
                matrix.mapPoints(touchPoint)
                element.getTightTextBounds().contains(touchPoint[0], touchPoint[1])
            }

        if (touchedElement != null && !view.isPanMode) {
            if (touchedElement.groupId != null) {
                handleGroupedElementTouch(touchedElement, x, y)
            } else {
                handleSingleElementTouch(touchedElement, x, y)
            }
            view.onStartBatchUpdate?.invoke(touchedElement.id, "drag")
            val reportForSelection = if (touchedElement.groupId != null) {
                val sent = view.canvasElements
                    .firstOrNull { it.type == ElementType.GROUP && it.id == touchedElement.groupId }
                if (sent != null) listOf(sent) else view.selectedElements.toList()
            } else {
                view.selectedElements.toList()
            }
            view.onElementSelected?.invoke(reportForSelection)
            view.invalidate()
            return true
        }
        return false
    }

    private fun handleGroupedElementTouch(touchedElement: CanvasElement, x: Float, y: Float) {
        val gid = touchedElement.groupId!!
        val isChildAlreadySelectedAlone =
            (view.selectedElements.size == 1 && view.selectedElements.first().id == touchedElement.id) ||
                (view.currentMode == Mode.GROUP_EDIT && view.activeGroupId == gid)

        if (isChildAlreadySelectedAlone) {
            if (view.currentMode != Mode.GROUP_EDIT) {
                view.activeGroupId = gid
                view.currentMode = Mode.GROUP_EDIT
            }
            view.canvasElements.forEach { it.isSelected = false }
            view.selectedElements.clear()
            touchedElement.isSelected = true
            view.selectedElements.add(touchedElement)
            lastTouchedElement = touchedElement
            touchStartX = x
            touchStartY = y
            view.currentMode = Mode.DRAG
        } else {
            val groupMembers = view.canvasElements.filter { it.groupId == gid }
            val sentinel = view.canvasElements.firstOrNull { it.type == ElementType.GROUP && it.id == gid }
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
            view.vibrateSoft()
        }
    }

    private fun handleSingleElementTouch(touchedElement: CanvasElement, x: Float, y: Float) {
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
                view.vibrateSoft()
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
            view.vibrateSoft()
        }
    }

    private fun handleEmptyOrPanTouch(event: MotionEvent) {
        val (x, y) = view.screenToCanvas(event.x, event.y)
        val bg = view.canvasElements.firstOrNull { it.type == ElementType.BACKGROUND && !it.isLocked }
        if (!view.isPanMode && bg?.bitmap != null) {
            view.canvasElements.forEach { it.isSelected = false }
            view.selectedElements.clear()
            bg.isSelected = true
            view.selectedElements.add(bg)
            view.onElementSelected?.invoke(view.selectedElements)
            view.currentMode = Mode.DRAG
            touchStartX = x
            touchStartY = y
            view.invalidate()
            return
        }
        if (view.selectedElements.isNotEmpty() && !view.isPanMode) {
            view.canvasElements.forEach { it.isSelected = false }
            view.selectedElements.clear()
            view.inSelectionMode = false
            view.onExitSelectionMode?.invoke()
            view.onElementSelected?.invoke(view.selectedElements)
            view.invalidate()
        } else {
            if (view.isCanvasPanLocked) {
                view.currentMode = Mode.NONE
                return
            }
            view.currentMode = Mode.CANVAS_PAN
            touchStartX = event.x
            touchStartY = event.y
        }
    }

    private fun handleActionMove(x: Float, y: Float, event: MotionEvent) {
        val elementsToModify = view.selectedElements.filter { !it.isLocked }

        if (elementsToModify.isEmpty()) {
            handleCanvasPanActionMove(event)
            return
        }

        if (isDragCandidate && touchedDownElement != null) {
            val dx = abs(x - touchStartX)
            val dy = abs(y - touchStartY)
            if (dx > touchSlop || dy > touchSlop) {
                lastTouchedElement = touchedDownElement
                view.currentMode = Mode.DRAG
                isDragCandidate = false
                touchedDownElement = null
            }
        }

        when (view.currentMode) {
            Mode.DRAG -> handleDragModeMove(x, y, elementsToModify)
            Mode.MULTI_TOUCH -> handleMultiTouchModeMove(event)
            Mode.ROTATE -> handleRotateModeMove(x, y, elementsToModify)
            Mode.RESIZE -> handleResizeModeMove(x, y, elementsToModify)
            Mode.TRANSFORM -> handleTransformModeMove(x, y)
            else -> {}
        }
    }

    private fun handleCanvasPanActionMove(event: MotionEvent) {
        if (view.currentMode == Mode.CANVAS_PAN && !view.isCanvasPanLocked) {
            if (event.pointerCount == 2) {
                val newDist = getPinchDistance(event)
                val factor = newDist / initialPinchDistance
                var newScale = (initialOverallScale * factor).coerceIn(0.5f, 3.0f)
                val snapTargets = listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f)
                val snapThreshold = 0.03f
                val snappedTarget = snapTargets.firstOrNull { abs(newScale - it) <= snapThreshold }
                if (snappedTarget != null) {
                    if (view.overallScale != snappedTarget) view.vibrateSoft()
                    newScale = snappedTarget
                }

                val pivotX = view.width / 2f
                val pivotY = view.height / 2f
                val scaleFactor = newScale / initialOverallScale
                view.overallOffsetX = initialOffsetXAtPinch + (pinchFocusX - pivotX) * (1f - scaleFactor)
                view.overallOffsetY = initialOffsetYAtPinch + (pinchFocusY - pivotY) * (1f - scaleFactor)

                view.overallScale = newScale
                view.clampOverallPan()
                view.suppressZoomCallback = true
                view.onZoomChanged?.invoke(view.overallScale)
                view.suppressZoomCallback = false
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

    private fun handleDragModeMove(x: Float, y: Float, elementsToModify: List<CanvasElement>) {
        val dx = x - touchStartX
        val dy = y - touchStartY

        elementsToModify.forEach { element ->
            if (element.type == ElementType.BACKGROUND && element.bitmap != null) {
                val (xRange, yRange) = view.computeBackgroundPanBounds(element)
                val newX = element.x + dx
                val newY = element.y + dy

                if (!view.allowFreeDrag) {
                    if (newX in xRange && newY in yRange) {
                        element.x = newX.coerceIn(xRange)
                        element.y = newY.coerceIn(yRange)
                    } else {
                        view.allowFreeDrag = true
                        element.x = newX
                        element.y = newY
                    }
                } else {
                    element.x = newX
                    element.y = newY
                }
            } else {
                element.x += dx
                element.y += dy
            }
            view.onElementChanged?.invoke(element)
        }

        if (view.selectedElements.isNotEmpty()) {
            view.checkDragSnap()
        } else {
            view.showVerticalGuide = false
            view.showHorizontalGuide = false
        }

        touchStartX = x
        touchStartY = y
        view.invalidate()
    }

    private fun handleMultiTouchModeMove(event: MotionEvent) {
        if (event.pointerCount >= 2) {
            val newPinchDistance = getPinchDistance(event)
            val newPinchAngle = getPinchAngle(event)

            if (initialPinchDistance > 0) {
                val scaleFactor = newPinchDistance / initialPinchDistance
                view.selectedElements.filter { !it.isLocked }.forEach { element ->
                    val minOnScreenPx = 20f * view.resources.displayMetrics.density
                    val logicalW = element.getLocalContentWidth().takeIf { it > 0 } ?: 1f
                    val minScale = (minOnScreenPx / (logicalW * view.scale * view.overallScale)).coerceAtMost(0.01f)

                    val newScale = (initialScale * scaleFactor).coerceIn(minScale, 100f)
                    element.scale = newScale
                    view.onElementChanged?.invoke(element)
                }
            }

            val rotationDelta = newPinchAngle - initialPinchAngle
            view.selectedElements.filter { !it.isLocked }.forEach { element ->
                element.rotation = (initialRotation + rotationDelta) % 360
                view.onElementChanged?.invoke(element)
            }

            view.checkDragSnap()

            if (view.selectedElements.size == 1) {
                view.checkRotationAlignment(view.selectedElements.first())
            } else {
                view.checkGroupRotationAlignment()
            }
            view.invalidate()
        }
    }

    private fun handleRotateModeMove(x: Float, y: Float, elementsToModify: List<CanvasElement>) {
        if (view.selectedElements.isEmpty()) return

        view.isRotating = true
        val currentAngle = atan2(y - initialGroupPivotY, x - initialGroupPivotX)
        val deltaAngle = Math.toDegrees((currentAngle - initialAngle).toDouble()).toFloat()

        elementsToModify.forEach { element ->
            val initialRotation = initialElementRotations[element.id] ?: element.rotation
            element.rotation = (initialRotation + deltaAngle) % 360

            val initialRelativeX = initialElementPositionsRelativeToGroupPivot[element.id]?.first ?: 0f
            val initialRelativeY = initialElementPositionsRelativeToGroupPivot[element.id]?.second ?: 0f

            val rotatedRelativeX = (initialRelativeX * cos(Math.toRadians(deltaAngle.toDouble()))) -
                    (initialRelativeY * sin(Math.toRadians(deltaAngle.toDouble())))
            val rotatedRelativeY = (initialRelativeX * sin(Math.toRadians(deltaAngle.toDouble()))) +
                    (initialRelativeY * cos(Math.toRadians(deltaAngle.toDouble())))

            element.x = initialGroupPivotX + rotatedRelativeX.toFloat()
            element.y = initialGroupPivotY + rotatedRelativeY.toFloat()

            view.onElementChanged?.invoke(element)
        }

        clampRotatedGroupBounds(elementsToModify)

        if (view.selectedElements.size == 1) {
            view.checkRotationAlignment(view.selectedElements.first())
        } else if (view.selectedElements.size > 1) {
            view.checkGroupRotationAlignment()
        }

        view.invalidate()
    }

    private fun clampRotatedGroupBounds(elementsToModify: List<CanvasElement>) {
        val newCombinedBounds = view.getCombinedSelectedBounds()
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
                view.onElementChanged?.invoke(element)
            }
            initialGroupPivotX += translationX
            initialGroupPivotY += translationY
        }
    }

    private fun handleResizeModeMove(x: Float, y: Float, elementsToModify: List<CanvasElement>) {
        if (view.selectedElements.isEmpty()) return

        val combined = view.getCombinedSelectedBounds()
        val pivotX = combined.centerX()
        val pivotY = combined.centerY()
        val currentDist = hypot(x - pivotX, y - pivotY)

        if (resizeStartDist > 0) {
            val scaleFactor = currentDist / resizeStartDist
            elementsToModify.forEach { element ->
                val initialScale = resizeInitialScales[element.id] ?: element.scale

                val minOnScreenPx = 20f * view.resources.displayMetrics.density
                val logicalW = element.getLocalContentWidth().takeIf { it > 0 } ?: 1f
                val minScale = (minOnScreenPx / (logicalW * view.scale * view.overallScale)).coerceAtMost(0.01f)

                val newScale = (initialScale * scaleFactor).coerceIn(minScale, 100f)
                element.scale = newScale

                val lastSignX = resizeLastSignX[element.id] ?: 0f
                val currSignX = (x - pivotX).sign
                if (currSignX != 0f && currSignX != lastSignX) {
                    element.isFlippedX = !element.isFlippedX
                    view.onElementChanged?.invoke(element)
                    resizeLastSignX[element.id] = currSignX
                }

                val lastSignY = resizeLastSignY[element.id] ?: 0f
                val currSignY = (y - pivotY).sign
                if (currSignY != 0f && currSignY != lastSignY) {
                    element.isFlippedY = !element.isFlippedY
                    view.onElementChanged?.invoke(element)
                    resizeLastSignY[element.id] = currSignY
                }

                view.onElementChanged?.invoke(element)
            }
        }
        view.invalidate()
    }

    private fun handleTransformModeMove(x: Float, y: Float) {
        if (view.selectedElements.isEmpty()) return

        val dx = x - touchStartX
        val dy = y - touchStartY

        view.selectedElements.forEach { element ->
            val (initialW, initialH) = view.initialElementSizes[element.id] ?: return@forEach
            val newW = (initialW - dx).coerceAtLeast(10f)
            val newH = (initialH + dy).coerceAtLeast(10f)

            element.logicalContentWidth = newW
            element.logicalContentHeight = newH
            view.onElementChanged?.invoke(element)
        }

        view.invalidate()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleActionUp(x: Float, y: Float, event: MotionEvent) {
        view.showVerticalGuide = false
        view.showHorizontalGuide = false
        view.showRotationVerticalGuide = false
        view.showRotationHorizontalGuide = false
        view.showCanvasCenterVerticalSnap = false
        view.showCanvasCenterHorizontalSnap = false

        if (view.currentMode == Mode.CANVAS_PAN) {
            view.currentMode = Mode.NONE
        }

        if (view.currentMode == Mode.TRANSFORM) {
            view.selectedElements.forEach {
                view.onElementChanged?.invoke(it)
                view.onEndBatchUpdate?.invoke(it.id)
            }
        }

        if (view.currentMode == Mode.DRAG || view.currentMode == Mode.ROTATE || view.currentMode == Mode.RESIZE) {
            view.selectedElements.filter { !it.isLocked }.forEach {
                view.onElementChanged?.invoke(it)
                view.onEndBatchUpdate?.invoke(it.id)
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

        resetTouchState()
    }

    private fun resetTouchState() {
        isDragCandidate = false
        touchedDownElement = null
        iconTouched = null
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
        initialElementPositionsRelativeToGroupPivot.clear()
        initialAngle = 0f
        initialGroupPivotX = 0f
        initialGroupPivotY = 0f

        if (view.currentMode != Mode.GROUP_EDIT) {
            lastTouchedElement = null
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
    }
}
