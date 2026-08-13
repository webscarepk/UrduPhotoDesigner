package com.webscare.urducanvas.data.repository

import android.content.Context
import android.graphics.Color
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.webscare.urducanvas.common.canvas.enums.LabelShape
import com.webscare.urducanvas.common.canvas.model.GradientItem
import com.webscare.urducanvas.data.model.PresetCategory
import com.webscare.urducanvas.data.model.TextStylePreset

object TextStylesRepository {

    private const val PREF_NAME = "saved_user_text_styles"
    private const val KEY_CUSTOM_STYLES = "custom_styles_list"
    private val gson = Gson()

    private val allPresets: MutableList<TextStylePreset> = mutableListOf()

    init {
        buildPresetLibrary()
    }

    private fun buildPresetLibrary() {
        allPresets.clear()

        // -------------------------------------------------------------
        // 1. MINIMAL (Pastel pills, subtle outlines, soft highlights)
        // -------------------------------------------------------------
        val minimalColors = listOf(
            0xFFE8F5E9.toInt(), 0xFFFFF3E0.toInt(), 0xFFE1F5FE.toInt(), 0xFFF3E5F5.toInt(),
            0xFFFCE4EC.toInt(), 0xFFFFFDE7.toInt(), 0xFFE0F2F1.toInt(), 0xFFF1F8E9.toInt(),
            0xFFEDE7F6.toInt(), 0xFFEFEBE9.toInt(), 0xFFECEFF1.toInt(), 0xFFFAFAFA.toInt(),
            0xFFF5F5F5.toInt(), 0xFFE8EAF6.toInt(), 0xFFE0F7FA.toInt()
        )
        val minimalTextColors = listOf(
            0xFF2E7D32.toInt(), 0xFFE65100.toInt(), 0xFF0277BD.toInt(), 0xFF6A1B9A.toInt(),
            0xFFC2185B.toInt(), 0xFFF57F17.toInt(), 0xFF00695C.toInt(), 0xFF33691E.toInt(),
            0xFF4527A0.toInt(), 0xFF4E342E.toInt(), 0xFF37474F.toInt(), 0xFF212121.toInt(),
            0xFF424242.toInt(), 0xFF283593.toInt(), 0xFF00838F.toInt()
        )

        for (i in minimalColors.indices) {
            allPresets.add(
                TextStylePreset(
                    id = "min_$i",
                    name = "Minimal ${i + 1}",
                    category = PresetCategory.MINIMAL,
                    textColor = minimalTextColors[i],
                    hasLabel = true,
                    labelShape = if (i % 2 == 0) LabelShape.CAPSULE_FILL else LabelShape.ROUNDED_RECTANGLE_FILL,
                    labelColor = minimalColors[i]
                )
            )
        }

        // -------------------------------------------------------------
        // 2. MODERN (Vibrant flat pills, slanted parallelograms, bold tags)
        // -------------------------------------------------------------
        val modernPalettes = listOf(
            Pair(0xFFFF5722.toInt(), Color.WHITE),
            Pair(0xFF00BCD4.toInt(), Color.WHITE),
            Pair(0xFF8BC34A.toInt(), Color.BLACK),
            Pair(0xFFFFEB3B.toInt(), Color.BLACK),
            Pair(0xFF9C27B0.toInt(), Color.WHITE),
            Pair(0xFF3F51B5.toInt(), Color.WHITE),
            Pair(0xFFFF4081.toInt(), Color.WHITE),
            Pair(0xFF00E676.toInt(), Color.BLACK),
            Pair(0xFFFF9100.toInt(), Color.WHITE),
            Pair(0xFF651FFF.toInt(), Color.WHITE),
            Pair(0xFF00B0FF.toInt(), Color.WHITE),
            Pair(0xFF1DE9B6.toInt(), Color.BLACK),
            Pair(0xFFFFC400.toInt(), Color.BLACK),
            Pair(0xFFFF3D00.toInt(), Color.WHITE),
            Pair(0xFFE040FB.toInt(), Color.WHITE)
        )

        val modernShapes = listOf(
            LabelShape.CAPSULE_FILL, LabelShape.SLANTED_FILL, LabelShape.TAG_FILL,
            LabelShape.REVERSE_TAG_FILL, LabelShape.ROUNDED_RECTANGLE_FILL, LabelShape.BADGE_FILL
        )

        for (i in modernPalettes.indices) {
            val (bg, txt) = modernPalettes[i]
            allPresets.add(
                TextStylePreset(
                    id = "mod_$i",
                    name = "Modern ${i + 1}",
                    category = PresetCategory.MODERN,
                    textColor = txt,
                    hasLabel = true,
                    labelShape = modernShapes[i % modernShapes.size],
                    labelColor = bg
                )
            )
        }

        // -------------------------------------------------------------
        // 3. BADGES & SALE (Glossy 3D commercial buttons with white border - Ref Img 2 & 4)
        // -------------------------------------------------------------
        val badgeGradients = listOf(
            GradientItem(colors = listOf(Color.parseColor("#D50000"), Color.parseColor("#8E0000")), positions = listOf(0f, 1f), angle = 90f),
            GradientItem(colors = listOf(Color.parseColor("#AA00FF"), Color.parseColor("#4A148C")), positions = listOf(0f, 1f), angle = 90f),
            GradientItem(colors = listOf(Color.parseColor("#00C853"), Color.parseColor("#1B5E20")), positions = listOf(0f, 1f), angle = 90f),
            GradientItem(colors = listOf(Color.parseColor("#2979FF"), Color.parseColor("#0D47A1")), positions = listOf(0f, 1f), angle = 90f),
            GradientItem(colors = listOf(Color.parseColor("#FF6D00"), Color.parseColor("#BF360C")), positions = listOf(0f, 1f), angle = 90f),
            GradientItem(colors = listOf(Color.parseColor("#FFD600"), Color.parseColor("#FF6F00")), positions = listOf(0f, 1f), angle = 90f),
            GradientItem(colors = listOf(Color.parseColor("#FF1744"), Color.parseColor("#880E4F")), positions = listOf(0f, 1f), angle = 90f),
            GradientItem(colors = listOf(Color.parseColor("#00BFA5"), Color.parseColor("#004D40")), positions = listOf(0f, 1f), angle = 90f),
            GradientItem(colors = listOf(Color.parseColor("#00E5FF"), Color.parseColor("#006064")), positions = listOf(0f, 1f), angle = 90f),
            GradientItem(colors = listOf(Color.parseColor("#AEEA00"), Color.parseColor("#33691E")), positions = listOf(0f, 1f), angle = 90f),
            GradientItem(colors = listOf(Color.parseColor("#6200EA"), Color.parseColor("#311B92")), positions = listOf(0f, 1f), angle = 90f),
            GradientItem(colors = listOf(Color.parseColor("#FFAB00"), Color.parseColor("#E65100")), positions = listOf(0f, 1f), angle = 90f),
            GradientItem(colors = listOf(Color.parseColor("#FF6E40"), Color.parseColor("#D84315")), positions = listOf(0f, 1f), angle = 90f),
            GradientItem(colors = listOf(Color.parseColor("#3D5AFE"), Color.parseColor("#1A237E")), positions = listOf(0f, 1f), angle = 90f),
            GradientItem(colors = listOf(Color.parseColor("#00E676"), Color.parseColor("#004D40")), positions = listOf(0f, 1f), angle = 90f)
        )

        val badgeShapes = listOf(
            LabelShape.CAPSULE_FILL, LabelShape.ROUNDED_RECTANGLE_FILL, LabelShape.TAG_FILL,
            LabelShape.SLANTED_FILL, LabelShape.BADGE_FILL, LabelShape.HEXAGON_BADGE_FILL
        )

        for (i in badgeGradients.indices) {
            allPresets.add(
                TextStylePreset(
                    id = "badge_$i",
                    name = "Sale Badge ${i + 1}",
                    category = PresetCategory.BADGES_SALE,
                    textColor = Color.WHITE,
                    shadowColor = Color.argb(120, 0, 0, 0),
                    shadowRadius = 8f,
                    shadowDy = 4f,
                    hasLabel = true,
                    labelShape = badgeShapes[i % badgeShapes.size],
                    labelGradient = badgeGradients[i],
                    labelStrokeColor = Color.WHITE,
                    labelStrokeWidth = 3f,
                    hasGlossHighlight = true
                )
            )
        }

        // -------------------------------------------------------------
        // 4. RIBBONS & BANNERS (Folded paper ribbons with dark tails - Ref Img 3 & 5)
        // -------------------------------------------------------------
        val ribbonCombos = listOf(
            Pair(0xFFFFC107.toInt(), 0xFF8D6E63.toInt()), // Yellow main + Dark Brown tail
            Pair(0xFF03A9F4.toInt(), 0xFF1565C0.toInt()), // Blue main + Dark Blue tail
            Pair(0xFFE91E63.toInt(), 0xFF880E4F.toInt()), // Pink main + Dark Crimson tail
            Pair(0xFF4CAF50.toInt(), 0xFF1B5E20.toInt()), // Green main + Dark Emerald tail
            Pair(0xFFFF5722.toInt(), 0xFFBF360C.toInt()), // Orange main + Dark Burnt tail
            Pair(0xFF9C27B0.toInt(), 0xFF4A148C.toInt()), // Purple main + Dark Violet tail
            Pair(0xFF00BCD4.toInt(), 0xFF006064.toInt()), // Cyan main + Dark Cyan tail
            Pair(0xFFFF9800.toInt(), 0xFFE65100.toInt()), // Amber main + Dark Amber tail
            Pair(0xFF673AB7.toInt(), 0xFF311B92.toInt()), // Deep Purple main + Dark Indigo tail
            Pair(0xFFF44336.toInt(), 0xFFB71C1C.toInt()), // Red main + Dark Red tail
            Pair(0xFF009688.toInt(), 0xFF004D40.toInt()), // Teal main + Dark Teal tail
            Pair(0xFF8BC34A.toInt(), 0xFF33691E.toInt()), // Light Green main + Dark Green tail
            Pair(0xFFFF4081.toInt(), 0xFFC2185B.toInt()), // Hot Pink main + Dark Pink tail
            Pair(0xFF7C4DFF.toInt(), 0xFF311B92.toInt()), // Violet main + Deep Purple tail
            Pair(0xFFFFAB00.toInt(), 0xFFFF6F00.toInt())  // Gold main + Orange tail
        )

        for (i in ribbonCombos.indices) {
            val (mainC, secC) = ribbonCombos[i]
            allPresets.add(
                TextStylePreset(
                    id = "ribbon_$i",
                    name = "Ribbon Banner ${i + 1}",
                    category = PresetCategory.RIBBONS_BANNERS,
                    textColor = Color.WHITE,
                    shadowColor = Color.argb(100, 0, 0, 0),
                    shadowRadius = 6f,
                    shadowDy = 3f,
                    hasLabel = true,
                    labelShape = LabelShape.RIBBON_FILL,
                    labelColor = mainC,
                    labelSecondaryColor = secC,
                    hasFoldedRibbonFlaps = true
                )
            )
        }

        // -------------------------------------------------------------
        // 5. NEON & GLOW (Electric outlines, glowing text shadows)
        // -------------------------------------------------------------
        val neonColors = listOf(
            0xFF00E5FF.toInt(), 0xFFFF007F.toInt(), 0xFF39FF14.toInt(), 0xFFFFE600.toInt(),
            0xFFBF00FF.toInt(), 0xFFFF3503.toInt(), 0xFF00FFCC.toInt(), 0xFFFF00D4.toInt(),
            0xFF00FF66.toInt(), 0xFFFFF000.toInt(), 0xFF9D00FF.toInt(), 0xFFFF6600.toInt(),
            0xFF00F0FF.toInt(), 0xFFFF0055.toInt(), 0xFF66FF00.toInt()
        )

        for (i in neonColors.indices) {
            allPresets.add(
                TextStylePreset(
                    id = "neon_$i",
                    name = "Neon Glow ${i + 1}",
                    category = PresetCategory.NEON_GLOW,
                    textColor = Color.WHITE,
                    strokeColor = neonColors[i],
                    strokeWidth = 3f,
                    shadowColor = neonColors[i],
                    shadowRadius = 20f,
                    hasLabel = i % 2 == 0,
                    labelShape = LabelShape.CAPSULE_STROKE,
                    labelColor = neonColors[i]
                )
            )
        }

        // -------------------------------------------------------------
        // 6. 3D & METALLIC (Gold/silver fills, extruded 3D shadows)
        // -------------------------------------------------------------
        val metallicGradients = listOf(
            GradientItem(colors = listOf(Color.parseColor("#FFE082"), Color.parseColor("#FFB300"), Color.parseColor("#FF8F00")), positions = listOf(0f, 0.5f, 1f), angle = 45f),
            GradientItem(colors = listOf(Color.parseColor("#FFFFFF"), Color.parseColor("#B0BEC5"), Color.parseColor("#78909C")), positions = listOf(0f, 0.5f, 1f), angle = 45f),
            GradientItem(colors = listOf(Color.parseColor("#FFCC80"), Color.parseColor("#FB8C00"), Color.parseColor("#E65100")), positions = listOf(0f, 0.5f, 1f), angle = 45f),
            GradientItem(colors = listOf(Color.parseColor("#F8BBD0"), Color.parseColor("#F06292"), Color.parseColor("#C2185B")), positions = listOf(0f, 0.5f, 1f), angle = 45f),
            GradientItem(colors = listOf(Color.parseColor("#ECEFF1"), Color.parseColor("#CFD8DC"), Color.parseColor("#90A4AE")), positions = listOf(0f, 0.5f, 1f), angle = 45f),
            GradientItem(colors = listOf(Color.parseColor("#A7F3D0"), Color.parseColor("#10B981"), Color.parseColor("#047857")), positions = listOf(0f, 0.5f, 1f), angle = 45f),
            GradientItem(colors = listOf(Color.parseColor("#FCA5A5"), Color.parseColor("#EF4444"), Color.parseColor("#B91C1C")), positions = listOf(0f, 0.5f, 1f), angle = 45f),
            GradientItem(colors = listOf(Color.parseColor("#93C5FD"), Color.parseColor("#3B82F6"), Color.parseColor("#1D4ED8")), positions = listOf(0f, 0.5f, 1f), angle = 45f),
            GradientItem(colors = listOf(Color.parseColor("#D8B4FE"), Color.parseColor("#A855F7"), Color.parseColor("#6B21A8")), positions = listOf(0f, 0.5f, 1f), angle = 45f),
            GradientItem(colors = listOf(Color.parseColor("#FFD8A8"), Color.parseColor("#FF922B"), Color.parseColor("#D9480F")), positions = listOf(0f, 0.5f, 1f), angle = 45f),
            GradientItem(colors = listOf(Color.parseColor("#FFF9C4"), Color.parseColor("#FBC02D"), Color.parseColor("#F57F17")), positions = listOf(0f, 0.5f, 1f), angle = 45f),
            GradientItem(colors = listOf(Color.parseColor("#E2E8F0"), Color.parseColor("#94A3B8"), Color.parseColor("#475569")), positions = listOf(0f, 0.5f, 1f), angle = 45f),
            GradientItem(colors = listOf(Color.parseColor("#FFFF00"), Color.parseColor("#FFD700"), Color.parseColor("#FF8C00")), positions = listOf(0f, 0.5f, 1f), angle = 45f),
            GradientItem(colors = listOf(Color.parseColor("#FFD54F"), Color.parseColor("#FF8A65"), Color.parseColor("#E53935")), positions = listOf(0f, 0.5f, 1f), angle = 45f),
            GradientItem(colors = listOf(Color.parseColor("#80DEEA"), Color.parseColor("#26C6DA"), Color.parseColor("#00838F")), positions = listOf(0f, 0.5f, 1f), angle = 45f)
        )

        for (i in metallicGradients.indices) {
            allPresets.add(
                TextStylePreset(
                    id = "metal_$i",
                    name = "3D Metallic ${i + 1}",
                    category = PresetCategory.METALLIC_3D,
                    textGradient = metallicGradients[i],
                    strokeColor = Color.BLACK,
                    strokeWidth = 2f,
                    shadowColor = Color.argb(180, 0, 0, 0),
                    shadowRadius = 4f,
                    shadowDx = 6f,
                    shadowDy = 6f,
                    hasLabel = true,
                    labelShape = LabelShape.ROUNDED_RECTANGLE_FILL,
                    labelColor = Color.argb(220, 20, 20, 20)
                )
            )
        }

        // -------------------------------------------------------------
        // 7. CALLIGRAPHY & TRADITIONAL (Urdu poetry quote banners)
        // -------------------------------------------------------------
        val calligraphyBgs = listOf(
            Pair(0xFF800000.toInt(), 0xFFFFD700.toInt()), // Crimson + Gold text
            Pair(0xFF004D40.toInt(), 0xFFE0F2F1.toInt()), // Deep Emerald + Cream text
            Pair(0xFF1A237E.toInt(), 0xFFFFD700.toInt()), // Royal Indigo + Gold text
            Pair(0xFF3E2723.toInt(), 0xFFFFECB3.toInt()), // Dark Walnut + Amber text
            Pair(0xFF263238.toInt(), 0xFF00E676.toInt()), // Charcoal + Mint Green text
            Pair(0xFF4A148C.toInt(), 0xFFFFD700.toInt()), // Deep Purple + Gold text
            Pair(0xFF880E4F.toInt(), 0xFFF8BBD0.toInt()), // Royal Maroon + Rose text
            Pair(0xFF006064.toInt(), 0xFFE0F7FA.toInt()), // Deep Teal + Soft Cyan text
            Pair(0xFFBF360C.toInt(), 0xFFFFE0B2.toInt()), // Deep Terracotta + Cream text
            Pair(0xFF311B92.toInt(), 0xFFD1C4E9.toInt()), // Imperial Violet + Lavender text
            Pair(0xFF1B5E20.toInt(), 0xFFFFD700.toInt()), // Forest Green + Gold text
            Pair(0xFF0D47A1.toInt(), 0xFFBBDEFB.toInt()), // Imperial Blue + Soft Blue text
            Pair(0xFF4E342E.toInt(), 0xFFFFF8E1.toInt()), // Chocolate + Ivory text
            Pair(0xFF212121.toInt(), 0xFFFFD700.toInt()), // Obsidian + Gold text
            Pair(0xFF004D40.toInt(), 0xFFFFD700.toInt())  // Deep Emerald + Gold text
        )

        for (i in calligraphyBgs.indices) {
            val (bg, txt) = calligraphyBgs[i]
            allPresets.add(
                TextStylePreset(
                    id = "cal_$i",
                    name = "Calligraphy ${i + 1}",
                    category = PresetCategory.CALLIGRAPHY,
                    textColor = txt,
                    shadowColor = Color.argb(120, 0, 0, 0),
                    shadowRadius = 4f,
                    shadowDy = 2f,
                    hasLabel = true,
                    labelShape = if (i % 2 == 0) LabelShape.RIBBON_FILL else LabelShape.ROUNDED_RECTANGLE_FILL,
                    labelColor = bg,
                    labelStrokeColor = txt,
                    labelStrokeWidth = 2f
                )
            )
        }

        // -------------------------------------------------------------
        // 8. DARK & HIGH CONTRAST (Midnight black pills with neon text)
        // -------------------------------------------------------------
        val darkTextColors = listOf(
            0xFF00FF66.toInt(), 0xFFFFE600.toInt(), 0xFF00E5FF.toInt(), 0xFFFF007F.toInt(),
            0xFF39FF14.toInt(), 0xFFBF00FF.toInt(), 0xFFFF9100.toInt(), 0xFF00B0FF.toInt(),
            0xFFFF3D00.toInt(), 0xFF1DE9B6.toInt(), 0xFFFFC400.toInt(), 0xFFE040FB.toInt(),
            0xFFFF6E40.toInt(), 0xFF00E676.toInt(), 0xFFFFFF00.toInt()
        )

        for (i in darkTextColors.indices) {
            allPresets.add(
                TextStylePreset(
                    id = "dark_$i",
                    name = "Dark Contrast ${i + 1}",
                    category = PresetCategory.DARK_CONTRAST,
                    textColor = darkTextColors[i],
                    hasLabel = true,
                    labelShape = if (i % 2 == 0) LabelShape.CAPSULE_FILL else LabelShape.SLANTED_FILL,
                    labelColor = 0xFF121212.toInt(),
                    labelStrokeColor = darkTextColors[i],
                    labelStrokeWidth = 2f
                )
            )
        }
    }

    fun getPresetsByCategory(category: PresetCategory, context: Context): List<TextStylePreset> {
        if (category == PresetCategory.MY_STYLES) {
            return getCustomUserSavedStyles(context)
        }
        return allPresets.filter { it.category == category }
    }

    // -------------------------------------------------------------
    // USER CUSTOM STYLES PERSISTENCE (Like custom gradients)
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
