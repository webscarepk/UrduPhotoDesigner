package com.webscare.urducanvas.common.canvas.enums

/**
 * Shelf a brush sits on in the styles picker. Order here is the order the rail renders,
 * so it runs from the everyday marks a user reaches for first out to the decorative and
 * effect brushes they go looking for.
 */
enum class BrushCategory(val displayName: String) {
    BASIC("Basic"),
    INK("Ink"),
    URDU_ARABIC("Urdu & Arabic"),
    MARKER("Marker"),
    PAINT("Paint"),
    PENCIL_SKETCH("Pencil & Sketch"),
    TEXTURE("Texture"),
    SPRAY("Spray"),
    DECORATIVE("Decorative"),
    SHAPES("Shapes & Strokes"),
    GLOW("Glow"),
    EFFECTS("Effects"),

    /** Styles kept only so older projects still open. Never shown in the picker. */
    LEGACY("Legacy")
}

/**
 * How a brush turns a drag into marks.
 *
 * [STROKE] is the classic model — paint laid continuously along the path — and covers
 * every category up to Spray plus Glow. The rest of the taxonomy does not fit it: an
 * arrow is one shape spanning the drag, leaves are a motif repeated along it, and smoke
 * is soft sprites layered over it. Those three behaviours are what [BrushStampEngine]
 * exists for.
 */
enum class BrushEngine {
    /** Paint applied continuously along the path. */
    STROKE,

    /** One shape fitted to the drag's start, end and bounds. */
    FITTED,

    /** A motif repeated along the path at an interval, with jitter. */
    SCATTERED,

    /** Soft alpha sprites composited along the path. */
    SPRITE
}
