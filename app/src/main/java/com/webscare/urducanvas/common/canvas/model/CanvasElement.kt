package com.webscare.urducanvas.common.canvas.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import com.google.gson.annotations.SerializedName
import com.webscare.urducanvas.common.canvas.enums.BlendType
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.FeatherDirection
import com.webscare.urducanvas.common.canvas.enums.LabelShape
import com.webscare.urducanvas.common.canvas.enums.LetterCasing
import com.webscare.urducanvas.common.canvas.enums.ListStyle
import com.webscare.urducanvas.common.canvas.enums.ShapeType
import com.webscare.urducanvas.common.canvas.enums.TextAlignment
import com.webscare.urducanvas.common.canvas.enums.TextDecoration
import com.webscare.urducanvas.common.canvas.sealed.ImageFilter
import com.webscare.urducanvas.common.utils.KashidaProcessor
import com.webscare.urducanvas.common.utils.ShapeRenderUtils
import java.io.Serializable
import java.util.UUID

data class CanvasElement(
    // Context is transient and should not be serialized. It will be re-provided on load.
    @field:Transient var context: Context? = null, // Made nullable for deserialization

    @SerializedName("type") var type: ElementType?,
    @SerializedName("customName") var customName: String? = null,
    @Transient  // or @Exclude if using Gson's @Expose
    @com.google.gson.annotations.JsonAdapter(value = Nothing::class) // skip serialization
    var svgDrawable: android.graphics.drawable.PictureDrawable? = null,

    @SerializedName("svgData")
    var svgData: String? = null,

    @SerializedName("text") var text: String = "",

    @SerializedName("bitmap") @field:Transient var bitmap: Bitmap? = null,

    @SerializedName("bitmapData") var bitmapData: String? = null,

    @SerializedName("groupId") var groupId: String? = null,

    @SerializedName("imageFilter") var imageFilter: ImageFilter = ImageFilter.None,

    @SerializedName("adjustments") var adjustments: AdjustmentValues = AdjustmentValues(),

    @SerializedName("x") var x: Float = 0f,

    @SerializedName("y") var y: Float = 0f,

    @SerializedName("scale") var scale: Float = 1f,

    @SerializedName("rotation") var rotation: Float = 0f,

    @SerializedName("hasOverlay") var hasOverlay: Boolean = false,

    @SerializedName("overlayColor") var overlayColor: Int = Color.YELLOW,

    @SerializedName("overlayOpacity") var overlayOpacity: Int = 255,

    @SerializedName("overlayGradient") var overlayGradient: GradientItem? = null,

    @SerializedName("id") val id: String = UUID.randomUUID().toString(),

    @SerializedName("isLocked") var isLocked: Boolean = false,

    @SerializedName("hasMask") var hasMask: Boolean = false,

    @SerializedName("hasLight") var hasLight: Boolean = false,

    @SerializedName("hasColor") var hasColor: Boolean = false,

    @SerializedName("hasDetail") var hasDetail: Boolean = false,

    @SerializedName("zIndex") var zIndex: Int = 0,

    @SerializedName("isSelected") var isSelected: Boolean = false,

    @SerializedName("fontId") var fontId: String? = null,

    @SerializedName("paintColor") var paintColor: Int = Color.BLACK,

    @SerializedName("paintTextSize") var paintTextSize: Float = 80f,

    @SerializedName("paintAlpha") var paintAlpha: Int = 255,

    @SerializedName("hasStroke") var hasStroke: Boolean = false,

    @SerializedName("strokeColor") var strokeColor: Int = Color.BLACK,

    @SerializedName("strokeWidth") var strokeWidth: Float = 1f,

    @SerializedName("hasShadow") var hasShadow: Boolean = false,

    @SerializedName("shadowColor") var shadowColor: Int = Color.GRAY,

    @SerializedName("shadowDx") var shadowDx: Float = 1f,

    @SerializedName("shadowDy") var shadowDy: Float = 1f,

    @SerializedName("shadowRadius") var shadowRadius: Float = 1f,

    @SerializedName("shadowOpacity") var shadowOpacity: Int = 1,

    @SerializedName("hasLabel") var hasLabel: Boolean = false,

    @SerializedName("labelColor") var labelColor: Int = Color.YELLOW,

    @SerializedName("labelShape") var labelShape: LabelShape = LabelShape.RECTANGLE_FILL,

    @SerializedName("lineSpacing") var lineSpacing: Float = 1.0f,

    @SerializedName("letterSpacing") var letterSpacing: Float = 0f,

    @SerializedName("letterCasing") var letterCasing: LetterCasing = LetterCasing.NONE,

    @SerializedName("textDecoration") var textDecoration: Set<TextDecoration> = emptySet(),

    @SerializedName("alignment") var alignment: TextAlignment = TextAlignment.CENTER,

    @SerializedName("currentIndent") var currentIndent: Float = 0f,

    @SerializedName("listStyle") var listStyle: ListStyle = ListStyle.NONE,

    @SerializedName("fillGradient")
    // text fill gradient
    var fillGradient: GradientItem? = null,

    @SerializedName("strokeGradient")
    // text stroke gradient
    var strokeGradient: GradientItem? = null,

    @SerializedName("labelGradient")
    // text label gradient
    var labelGradient: GradientItem? = null,

    @SerializedName("originalTypeface") @field:Transient var originalTypeface: Typeface? = null,

    @SerializedName("hasBlur") var hasBlur: Boolean = false,

    @SerializedName("blurValue") var blurValue: Float = 0f,

    @SerializedName("hasFeather") var hasFeather: Boolean = false,

    @SerializedName("featherRadius") var featherRadius: Float = 0f,

    @SerializedName("featherWidth") var featherWidth: Float = 50f,

    @SerializedName("featherDirection")
    var featherDirection: FeatherDirection? = FeatherDirection.ALL,

    @SerializedName("blendType") var blendType: BlendType = BlendType.NORMAL,

    @SerializedName("isVisible") var isVisible: Boolean = true,

    @SerializedName("backgroundColor") var backgroundColor: Int = Color.WHITE,

    @SerializedName("logicalContentWidth") var logicalContentWidth: Float = 0f,

    @SerializedName("logicalContentHeight") var logicalContentHeight: Float = 0f,

    @SerializedName("isFlippedX") var isFlippedX: Boolean = false,

    @SerializedName("isFlippedY") var isFlippedY: Boolean = false,

    @SerializedName("kashidaSize") var kashidaSize: Int = 0,

    @SerializedName("drawStrokes") var drawStrokes: MutableList<StrokeData>? = null,

    @SerializedName("brushSettings") var brushSettings: BrushSettings? = null,

    @SerializedName("allowsStrokeEditing") var allowsStrokeEditing: Boolean = false,

    @SerializedName("shapeType") var shapeType: ShapeType? = null,

    @SerializedName("shapeFillColor") var shapeFillColor: Int = Color.TRANSPARENT,

    @SerializedName("shapeStrokeColor") var shapeStrokeColor: Int = Color.BLACK,

    @SerializedName("shapeStrokeWidth") var shapeStrokeWidth: Float = 6f,

    @SerializedName("shapeCornerRadius") var shapeCornerRadius: Float = 0f,

    @SerializedName("shapeHasFill") var shapeHasFill: Boolean = true,

    @SerializedName("shapeHasStroke") var shapeHasStroke: Boolean = true,

    @SerializedName("shapeHasCorner") var shapeHasCorner: Boolean = false,

    @SerializedName("shapeFillGradient") var shapeFillGradient: GradientItem? = null,

    @SerializedName("shapeStrokeGradient") var shapeStrokeGradient: GradientItem? = null,

    @SerializedName("imagePanX") var imagePanX: Float = 0f,

    @SerializedName("imagePanY") var imagePanY: Float = 0f,

    @SerializedName("imageScale") var imageScale: Float = 1f,

    @SerializedName("imageFitMode") var imageFitMode: String? = "cover",

    @SerializedName("isPremium") var isPremium: Boolean = false,

    @SerializedName("isSubscribed") var isSubscribed: Boolean = false,

    // ── Grouping ──────────────────────────────────────────────────────────────
    // Only meaningful when type == ElementType.GROUP.
    // Controls whether child rows are visible in the layers panel.
    @SerializedName("isGroupCollapsed") var isGroupCollapsed: Boolean = false,

    ) : Serializable {

    @field:Transient var cachedAdjustedBitmap: Bitmap? = null
    @field:Transient var isAdjustmentDirty: Boolean = true

    @field:Transient
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
        } else if (type == ElementType.SHAPE) {
            logicalContentWidth
        } else if (type == ElementType.TEXT) {
            val lines = getTextWithKashida().split("\n")
            if (::paint.isInitialized) {
                lines.maxOfOrNull { line -> paint.measureText(line) } ?: 0f
            } else {
                0f
            }
        } else {
            if (svgDrawable != null) {
                logicalContentWidth.takeIf { it > 0 }
                    ?: svgDrawable?.picture?.width?.toFloat()?.takeIf { it > 0 }
                    ?: 0f
            } else {
                // ── Prefer logicalContentWidth over bitmap.width ──────────────
                // bitmap.width changes after JPEG encode/decode: bitmapToBase64
                // downsamples a 4000px image to ~1920px. After undo/reload, the
                // decoded bitmap is smaller, so draw rect shrinks and visual size
                // drops even though element.scale is correct.
                // logicalContentWidth stores the ORIGINAL pixel dimensions set
                // at add-time (serialized to JSON), so it survives encode/decode
                // and keeps visual size consistent across undo/redo/reload.
                logicalContentWidth.takeIf { it > 0 }
                    ?: bitmap?.width?.toFloat()
                    ?: 0f
            }
        }
    }

    fun getLocalContentHeight(): Float {
        return if (type == ElementType.BACKGROUND) {
            logicalContentHeight
        } else if (type == ElementType.SHAPE) {
            logicalContentHeight
        } else if (type == ElementType.TEXT) {
            if (::paint.isInitialized) {
                val fm = paint.fontMetrics
                val lineHeight = (fm.bottom - fm.top) * lineSpacing
                val lines = getTextWithKashida().split("\n")
                lines.size * lineHeight
            } else {
                0f
            }
        } else {
            if (svgDrawable != null) {
                logicalContentHeight.takeIf { it > 0 }
                    ?: svgDrawable?.picture?.height?.toFloat()?.takeIf { it > 0 }
                    ?: 0f
            } else {
                // ── Prefer logicalContentHeight over bitmap.height ────────────
                // Same reason as getLocalContentWidth above.
                logicalContentHeight.takeIf { it > 0 }
                    ?: bitmap?.height?.toFloat()
                    ?: 0f
            }
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
            val lineHeight = (fm.descent - fm.ascent) * lineSpacing

            val tempRect = Rect()
            var maxLineWidth = 0f

            for (line in lines) {
                if (line.isEmpty()) continue
                paint.getTextBounds(line, 0, line.length, tempRect)
                maxLineWidth = maxOf(maxLineWidth, tempRect.width().toFloat())
            }

            val totalHeight = lines.size * lineHeight

            bounds.set(
                -maxLineWidth / 2f, -totalHeight / 2f, maxLineWidth / 2f, totalHeight / 2f
            )
        } else if (type == ElementType.DRAW) {
            val drawBounds = getDrawBounds()
            bounds.set(drawBounds)
        } else if (type == ElementType.SHAPE) {
            val visual = ShapeRenderUtils.computeVisualBounds(
                shapeType ?: ShapeType.RECTANGLE,
                logicalContentWidth,
                logicalContentHeight,
                shapeStrokeWidth
            )
            bounds.set(visual)
        } else {
            // Image / SVG / Background — full logical rect
            val w = getLocalContentWidth()
            val h = getLocalContentHeight()
            bounds.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        }

        // ✅ Type-specific padding
        val totalPadding = when (type) {
            ElementType.TEXT -> {
                val basePadding = 6f
                val dynamicPadding = if (::paint.isInitialized) paint.textSize * 0.25f else 0f
                basePadding + dynamicPadding
            }
            ElementType.SHAPE -> {
                val strokePad = if (shapeHasStroke) shapeStrokeWidth / 2f else 0f
                strokePad + 12f
            }
            ElementType.DRAW -> 8f
            else -> 8f
        }

        bounds.inset(-totalPadding, -totalPadding)

        return bounds
    }

    fun getRotatedCorners(): FloatArray {
        val bounds = getTightTextBounds()

        val corners = floatArrayOf(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.top,
            bounds.right,
            bounds.bottom,
            bounds.left,
            bounds.bottom
        )

        // --- Normalize rotation into [0, 360)
        val normalizedRotation = ((rotation % 360) + 360) % 360

        val matrix = Matrix().apply {
            // ✅ include scale + flip exactly like drawCanvasElements()
            postScale(
                scale * if (isFlippedX) -1f else 1f, scale * if (isFlippedY) -1f else 1f
            )

            // ✅ Rotate around the element's true local center (0,0)
            if (normalizedRotation != 0f) postRotate(normalizedRotation)

            // ✅ Move into world space
            postTranslate(x, y)
        }

        matrix.mapPoints(corners)
        return corners
    }

    fun getDrawBounds(): RectF {
        // --- Case 1: Committed/rasterized draw element — has bitmap, no strokes
        if (bitmap != null && drawStrokes.isNullOrEmpty()) {
            val halfW = logicalContentWidth / 2f
            val halfH = logicalContentHeight / 2f
            return RectF(-halfW, -halfH, halfW, halfH)
        }

        // --- Case 2: Active session element — has absolute coordinate strokes, no bitmap
        val strokes = drawStrokes ?: return RectF(0f, 0f, 0f, 0f)
        if (strokes.isEmpty()) return RectF(0f, 0f, 0f, 0f)

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var hasValidStroke = false

        for (stroke in strokes) {
            if (stroke.path == null || stroke.path!!.isEmpty) {
                stroke.restorePath()
            }

            val path = stroke.path
            if (path == null || path.isEmpty) continue

            val pathBounds = RectF()
            try {
                path.computeBounds(pathBounds, true)
            } catch (e: Exception) {
                continue
            }

            // Expand bounds by half stroke thickness so selection outline hugs the stroke edges
            val expand = (stroke.thickness.takeIf { it.isFinite() } ?: 0f) * 0.5f
            pathBounds.inset(-expand, -expand)

            minX = minOf(minX, pathBounds.left)
            minY = minOf(minY, pathBounds.top)
            maxX = maxOf(maxX, pathBounds.right)
            maxY = maxOf(maxY, pathBounds.bottom)

            hasValidStroke = true
        }

        if (!hasValidStroke) return RectF(0f, 0f, 0f, 0f)

        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        val halfW = (maxX - minX) / 2f
        val halfY = (maxY - minY) / 2f
        return RectF(
            centerX - halfW,
            centerY - halfY,
            centerX + halfW,
            centerY + halfY
        )
    }
}