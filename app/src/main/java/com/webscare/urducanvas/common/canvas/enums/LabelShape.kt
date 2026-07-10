package com.webscare.urducanvas.common.canvas.enums

import com.google.gson.annotations.SerializedName

enum class LabelShape {
    // Rectangle
    @SerializedName("RectangleFill")
    RECTANGLE_FILL,

    @SerializedName("RectangleStroke")
    RECTANGLE_STROKE,

    // Oval
    @SerializedName("OvalFill")
    OVAL_FILL,

    @SerializedName("OvalStroke")
    OVAL_STROKE,

    // Circle
    @SerializedName("CircleFill")
    CIRCLE_FILL,

    @SerializedName("CircleStroke")
    CIRCLE_STROKE,

    // Rounded Rectangle
    @SerializedName("RoundedRectangleFill")
    ROUNDED_RECTANGLE_FILL,

    @SerializedName("RoundedRectangleStroke")
    ROUNDED_RECTANGLE_STROKE,
}
