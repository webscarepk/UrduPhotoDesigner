package com.webscare.urducanvas.data.repository

import android.content.Context
import android.graphics.Color
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.webscare.urducanvas.common.canvas.enums.LabelShape
import com.webscare.urducanvas.common.canvas.model.GradientItem
import com.webscare.urducanvas.data.model.PresetCategory
import com.webscare.urducanvas.data.model.TextStylePreset
import java.io.InputStreamReader

object TextStylesRepository {

    private const val PREF_NAME = "saved_user_text_styles"
    private const val KEY_CUSTOM_STYLES = "custom_styles_list"
    private val gson = Gson()

    private val cachedPresets: MutableList<TextStylePreset> = mutableListOf()
    private var isLoadedFromJson = false

    private data class TextStylePresetJson(
        val id: String = "",
        val name: String = "",
        val category: String = "THREE_D",
        val textColor: String? = null,
        val textGradientColors: List<String>? = null,
        val textGradientPositions: List<Float>? = null,
        val textGradientAngle: Float = 90f,
        val strokeColor: String? = null,
        val strokeWidth: Float = 0f,
        val hasUnderStroke: Boolean = false,
        val underStrokeColor: String? = null,
        val underStrokeWidth: Float = 0f,
        // 3D Extrusion
        val has3dExtrude: Boolean = false,
        val extrudeColor: String? = null,
        val extrudeDepth: Float = 0f,
        val extrudeDx: Float = 4f,
        val extrudeDy: Float = 4f,
        // Double Extrude
        val hasDoubleExtrude: Boolean = false,
        val extrudeStep2Color: String? = null,
        val extrudeStep2Depth: Float = 0f,
        val extrudeStep2Dx: Float = 8f,
        val extrudeStep2Dy: Float = 8f,
        // Anaglyph 3D
        val hasAnaglyph: Boolean = false,
        val anaglyphOffset: Float = 4f,
        val anaglyphColor1: String? = null,
        val anaglyphColor2: String? = null,
        // 3D Bevel
        val hasBevel: Boolean = false,
        val bevelHighlightColor: String? = null,
        val bevelShadowColor: String? = null,
        val bevelDepth: Float = 2.5f,
        // 3D Emboss
        val hasEmboss: Boolean = false,
        val isDebossed: Boolean = false,
        val embossDepth: Float = 2.0f,
        val embossHighlightColor: String? = null,
        val embossShadowColor: String? = null,
        // Outer & Inner Glow
        val hasOuterGlow: Boolean = false,
        val outerGlowColor: String? = null,
        val outerGlowRadius: Float = 12f,
        val outerGlowOpacity: Int = 255,
        val hasInnerGlow: Boolean = false,
        val innerGlowColor: String? = null,
        val innerGlowRadius: Float = 6f,
        val innerGlowOpacity: Int = 255,
        // Shadow
        val shadowColor: String? = null,
        val shadowRadius: Float = 0f,
        val shadowDx: Float = 0f,
        val shadowDy: Float = 0f,
        val shadowOpacity: Int = 255,
        // Label
        val hasLabel: Boolean = false,
        val labelShape: String? = null,
        val labelColor: String? = null,
        val labelGradientColors: List<String>? = null,
        val labelSecondaryColor: String? = null,
        val labelStrokeColor: String? = null,
        val labelStrokeWidth: Float = 0f,
        val hasGlossHighlight: Boolean = false,
        val hasFoldedRibbonFlaps: Boolean = false
    )

