package com.webscare.urducanvas.common.canvas.model

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import com.webscare.urducanvas.common.canvas.enums.BrushStyle

/**
 * How one brush lays down paint, expressed as parameters rather than as a branch.
 *
 * The renderer used to carry a `when (style)` arm per brush in two places — the paint
 * builder and the draw dispatcher — which meant a 68-brush catalog would have meant 136
 * arms kept in sync by hand. Instead every brush names a [Kind] (the routine that draws
 * it) and tunes it with the fields below, so "Dry Ink" and "Wet Ink" are two rows of a
 * table rather than two blocks of drawing code.
 */
data class BrushProfile(
    val kind: Kind,

    /** Multiplier on the user's thickness. Fine liners are thin, chisels broad. */
    val widthScale: Float = 1f,

    /** Alpha at fully soft (hardness 0) and fully crisp (hardness 1). */
    val alphaMin: Int = 180,
    val alphaMax: Int = 255,

    val cap: Paint.Cap = Paint.Cap.ROUND,
    val join: Paint.Join = Paint.Join.ROUND,

    /** Blur radius as a fraction of `softness * thickness`. 0 disables the blur. */
    val blurFactor: Float = 0.35f,

    /** When set, the blur applies at any softness rather than only when soft. */
    val blurAlways: Boolean = false,
    val blurStyle: BlurMaskFilter.Blur = BlurMaskFilter.Blur.NORMAL,

    /**
     * Dash intervals in units of thickness — the grain that separates a pencil from a
     * pen. Scaled by thickness at paint time so the texture holds at any size.
     */
    val dash: FloatArray? = null,

    // ── Nib (calligraphy) ────────────────────────────────────────────────────
    /** Bearing of the nib's flat edge. The stroke is widest across it, thinnest along it. */
    val nibAngleDeg: Float = 45f,

    /** Narrowest the nib gets, as a fraction of full width. Lower = more contrast. */
    val nibMinRatio: Float = 0.2f,

    /** Nib strokes that also thin with speed, the way a loaded brush does. */
    val nibTapers: Boolean = false,

    // ── Scatter ──────────────────────────────────────────────────────────────
    /** Dot radius as a fraction of thickness. */
    val dotScale: Float = 0.3f,

    /** How far off the path dots land, in units of thickness. */
    val spread: Float = 2f,

    /** Dots per sample point. */
    val density: Int = 3,

    /** Width of the solid core drawn under a scatter, as a fraction of thickness. 0 = none. */
    val coreWidth: Float = 0.7f,

    // ── Wet (layered washes) ─────────────────────────────────────────────────
    val washWidth: Float = 1.3f,
    val washAlpha: Float = 0.35f,
    val coreAlpha: Float = 0.65f,

    // ── Glow ─────────────────────────────────────────────────────────────────
    /** Halo radius as a fraction of thickness. */
    val glowRadius: Float = 0.8f,

    /** Bright core width as a fraction of thickness. */
    val glowCore: Float = 0.35f,

    // ── Bristle ──────────────────────────────────────────────────────────────
    val bristles: Int = 5,

    /** Sideways wobble per bristle, in units of thickness. 0 = perfectly parallel. */
    val bristleJitter: Float = 0f,

    // ── Texture (lengthwise streaks) ─────────────────────────────────────────
    /**
     * How many streaks run *along* the stroke.
     *
     * Grain in a real mark runs with the drag — a pencil's tooth, a dry brush's split
     * bristles, charcoal skipping over paper. The renderer used to fake it with a dash
     * pattern, which breaks the line *across* its length and reads as a dotted line
     * rather than as texture. These streaks are what actually make Pencil look like
     * pencil and Dry Ink look dry.
     */
    val streaks: Int = 7,

    /** Sideways wander of each streak, in units of thickness. */
    val streakJitter: Float = 0.12f,

    /** Fraction of streak segments dropped outright, leaving bare paper. */
    val streakDropout: Float = 0.25f,

    /** How much streak alpha varies, 0 = uniform, 1 = anywhere from nothing to full. */
    val streakAlphaVar: Float = 0.5f,

    /** Length of one streak segment, in units of thickness. Short = scratchy. */
    val streakStep: Float = 0.9f
) {
    /** The draw routine a brush runs through. */
    enum class Kind {
        /** One pass along the path at constant width. */
        SOLID,

        /** Width and alpha fall off toward the ends and with speed. */
        TAPERED,

        /** Broken, grainy coverage from a dash pattern. */
        TEXTURED,

        /** Several thin parallel lines — a loaded, splayed brush. */
        BRISTLE,

        /** A wide translucent wash with a denser core over it. */
        WET,

        /** Dots thrown along the path, with an optional core. */
        SCATTER,

        /** A blurred halo with a bright core inside it. */
        GLOW,

        /** An angled flat nib: width depends on the angle between travel and nib. */
        NIB,

        /** Removes paint instead of adding it. */
        ERASE
    }

    companion object {

        /**
         * The catalog. One row per brush; anything not listed falls back to a plain
         * round brush, which is what legacy styles and any future addition get.
         */
        fun of(style: BrushStyle): BrushProfile = when (style) {

            // ── Basic ────────────────────────────────────────────────────────
            BrushStyle.MONOLINE -> BrushProfile(Kind.SOLID, alphaMin = 255, alphaMax = 255, blurFactor = 0f)
            BrushStyle.FINE_LINER -> BrushProfile(Kind.SOLID, widthScale = 0.4f, alphaMin = 200, blurFactor = 0.15f)
            BrushStyle.BOLD_LINE -> BrushProfile(Kind.SOLID, widthScale = 1.7f, alphaMin = 255, alphaMax = 255, blurFactor = 0f)
            BrushStyle.PEN -> BrushProfile(Kind.TAPERED, alphaMin = 140, blurFactor = 0.2f)
            BrushStyle.PENCIL -> BrushProfile(
                Kind.TEXTURED, widthScale = 0.8f, alphaMin = 130, alphaMax = 225, blurFactor = 0f,
                streaks = 5, streakJitter = 0.18f, streakDropout = 0.30f,
                streakAlphaVar = 0.55f, streakStep = 0.7f
            )
            BrushStyle.MARKER -> BrushProfile(
                Kind.SOLID, cap = Paint.Cap.SQUARE, alphaMin = 110, alphaMax = 240, blurFactor = 0.2f
            )

            // ── Ink ──────────────────────────────────────────────────────────
            // Dry Ink and Rough Ink are broad and mostly solid, split by a few dry gaps —
            // long segments, few streaks. That is the difference from a pencil, which is
            // fine, scratchy and mostly gap.
            BrushStyle.DRY_INK -> BrushProfile(
                Kind.TEXTURED, widthScale = 1.15f, alphaMin = 205, blurFactor = 0f,
                streaks = 12, streakJitter = 0.06f, streakDropout = 0.18f,
                streakAlphaVar = 0.35f, streakStep = 0.9f
            )
            BrushStyle.WET_INK -> BrushProfile(
                Kind.WET, widthScale = 1.05f, washWidth = 1.2f, washAlpha = 0.45f, coreAlpha = 0.95f
            )
            BrushStyle.ROUGH_INK -> BrushProfile(
                Kind.TEXTURED, widthScale = 1.2f, alphaMin = 215, blurFactor = 0f,
                streaks = 12, streakJitter = 0.13f, streakDropout = 0.27f,
                streakAlphaVar = 0.5f, streakStep = 0.8f
            )
            BrushStyle.SUMI_INK -> BrushProfile(Kind.TAPERED, widthScale = 1.25f, alphaMin = 200, blurFactor = 0.18f)
            BrushStyle.BRUSH_INK -> BrushProfile(Kind.TAPERED, widthScale = 1.1f, alphaMin = 230, blurFactor = 0.15f)

            // ── Urdu & Arabic ────────────────────────────────────────────────
            // Nib angle and contrast are what separate the scripts: Nastaleeq and Diwani
            // cut steeply for extreme thick/thin, Ruq'ah is blunt and even, Kufic is so
            // square it reads as monoline with hard terminals.
            BrushStyle.NASTALEEQ -> BrushProfile(Kind.NIB, nibAngleDeg = 52f, nibMinRatio = 0.12f, widthScale = 1.1f)
            BrushStyle.NASKH -> BrushProfile(Kind.NIB, nibAngleDeg = 30f, nibMinRatio = 0.28f)
            BrushStyle.RUQAH -> BrushProfile(Kind.NIB, nibAngleDeg = 20f, nibMinRatio = 0.48f, cap = Paint.Cap.ROUND)
            BrushStyle.DIWANI -> BrushProfile(Kind.NIB, nibAngleDeg = 62f, nibMinRatio = 0.14f, nibTapers = true)
            BrushStyle.THULUTH -> BrushProfile(Kind.NIB, nibAngleDeg = 40f, nibMinRatio = 0.18f, widthScale = 1.3f)
            BrushStyle.KUFIC -> BrushProfile(
                Kind.NIB, nibAngleDeg = 0f, nibMinRatio = 0.85f, cap = Paint.Cap.SQUARE, join = Paint.Join.MITER
            )
            BrushStyle.ARABIC_BRUSH -> BrushProfile(
                Kind.NIB, nibAngleDeg = 45f, nibMinRatio = 0.22f, nibTapers = true, widthScale = 1.2f
            )
            BrushStyle.URDU_CALLIGRAPHY -> BrushProfile(Kind.NIB, nibAngleDeg = 48f, nibMinRatio = 0.15f)
            BrushStyle.URDU_SIGNATURE -> BrushProfile(
                Kind.NIB, nibAngleDeg = 55f, nibMinRatio = 0.10f, nibTapers = true, widthScale = 0.85f
            )

            // ── Marker ───────────────────────────────────────────────────────
            BrushStyle.HIGHLIGHTER -> BrushProfile(
                Kind.SOLID, widthScale = 1.45f, cap = Paint.Cap.BUTT, alphaMin = 70, alphaMax = 150, blurFactor = 0.1f
            )
            BrushStyle.CHISEL_MARKER -> BrushProfile(
                Kind.NIB, nibAngleDeg = 45f, nibMinRatio = 0.33f, cap = Paint.Cap.BUTT, widthScale = 1.15f
            )
            BrushStyle.SOFT_MARKER -> BrushProfile(
                Kind.SOLID, alphaMin = 150, alphaMax = 225, blurFactor = 0.3f, blurAlways = true
            )
            BrushStyle.NEON_MARKER -> BrushProfile(
                Kind.GLOW, glowRadius = 0.55f, glowCore = 0.6f, alphaMin = 255, alphaMax = 255
            )
            BrushStyle.SKETCH_MARKER -> BrushProfile(
                Kind.BRISTLE, bristles = 3, bristleJitter = 0.05f, alphaMin = 110, alphaMax = 170
            )

            // ── Paint ────────────────────────────────────────────────────────
            BrushStyle.WATERCOLOR -> BrushProfile(Kind.WET, washWidth = 1.35f, washAlpha = 0.3f, coreAlpha = 0.6f)
            BrushStyle.ACRYLIC -> BrushProfile(Kind.BRISTLE, bristles = 6, widthScale = 1.1f, alphaMin = 245, alphaMax = 255)
            BrushStyle.OIL_PAINT -> BrushProfile(
                Kind.BRISTLE, bristles = 7, widthScale = 1.2f, bristleJitter = 0.04f, alphaMin = 250, alphaMax = 255
            )
            BrushStyle.GOUACHE -> BrushProfile(Kind.WET, washWidth = 1.1f, washAlpha = 0.6f, coreAlpha = 1f)
            BrushStyle.DRY_PAINT -> BrushProfile(
                Kind.BRISTLE, bristles = 9, bristleJitter = 0.09f, widthScale = 1.15f,
                dash = floatArrayOf(0.35f, 0.14f), alphaMin = 220
            )

            // ── Pencil & Sketch ──────────────────────────────────────────────
            BrushStyle.GRAPHITE -> BrushProfile(
                Kind.TEXTURED, widthScale = 0.75f, alphaMin = 110, alphaMax = 200, blurFactor = 0f,
                streaks = 6, streakJitter = 0.22f, streakDropout = 0.35f,
                streakAlphaVar = 0.6f, streakStep = 0.6f
            )
            BrushStyle.HB_PENCIL -> BrushProfile(
                Kind.TEXTURED, widthScale = 0.55f, alphaMin = 150, alphaMax = 225, blurFactor = 0f,
                streaks = 4, streakJitter = 0.12f, streakDropout = 0.20f,
                streakAlphaVar = 0.4f, streakStep = 0.8f
            )
            BrushStyle.SOFT_PENCIL -> BrushProfile(
                Kind.TEXTURED, widthScale = 0.95f, alphaMin = 95, alphaMax = 185, blurFactor = 0.2f,
                streaks = 8, streakJitter = 0.26f, streakDropout = 0.22f,
                streakAlphaVar = 0.5f, streakStep = 0.9f
            )
            BrushStyle.CHARCOAL -> BrushProfile(
                Kind.TEXTURED, widthScale = 1.25f, alphaMin = 175, alphaMax = 255, blurFactor = 0f,
                streaks = 9, streakJitter = 0.30f, streakDropout = 0.30f,
                streakAlphaVar = 0.6f, streakStep = 1.1f
            )
            BrushStyle.ROUGH_SKETCH -> BrushProfile(
                Kind.BRISTLE, bristles = 3, bristleJitter = 0.16f, widthScale = 0.8f, alphaMin = 120, alphaMax = 190
            )

            // ── Texture ──────────────────────────────────────────────────────
            BrushStyle.GRAIN -> BrushProfile(
                Kind.TEXTURED, alphaMin = 200, blurFactor = 0f,
                streaks = 10, streakJitter = 0.10f, streakDropout = 0.42f,
                streakAlphaVar = 0.6f, streakStep = 0.5f
            )
            BrushStyle.CHALK -> BrushProfile(
                Kind.TEXTURED, alphaMin = 160, alphaMax = 240, blurFactor = 0f,
                streaks = 8, streakJitter = 0.24f, streakDropout = 0.32f,
                streakAlphaVar = 0.55f, streakStep = 0.8f
            )
            BrushStyle.PAPER -> BrushProfile(
                Kind.TEXTURED, alphaMin = 110, alphaMax = 175, blurFactor = 0.15f, blurAlways = true,
                streaks = 12, streakJitter = 0.16f, streakDropout = 0.46f,
                streakAlphaVar = 0.65f, streakStep = 0.55f
            )
            BrushStyle.ROUGH -> BrushProfile(
                Kind.TEXTURED, widthScale = 1.15f, alphaMin = 210, blurFactor = 0f,
                streaks = 7, streakJitter = 0.32f, streakDropout = 0.36f,
                streakAlphaVar = 0.5f, streakStep = 1.25f
            )
            BrushStyle.NOISE -> BrushProfile(
                Kind.SCATTER, dotScale = 0.09f, spread = 1.1f, density = 12, coreWidth = 0f, alphaMin = 200
            )

            // ── Spray ────────────────────────────────────────────────────────
            BrushStyle.AIRBRUSH -> BrushProfile(
                Kind.SOLID, widthScale = 1.2f, alphaMin = 80, alphaMax = 190,
                blurFactor = 0.7f, blurAlways = true
            )
            BrushStyle.FINE_SPRAY -> BrushProfile(
                Kind.SCATTER, dotScale = 0.10f, spread = 1.5f, density = 9, coreWidth = 0f
            )
            BrushStyle.SPLATTER -> BrushProfile(Kind.SCATTER, dotScale = 0.35f, spread = 2.3f, density = 3, coreWidth = 0.7f)
            BrushStyle.MIST -> BrushProfile(
                Kind.SCATTER, dotScale = 0.16f, spread = 3.1f, density = 7, coreWidth = 0f,
                alphaMin = 60, alphaMax = 120, blurFactor = 0.5f, blurAlways = true
            )
            BrushStyle.SPRAY -> BrushProfile(Kind.SCATTER, dotScale = 0.22f, spread = 2.6f, density = 6, coreWidth = 0.4f)

            // ── Glow ─────────────────────────────────────────────────────────
            BrushStyle.NEON_GLOW -> BrushProfile(
                Kind.GLOW, glowRadius = 0.95f, glowCore = 0.35f, alphaMin = 255, alphaMax = 255
            )
            BrushStyle.GLOW_PEN -> BrushProfile(Kind.GLOW, glowRadius = 0.5f, glowCore = 0.5f, alphaMin = 255, alphaMax = 255)
            BrushStyle.LIGHT_TRAIL -> BrushProfile(Kind.GLOW, glowRadius = 0.75f, glowCore = 0.22f, alphaMin = 230)
            BrushStyle.SOFT_GLOW -> BrushProfile(
                Kind.GLOW, glowRadius = 1.4f, glowCore = 0.15f, alphaMin = 140, alphaMax = 200
            )

            // ── Shapes & Strokes (the one stroke-based member) ───────────────
            BrushStyle.DASHED -> BrushProfile(
                Kind.SOLID, alphaMin = 255, alphaMax = 255, blurFactor = 0.1f,
                dash = floatArrayOf(2.5f, 2f)
            )

            // ── Legacy ───────────────────────────────────────────────────────
            BrushStyle.CALLIGRAPHY, BrushStyle.RIBBON -> BrushProfile(Kind.NIB)
            BrushStyle.INK_PEN, BrushStyle.BRUSH_PEN -> BrushProfile(Kind.TAPERED, alphaMin = 140, blurFactor = 0.2f)
            BrushStyle.FLAT_BRUSH -> BrushProfile(Kind.BRISTLE)
            BrushStyle.TEXTURE -> BrushProfile(
                Kind.TEXTURED, alphaMin = 240, streaks = 8, streakDropout = 0.3f
            )
            BrushStyle.GLITTER -> BrushProfile(Kind.SCATTER, dotScale = 0.25f, spread = 1.6f, density = 6, coreWidth = 0.5f)
            BrushStyle.CRAYON -> BrushProfile(
                Kind.TEXTURED, alphaMin = 190, streaks = 6, streakJitter = 0.2f,
                streakDropout = 0.28f, streakStep = 0.9f
            )
            BrushStyle.DOTTED -> BrushProfile(Kind.SOLID, alphaMin = 255, alphaMax = 255, dash = floatArrayOf(0.05f, 1.8f))
            BrushStyle.SOFT_AIR -> BrushProfile(
                Kind.SOLID, alphaMin = 90, alphaMax = 210, blurFactor = 0.7f, blurAlways = true
            )
            BrushStyle.PASTEL -> BrushProfile(
                Kind.TEXTURED, alphaMin = 180, streaks = 9, streakJitter = 0.26f,
                streakDropout = 0.24f, streakStep = 1f
            )
            BrushStyle.PIXEL -> BrushProfile(
                Kind.SOLID, cap = Paint.Cap.SQUARE, join = Paint.Join.MITER,
                alphaMin = 255, alphaMax = 255, blurFactor = 0f
            )
            BrushStyle.ERASER -> BrushProfile(Kind.ERASE, alphaMin = 255, alphaMax = 255, blurFactor = 0f)

            // Round brush is the fallback everything else inherits.
            else -> BrushProfile(Kind.SOLID)
        }
    }

    /** Dash intervals in pixels for a given thickness, or null when the brush has none. */
    fun dashIntervals(thickness: Float): FloatArray? {
        val pattern = dash ?: return null
        val scale = thickness.coerceAtLeast(1f)
        return FloatArray(pattern.size) { (pattern[it] * scale).coerceAtLeast(0.5f) }
    }

    /** Alpha for a hardness in 0..1, before the stroke's own opacity is applied. */
    fun alphaFor(hardness: Float): Int =
        (alphaMin + (alphaMax - alphaMin) * hardness.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
}
