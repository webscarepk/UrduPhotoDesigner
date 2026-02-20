package com.webscare.urducanvas.common.canvas.sealed

import com.webscare.urducanvas.common.canvas.model.CanvasElement

sealed class BatchedCanvasAction {
    data class DragBatch(val elementId: String, val initialElement: CanvasElement) : BatchedCanvasAction()
    data class RotateBatch(val elementId: String, val initialElement: CanvasElement) : BatchedCanvasAction()
    data class ResizeBatch(val elementId: String, val initialElement: CanvasElement) : BatchedCanvasAction()
}