    @Synchronized
    private fun ensurePresetsLoaded(context: Context) {
        if (isLoadedFromJson && cachedPresets.isNotEmpty()) return

        cachedPresets.clear()
        try {
            context.assets.open("presets/text_styles.json").use { inputStream ->
                val reader = InputStreamReader(inputStream)
                val type = object : TypeToken<List<TextStylePresetJson>>() {}.type
                val rawList: List<TextStylePresetJson>? = gson.fromJson(reader, type)

                if (!rawList.isNullOrEmpty()) {
                    val parsedList = rawList.map { item ->
                        val textGradient = if (!item.textGradientColors.isNullOrEmpty()) {
                            val colors = item.textGradientColors.mapNotNull { parseColorSafe(it) }
                            val positions = item.textGradientPositions ?: if (colors.size == 2) listOf(0f, 1f) else listOf(0f, 0.5f, 1f)
                            GradientItem(
                                colors = colors,
                                positions = positions,
                                angle = item.textGradientAngle
                            )
                        } else null

                        val labelGradient = if (!item.labelGradientColors.isNullOrEmpty()) {
                            val colors = item.labelGradientColors.mapNotNull { parseColorSafe(it) }
                            val positions = if (colors.size == 2) listOf(0f, 1f) else listOf(0f, 0.5f, 1f)
                            GradientItem(
                                colors = colors,
                                positions = positions,
                                angle = 90f
                            )
                        } else null

                        TextStylePreset(
                            id = item.id,
                            name = item.name,
                            category = parseCategorySafe(item.category),
                            textColor = parseColorSafe(item.textColor),
                            textGradient = textGradient,
                            strokeColor = parseColorSafe(item.strokeColor),
                            strokeWidth = item.strokeWidth,
                            hasUnderStroke = item.hasUnderStroke,
                            underStrokeColor = parseColorSafe(item.underStrokeColor),
                            underStrokeWidth = item.underStrokeWidth,
                            has3dExtrude = item.has3dExtrude,
                            extrudeColor = parseColorSafe(item.extrudeColor),
                            extrudeDepth = item.extrudeDepth,
                            extrudeDx = item.extrudeDx,
                            extrudeDy = item.extrudeDy,
                            hasDoubleExtrude = item.hasDoubleExtrude,
                            extrudeStep2Color = parseColorSafe(item.extrudeStep2Color),
                            extrudeStep2Depth = item.extrudeStep2Depth,
                            extrudeStep2Dx = item.extrudeStep2Dx,
                            extrudeStep2Dy = item.extrudeStep2Dy,
                            hasAnaglyph = item.hasAnaglyph,
                            anaglyphOffset = item.anaglyphOffset,
                            anaglyphColor1 = parseColorSafe(item.anaglyphColor1),
                            anaglyphColor2 = parseColorSafe(item.anaglyphColor2),
                            hasBevel = item.hasBevel,
                            bevelHighlightColor = parseColorSafe(item.bevelHighlightColor),
                            bevelShadowColor = parseColorSafe(item.bevelShadowColor),
                            bevelDepth = item.bevelDepth,
                            hasEmboss = item.hasEmboss,
                            isDebossed = item.isDebossed,
                            embossDepth = item.embossDepth,
                            embossHighlightColor = parseColorSafe(item.embossHighlightColor),
                            embossShadowColor = parseColorSafe(item.embossShadowColor),
                            hasOuterGlow = item.hasOuterGlow,
                            outerGlowColor = parseColorSafe(item.outerGlowColor),
                            outerGlowRadius = item.outerGlowRadius,
                            outerGlowOpacity = item.outerGlowOpacity,
                            hasInnerGlow = item.hasInnerGlow,
                            innerGlowColor = parseColorSafe(item.innerGlowColor),
                            innerGlowRadius = item.innerGlowRadius,
                            innerGlowOpacity = item.innerGlowOpacity,
                            shadowColor = parseColorSafe(item.shadowColor),
                            shadowRadius = item.shadowRadius,
                            shadowDx = item.shadowDx,
                            shadowDy = item.shadowDy,
                            shadowOpacity = item.shadowOpacity,
                            hasLabel = item.hasLabel,
                            labelShape = parseShapeSafe(item.labelShape),
                            labelColor = parseColorSafe(item.labelColor) ?: Color.TRANSPARENT,
                            labelGradient = labelGradient,
                            labelSecondaryColor = parseColorSafe(item.labelSecondaryColor),
                            labelStrokeColor = parseColorSafe(item.labelStrokeColor),
                            labelStrokeWidth = item.labelStrokeWidth,
                            hasGlossHighlight = item.hasGlossHighlight,
                            hasFoldedRibbonFlaps = item.hasFoldedRibbonFlaps
                        )
                    }
                    cachedPresets.addAll(parsedList)
                    isLoadedFromJson = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseColorSafe(colorStr: String?): Int? {
        if (colorStr.isNullOrBlank()) return null
        return try {
            Color.parseColor(colorStr.trim())
        } catch (e: Exception) {
            null
        }
    }

    private fun parseShapeSafe(shapeStr: String?): LabelShape {
        if (shapeStr.isNullOrBlank()) return LabelShape.ROUNDED_RECTANGLE_FILL
        return try {
            LabelShape.valueOf(shapeStr.trim())
        } catch (e: Exception) {
            LabelShape.ROUNDED_RECTANGLE_FILL
        }
    }

    private fun parseCategorySafe(catStr: String?): PresetCategory {
        if (catStr.isNullOrBlank()) return PresetCategory.THREE_D
        return try {
            val upper = catStr.trim().uppercase()
            when (upper) {
                "3D", "THREE_D" -> PresetCategory.THREE_D
                "LAYERS", "LAYERED_3D" -> PresetCategory.LAYERS
                "EMBOSS", "EMBOSSED", "DEBOSSED" -> PresetCategory.EMBOSS
                "PERSPECTIVE", "ISOMETRIC" -> PresetCategory.PERSPECTIVE
                "CALLIGRAPHY" -> PresetCategory.CALLIGRAPHY
                "PAKISTAN", "PATRIOTIC" -> PresetCategory.PATRIOTIC
                "NEON", "NEON_GLOW" -> PresetCategory.NEON
                "GOLD", "METALLIC_3D" -> PresetCategory.GOLD
                "BADGES", "BADGES_SALE" -> PresetCategory.BADGES
                "RIBBONS", "RIBBONS_BANNERS" -> PresetCategory.RIBBONS
                "MINIMAL" -> PresetCategory.MINIMAL
                "MODERN" -> PresetCategory.MODERN
                "DARK", "DARK_CONTRAST" -> PresetCategory.DARK
                else -> PresetCategory.valueOf(upper)
            }
        } catch (e: Exception) {
            PresetCategory.THREE_D
        }
    }

    fun getAllPresets(context: Context): List<TextStylePreset> {
        ensurePresetsLoaded(context)
        val custom = getCustomUserSavedStyles(context)
        return custom + cachedPresets
    }

    fun getPresetsByCategory(category: PresetCategory, context: Context): List<TextStylePreset> {
        if (category == PresetCategory.MY_STYLES) {
            return getCustomUserSavedStyles(context)
        }
        ensurePresetsLoaded(context)
        return cachedPresets.filter { it.category == category }
    }

    // -------------------------------------------------------------
    // USER CUSTOM STYLES PERSISTENCE
    // -------------------------------------------------------------
    fun getCustomUserSavedStyles(context: Context): List<TextStylePreset> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CUSTOM_STYLES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<TextStylePreset>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomUserStyle(context: Context, preset: TextStylePreset) {
        val currentList = getCustomUserSavedStyles(context).toMutableList()
        currentList.add(0, preset.copy(isCustomUserSaved = true)) // newest first
        val json = gson.toJson(currentList)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_STYLES, json)
            .apply()
    }

    fun hasSameStyleProperties(p1: TextStylePreset, p2: TextStylePreset): Boolean {
        if (p1.textColor != p2.textColor) return false
        if (p1.strokeColor != p2.strokeColor) return false
        if (p1.strokeWidth != p2.strokeWidth) return false
        if (p1.hasUnderStroke != p2.hasUnderStroke) return false
        if (p1.underStrokeColor != p2.underStrokeColor) return false
        if (p1.underStrokeWidth != p2.underStrokeWidth) return false
        if (p1.has3dExtrude != p2.has3dExtrude) return false
        if (p1.extrudeColor != p2.extrudeColor) return false
        if (p1.extrudeDepth != p2.extrudeDepth) return false
        if (p1.hasDoubleExtrude != p2.hasDoubleExtrude) return false
        if (p1.extrudeStep2Color != p2.extrudeStep2Color) return false
        if (p1.extrudeStep2Depth != p2.extrudeStep2Depth) return false
        if (p1.hasAnaglyph != p2.hasAnaglyph) return false
        if (p1.anaglyphOffset != p2.anaglyphOffset) return false
        if (p1.hasBevel != p2.hasBevel) return false
        if (p1.bevelDepth != p2.bevelDepth) return false
        if (p1.hasEmboss != p2.hasEmboss) return false
        if (p1.isDebossed != p2.isDebossed) return false
        if (p1.embossDepth != p2.embossDepth) return false
        if (p1.hasOuterGlow != p2.hasOuterGlow) return false
        if (p1.outerGlowColor != p2.outerGlowColor) return false
        if (p1.outerGlowRadius != p2.outerGlowRadius) return false
        if (p1.outerGlowOpacity != p2.outerGlowOpacity) return false
        if (p1.hasInnerGlow != p2.hasInnerGlow) return false
        if (p1.innerGlowColor != p2.innerGlowColor) return false
        if (p1.innerGlowRadius != p2.innerGlowRadius) return false
        if (p1.innerGlowOpacity != p2.innerGlowOpacity) return false
        if (p1.shadowColor != p2.shadowColor) return false
        if (p1.shadowRadius != p2.shadowRadius) return false
        if (p1.shadowDx != p2.shadowDx) return false
        if (p1.shadowDy != p2.shadowDy) return false
        if (p1.hasLabel != p2.hasLabel) return false
        if (p1.hasGlossHighlight != p2.hasGlossHighlight) return false
        if (p1.hasFoldedRibbonFlaps != p2.hasFoldedRibbonFlaps) return false

        if (p1.hasLabel) {
            if (p1.labelShape != p2.labelShape) return false
            if (p1.labelColor != p2.labelColor) return false
            if (p1.labelSecondaryColor != p2.labelSecondaryColor) return false
            if (p1.labelStrokeColor != p2.labelStrokeColor) return false
            if (p1.labelStrokeWidth != p2.labelStrokeWidth) return false
            if (!isSameGradient(p1.labelGradient, p2.labelGradient)) return false
        }

        if (!isSameGradient(p1.textGradient, p2.textGradient)) return false

        return true
    }

    private fun isSameGradient(g1: GradientItem?, g2: GradientItem?): Boolean {
        if (g1 == null && g2 == null) return true
        if (g1 == null || g2 == null) return false
        return g1.colors == g2.colors &&
                g1.positions == g2.positions &&
                g1.angle == g2.angle &&
                g1.scale == g2.scale &&
                g1.type == g2.type &&
                g1.radialRadiusFactor == g2.radialRadiusFactor &&
                g1.sweepStartAngle == g2.sweepStartAngle &&
                g1.centerX == g2.centerX &&
                g1.centerY == g2.centerY
    }
}
