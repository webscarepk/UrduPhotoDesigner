package com.webscare.urducanvas.common.canvas.enums

/**
 * The brush catalog.
 *
 * SERIALISATION: strokes persist their style by enum *name* (Gson writes the constant
 * name, not the ordinal), so appending constants is safe for existing projects but
 * renaming one is not. That is why several constants keep names that no longer match
 * their label — FINE_LINER shows as "Fine Line", SPRAY as "Paint Spray", NEON_GLOW as
 * "Neon" — and why the styles this catalog replaced are kept as [BrushCategory.LEGACY]
 * rather than deleted. Legacy entries still render; they are just not offered.
 */
enum class BrushStyle(
    val displayName: String,
    val category: BrushCategory = BrushCategory.LEGACY,
    val engine: BrushEngine = BrushEngine.STROKE
) {

    // ── Basic ────────────────────────────────────────────────────────────────
    MONOLINE("Monoline", BrushCategory.BASIC),
    FINE_LINER("Fine Line", BrushCategory.BASIC),
    BOLD_LINE("Bold Line", BrushCategory.BASIC),
    PEN("Pen", BrushCategory.BASIC),
    PENCIL("Pencil", BrushCategory.BASIC),
    MARKER("Marker", BrushCategory.BASIC),

    // ── Ink ──────────────────────────────────────────────────────────────────
    DRY_INK("Dry Ink", BrushCategory.INK),
    WET_INK("Wet Ink", BrushCategory.INK),
    ROUGH_INK("Rough Ink", BrushCategory.INK),
    SUMI_INK("Sumi Ink", BrushCategory.INK),
    BRUSH_INK("Brush Ink", BrushCategory.INK),

    // ── Urdu & Arabic ────────────────────────────────────────────────────────
    NASTALEEQ("Nastaleeq", BrushCategory.URDU_ARABIC),
    NASKH("Naskh", BrushCategory.URDU_ARABIC),
    RUQAH("Ruq'ah", BrushCategory.URDU_ARABIC),
    DIWANI("Diwani", BrushCategory.URDU_ARABIC),
    THULUTH("Thuluth", BrushCategory.URDU_ARABIC),
    KUFIC("Kufic", BrushCategory.URDU_ARABIC),
    ARABIC_BRUSH("Arabic Brush", BrushCategory.URDU_ARABIC),
    URDU_CALLIGRAPHY("Urdu Calligraphy", BrushCategory.URDU_ARABIC),
    URDU_SIGNATURE("Urdu Signature", BrushCategory.URDU_ARABIC),

    // ── Marker ───────────────────────────────────────────────────────────────
    HIGHLIGHTER("Highlighter", BrushCategory.MARKER),
    CHISEL_MARKER("Chisel Marker", BrushCategory.MARKER),
    SOFT_MARKER("Soft Marker", BrushCategory.MARKER),
    NEON_MARKER("Neon Marker", BrushCategory.MARKER),
    SKETCH_MARKER("Sketch Marker", BrushCategory.MARKER),

    // ── Paint ────────────────────────────────────────────────────────────────
    WATERCOLOR("Watercolor", BrushCategory.PAINT),
    ACRYLIC("Acrylic", BrushCategory.PAINT),
    OIL_PAINT("Oil", BrushCategory.PAINT),
    GOUACHE("Gouache", BrushCategory.PAINT),
    DRY_PAINT("Dry Paint", BrushCategory.PAINT),

    // ── Pencil & Sketch ──────────────────────────────────────────────────────
    GRAPHITE("Graphite", BrushCategory.PENCIL_SKETCH),
    HB_PENCIL("HB Pencil", BrushCategory.PENCIL_SKETCH),
    SOFT_PENCIL("Soft Pencil", BrushCategory.PENCIL_SKETCH),
    CHARCOAL("Charcoal", BrushCategory.PENCIL_SKETCH),
    ROUGH_SKETCH("Rough Sketch", BrushCategory.PENCIL_SKETCH),

    // ── Texture ──────────────────────────────────────────────────────────────
    GRAIN("Grain", BrushCategory.TEXTURE),
    CHALK("Chalk", BrushCategory.TEXTURE),
    PAPER("Paper", BrushCategory.TEXTURE),
    ROUGH("Rough", BrushCategory.TEXTURE),
    NOISE("Noise", BrushCategory.TEXTURE),

    // ── Spray ────────────────────────────────────────────────────────────────
    AIRBRUSH("Airbrush", BrushCategory.SPRAY),
    FINE_SPRAY("Fine Spray", BrushCategory.SPRAY),
    SPLATTER("Splatter", BrushCategory.SPRAY),
    MIST("Mist", BrushCategory.SPRAY),
    SPRAY("Paint Spray", BrushCategory.SPRAY),

    // ── Decorative ───────────────────────────────────────────────────────────
    SWIRLS("Swirls", BrushCategory.DECORATIVE, BrushEngine.FITTED),
    FLOURISHES("Flourishes", BrushCategory.DECORATIVE, BrushEngine.FITTED),
    WAVES("Waves", BrushCategory.DECORATIVE, BrushEngine.FITTED),
    UNDERLINES("Underlines", BrushCategory.DECORATIVE, BrushEngine.FITTED),
    DOODLES("Doodles", BrushCategory.DECORATIVE, BrushEngine.SCATTERED),
    LEAVES("Leaves", BrushCategory.DECORATIVE, BrushEngine.SCATTERED),

    // ── Shapes & Strokes ─────────────────────────────────────────────────────
    ARROWS("Arrows", BrushCategory.SHAPES, BrushEngine.FITTED),
    CIRCLES("Circles", BrushCategory.SHAPES, BrushEngine.FITTED),
    FRAMES("Frames", BrushCategory.SHAPES, BrushEngine.FITTED),
    DIVIDERS("Dividers", BrushCategory.SHAPES, BrushEngine.FITTED),
    DASHED("Dashed Lines", BrushCategory.SHAPES),
    CURVES("Curves", BrushCategory.SHAPES, BrushEngine.FITTED),

    // ── Glow ─────────────────────────────────────────────────────────────────
    NEON_GLOW("Neon", BrushCategory.GLOW),
    GLOW_PEN("Glow Pen", BrushCategory.GLOW),
    LIGHT_TRAIL("Light Trail", BrushCategory.GLOW),
    SPARK("Spark", BrushCategory.GLOW, BrushEngine.SCATTERED),
    SOFT_GLOW("Soft Glow", BrushCategory.GLOW),

    // ── Effects ──────────────────────────────────────────────────────────────
    SMOKE("Smoke", BrushCategory.EFFECTS, BrushEngine.SPRITE),
    FIRE("Fire", BrushCategory.EFFECTS, BrushEngine.SPRITE),
    WATER("Water", BrushCategory.EFFECTS, BrushEngine.SPRITE),
    LIGHTNING("Lightning", BrushCategory.EFFECTS, BrushEngine.FITTED),
    DUST("Dust", BrushCategory.EFFECTS, BrushEngine.SCATTERED),
    STARS("Stars", BrushCategory.EFFECTS, BrushEngine.SCATTERED),

    // ── Legacy ───────────────────────────────────────────────────────────────
    // Superseded by the catalog above but still referenced by saved projects, so they
    // keep their names, their render branches, and their place in the enum.
    ROUND_BRUSH("Round Brush"),
    CALLIGRAPHY("Calligraphy"),
    INK_PEN("Ink Pen"),
    BRUSH_PEN("Brush Pen"),
    FLAT_BRUSH("Flat Brush"),
    TEXTURE("Texture"),
    GLITTER("Glitter"),
    CRAYON("Crayon"),
    RIBBON("Ribbon"),
    DOTTED("Dotted"),
    SOFT_AIR("Soft Air"),
    PASTEL("Pastel"),
    PIXEL("Pixel"),
    BRUSH("Brush"),
    ERASER("Eraser");

    val isLegacy: Boolean get() = category == BrushCategory.LEGACY

    override fun toString(): String = displayName

    companion object {
        /** Everything the picker offers, in catalog order. */
        val selectable: List<BrushStyle> by lazy { entries.filterNot { it.isLegacy } }

        fun inCategory(category: BrushCategory): List<BrushStyle> =
            selectable.filter { it.category == category }

        /** Categories that actually have brushes, in catalog order. */
        val categories: List<BrushCategory> by lazy {
            BrushCategory.entries.filter { it != BrushCategory.LEGACY }
        }

        /**
         * Name search for the picker's search dialog. Matches the label and the category,
         * so "urdu" finds every Urdu & Arabic brush and "ink" finds the Ink shelf.
         */
        fun search(query: String): List<BrushStyle> {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return selectable
            return selectable.filter {
                it.displayName.lowercase().contains(q) ||
                        it.category.displayName.lowercase().contains(q)
            }
        }
    }
}
