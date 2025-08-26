package com.example.urduphotodesigner.common.canvas.enums

import com.google.gson.annotations.SerializedName

enum class Mode {
    @SerializedName("None")
    NONE,

    @SerializedName("Drag")
    DRAG,

    @SerializedName("Rotate")
    ROTATE,

    @SerializedName("Resize")
    RESIZE,

    @SerializedName("MultiTouch")
    MULTI_TOUCH,

    @SerializedName("GroupEdit")
    GROUP_EDIT
}
