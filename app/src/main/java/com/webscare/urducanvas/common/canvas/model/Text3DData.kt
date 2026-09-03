package com.webscare.urducanvas.common.canvas.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Text3DRotation(
    @SerializedName("x") var x: Float = 0f,
    @SerializedName("y") var y: Float = 0f,
    @SerializedName("z") var z: Float = 0f,
    @SerializedName("enabled") var enabled: Boolean = true
) : Serializable

data class Text3DPerspective(
    @SerializedName("strength") var strength: Float = 600f,
    @SerializedName("fov") var fov: Float = 45f,
    @SerializedName("type") var type: String = "perspective" // "perspective" or "orthographic"
) : Serializable

data class Text3DPosition(
    @SerializedName("z") var z: Float = 0f
) : Serializable

data class Text3DPivot(
    @SerializedName("x") var x: Float = 0f,
    @SerializedName("y") var y: Float = 0f,
    @SerializedName("z") var z: Float = 0f
) : Serializable

data class Text3DExtrusion(
    @SerializedName("depth") var depth: Float = 0f,
    @SerializedName("scale") var scale: Float = 100f,
    @SerializedName("direction") var direction: String = "bottom-right",
    @SerializedName("bevel") var bevel: Float = 0f,
    @SerializedName("smoothness") var smoothness: Float = 70f,
    @SerializedName("enabled") var enabled: Boolean = true
) : Serializable

data class Text3DMaterial(
    @SerializedName("frontColor") var frontColor: String = "#2E7D4F",
    @SerializedName("extrusionColor") var extrusionColor: String = "#0F3A22",
    @SerializedName("surface") var surface: String = "plain",
    /** When true the extrusion side keeps tracking [frontColor] instead of holding its own. */
    @SerializedName("sameAsFront") var sameAsFront: Boolean = true,
    @SerializedName("roughness") var roughness: Float = 30f,
    @SerializedName("metallic") var metallic: Float = 0f,
    @SerializedName("specular") var specular: Float = 50f,
    @SerializedName("reflection") var reflection: Float = 10f,
    @SerializedName("enabled") var enabled: Boolean = true
) : Serializable

data class Text3DLighting(
    @SerializedName("angle") var angle: Float = 315f,
    @SerializedName("intensity") var intensity: Float = 75f,
    @SerializedName("ambient") var ambient: Float = 25f,
    @SerializedName("softness") var softness: Float = 30f,
    @SerializedName("highlight") var highlight: Float = 60f,
    @SerializedName("enabled") var enabled: Boolean = true
) : Serializable

data class Text3DShadow(
    @SerializedName("enabled") var enabled: Boolean = false,
    @SerializedName("angle") var angle: Float = 135f,
    @SerializedName("distance") var distance: Float = 12f,
    @SerializedName("opacity") var opacity: Int = 0,
    @SerializedName("blur") var blur: Float = 14f,
    @SerializedName("spread") var spread: Float = 0f,
    @SerializedName("scale") var scale: Float = 100f,
    @SerializedName("color") var color: String = "#101010"
) : Serializable

/**
 * A material surface is a shading recipe, not a fixed colour â every stop is a lightness
 * multiplier applied to whatever front colour the user picked, so one surface works across
 * the whole palette. That is why a single "Matte" entry is enough: recolouring it covers
 * what separate per-colour matte surfaces would have.
 */
data class Text3DSurface(
    val id: String,
    val label: String,
    /** Lightness multipliers sampled across the face. 1f leaves the base colour untouched. */
    val stops: List<Float>,
    /** How [stops] are laid out: "linear" (topâbottom), "radial" or "sweep". */
    val shading: String = "linear",
    /** Repeating texture drawn inside the glyphs: hatch, weave, grain, speckle or veins. */
    val pattern: String? = null,
    /** 0fâ1f opacity of the [pattern] overlay. */
    val patternAlpha: Float = 0f,
    /** Extra white specular band strength across the face. */
    val sheen: Float = 0f,
    /** Face opacity multiplier â below 1f reads as translucent. */
    val alpha: Float = 1f,
    /**
     * Colour the swatch previews itself in. The canvas always shades the user's own front
     * colour â this only stops the whole grid coming out as one wall of the app green, so a
     * finish can be recognised by its natural colour. Null keeps the swatch tracking the
     * live front colour.
     */
    val previewColor: String? = null
) : Serializable

