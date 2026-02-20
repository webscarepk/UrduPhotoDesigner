package com.webscare.urducanvas.common.canvas.sealed

import android.graphics.Bitmap
import com.webscare.urducanvas.common.canvas.enums.TextAlignment
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.common.canvas.model.GradientItem
import com.webscare.urducanvas.data.model.FontEntity

sealed class CanvasAction {
    data class UpdateElement(
        val elementId: String,
        val newElement: CanvasElement,
        val oldElement: CanvasElement
    ) : CanvasAction()

    data class SetBackgroundColor(val color: Int, val previousColor: Int) : CanvasAction()
    data class SetBackgroundImage(val bitmap: Bitmap?, val previousBitmap: Bitmap?) : CanvasAction()
    data class SetBackgroundGradient(
        val gradientItem: GradientItem, val prevGradientItem: GradientItem?
    ) : CanvasAction()

    data class AddDrawStroke(val element: CanvasElement) : CanvasAction()
    data class AddShape(val element: CanvasElement) : CanvasAction()
    data class AddSticker(val sticker: CanvasElement) : CanvasAction()
    data class AddText(val text: String, val element: CanvasElement) : CanvasAction()

    // Modified SetFont to store a list of affected elements and their old font IDs
    data class SetFont(
        val newFontEntity: FontEntity,
        val affectedElements: List<Pair<String, String?>>
    ) : CanvasAction()

    data class SetTextColor(val color: Int, val previousColor: Int, val elementId: String) :
        CanvasAction()

    data class SetTextSize(val size: Float, val previousSize: Float, val elementId: String) :
        CanvasAction()

    data class SetTextAlignment(
        val alignment: TextAlignment,
        val previousAlignment: TextAlignment,
        val elementId: String
    ) : CanvasAction()

    data class SetOpacity(val opacity: Int, val previousOpacity: Int, val elementId: String) :
        CanvasAction()

    data class UpdateText(val elementId: String, val text: String, val previousText: String) :
        CanvasAction()

    data class RemoveElement(val element: CanvasElement) : CanvasAction()

    data class UpdateCanvasElementsOrder(
        val oldList: List<CanvasElement>,
        val newList: List<CanvasElement>
    ) : CanvasAction()

    data class SetCanvasSize(val newSize: CanvasSize, val oldSize: CanvasSize) : CanvasAction()

    data class ApplyImageFilter(
        val elementId: String,
        val newFilter: ImageFilter?,
        val oldFilter: ImageFilter?
    ) : CanvasAction()
}