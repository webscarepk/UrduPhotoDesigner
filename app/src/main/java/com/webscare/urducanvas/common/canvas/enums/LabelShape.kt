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

    // Capsule / Pill
    @SerializedName("CapsuleFill")
    CAPSULE_FILL,

    @SerializedName("CapsuleStroke")
    CAPSULE_STROKE,

    // Tag / Arrow
    @SerializedName("TagFill")
    TAG_FILL,

    @SerializedName("TagStroke")
    TAG_STROKE,

    // Ribbon Banner
    @SerializedName("RibbonFill")
    RIBBON_FILL,

    @SerializedName("RibbonStroke")
    RIBBON_STROKE,

    // Slanted Parallelogram
    @SerializedName("SlantedFill")
    SLANTED_FILL,

    @SerializedName("SlantedStroke")
    SLANTED_STROKE,

    // Octagonal Badge
    @SerializedName("BadgeFill")
    BADGE_FILL,

    @SerializedName("BadgeStroke")
    BADGE_STROKE
}