data class Text3DPreset(
    val id: String,
    val label: String,
    val title: String,
    val desc: String,
    val depth: Float,
    val frontColor: String,
    val extrusionColor: String,
    val surface: String,
    val bevel: Float,
    val shadowOpacityPercent: Float,
    val glow: String? = null
) : Serializable

data class Text3DData(
    @SerializedName("enabled") var enabled: Boolean = true,
    @SerializedName("rotation") var rotation: Text3DRotation = Text3DRotation(),
    @SerializedName("perspective") var perspective: Text3DPerspective = Text3DPerspective(),
    @SerializedName("position") var position: Text3DPosition = Text3DPosition(),
    @SerializedName("pivot") var pivot: Text3DPivot = Text3DPivot(),
    @SerializedName("extrusion") var extrusion: Text3DExtrusion = Text3DExtrusion(),
    @SerializedName("material") var material: Text3DMaterial = Text3DMaterial(),
    @SerializedName("lighting") var lighting: Text3DLighting = Text3DLighting(),
    @SerializedName("shadow") var shadow: Text3DShadow = Text3DShadow(),
    @SerializedName("glow") var glow: String? = null,
    @SerializedName("selectedPreset") var selectedPreset: String? = "none"
) : Serializable {

    fun deepCopy(): Text3DData {
        return Text3DData(
            enabled = enabled,
            rotation = rotation.copy(),
            perspective = perspective.copy(),
            position = position.copy(),
            pivot = pivot.copy(),
            extrusion = extrusion.copy(),
            material = material.copy(),
            lighting = lighting.copy(),
            shadow = shadow.copy(),
            glow = glow,
            selectedPreset = selectedPreset
        )
    }

    fun applyPreset(preset: Text3DPreset) {
        if (preset.id == "none") {
            enabled = false
            rotation = Text3DRotation(0f, 0f, 0f)
            perspective = Text3DPerspective(600f, 45f, "perspective")
            position = Text3DPosition(0f)
            pivot = Text3DPivot(0f, 0f, 0f)
            extrusion = Text3DExtrusion(0f, 100f, "bottom-right", 0f, 70f)
            material = Text3DMaterial()
            lighting = Text3DLighting()
            shadow = Text3DShadow(enabled = false, opacity = 0)
            glow = null
            selectedPreset = "none"
            return
        }

        enabled = true
        extrusion.depth = preset.depth
        extrusion.bevel = preset.bevel
        material.frontColor = preset.frontColor
        material.extrusionColor = preset.extrusionColor
        material.surface = preset.surface
        shadow.opacity = (preset.shadowOpacityPercent * 2.55f).toInt().coerceIn(0, 255)
        shadow.enabled = preset.shadowOpacityPercent > 0f
        glow = preset.glow
        selectedPreset = preset.id
    }

    companion object {
        val PRESETS = listOf(
            Text3DPreset("none", "None", "No 3D", "Flat text with no depth, bevel or cast shadow.", 0f, "#2B2B2B", "#2B2B2B", "matte", 0f, 0f),
            Text3DPreset("soft", "Soft", "Soft 3D", "Shallow depth and a wide, low-opacity shadow. Reads well at body sizes.", 10f, "#2B2B2B", "#141414", "matte", 3f, 45f),
            Text3DPreset("bold", "Bold", "Bold 3D", "Strong 3D look with deep extrusion and soft shadows. Perfect for headlines.", 26f, "#2E7D4F", "#0F3A22", "matte", 6f, 60f),
            Text3DPreset("metal", "Metal", "Brushed Metal", "Warm metallic front with a dark, tight extrusion and a hard highlight.", 20f, "#E0B040", "#8A6412", "metal", 9f, 55f),
            Text3DPreset("chrome", "Chrome", "Chrome", "Cool grey gradient face, mirror-bright bevel, short depth.", 16f, "#C9D1D6", "#79838A", "chrome", 12f, 40f),
            Text3DPreset("neon", "Neon", "Neon Glow", "Bright face over a saturated glow. Depth stays shallow so the glow carries.", 8f, "#7FD8FF", "#1F62C6", "glossy", 2f, 0f, glow = "#2A7BFF"),
            Text3DPreset("glass", "Glass", "Glass", "Pale translucent face with a light rim and almost no shadow.", 12f, "#DCE9ED", "#A9BFC7", "glass", 10f, 25f),
            Text3DPreset("colorful", "Colorful", "Colour Shift", "Saturated face against a contrasting extrusion for a printed look.", 22f, "#E23F7B", "#5F2FA8", "glossy", 5f, 50f)
        )

        val SWATCHES = listOf(
            "#FFFFFF", "#E7E7E7", "#8E94A2", "#2B2B2B",
            "#0F3A22", "#2E7D4F", "#4CAF7D", "#A8DFC1",
            "#0B3D91", "#2A7BFF", "#7FD8FF", "#123D26",
            "#E0B040", "#E23F7B", "#5F2FA8", "#C0392B"
        )

        val SHADOW_COLORS = listOf(
            "#000000", "#8E8E8E", "#2E4A4A", "#4B0F82",
            "#3F4A8C", "#6C63E8", "#7A8B99", "#C0392B"
        )

        /**
         * Grouped roughly the way the grid reads them: plain finishes, then metals, then
         * the light-play and translucent families, then textiles, stone and print. Every
         * entry carries its own preview colour so the swatch grid shows a spread of real
         * finishes instead of one wall of app-green balls.
         */
        val SURFACES = listOf(
            // ── Basic finishes ──
            Text3DSurface("plain", "Plain", listOf(1f, 1f), previewColor = "#8E94A2"),
            Text3DSurface("matte", "Matte", listOf(1.06f, 0.92f), sheen = 0f, previewColor = "#5A6270"),
            Text3DSurface("satin", "Satin", listOf(1.18f, 0.98f, 0.86f), sheen = 0.12f, previewColor = "#7C6FA8"),
            Text3DSurface("glossy", "Glossy", listOf(1.45f, 1.05f, 0.72f, 0.95f), sheen = 0.35f, previewColor = "#2A7BFF"),
            Text3DSurface("plastic", "Plastic", listOf(1.5f, 1.0f, 0.88f), sheen = 0.42f, previewColor = "#E23F7B"),
            Text3DSurface("rubber", "Rubber", listOf(0.96f, 0.78f), sheen = 0f, previewColor = "#2F3438"),
            Text3DSurface("enamel", "Enamel", listOf(1.55f, 1.12f, 0.9f, 1.02f), shading = "radial", sheen = 0.5f, previewColor = "#D93A3A"),
            Text3DSurface("chalk", "Chalk", listOf(1.22f, 1.08f), pattern = "sand", patternAlpha = 0.18f, sheen = 0f, previewColor = "#C9C3B4"),

            // ── Metals ──
            Text3DSurface("metal", "Metal", listOf(1.35f, 0.78f, 1.15f, 0.7f, 1.25f), sheen = 0.3f, previewColor = "#9AA3AB"),
            Text3DSurface("brushed", "Brushed", listOf(1.2f, 0.85f, 1.1f, 0.82f, 1.15f, 0.8f), pattern = "grain", patternAlpha = 0.22f, sheen = 0.18f, previewColor = "#B0B6BC"),
            Text3DSurface("chrome", "Chrome", listOf(1.7f, 0.55f, 1.5f, 0.6f, 1.8f, 0.75f), sheen = 0.5f, previewColor = "#C9D1D6"),
            Text3DSurface("gold", "Gold", listOf(1.6f, 1.1f, 0.72f, 1.3f, 0.85f), sheen = 0.4f, previewColor = "#E0B040"),
            Text3DSurface("rosegold", "Rose Gold", listOf(1.55f, 1.12f, 0.8f, 1.28f, 0.9f), sheen = 0.38f, previewColor = "#E8A08D"),
            Text3DSurface("copper", "Copper", listOf(1.45f, 0.92f, 1.2f, 0.72f), sheen = 0.32f, previewColor = "#C2703C"),
            Text3DSurface("bronze", "Bronze", listOf(1.3f, 0.85f, 1.08f, 0.68f), pattern = "grain", patternAlpha = 0.16f, sheen = 0.22f, previewColor = "#9C6B33"),
            Text3DSurface("steel", "Steel Plate", listOf(1.28f, 0.8f, 1.12f, 0.76f), pattern = "diamondplate", patternAlpha = 0.4f, sheen = 0.24f, previewColor = "#77808A"),
            Text3DSurface("foil", "Foil", listOf(1.75f, 0.7f, 1.55f, 0.82f, 1.7f), pattern = "crinkle", patternAlpha = 0.34f, sheen = 0.45f, previewColor = "#D6DCE4"),
            Text3DSurface("gunmetal", "Gunmetal", listOf(1.1f, 0.6f, 0.95f, 0.55f), sheen = 0.2f, previewColor = "#4A5058"),

            // ── Light-play ──
            Text3DSurface("pearl", "Pearl", listOf(1.5f, 1.1f, 0.85f, 1.2f, 1.45f), shading = "sweep", sheen = 0.3f, previewColor = "#EADFF0"),
            Text3DSurface("holo", "Holo", listOf(1.6f, 0.8f, 1.4f, 0.75f, 1.55f, 0.85f, 1.6f), shading = "sweep", sheen = 0.35f, previewColor = "#9B6BFF"),
            Text3DSurface("oilslick", "Oil Slick", listOf(1.5f, 0.62f, 1.35f, 0.7f, 1.45f, 0.6f), shading = "sweep", sheen = 0.4f, previewColor = "#2C3E6B"),
            Text3DSurface("glitter", "Glitter", listOf(1.5f, 1.05f, 1.3f), pattern = "glitter", patternAlpha = 0.55f, sheen = 0.4f, previewColor = "#E9C46A"),
            Text3DSurface("prism", "Prism", listOf(1.65f, 1.15f, 1.4f, 1.6f), shading = "radial", sheen = 0.42f, previewColor = "#57C7E8"),
            Text3DSurface("neon", "Neon", listOf(1.7f, 1.25f, 1.5f), sheen = 0.6f, previewColor = "#39E0A6"),

            // ── Sweets and soft goods ──
            Text3DSurface("candy", "Candy", listOf(1.6f, 1.05f, 0.7f), shading = "radial", sheen = 0.45f, previewColor = "#FF5B8A"),
            Text3DSurface("gummy", "Gummy", listOf(1.45f, 1.15f, 0.85f), shading = "radial", sheen = 0.3f, alpha = 0.92f, previewColor = "#F26A2E"),
            Text3DSurface("velvet", "Velvet", listOf(1.15f, 0.8f, 0.5f), shading = "radial", sheen = 0.05f, previewColor = "#6B1F52"),
            Text3DSurface("suede", "Suede", listOf(1.12f, 0.82f, 0.66f), shading = "radial", pattern = "sand", patternAlpha = 0.22f, previewColor = "#8A6A4F"),

            // ── Translucent ──
            Text3DSurface("glass", "Glass", listOf(1.5f, 0.95f, 1.3f), sheen = 0.4f, alpha = 0.72f, previewColor = "#BFD9E2"),
            Text3DSurface("frosted", "Frosted", listOf(1.35f, 1.12f), sheen = 0.15f, alpha = 0.85f, previewColor = "#D9E4EA"),
            Text3DSurface("ice", "Ice", listOf(1.6f, 1.05f, 1.35f, 0.95f), pattern = "crinkle", patternAlpha = 0.2f, sheen = 0.38f, alpha = 0.8f, previewColor = "#9FD8F2"),
            Text3DSurface("jelly", "Jelly", listOf(1.55f, 1.0f, 1.25f), shading = "radial", sheen = 0.35f, alpha = 0.68f, previewColor = "#5FD0C4"),
            Text3DSurface("liquid", "Liquid", listOf(1.45f, 0.85f, 1.25f, 0.9f), pattern = "waves", patternAlpha = 0.3f, sheen = 0.36f, alpha = 0.9f, previewColor = "#2E86C1"),

            // ── Textiles ──
            Text3DSurface("denim", "Denim", listOf(1.1f, 0.82f), pattern = "hatch", patternAlpha = 0.3f, previewColor = "#3B5D8F"),
            Text3DSurface("linen", "Linen", listOf(1.14f, 0.95f), pattern = "linen", patternAlpha = 0.32f, previewColor = "#D8CDB8"),
            Text3DSurface("tweed", "Tweed", listOf(1.1f, 0.84f), pattern = "crosshatch", patternAlpha = 0.36f, previewColor = "#7A6A55"),
            Text3DSurface("plaid", "Plaid", listOf(1.18f, 0.86f), pattern = "plaid", patternAlpha = 0.45f, previewColor = "#A6402F"),
            Text3DSurface("knit", "Knit", listOf(1.12f, 0.88f), pattern = "mesh", patternAlpha = 0.38f, previewColor = "#C06C84"),
            Text3DSurface("leather", "Leather", listOf(1.12f, 0.74f, 0.94f), shading = "radial", pattern = "leather", patternAlpha = 0.4f, sheen = 0.14f, previewColor = "#6E4327"),
            Text3DSurface("carbon", "Carbon", listOf(1.05f, 0.62f), pattern = "weave", patternAlpha = 0.4f, sheen = 0.2f, previewColor = "#2B2F33"),

            // ── Stone, earth and print ──
            Text3DSurface("concrete", "Concrete", listOf(1.08f, 0.86f), pattern = "speckle", patternAlpha = 0.34f, previewColor = "#9E9E96"),
            Text3DSurface("marble", "Marble", listOf(1.32f, 1.02f, 1.2f), pattern = "veins", patternAlpha = 0.3f, sheen = 0.12f, previewColor = "#EFEDE6"),
            Text3DSurface("granite", "Granite", listOf(1.15f, 0.72f, 0.95f), pattern = "speckle", patternAlpha = 0.5f, sheen = 0.1f, previewColor = "#5C5F66"),
            Text3DSurface("sandstone", "Sandstone", listOf(1.2f, 0.9f, 1.02f), pattern = "sand", patternAlpha = 0.4f, previewColor = "#D2A76A"),
            Text3DSurface("obsidian", "Obsidian", listOf(1.25f, 0.45f, 0.9f, 0.4f), sheen = 0.3f, previewColor = "#1F2226"),
            Text3DSurface("wood", "Wood", listOf(1.18f, 0.88f, 1.05f, 0.8f), pattern = "grain", patternAlpha = 0.38f, previewColor = "#A9713F"),
            Text3DSurface("rust", "Rust", listOf(1.2f, 0.7f, 1.0f, 0.62f), pattern = "speckle", patternAlpha = 0.45f, previewColor = "#A4552B"),
            Text3DSurface("honeycomb", "Honeycomb", listOf(1.35f, 0.95f), pattern = "honeycomb", patternAlpha = 0.4f, sheen = 0.16f, previewColor = "#E8A317"),
            Text3DSurface("reptile", "Reptile", listOf(1.25f, 0.78f, 1.05f), pattern = "scales", patternAlpha = 0.45f, sheen = 0.22f, previewColor = "#3F7D4A"),
            Text3DSurface("camo", "Camo", listOf(1.12f, 0.82f), pattern = "camo", patternAlpha = 0.55f, previewColor = "#6B7A44"),
            Text3DSurface("polka", "Polka", listOf(1.25f, 1.0f), pattern = "dots", patternAlpha = 0.42f, previewColor = "#EF6C8B"),
            Text3DSurface("stripes", "Stripes", listOf(1.28f, 0.98f), pattern = "stripes", patternAlpha = 0.4f, previewColor = "#3AAFA9")
        )

        fun surfaceById(id: String?): Text3DSurface? =
            SURFACES.firstOrNull { it.id == id }

        val DIRECTIONS = listOf(
            "top-left" to Pair(-0.7f, -0.7f),
            "top" to Pair(0f, -1f),
            "top-right" to Pair(0.7f, -0.7f),
            "left" to Pair(-1f, 0f),
            "center" to Pair(0f, 0f),
            "right" to Pair(1f, 0f),
            "bottom-left" to Pair(-0.7f, 0.7f),
            "bottom" to Pair(0f, 1f),
            "bottom-right" to Pair(0.7f, 0.7f)
        )
    }
}
