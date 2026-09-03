package com.webscare.urducanvas.common.canvas.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Text3DRotation(
    @SerializedName("x") var x: Float = -15f,
    @SerializedName("y") var y: Float = 20f,
    @SerializedName("z") var z: Float = 0f
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
    @SerializedName("depth") var depth: Float = 24f,
    @SerializedName("scale") var scale: Float = 100f,
    @SerializedName("direction") var direction: String = "bottom-right",
    @SerializedName("bevel") var bevel: Float = 6f,
    @SerializedName("smoothness") var smoothness: Float = 70f
) : Serializable

data class Text3DMaterial(
    @SerializedName("frontColor") var frontColor: String = "#2E7D4F",
    @SerializedName("extrusionColor") var extrusionColor: String = "#0F3A22",
    @SerializedName("surface") var surface: String = "matte", // matte, glossy, metal, chrome, glass
    @SerializedName("roughness") var roughness: Float = 50f,
    @SerializedName("metallic") var metallic: Float = 0f,
    @SerializedName("specular") var specular: Float = 40f,
    @SerializedName("reflection") var reflection: Float = 20f
) : Serializable

data class Text3DLighting(
    @SerializedName("angle") var angle: Float = 315f,
    @SerializedName("intensity") var intensity: Float = 75f,
    @SerializedName("ambient") var ambient: Float = 25f,
    @SerializedName("softness") var softness: Float = 30f,
    @SerializedName("highlight") var highlight: Float = 60f
) : Serializable

data class Text3DShadow(
    @SerializedName("enabled") var enabled: Boolean = true,
    @SerializedName("angle") var angle: Float = 135f,
    @SerializedName("distance") var distance: Float = 12f,
    @SerializedName("opacity") var opacity: Int = 160,
    @SerializedName("blur") var blur: Float = 14f,
    @SerializedName("spread") var spread: Float = 0f,
    @SerializedName("scale") var scale: Float = 100f,
    @SerializedName("color") var color: String = "#101010"
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
    @SerializedName("selectedPreset") var selectedPreset: String? = "bold"
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

        val SURFACES = listOf(
            "matte" to "Matte",
            "glossy" to "Glossy",
            "metal" to "Metal",
            "chrome" to "Chrome",
            "glass" to "Glass"
        )

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
