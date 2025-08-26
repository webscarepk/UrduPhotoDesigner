package com.example.urduphotodesigner.common.canvas.enums

import com.google.gson.annotations.SerializedName

enum class ParagraphIndentation {
    @SerializedName("None")
    NONE,

    @SerializedName("IncreaseIndent")
    INCREASE_INDENT,

    @SerializedName("DecreaseIndent")
    DECREASE_INDENT
}
