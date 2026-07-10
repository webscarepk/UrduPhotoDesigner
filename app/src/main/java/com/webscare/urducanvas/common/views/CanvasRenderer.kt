package com.webscare.urducanvas.common.views

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.Mode
import com.webscare.urducanvas.common.canvas.model.CanvasElement

class CanvasRenderer(private val view: CanvasView) {
    private val backgroundRenderer = BackgroundRenderer(view)
    private val textRenderer = TextElementRenderer(view)
    private val shapeRenderer = ShapeElementRenderer(view)
    private val stickerRenderer = StickerElementRenderer(view)

    fun render(canvas: Canvas) {
        val pivotX = view.width / 2f
        val pivotY = view.height / 2f

        canvas.translate(view.overallOffsetX, view.overallOffsetY)
        canvas.scale(view.overallScale, view.overallScale, pivotX, pivotY)

        val scaledWidth = view.canvasWidth * view.scale
        val scaledHeight = view.canvasHeight * view.scale
        view.offsetX = (view.width - scaledWidth) / 2f
        view.offsetY = (view.height - scaledHeight) / 2f

        canvas.withTranslation(view.offsetX, view.offsetY) {
            scale(view.scale, view.scale)
            if (view.isDrawing) {
                view.drawCanvasShadow(this)
                drawCanvasElements(this, showOverlays = false, showCheckerboard = false)

                // Semi-transparent overlay on top
                drawRect(
                    0f,
                    0f,
                    view.canvasWidth.toFloat(),
                    view.canvasHeight.toFloat(),
                    view.drawingModeOverlayPaint,
                )

                // Draw all committed session strokes ABOVE the overlay
                view.activeSessionElement?.let { session ->
                    if (!session.drawStrokes.isNullOrEmpty()) {
                        view.drawDrawElement(this, session)
                    }
                }

                // Draw the current live in-progress stroke ABOVE everything
                if (view.currentStrokePath != null && view.currentStrokePaint != null) {
                    view.drawLivePreviewStroke(this)
                }
            } else {
                view.drawCanvasShadow(this)
                if (view.currentMode == Mode.GROUP_EDIT && view.activeGroupId != null) {
                    drawCanvasElements(this, showOverlays = false)
                    drawRect(
                        0f,
                        0f,
                        view.canvasWidth.toFloat(),
                        view.canvasHeight.toFloat(),
                        Paint().apply {
                            color = Color.argb(140, 255, 255, 255)
                            style = Paint.Style.FILL
                        },
                    )
                    // Re-draw group children at full opacity on top
                    val groupChildIds = view.canvasElements.filter { it.groupId == view.activeGroupId }.map { it.id }.toSet()
                    drawCanvasElements(this, showOverlays = true, showCheckerboard = false, isolatedIds = groupChildIds)
                } else {
                    // Normal render
                    drawCanvasElements(this)
                }
            }
        }

        // Guides & Overlays
        view.drawGuides(canvas)
    }

    private fun drawCanvasElements(
        canvas: Canvas,
        showOverlays: Boolean = true,
        showCheckerboard: Boolean = true,
        isolatedIds: Set<String>? = null,
    ) {
        canvas.save()
        val clipRect = RectF(0f, 0f, view.canvasWidth.toFloat(), view.canvasHeight.toFloat())
        canvas.clipRect(clipRect)

        if (showCheckerboard) {
            canvas.drawRect(0f, 0f, view.canvasWidth.toFloat(), view.canvasHeight.toFloat(), view.checkerPaint)
        }

        // Draw all elements
        view.canvasElements.forEach { element ->
            if (!element.isVisible) return@forEach
            if (element.type == ElementType.GROUP) return@forEach
            if (isolatedIds != null && element.id !in isolatedIds) return@forEach

            if (element.type == ElementType.BACKGROUND) {
                backgroundRenderer.draw(canvas, element)
                return@forEach
            }

            // Cover full-canvas image layer
            if (element.type == ElementType.IMAGE &&
                element.logicalContentWidth == view.canvasWidth.toFloat() &&
                element.logicalContentHeight == view.canvasHeight.toFloat() &&
                element.imageFitMode == "cover"
            ) {
                backgroundRenderer.draw(canvas, element)
                return@forEach
            }

            canvas.withTranslation(element.x, element.y) {
                rotate(element.rotation)
                val fx = if (element.isFlippedX) -1f else 1f
                val fy = if (element.isFlippedY) -1f else 1f
                scale(element.scale * fx, element.scale * fy)

                when (element.type) {
                    ElementType.DRAW -> {
                        if (element.bitmap != null) {
                            val bmp = element.bitmap!!
                            if (!bmp.isRecycled) {
                                val left = -bmp.width / 2f
                                val top = -bmp.height / 2f
                                val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    alpha = element.paintAlpha
                                    isFilterBitmap = true
                                }
                                canvas.drawBitmap(bmp, left, top, drawPaint)
                            }
                        } else {
                            view.drawDrawElement(canvas, element)
                        }
                    }
                    ElementType.SHAPE -> shapeRenderer.draw(canvas, element)
                    ElementType.TEXT -> textRenderer.draw(canvas, element)
                    else -> stickerRenderer.draw(canvas, element)
                }
            }
        }

        canvas.restore()

        if (showOverlays) {
            view.drawElementOverlays(canvas, showOverlays)
        }

        // Draw color picker if active
        if (showOverlays && view.isColorPickerMode) {
            val halfIcon = view.desiredPickerIconSizePx
            val bmp = view.colorPickerBitmap

            if (bmp != null && !bmp.isRecycled) {
                val px = view.pickerX.toInt().coerceIn(0, bmp.width - 1)
                val py = view.pickerY.toInt().coerceIn(0, bmp.height - 1)
                val pixelColor = bmp.getPixel(px, py)
                val dark = view.isColorDark(pixelColor)

                canvas.drawCircle(
                    view.pickerX,
                    view.pickerY - halfIcon * 3,
                    halfIcon + 20f,
                    Paint().apply {
                        color = pixelColor
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    },
                )

                canvas.drawCircle(
                    view.pickerX,
                    view.pickerY - halfIcon * 3,
                    halfIcon + 20f,
                    Paint().apply {
                        color = if (dark) Color.WHITE else Color.BLACK
                        style = Paint.Style.STROKE
                        strokeWidth = 4f
                    },
                )
            }

            // Crosshair cursor
            canvas.drawCircle(
                view.pickerX,
                view.pickerY,
                halfIcon / 4,
                Paint().apply {
                    color = Color.BLACK
                    style = Paint.Style.FILL
                    isAntiAlias = true
                },
            )
        }
    }
}
