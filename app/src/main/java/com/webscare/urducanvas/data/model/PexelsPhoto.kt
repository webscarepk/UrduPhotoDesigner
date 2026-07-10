package com.webscare.urducanvas.data.model

data class PexelsPhoto(
    val id: Int,
    val width: Int,
    val height: Int,
    val alt: String?,
    val src: PexelsSrc,
    val photographer: String,
)
