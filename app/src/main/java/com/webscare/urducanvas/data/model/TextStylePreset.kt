package com.webscare.urducanvas.data.model

import android.graphics.Color
import com.webscare.urducanvas.common.canvas.enums.LabelShape
import com.webscare.urducanvas.common.canvas.model.GradientItem

enum class PresetCategory(val displayName: String) {
    MY_STYLES("My Styles"),
    THREE_D("3D"),
    LAYERS("Layers"),
    EMBOSS("Emboss"),
    PERSPECTIVE("Perspective"),
    CALLIGRAPHY("Calligraphy"),
    PATRIOTIC("Pakistan"),
    NEON("Neon"),
    GOLD("Gold"),
    BADGES("Badges"),
    RIBBONS("Ribbons"),
    MINIMAL("Minimal"),
    MODERN("Modern"),
    DARK("Dark")
}

data class TextStylePreset(
    val id: String,
    val name: String,
    val category: PresetCategory,
    // Text Fill & Stroke Properties
    val textColor: Int? = null,
    val textGradient: GradientItem? = null,
    val strokeColor: Int? = null,
    val strokeWidth: Float = 0f,
    val hasUnderStroke: Boolean = false,
    val underStrokeColor: Int? = null,
    val underStrokeWidth: Float = 0f,
    // 3D Block Extrusion
    val has3dExtrude: Boolean = false,
    val extrudeColor: Int? = null,
    val extrudeDepth: Float = 0f,
    val extrudeDx: Float = 4f,
    val extrudeDy: Float = 4f,
    val hasDoubleExtrude: Boolean = false,
    val extrudeStep2Color: Int? = null,
    val extrudeStep2Depth: Float = 0f,
    val extrudeStep2Dx: Float = 8f,
    val extrudeStep2Dy: Float = 8f,
    // Anaglyph 3D (Stereoscopic Red-Cyan / Glitch)
    val hasAnaglyph: Boolean = false,
    val anaglyphOffset: Float = 4f,
    val anaglyphColor1: Int? = null,
    val anaglyphColor2: Int? = null,
    // 3D Bevel (Chisel / Emboss Highlight & Shadow)
    val hasBevel: Boolean = false,
    val bevelHighlightColor: Int? = null,
    val bevelShadowColor: Int? = null,
    val bevelDepth: Float = 2.5f,
    // 3D Emboss & Deboss (Letterpress / Carved / Sunken)
    val hasEmboss: Boolean = false,
    val isDebossed: Boolean = false,
    val embossDepth: Float = 2.0f,
    val embossHighlightColor: Int? = null,
    val embossShadowColor: Int? = null,
    // Outer & Inner Glow
    val hasOuterGlow: Boolean = false,
    val outerGlowColor: Int? = null,
    val outerGlowRadius: Float = 12f,
    val outerGlowOpacity: Int = 255,
    val hasInnerGlow: Boolean = false,
    val innerGlowColor: Int? = null,
    val innerGlowRadius: Float = 6f,
    val innerGlowOpacity: Int = 255,
    // Shadow & Elevation
    val shadowColor: Int? = null,
    val shadowRadius: Float = 0f,
    val shadowDx: Float = 0f,
    val shadowDy: Float = 0f,
    val shadowOpacity: Int = 255,
    // Label Background Properties
    val hasLabel: Boolean = false,
    val labelShape: LabelShape = LabelShape.ROUNDED_RECTANGLE_FILL,
    val labelColor: Int = Color.TRANSPARENT,
    val labelGradient: GradientItem? = null,
    val labelSecondaryColor: Int? = null,
    val labelStrokeColor: Int? = null,
    val labelStrokeWidth: Float = 0f,
    val hasGlossHighlight: Boolean = false,
    val hasFoldedRibbonFlaps: Boolean = false,
    val isCustomUserSaved: Boolean = false
) {
    companion object {
        /** Id of the synthetic "no style" cell that leads every category grid. */
        const val NONE_ID = "none"

        /**
         * A preset with every styling property left at its default. Applying it through
         * the ordinary preset path clears stroke, extrusion, bevel, emboss, glow, shadow
         * and label in one step, so "None" needs no separate reset routine. The element's
         * own fill colour survives — [CanvasViewModel.applyTextStylePreset] falls back to
         * it when the preset carries no textColor.
         */
        fun none(category: PresetCategory): TextStylePreset =
            TextStylePreset(id = NONE_ID, name = "None", category = category)
    }
}
