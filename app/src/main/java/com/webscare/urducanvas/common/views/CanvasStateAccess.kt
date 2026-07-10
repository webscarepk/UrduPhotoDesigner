package com.webscare.urducanvas.common.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.webscare.urducanvas.common.canvas.enums.Mode
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import java.util.concurrent.CopyOnWriteArrayList

interface CanvasStateAccess {
    val stateContext: Context
    val canvasWidth: Int
    val canvasHeight: Int
    val overallScale: Float
    val overallOffsetX: Float
    val overallOffsetY: Float
    val scale: Float
    val offsetX: Float
    val offsetY: Float
    var allowFreeDrag: Boolean
    val canvasElements: CopyOnWriteArrayList<CanvasElement>
    val selectedElements: CopyOnWriteArrayList<CanvasElement>
    val cacheManager: CanvasCacheManager
    val lastDrawnIconRect: MutableMap<String, RectF>

    val isDrawing: Boolean
    val activeSessionElement: CanvasElement?
    val currentStrokePath: Path?
    val currentStrokePaint: Paint?
    var activeGroupId: String?
    var currentMode: Mode

    val showVerticalGuide: Boolean
    val showHorizontalGuide: Boolean
    val showRotationVerticalGuide: Boolean
    val showRotationHorizontalGuide: Boolean
    val showCanvasCenterVerticalSnap: Boolean
    val showCanvasCenterHorizontalSnap: Boolean
    val showGrid: Boolean
    val showRuler: Boolean

    val isColorPickerMode: Boolean
    val colorPickerBitmap: Bitmap?
    val pickerX: Float
    val pickerY: Float
    val desiredPickerIconSizePx: Float
    fun isColorDark(color: Int): Boolean

    fun invalidate()
}
