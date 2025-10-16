package com.example.urduphotodesigner.common.canvas.enums

import kotlinx.serialization.Serializable

@Serializable
enum class ShapeType {
    RECTANGLE,
    ROUNDED_RECTANGLE,
    ELLIPSE,
    LINE,
    ARROW_RIGHT,
    ARROW_LEFT,
    DOUBLE_ARROW,
    TRIANGLE,
    RIGHT_TRIANGLE,
    PARALLELOGRAM,
    TRAPEZOID,
    PENTAGON,
    HEXAGON,
    OCTAGON,
    STAR_FIVE,
    STAR_SIX,
    STAR_SEVEN,
    STAR_TEN,
    HEART,
    DIAMOND
}