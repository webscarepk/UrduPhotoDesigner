package com.example.urduphotodesigner.common.canvas.enums

enum class BrushStyle(val displayName: String) {
    PEN("Pen"),
    MARKER("Marker"),
    PENCIL("Pencil"),
    HIGHLIGHTER("Highlighter"),
    BRUSH("Brush"),
    ERASER("Eraser");

    override fun toString(): String = displayName
}
