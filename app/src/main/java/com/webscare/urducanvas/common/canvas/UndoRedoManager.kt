package com.webscare.urducanvas.common.canvas

import com.webscare.urducanvas.common.canvas.model.CanvasAction
import java.util.Stack

class UndoRedoManager {
    private val canvasActions = Stack<CanvasAction>()
    private val redoStack = Stack<CanvasAction>()

    fun pushAction(action: CanvasAction) {
        canvasActions.push(action)
        redoStack.clear()
    }

    fun canUndo(): Boolean = canvasActions.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun popUndo(): CanvasAction? = if (canvasActions.isNotEmpty()) canvasActions.pop() else null
    fun pushRedo(action: CanvasAction) {
        redoStack.push(action)
    }

    fun popRedo(): CanvasAction? = if (redoStack.isNotEmpty()) redoStack.pop() else null
    fun pushUndo(action: CanvasAction) {
        canvasActions.push(action)
    }

    fun removeAllActions(predicate: (CanvasAction) -> Boolean) {
        canvasActions.removeAll(predicate)
        redoStack.removeAll(predicate)
    }

    fun clear() {
        canvasActions.clear()
        redoStack.clear()
    }

    fun clearRedo() {
        redoStack.clear()
    }

    fun getActions(): List<CanvasAction> = canvasActions.toList()
}
