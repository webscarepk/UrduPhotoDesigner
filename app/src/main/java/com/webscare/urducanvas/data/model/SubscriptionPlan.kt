package com.webscare.urducanvas.data.model

data class SubscriptionPlan(
    val id: Int,
    val title: String,
    val productId: String,
    val price: String,
    val duration: String,
    val badge: String? = null,
    var isSelected: Boolean = false
)
