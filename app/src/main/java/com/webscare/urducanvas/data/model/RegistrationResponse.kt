package com.webscare.urducanvas.data.model

import com.google.gson.annotations.SerializedName

data class RegistrationResponse(
    @SerializedName("message") val message: String,
    @SerializedName("user") val user: User,
    @SerializedName("role") val role: String,
)
