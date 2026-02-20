package com.webscare.urducanvas.common.canvas.model

import com.example.urduphotodesigner.common.canvas.sealed.ImageFilter
import com.webscare.urducanvas.common.canvas.sealed.ImageFilter

data class FilterItem(
    val name: String,
    val filter: ImageFilter,
    var isSelected: Boolean = false // New property to track selection state
)