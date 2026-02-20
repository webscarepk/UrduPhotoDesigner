package com.webscare.urducanvas.common.canvas.enums

import com.google.gson.annotations.SerializedName

enum class ElementType {
    @SerializedName("Text")
    TEXT,

    @SerializedName("Image")
    IMAGE,
    @SerializedName("Sticker")
    STICKER,

    @SerializedName("Background")
    BACKGROUND,

    @SerializedName("Draw")
    DRAW,

    @SerializedName("Shape")
    SHAPE
}
