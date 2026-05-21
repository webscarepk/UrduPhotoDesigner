package com.webscare.urducanvas.data.model

data class PexelsResponse(
    val photos: List<PexelsPhoto>,
    val total_results: Int,
    val page: Int,
    val per_page: Int,
    val next_page: String?
)