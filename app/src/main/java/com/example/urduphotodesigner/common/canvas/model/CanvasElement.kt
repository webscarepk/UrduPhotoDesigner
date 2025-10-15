package com.example.urduphotodesigner.common.canvas.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import com.example.urduphotodesigner.common.canvas.enums.BlendType
import com.example.urduphotodesigner.common.canvas.enums.ElementType
import com.example.urduphotodesigner.common.canvas.enums.LabelShape
import com.example.urduphotodesigner.common.canvas.enums.LetterCasing
import com.example.urduphotodesigner.common.canvas.enums.ListStyle
import com.example.urduphotodesigner.common.canvas.enums.ShapeType
import com.example.urduphotodesigner.common.canvas.enums.TextAlignment
import com.example.urduphotodesigner.common.canvas.enums.TextDecoration
import com.example.urduphotodesigner.common.canvas.sealed.ImageFilter
import com.example.urduphotodesigner.common.utils.KashidaProcessor
import java.io.Serializable
import java.util.UUID

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
    var adjustments: AdjustmentValues = AdjustmentValues(),
    var x: Float = 0f,
    var y: Float = 0f,
    var scale: Float = 1f,
    var rotation: Float = 0f,
    val id: String = UUID.randomUUID().toString(),
    var isLocked: Boolean = false,
    var zIndex: Int = 0,
    var isSelected: Boolean = false,
    var fontId: String? = null,
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
    var kashidaSize: Int = 0,
    var drawStrokes: MutableList<StrokeData>? = null,
    var brushSettings: BrushSettings? = null,
    var allowsStrokeEditing: Boolean = false,
    var shapeType: ShapeType? = null,
    var shapeFillColor: Int = Color.TRANSPARENT,
    var shapeStrokeColor: Int = Color.BLACK,
    var shapeStrokeWidth: Float = 6f,
    var shapeCornerRadius: Float = 0f,
    var shapeHasFill: Boolean = true,
    var shapeHasStroke: Boolean = true,
    var shapeFillGradient: GradientItem? = null,
    var shapeStrokeGradient: GradientItem? = null
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

            // True line height
            val lineHeight = (fm.descent - fm.ascent) * lineSpacing

            // Get actual text bounds for each line using getTextBounds()
            val tempRect = android.graphics.Rect()
            var maxLineWidth = 0f

            for (line in lines) {
                if (line.isEmpty()) continue
                paint.getTextBounds(line, 0, line.length, tempRect)
                maxLineWidth = maxOf(maxLineWidth, tempRect.width().toFloat())
            }

            val totalHeight = lines.size * lineHeight

            // === Instead of rotating here, keep bounds in logical space ===
            bounds.set(
                -maxLineWidth / 2f,
                -totalHeight / 2f,
                maxLineWidth / 2f,
                totalHeight / 2f
            )
        } else if (type == ElementType.DRAW && ::paint.isInitialized){
            val drawBounds = getDrawBounds()
            bounds.set(drawBounds)
        }
        else {
            bounds.set(
                -getLocalContentWidth() / 2f,
                -getLocalContentHeight() / 2f,
                getLocalContentWidth() / 2f,
                getLocalContentHeight() / 2f
            )
        }

        // Padding for selection outline
        val basePadding = 6f
        val dynamicPadding = paint.textSize * 0.25f
        val totalPadding = basePadding + dynamicPadding

        bounds.inset(-totalPadding, -totalPadding)

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

        // --- Normalize rotation into [0, 360)
        val normalizedRotation = ((rotation % 360) + 360) % 360

        val matrix = Matrix().apply {
            // ✅ include scale + flip exactly like drawCanvasElements()
            postScale(
                scale * if (isFlippedX) -1f else 1f,
                scale * if (isFlippedY) -1f else 1f
            )

            // ✅ Rotate around the element’s true local center (0,0)
            if (normalizedRotation != 0f)
                postRotate(normalizedRotation)

            // ✅ Move into world space
            postTranslate(x, y)
        }

        matrix.mapPoints(corners)
        return corners
    }

    fun getDrawBounds(): RectF {
        val strokes = drawStrokes ?: return RectF(0f, 0f, 0f, 0f)
        val bounds = RectF()

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        var hasValidStroke = false

        for (stroke in strokes) {
            val path = stroke.path
            val pathBounds = RectF()
            path.computeBounds(pathBounds, true)

            // Account for stroke thickness
            val expand = stroke.thickness * 0.5f
            pathBounds.inset(-expand, -expand)

            minX = minOf(minX, pathBounds.left)
            minY = minOf(minY, pathBounds.top)
            maxX = maxOf(maxX, pathBounds.right)
            maxY = maxOf(maxY, pathBounds.bottom)

            hasValidStroke = true
        }

        if (!hasValidStroke) return RectF(0f, 0f, 0f, 0f)

        // ✅ Convert to local-space centered bounds (like text)
        val width = maxX - minX
        val height = maxY - minY
        bounds.set(-width / 2f, -height / 2f, width / 2f, height / 2f)

        // ✅ Optional small padding for selection clarity
        bounds.inset(-6f, -6f)

        return bounds
    }
}