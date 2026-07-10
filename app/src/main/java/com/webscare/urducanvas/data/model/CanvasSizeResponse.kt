package com.webscare.urducanvas.data.model

import com.google.gson.annotations.SerializedName
import com.webscare.urducanvas.common.canvas.model.CanvasSize

data class CanvasSizeResponse(
    @SerializedName("Message") val message: String,
    @SerializedName("Canvas Sizes") val sizes: List<CanvasSize>,
)
