package com.example.urduphotodesigner.common.canvas.enums

import com.google.gson.annotations.SerializedName

enum class ElementType {
    @SerializedName("Text")
    TEXT,

    @SerializedName("Image")
    IMAGE,

    @SerializedName("Background")
    BACKGROUND,

    @SerializedName("Draw")
    DRAW
}
