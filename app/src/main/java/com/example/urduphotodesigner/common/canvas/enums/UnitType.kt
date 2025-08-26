package com.example.urduphotodesigner.common.canvas.enums

import com.google.gson.annotations.SerializedName
import java.io.Serializable

enum class UnitType : Serializable {
    @SerializedName("Pixels")
    PIXELS,

    @SerializedName("Inches")
    INCHES,

    @SerializedName("Centimeters")
    CENTIMETERS
}
