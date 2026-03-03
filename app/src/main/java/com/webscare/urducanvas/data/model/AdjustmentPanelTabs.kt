package com.webscare.urducanvas.data.model

data class AdjustmentPanelTabs(
    val id: Int,
    val tab_name: String,
    var is_selected:Boolean = false,
    var is_enabled:Boolean = false
)
