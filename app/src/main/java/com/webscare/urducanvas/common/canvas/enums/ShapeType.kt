package com.webscare.urducanvas.common.canvas.enums

import kotlinx.serialization.Serializable

@Serializable
enum class ShapeType(val displayName: String) {
    RECTANGLE("Rectangle"),
    ROUNDED_RECTANGLE("Rounded Rectangle"),
    ELLIPSE("Ellipse"),
    LINE("Line"),
    ARROW_RIGHT("Arrow →"),
    ARROW_LEFT("← Arrow"),
    DOUBLE_ARROW("⇄ Double Arrow"),
    TRIANGLE("Triangle"),
    RIGHT_TRIANGLE("Right Triangle"),
    PARALLELOGRAM("Parallelogram"),
    TRAPEZOID("Trapezoid"),
    PENTAGON("Pentagon"),
    HEXAGON("Hexagon"),
    OCTAGON("Octagon"),
    STAR_FIVE("★ 5-Point Star"),
    STAR_SIX("✶ 6-Point Star"),
    STAR_SEVEN("✷ 7-Point Star"),
    STAR_TEN("✸ 10-Point Star"),
    HEART("♥ Heart"),
    DIAMOND("♦ Diamond"),
    ;

    companion object {
        /** Get ShapeType by display name (case-insensitive) */
        fun fromDisplayName(name: String): ShapeType? = values().find { it.displayName.equals(name, ignoreCase = true) }
    }
}
