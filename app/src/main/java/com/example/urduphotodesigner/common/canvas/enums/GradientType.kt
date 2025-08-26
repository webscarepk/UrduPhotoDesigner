package com.example.urduphotodesigner.common.canvas.enums

import com.google.gson.annotations.SerializedName

enum class GradientType {
    @SerializedName("Linear")
    LINEAR,

    @SerializedName("Radial")
    RADIAL,

    @SerializedName("Sweep")
    SWEEP
}
