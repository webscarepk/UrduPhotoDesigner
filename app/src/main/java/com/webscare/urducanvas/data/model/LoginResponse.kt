package com.webscare.urducanvas.data.model

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("user") val user: User? = null,  // Mark as nullable
    @SerializedName("role") val role: String? = null,
    @SerializedName("token") val token: String? = null
)