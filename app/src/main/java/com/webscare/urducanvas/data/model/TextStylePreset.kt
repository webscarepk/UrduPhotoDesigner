package com.webscare.urducanvas.data.model

import android.graphics.Color
import com.webscare.urducanvas.common.canvas.enums.LabelShape
import com.webscare.urducanvas.common.canvas.model.GradientItem

enum class PresetCategory(val displayName: String) {
    MY_STYLES("My Styles"),
    MINIMAL("Minimal"),
    MODERN("Modern"),
    BADGES_SALE("Badges"),
    RIBBONS_BANNERS("Ribbons"),
    NEON_GLOW("Neon"),
    METALLIC_3D("3D Metal"),
    CALLIGRAPHY("Calligraphy"),
    DARK_CONTRAST("Contrast")
}

data class TextStylePreset(
    val id: String,
    val name: String,
    val category: PresetCategory,
    // Text Properties
    val textColor: Int? = null,
    val textGradient: GradientItem? = null,
    val strokeColor: Int? = null,
    val strokeWidth: Float = 0f,
    val shadowColor: Int? = null,
    val shadowRadius: Float = 0f,
    val shadowDx: Float = 0f,
    val shadowDy: Float = 0f,
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
)
