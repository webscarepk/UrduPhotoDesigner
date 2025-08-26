package com.example.urduphotodesigner.common.canvas.enums

import com.google.gson.annotations.SerializedName

enum class BlendType {
    @SerializedName("Src")
    SRC,

    @SerializedName("Dst")
    DST,

    @SerializedName("SrcOver")
    SRC_OVER,

    @SerializedName("DstOver")
    DST_OVER,

    @SerializedName("SrcIn")
    SRC_IN,

    @SerializedName("DstIn")
    DST_IN,

    @SerializedName("SrcOut")
    SRC_OUT,

    @SerializedName("DstOut")
    DST_OUT,

    @SerializedName("SrcAtop")
    SRC_ATOP,

    @SerializedName("DstAtop")
    DST_ATOP,

    @SerializedName("Xor")
    XOR,

    @SerializedName("Darken")
    DARKEN,

    @SerializedName("Lighten")
    LIGHTEN,

    @SerializedName("Add")
    ADD,

    @SerializedName("Multiply")
    MULTIPLY,

    @SerializedName("Screen")
    SCREEN
}
