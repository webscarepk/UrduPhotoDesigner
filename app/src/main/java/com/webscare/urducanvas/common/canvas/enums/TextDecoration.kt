package com.webscare.urducanvas.common.canvas.enums

import com.google.gson.annotations.SerializedName

enum class TextDecoration {
    @SerializedName("None")
    NONE,

    @SerializedName("Bold")
    BOLD,

    @SerializedName("Italic")
    ITALIC,

    @SerializedName("Underline")
    UNDERLINE,

    @SerializedName("StrikeThrough")
    STRIKE_THROUGH
}
