package com.example.urduphotodesigner.common.canvas.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import com.example.urduphotodesigner.common.canvas.enums.BlendType
import com.example.urduphotodesigner.common.canvas.enums.ElementType
import com.example.urduphotodesigner.common.canvas.enums.LabelShape
import com.example.urduphotodesigner.common.canvas.enums.LetterCasing
import com.example.urduphotodesigner.common.canvas.enums.ListStyle
import com.example.urduphotodesigner.common.canvas.enums.TextAlignment
import com.example.urduphotodesigner.common.canvas.enums.TextDecoration
import com.example.urduphotodesigner.common.canvas.sealed.ImageFilter
import com.example.urduphotodesigner.common.utils.KashidaProcessor
import java.io.Serializable
import java.util.UUID

private const val ICON_PADDING =
    10f // Or whatever value suits your icon sizes and visual preference

// Make CanvasElement Serializable to allow it to be passed via Bundles and saved.
data class CanvasElement(
    // Context is transient and should not be serialized. It will be re-provided on load.
    @Transient var context: Context? = null, // Made nullable for deserialization
    var type: ElementType,
    var text: String = "",
    // Bitmap is also transient. It needs to be handled separately for serialization (e.g., to Base64 or URI).
    @Transient var bitmap: Bitmap? = null,
    var bitmapData: String? = null,
    var groupId: String? = null,
    var imageFilter: ImageFilter = ImageFilter.None,
    var x: Float = 0f,
    var y: Float = 0f,
    var scale: Float = 1f,
    var rotation: Float = 0f,
    val id: String = UUID.randomUUID().toString(),
    var isLocked: Boolean = false,
    var zIndex: Int = 0,
    var isSelected: Boolean = false,
    var fontId: String? = null, // Store font ID for serialization
    // Properties of TextPaint for serialization
    var paintColor: Int = Color.BLACK,
    var paintTextSize: Float = 80f,
    var paintAlpha: Int = 255,
    // Border
    var hasStroke: Boolean = false,
    var strokeColor: Int = Color.BLACK,
    var strokeWidth: Float = 1f,

    // Shadow
    var hasShadow: Boolean = false,
    var shadowColor: Int = Color.GRAY,
    var shadowDx: Float = 1f,
    var shadowDy: Float = 1f,
    var shadowRadius: Float = 1f,
    var shadowOpacity: Int = 1,

    // Label
    var hasLabel: Boolean = false,
    var labelColor: Int = Color.YELLOW,
    var labelShape: LabelShape = LabelShape.RECTANGLE_FILL,

    var lineSpacing: Float = 1.0f,
    var letterSpacing: Float = 0f,
    var letterCasing: LetterCasing = LetterCasing.NONE,
    var textDecoration: Set<TextDecoration> = emptySet(),
    var alignment: TextAlignment = TextAlignment.CENTER,
    var currentIndent: Float = 0f,
    var listStyle: ListStyle = ListStyle.NONE,

    // text fill gradient
    var fillGradient: GradientItem? = null,

    // text stroke gradient
    var strokeGradient: GradientItem? = null,

    // text label gradient
    var labelGradient: GradientItem? = null,

    @Transient
    var originalTypeface: Typeface? = null,
    var hasBlur: Boolean = false,
    var blurValue: Float = 0f,
    var blendType: BlendType = BlendType.SRC,

    var isVisible: Boolean = true,
    var backgroundColor: Int = Color.WHITE,

    var logicalContentWidth: Float = 0f,
    var logicalContentHeight: Float = 0f,

    var isFlippedX: Boolean = false,
    var isFlippedY: Boolean = false,
    var kashidaSize: Int = 0
) : Serializable {

    @Transient
    lateinit var paint: TextPaint

    init {
        paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        updatePaintProperties()
    }

    fun updatePaintProperties() {
        if (!::paint.isInitialized) paint = TextPaint(Paint.ANTI_ALIAS_FLAG)

        // Basic properties
        paint.color = paintColor
        paint.textSize = paintTextSize
        paint.alpha = paintAlpha
    }

    fun getLocalContentWidth(): Float {
        return if (type == ElementType.BACKGROUND) {
            logicalContentWidth
        } else if (type == ElementType.TEXT) {
            val lines = getTextWithKashida().split("\n")
            // Ensure paint is initialized before using it
            if (::paint.isInitialized) {
                lines.maxOfOrNull { line -> paint.measureText(line) } ?: 0f
            } else {
                0f
            }
        } else {
            bitmap?.width?.toFloat() ?: 0f
        }
    }

    fun getLocalContentHeight(): Float {
        return if (type == ElementType.BACKGROUND) {
            logicalContentHeight
        } else if (type == ElementType.TEXT) {
            // Ensure paint is initialized before using it
            if (::paint.isInitialized) {
                val fm = paint.fontMetrics
                val lineHeight = (fm.bottom - fm.top) * lineSpacing
                val lines = getTextWithKashida().split("\n")
                lines.size * lineHeight
            } else {
                0f
            }
        } else {
            bitmap?.height?.toFloat() ?: 0f
        }
    }

    fun getIconPositions(): Map<String, PointF> {
        val bounds = getTightTextBounds() // use corrected tight bounds
        val halfW = bounds.width() / 2f
        val halfH = bounds.height() / 2f

        val iconOffsetX = halfW + ICON_PADDING
        val iconOffsetY = halfH + ICON_PADDING

        return mapOf(
            "delete" to PointF(iconOffsetX, -iconOffsetY),   // top-right
            "resize" to PointF(iconOffsetX, iconOffsetY),   // bottom-right
            "rotate" to PointF(-iconOffsetX, iconOffsetY),  // bottom-left
            "edit" to PointF(-iconOffsetX, -iconOffsetY)    // top-left
        )
    }

    fun getTextWithKashida(): String {
        return applyKashidaToText(text, kashidaSize)
    }

    private fun applyKashidaToText(inputText: String, size: Int): String {
        val kashidaProcessor = KashidaProcessor(insertionFreq = size)

        val typeface = paint.typeface
        return if (typeface != null) {
            kashidaProcessor.processSafe(inputText, typeface)
        } else {
            kashidaProcessor.process(inputText)
        }
    }

    fun getTightTextBounds(): RectF {
        val bounds = RectF()
        if (type == ElementType.TEXT && ::paint.isInitialized) {
            val lines = getTextWithKashida().split("\n")
            val fm = paint.fontMetrics

            // Correct line height with spacing
            val lineHeight = (fm.descent - fm.ascent) * lineSpacing

            var maxWidth = 0f

            for (line in lines) {
                if (line.isEmpty()) continue

                // Base width of all glyphs
                val baseWidth = paint.measureText(line)

                // Extra width contributed by letterSpacing
                val extraWidth = if (letterSpacing != 0f) {
                    (line.length - 1) * (letterSpacing * paint.textSize)
                } else 0f

                val totalWidth = baseWidth + extraWidth
                maxWidth = maxOf(maxWidth, totalWidth)
            }

            val totalHeight = lines.size * lineHeight

            bounds.set(
                -maxWidth / 2f,
                -totalHeight / 2f,
                maxWidth / 2f,
                totalHeight / 2f
            )
        } else {
            bounds.set(
                -getLocalContentWidth() / 2f,
                -getLocalContentHeight() / 2f,
                getLocalContentWidth() / 2f,
                getLocalContentHeight() / 2f
            )
        }

        val padding = 6f
        bounds.inset(-padding, -padding)

        return bounds
    }

    fun getRotatedCorners(): FloatArray {
        val bounds = getTightTextBounds()

        val corners = floatArrayOf(
            bounds.left, bounds.top,
            bounds.right, bounds.top,
            bounds.right, bounds.bottom,
            bounds.left, bounds.bottom
        )

        val matrix = Matrix().apply {
            // include scale + flip exactly like drawCanvasElements()
            postScale(
                scale * if (isFlippedX) -1f else 1f,
                scale * if (isFlippedY) -1f else 1f
            )
            postRotate(rotation)
            postTranslate(x, y)
        }

        matrix.mapPoints(corners)
        return corners
    }

}