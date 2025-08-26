package com.example.urduphotodesigner.common.canvas.enums

import com.google.gson.annotations.SerializedName

enum class LetterCasing {
    @SerializedName("None")
    NONE,

    @SerializedName("AllCaps")
    ALL_CAPS,

    @SerializedName("LowerCase")
    LOWER_CASE,

    @SerializedName("TitleCase")
    TITLE_CASE
}
