package com.webscare.urducanvas.common.canvas.enums

enum class BrushStyle(val displayName: String) {
    ROUND_BRUSH("Round Brush"),
    PENCIL("Pencil"),
    MARKER("Marker"),
    CALLIGRAPHY("Calligraphy"),
    INK_PEN("Ink Pen"),
    AIRBRUSH("Airbrush"),
    CHALK("Chalk"),
    CHARCOAL("Charcoal"),
    WATERCOLOR("Watercolor"),
    TEXTURE("Texture"),
    FINE_LINER("Fine Liner"),
    BRUSH_PEN("Brush Pen"),
    FLAT_BRUSH("Flat Brush"),
    SPLATTER("Splatter"),
    GLITTER("Glitter"),
    
    // Legacy / fallback entries
    PEN("Pen"),
    BRUSH("Brush"),
    HIGHLIGHTER("Highlighter"),
    ERASER("Eraser");

    override fun toString(): String = displayName
}
