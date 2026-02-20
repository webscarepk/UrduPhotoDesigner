package com.webscare.urducanvas.common.canvas

import android.graphics.Bitmap
import com.example.urduphotodesigner.common.canvas.model.CanvasElement
import com.example.urduphotodesigner.common.canvas.model.GradientItem
import com.example.urduphotodesigner.common.canvas.sealed.ImageFilter
import com.example.urduphotodesigner.common.views.CanvasView
import com.example.urduphotodesigner.data.model.FontEntity
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.canvas.model.GradientItem
import com.webscare.urducanvas.common.canvas.sealed.ImageFilter
import com.webscare.urducanvas.common.views.CanvasView
import com.webscare.urducanvas.data.model.FontEntity

class CanvasManager(private val canvasView: CanvasView) {
    fun setCanvasBackgroundColor(color: Int) {
        canvasView.setCanvasBackgroundColor(color)
    }

    fun setCanvasBackgroundImage(bitmap: Bitmap) {
        canvasView.setCanvasBackgroundImage(bitmap)
    }

    fun setCanvasBackgroundGradient(gradientItem: GradientItem) {
        canvasView.setCanvasBackgroundGradient(gradientItem)
    }

    fun setFont(fontEntity: FontEntity) {
        canvasView.setFont(fontEntity)
    }

    fun setOpacity(opacity: Int) {
        canvasView.setOpacity(opacity)
    }

    fun syncElements(newElements: List<CanvasElement>) {
        canvasView.syncElements(newElements)
    }

    fun applyImageFilter(filter: ImageFilter?) {
        canvasView.applyImageFilter(filter)
    }
}
