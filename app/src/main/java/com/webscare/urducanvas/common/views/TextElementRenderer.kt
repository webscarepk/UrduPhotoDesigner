package com.webscare.urducanvas.common.views

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.Log
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.canvas.enums.LabelShape
import com.webscare.urducanvas.common.canvas.enums.ListStyle
import com.webscare.urducanvas.common.canvas.enums.LetterCasing
import com.webscare.urducanvas.common.canvas.enums.TextAlignment
import com.webscare.urducanvas.common.canvas.enums.TextDecoration
import kotlin.math.min

class TextElementRenderer(private val view: CanvasView) {

    fun draw(canvas: Canvas, element: CanvasElement) {
        if (element.paintAlpha == 0) return

        val lines = element.getTextWithKashida().split("\n")
        val fm = try {
            element.paint.fontMetrics
        } catch (e: Exception) {
            Log.e("TextElementRenderer", "Failed to get font metrics for element", e)
            Paint.FontMetrics()
        }
        val lineHeight = (fm.descent - fm.ascent) * element.lineSpacing
        val totalHeight = lineHeight * lines.size

        // ----- DRAW LABEL -----
        if (element.hasLabel) {
            drawLabel(canvas, element, lines, totalHeight)
        }

        // ----- DRAW TEXT -----
        val baselineShift = (fm.ascent + fm.descent) / 2f
        var yOffset = -((lines.size - 1) * lineHeight / 2f) - baselineShift

        lines.forEachIndexed { i, rawLine ->
            drawTextLine(canvas, element, rawLine, i, yOffset)
            yOffset += lineHeight
        }
    }

    private fun drawLabel(canvas: Canvas, element: CanvasElement, lines: List<String>, totalHeight: Float) {
        val maxLineWidth = try {
            lines.maxOf { element.paint.measureText(it) }
        } catch (e: Exception) {
            Log.e("TextElementRenderer", "Failed to measure text lines for element", e)
            0f
        }
        val labelPadding = 16f
        val left = -maxLineWidth / 2f - labelPadding
        val top = -totalHeight / 2f - labelPadding
        val right = maxLineWidth / 2f + labelPadding
        val bottom = totalHeight / 2f + labelPadding

        val labelRect = RectF(left, top, right, bottom)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        if (element.labelGradient != null) {
            labelPaint.shader = view.createGradientShader(
                gradientItem = element.labelGradient!!,
                width = labelRect.width(),
                height = labelRect.height(),
            )
        } else {
            labelPaint.shader = null
            labelPaint.color = element.labelColor
        }

        val prevAlpha = labelPaint.alpha
        labelPaint.alpha = element.paintAlpha

        drawSpecificLabelShape(canvas, element.labelShape, labelRect, labelPaint)

        labelPaint.alpha = prevAlpha
    }

    private fun drawSpecificLabelShape(
        canvas: Canvas,
        labelShape: LabelShape,
        labelRect: RectF,
        labelPaint: Paint
    ) {
        when (labelShape) {
            LabelShape.RECTANGLE_FILL -> canvas.drawRect(labelRect, labelPaint)
            LabelShape.RECTANGLE_STROKE -> {
                labelPaint.style = Paint.Style.STROKE
                labelPaint.strokeWidth = 4f
                canvas.drawRect(labelRect, labelPaint)
            }
            LabelShape.OVAL_FILL -> canvas.drawOval(labelRect, labelPaint)
            LabelShape.OVAL_STROKE -> {
                labelPaint.style = Paint.Style.STROKE
                labelPaint.strokeWidth = 4f
                canvas.drawOval(labelRect, labelPaint)
            }
            LabelShape.CIRCLE_FILL -> {
                val r = min(labelRect.width(), labelRect.height()) / 2f
                canvas.drawCircle(labelRect.centerX(), labelRect.centerY(), r, labelPaint)
            }
            LabelShape.CIRCLE_STROKE -> {
                labelPaint.style = Paint.Style.STROKE
                labelPaint.strokeWidth = 4f
                val r = min(labelRect.width(), labelRect.height()) / 2f
                canvas.drawCircle(labelRect.centerX(), labelRect.centerY(), r, labelPaint)
            }
            LabelShape.ROUNDED_RECTANGLE_FILL -> {
                canvas.drawRoundRect(labelRect, 20f, 20f, labelPaint)
            }
            LabelShape.ROUNDED_RECTANGLE_STROKE -> {
                labelPaint.style = Paint.Style.STROKE
                labelPaint.strokeWidth = 4f
                canvas.drawRoundRect(labelRect, 20f, 20f, labelPaint)
            }
        }
    }

