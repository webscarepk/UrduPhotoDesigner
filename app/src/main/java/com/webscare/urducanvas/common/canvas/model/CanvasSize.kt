package com.webscare.urducanvas.common.canvas.model

import java.io.Serializable

data class CanvasSize(
    val id: Int,
    val name: String,
    val width: Float,
    val height: Float
): Serializable