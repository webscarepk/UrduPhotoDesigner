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
    @SerializedName("tag") var tag: String? = null,
    @Transient  // or @Exclude if using Gson's @Expose
    @com.google.gson.annotations.JsonAdapter(value = Nothing::class) // skip serialization
    var svgDrawable: android.graphics.drawable.PictureDrawable? = null,

    @SerializedName("svgData")
    var svgData: String? = null,

    @SerializedName("applyWhiteTintInDarkMode") var applyWhiteTintInDarkMode: Boolean = false,

    @SerializedName("text") var text: String = "",

    @SerializedName("bitmap") @field:Transient var bitmap: Bitmap? = null,

    @SerializedName("bitmapData") var bitmapData: String? = null,

    @SerializedName("groupId") var groupId: String? = null,
    @SerializedName("isGroupCollapsed") var isGroupCollapsed: Boolean = false,

    @SerializedName("imageFilter") var imageFilter: ImageFilter = ImageFilter.None,

    @SerializedName("filterIntensity") var filterIntensity: Float = 1.0f,

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

    @SerializedName("fontUrl") var fontUrl: String? = null,

    @SerializedName("boxWidth") var boxWidth: Float? = null,

    @SerializedName("boxHeight") var boxHeight: Float? = null,

    @SerializedName("paintColor") var paintColor: Int = Color.BLACK,

    @SerializedName("paintTextSize") var paintTextSize: Float = 80f,

    @SerializedName("paintAlpha") var paintAlpha: Int = 255,

    @SerializedName("hasStroke") var hasStroke: Boolean = false,

    @SerializedName("strokeColor") var strokeColor: Int = Color.BLACK,

    @SerializedName("strokeWidth") var strokeWidth: Float = 1f,

    @SerializedName("hasUnderStroke") var hasUnderStroke: Boolean = false,

    @SerializedName("underStrokeColor") var underStrokeColor: Int = Color.BLACK,

    @SerializedName("underStrokeWidth") var underStrokeWidth: Float = 0f,

    @SerializedName("has3dExtrude") var has3dExtrude: Boolean = false,

    @SerializedName("extrudeColor") var extrudeColor: Int = Color.BLACK,

    @SerializedName("extrudeDepth") var extrudeDepth: Float = 0f,

    @SerializedName("extrudeDx") var extrudeDx: Float = 4f,

    @SerializedName("extrudeDy") var extrudeDy: Float = 4f,

    @SerializedName("hasDoubleExtrude") var hasDoubleExtrude: Boolean = false,

    @SerializedName("extrudeStep2Color") var extrudeStep2Color: Int = Color.BLACK,

    @SerializedName("extrudeStep2Depth") var extrudeStep2Depth: Float = 0f,

    @SerializedName("extrudeStep2Dx") var extrudeStep2Dx: Float = 8f,

    @SerializedName("extrudeStep2Dy") var extrudeStep2Dy: Float = 8f,

    @SerializedName("hasAnaglyph") var hasAnaglyph: Boolean = false,

    @SerializedName("anaglyphOffset") var anaglyphOffset: Float = 4f,

    @SerializedName("anaglyphColor1") var anaglyphColor1: Int = Color.parseColor("#FF0055"),

    @SerializedName("anaglyphColor2") var anaglyphColor2: Int = Color.parseColor("#00E5FF"),

    @SerializedName("hasBevel") var hasBevel: Boolean = false,

    @SerializedName("bevelHighlightColor") var bevelHighlightColor: Int = Color.parseColor("#80FFFFFF"),

    @SerializedName("bevelShadowColor") var bevelShadowColor: Int = Color.parseColor("#80000000"),

    @SerializedName("bevelDepth") var bevelDepth: Float = 2.5f,

    @SerializedName("hasEmboss") var hasEmboss: Boolean = false,

    @SerializedName("isDebossed") var isDebossed: Boolean = false,

    @SerializedName("embossDepth") var embossDepth: Float = 2.0f,

    @SerializedName("embossHighlightColor") var embossHighlightColor: Int = Color.parseColor("#80FFFFFF"),

    @SerializedName("embossShadowColor") var embossShadowColor: Int = Color.parseColor("#80000000"),

    @SerializedName("hasOuterGlow") var hasOuterGlow: Boolean = false,

    @SerializedName("outerGlowColor") var outerGlowColor: Int = Color.parseColor("#00E5FF"),

    @SerializedName("outerGlowRadius") var outerGlowRadius: Float = 12f,

    @SerializedName("outerGlowOpacity") var outerGlowOpacity: Int = 255,

    @SerializedName("hasInnerGlow") var hasInnerGlow: Boolean = false,

    @SerializedName("innerGlowColor") var innerGlowColor: Int = Color.parseColor("#FFFFFF"),

    @SerializedName("innerGlowRadius") var innerGlowRadius: Float = 6f,

    @SerializedName("innerGlowOpacity") var innerGlowOpacity: Int = 255,

    @SerializedName("hasShadow") var hasShadow: Boolean = false,

    @SerializedName("shadowColor") var shadowColor: Int = Color.GRAY,

    @SerializedName("shadowDx") var shadowDx: Float = 1f,

    @SerializedName("shadowDy") var shadowDy: Float = 1f,

    @SerializedName("shadowRadius") var shadowRadius: Float = 1f,

    @SerializedName("shadowOpacity") var shadowOpacity: Int = 255,

    @SerializedName("hasLabel") var hasLabel: Boolean = false,

    @SerializedName("labelColor") var labelColor: Int = Color.parseColor("#30005D28"),

    @SerializedName("labelShape") var labelShape: LabelShape = LabelShape.RECTANGLE_FILL,

    @SerializedName("labelSecondaryColor") var labelSecondaryColor: Int? = null,

    @SerializedName("labelStrokeColor") var labelStrokeColor: Int? = null,

    @SerializedName("labelStrokeWidth") var labelStrokeWidth: Float = 0f,

    @SerializedName("hasGlossHighlight") var hasGlossHighlight: Boolean = false,

    @SerializedName("hasFoldedRibbonFlaps") var hasFoldedRibbonFlaps: Boolean = false,

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

    @SerializedName("featherBiasX") var featherBiasX: Float = 0f,
    @SerializedName("featherBiasY") var featherBiasY: Float = 0f,

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

    // ── Table Data ────────────────────────────────────────────────────────────
    @SerializedName("tableData") var tableData: TableData? = null,

    ) : Serializable {

    @field:Transient var cachedAdjustedBitmap: Bitmap? = null
    @field:Transient var isAdjustmentDirty: Boolean = true
    @field:Transient var tableLayoutCache: Any? = null

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

    fun getLabelPaddingX(): Float {
        if (!hasLabel) return 0f
        return when (labelShape) {
            LabelShape.TAG_FILL, LabelShape.TAG_STROKE -> 48f
            LabelShape.RIBBON_FILL, LabelShape.RIBBON_STROKE -> 54f
            LabelShape.CAPSULE_FILL, LabelShape.CAPSULE_STROKE,
            LabelShape.OVAL_FILL, LabelShape.OVAL_STROKE -> 36f
            LabelShape.SLANTED_FILL, LabelShape.SLANTED_STROKE -> 40f
            LabelShape.BADGE_FILL, LabelShape.BADGE_STROKE -> 32f
            LabelShape.ROUNDED_RECTANGLE_FILL, LabelShape.ROUNDED_RECTANGLE_STROKE -> 28f
            else -> 24f
        }
    }

    fun getLabelPaddingY(): Float {
        if (!hasLabel) return 0f
        return 18f
    }

    fun getLocalContentWidth(): Float {
        return if (type == ElementType.BACKGROUND || type == ElementType.SHAPE || type == ElementType.TABLE) {
            logicalContentWidth
        } else if (type == ElementType.TEXT) {
            val baseW = if (boxWidth != null && boxWidth!! > 0f) {
                boxWidth!!
            } else {
                val lines = getVisualLines()
                if (::paint.isInitialized) {
                    lines.maxOfOrNull { line -> paint.measureText(line) } ?: 0f
                } else {
                    0f
                }
            }
            baseW + getLabelPaddingX() * 2f
        } else {
            if (svgDrawable != null) {
                logicalContentWidth.takeIf { it > 0 }
                    ?: svgDrawable?.picture?.width?.toFloat()?.takeIf { it > 0 }
                    ?: 0f
            } else {
                logicalContentWidth.takeIf { it > 0 }
                    ?: bitmap?.width?.toFloat()
                    ?: 0f
            }
        }
    }

    fun getLocalContentHeight(): Float {
        return if (type == ElementType.BACKGROUND || type == ElementType.SHAPE || type == ElementType.TABLE) {
            logicalContentHeight
        } else if (type == ElementType.TEXT) {
            val baseH = if (boxHeight != null && boxHeight!! > 0f) {
                boxHeight!!
            } else if (::paint.isInitialized) {
                val fm = paint.fontMetrics
                val lineHeight = (fm.bottom - fm.top) * lineSpacing
                val lines = getVisualLines()
                lines.size * lineHeight
            } else {
                0f
            }
            baseH + getLabelPaddingY() * 2f
        } else {
            if (svgDrawable != null) {
                logicalContentHeight.takeIf { it > 0 }
                    ?: svgDrawable?.picture?.height?.toFloat()?.takeIf { it > 0 }
                    ?: 0f
            } else {
                logicalContentHeight.takeIf { it > 0 }
                    ?: bitmap?.height?.toFloat()
                    ?: 0f
            }
        }
    }

    fun getTextWithKashida(): String {
        return applyKashidaToText(text, kashidaSize)
    }

    @Transient private var cachedRawTextForTokens: String? = null
    @Transient private var cachedExplicitLinesTokens: List<List<String>>? = null

    private fun getExplicitLinesTokens(): List<List<String>> {
        val rawText = getTextWithKashida()
        if (rawText == cachedRawTextForTokens && cachedExplicitLinesTokens != null) {
            return cachedExplicitLinesTokens!!
        }
        val explicitLines = rawText.split("\n")
        val tokenized = explicitLines.map { line ->
            if (line.isEmpty()) emptyList()
            else line.split(Regex("(?<=\\s)|(?=\\s)"))
        }
        cachedRawTextForTokens = rawText
        cachedExplicitLinesTokens = tokenized
        return tokenized
    }

    fun getVisualLines(targetWidth: Float? = null, maxCanvasWidth: Float? = null): List<String> {
        val rawText = getTextWithKashida()
        if (rawText.isEmpty()) return listOf("")
        val explicitTokens = getExplicitLinesTokens()

        val defaultMaxW = maxCanvasWidth
            ?: (context?.resources?.displayMetrics?.widthPixels?.let { it * 0.85f } ?: 800f)

        val effectiveWidth = targetWidth
            ?: (if (boxWidth != null && boxWidth!! > 0f) boxWidth else defaultMaxW)

        if (effectiveWidth == null || effectiveWidth <= 0f || !::paint.isInitialized) {
            return rawText.split("\n")
        }

        val resultLines = mutableListOf<String>()
        for (tokens in explicitTokens) {
            if (tokens.isEmpty()) {
                resultLines.add("")
                continue
            }
            val lineStr = tokens.joinToString("")
            val measured = paint.measureText(lineStr)
            if (measured <= effectiveWidth) {
                resultLines.add(lineStr)
            } else {
                var currentLine = StringBuilder()
                for (word in tokens) {
                    if (paint.measureText(word.trim()) > effectiveWidth) {
                        if (currentLine.isNotEmpty()) {
                            resultLines.add(currentLine.toString().trimEnd())
                            currentLine = StringBuilder()
                        }
                        val trimmedWord = word.trim()
                        val charLine = StringBuilder()
                        for (ch in trimmedWord) {
                            val test = charLine.toString() + ch
                            if (paint.measureText(test) > effectiveWidth && charLine.isNotEmpty()) {
                                resultLines.add(charLine.toString())
                                charLine.clear()
                            }
                            charLine.append(ch)
                        }
                        if (charLine.isNotEmpty()) {
                            currentLine = charLine
                        }
                        continue
                    }

                    if (currentLine.isEmpty()) {
                        currentLine.append(word)
                    } else {
                        val testLine = currentLine.toString() + word
                        if (paint.measureText(testLine) <= effectiveWidth) {
                            currentLine.append(word)
                        } else {
                            resultLines.add(currentLine.toString().trimEnd())
                            currentLine = StringBuilder(word.trimStart())
                        }
                    }
                }
                if (currentLine.isNotEmpty()) {
                    resultLines.add(currentLine.toString())
                }
            }
        }
        return resultLines
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

    fun getNaturalUnwrappedWidth(): Float {
        if (type != ElementType.TEXT || !::paint.isInitialized) return 0f
        val rawText = getTextWithKashida()
        if (rawText.isEmpty()) return 0f
        val explicitLines = rawText.split("\n")
        var maxW = 0f
        for (line in explicitLines) {
            if (line.isEmpty()) continue
            maxW = maxOf(maxW, paint.measureText(line))
        }
        return maxW.coerceAtLeast(40f)
    }

    fun getNaturalContentHeight(targetW: Float? = null): Float {
        if (type != ElementType.TEXT || !::paint.isInitialized) return 0f
        val lines = getVisualLines(targetW)
        val fm = paint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent) * lineSpacing
        return (lines.size * lineHeight).coerceAtLeast(30f)
    }

    fun getMinWordWidth(): Float {
        if (type != ElementType.TEXT || !::paint.isInitialized) return 40f
        val rawText = getTextWithKashida()
        if (rawText.isEmpty()) return 40f
        val words = rawText.split(Regex("\\s+"))
        var maxWordW = 0f
        for (w in words) {
            if (w.trim().isEmpty()) continue
            maxWordW = maxOf(maxWordW, paint.measureText(w.trim()))
        }
        return maxWordW.coerceAtLeast(40f)
    }

    fun autoFitTextSize(canvasWidth: Float, canvasHeight: Float) {
        if (type != ElementType.TEXT || text.isEmpty()) return
        if (!::paint.isInitialized) updatePaintProperties()

        val maxCanvasW = if (canvasWidth > 0f) canvasWidth * 0.85f else 0f
        val maxCanvasH = if (canvasHeight > 0f) canvasHeight * 0.85f else 0f

        val targetW = if (boxWidth != null && boxWidth!! > 0f) boxWidth!! else maxCanvasW
        val targetH = if (boxHeight != null && boxHeight!! > 0f) boxHeight!! else maxCanvasH

        if (targetW <= 0f) return

        val refDim = minOf(
            if (canvasWidth > 0f) canvasWidth else 1000f,
            if (canvasHeight > 0f) canvasHeight else 1000f
        )
        val minTextSize = maxOf(12f, refDim * 0.015f)

        fun testFits(size: Float): Boolean {
            paint.textSize = size
            val lines = getVisualLines(targetW)
            val fm = paint.fontMetrics
            val lineHeight = (fm.descent - fm.ascent) * lineSpacing
            val totalH = lines.size * lineHeight
            val maxW = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
            val fitsW = targetW <= 0f || maxW <= targetW + 1f
            val fitsH = targetH <= 0f || totalH <= targetH + 1f
            return fitsW && fitsH
        }

        var currentSize = paintTextSize
        if (!testFits(currentSize)) {
            var low = minTextSize
            var high = currentSize
            var bestSize = minTextSize

            repeat(10) {
                val mid = (low + high) / 2f
                if (testFits(mid)) {
                    bestSize = mid
                    low = mid
                } else {
                    high = mid
                }
            }
            currentSize = bestSize
            paintTextSize = currentSize
            updatePaintProperties()
        }

        paint.textSize = paintTextSize
        val lines = getVisualLines(boxWidth)
        val fm = paint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent) * lineSpacing
        val totalH = lines.size * lineHeight
        val maxW = lines.maxOfOrNull { paint.measureText(it) } ?: 0f

        val naturalMaxW = getNaturalUnwrappedWidth()
        if (boxWidth != null) {
            boxWidth = boxWidth!!.coerceIn(getMinWordWidth(), naturalMaxW)
        }
        if (boxHeight != null) {
            boxHeight = boxHeight!!.coerceIn(lineHeight, getNaturalContentHeight(boxWidth))
        }

        logicalContentWidth = (boxWidth ?: maxW) + getLabelPaddingX() * 2f
        logicalContentHeight = (boxHeight ?: totalH) + getLabelPaddingY() * 2f
    }

    fun recalculateTextBounds(canvasWidth: Float? = null, canvasHeight: Float? = null) {
        if (type != ElementType.TEXT) return
        if (!::paint.isInitialized) updatePaintProperties()

        cachedRawTextForTokens = null
        cachedExplicitLinesTokens = null

        val lines = getVisualLines(boxWidth, canvasWidth)
        val fm = paint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent) * lineSpacing
        val totalH = lines.size * lineHeight
        val maxW = lines.maxOfOrNull { paint.measureText(it) } ?: 0f

        val naturalMaxW = getNaturalUnwrappedWidth()
        if (boxWidth != null) {
            boxWidth = boxWidth!!.coerceIn(getMinWordWidth(), naturalMaxW)
        }
        if (boxHeight != null) {
            boxHeight = boxHeight!!.coerceIn(lineHeight, getNaturalContentHeight(boxWidth))
        }

        logicalContentWidth = (boxWidth ?: maxW) + getLabelPaddingX() * 2f
        logicalContentHeight = (boxHeight ?: totalH) + getLabelPaddingY() * 2f
    }

    fun getTightTextBounds(): RectF {
        val bounds = RectF()

        if (type == ElementType.TEXT && ::paint.isInitialized) {
            val lines = getVisualLines()
            val fm = paint.fontMetrics
            val lineHeight = (fm.descent - fm.ascent) * lineSpacing

            val tempRect = Rect()
            var maxLineWidth = 0f

            for (line in lines) {
                if (line.isEmpty()) continue
                paint.getTextBounds(line, 0, line.length, tempRect)
                maxLineWidth = maxOf(maxLineWidth, tempRect.width().toFloat(), paint.measureText(line))
            }

            var boxW = boxWidth ?: maxLineWidth
            val textContentHeight = lines.size * lineHeight
            var boxH = if (boxHeight != null && boxHeight!! > 0f) maxOf(boxHeight!!, textContentHeight) else textContentHeight

            if (hasLabel) {
                boxW += getLabelPaddingX() * 2f
                boxH += getLabelPaddingY() * 2f
            }

            bounds.set(
                -boxW / 2f, -boxH / 2f, boxW / 2f, boxH / 2f
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
            ElementType.TABLE -> 4f
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

    fun collectFontIds(): List<String> = when (type) {
        ElementType.TEXT -> listOfNotNull(fontId)
        ElementType.TABLE -> tableData?.allFontIds().orEmpty()
        else -> emptyList()
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