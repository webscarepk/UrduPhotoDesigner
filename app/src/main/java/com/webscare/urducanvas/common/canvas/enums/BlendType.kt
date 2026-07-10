package com.webscare.urducanvas.common.canvas.enums

import com.google.gson.annotations.SerializedName

enum class BlendType {
    @SerializedName("Source")
    SRC,

    @SerializedName("Normal")
    NORMAL,

    @SerializedName("Darken")
    DARKEN,

    @SerializedName("Lighten")
    LIGHTEN,

    @SerializedName("Multiply")
    MULTIPLY,

    @SerializedName("Screen")
    SCREEN,
}