    private fun drawTextLine(
        canvas: Canvas,
        element: CanvasElement,
        rawLine: String,
        index: Int,
        yOffset: Float
    ) {
        val fillPaint = prepareTextPaint(element)
        val displayText = applyCasingAndListStyle(element, rawLine, index)

        val alignment = when (element.alignment) {
            TextAlignment.LEFT -> Paint.Align.LEFT
            TextAlignment.CENTER -> Paint.Align.CENTER
            TextAlignment.RIGHT -> Paint.Align.RIGHT
            TextAlignment.JUSTIFY -> Paint.Align.LEFT
        }
        fillPaint.textAlign = alignment

        val indentOffset = if (index == 0) element.currentIndent else 0f
        val xPos = when (alignment) {
            Paint.Align.LEFT -> -element.getLocalContentWidth() / 2f + indentOffset
            Paint.Align.CENTER -> 0f
            Paint.Align.RIGHT -> element.getLocalContentWidth() / 2f + indentOffset
        }

        if (element.fillGradient != null) {
            val w = fillPaint.measureText(displayText)
            fillPaint.shader = view.createGradientShader(element.fillGradient!!, w, fillPaint.textSize)
        }

        drawStrokeAndFillText(canvas, element, displayText, xPos, yOffset, fillPaint)
    }

    private fun prepareTextPaint(element: CanvasElement): TextPaint {
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = element.paintColor
            textSize = element.paintTextSize
            letterSpacing = element.letterSpacing
            style = Paint.Style.FILL
            isAntiAlias = true

            isUnderlineText = TextDecoration.UNDERLINE in element.textDecoration
            val baseTf = element.paint.typeface ?: Typeface.DEFAULT
            val bold = TextDecoration.BOLD in element.textDecoration
            val italic = TextDecoration.ITALIC in element.textDecoration
            val style = when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            typeface = Typeface.create(baseTf, style)

            if (element.hasBlur) {
                maskFilter = BlurMaskFilter(element.blurValue, BlurMaskFilter.Blur.NORMAL)
            }
            xfermode = view.drawWithBlend(element)
        }
    }

    private fun applyCasingAndListStyle(element: CanvasElement, rawLine: String, index: Int): String {
        val text = when (element.listStyle) {
            ListStyle.BULLETED -> "• $rawLine"
            ListStyle.NUMBERED -> "${index + 1}. $rawLine"
            else -> rawLine
        }

        return when (element.letterCasing) {
            LetterCasing.ALL_CAPS -> text.uppercase()
            LetterCasing.LOWER_CASE -> text.lowercase()
            LetterCasing.TITLE_CASE -> text.split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            else -> text
        }
    }

    private fun drawStrokeAndFillText(
        canvas: Canvas,
        element: CanvasElement,
        displayText: String,
        xPos: Float,
        yOffset: Float,
        fillPaint: TextPaint
    ) {
        if (element.hasShadow) {
            val sc = (element.shadowColor and 0x00FFFFFF) or (element.shadowOpacity shl 24)
            val sp = TextPaint(fillPaint).apply {
                shader = null
                color = sc
                maskFilter = BlurMaskFilter(element.shadowRadius, BlurMaskFilter.Blur.NORMAL)
            }
            val sa = sp.alpha
            sp.alpha = element.shadowOpacity
            canvas.drawText(displayText, xPos + element.shadowDx, yOffset + element.shadowDy, sp)
            sp.alpha = sa
        }

        if (element.alignment == TextAlignment.JUSTIFY) {
            element.paint = fillPaint
            view.justifyText(canvas, displayText, yOffset, element)
        } else {
            if (element.hasStroke && element.strokeWidth > 0f) {
                drawTextStroke(canvas, element, displayText, xPos, yOffset, fillPaint)
            }

            val oldFillAlpha = fillPaint.alpha
            fillPaint.alpha = element.paintAlpha
            canvas.drawText(displayText, xPos, yOffset, fillPaint)
            fillPaint.alpha = oldFillAlpha
        }
    }

    private fun drawTextStroke(
        canvas: Canvas,
        element: CanvasElement,
        displayText: String,
        xPos: Float,
        yOffset: Float,
        fillPaint: TextPaint
    ) {
        val strokePaint = TextPaint(fillPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = element.strokeWidth
        }
        if (element.strokeGradient != null) {
            val w = fillPaint.measureText(displayText)
            strokePaint.shader = view.createGradientShader(element.strokeGradient!!, w, fillPaint.textSize)
        } else {
            strokePaint.color = element.strokeColor
        }
        val old = strokePaint.alpha
        strokePaint.alpha = element.paintAlpha
        canvas.drawText(displayText, xPos, yOffset, strokePaint)
        strokePaint.alpha = old
    }
}
