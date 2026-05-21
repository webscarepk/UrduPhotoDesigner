package com.webscare.urducanvas.data.remote

import com.webscare.urducanvas.data.model.PexelsResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface PexelsApi {

    /**
     * Search photos by query.
     * Authorization header uses the Pexels API key (stored in BuildConfig / Constants).
     * per_page max = 80 on Pexels free tier.
     */
    @GET("v1/search")
    suspend fun searchPhotos(
        @Header("Authorization") apiKey: String,
        @Query("query") query: String,
        @Query("per_page") perPage: Int = 80,
        @Query("page") page: Int = 1,
        @Query("orientation") orientation: String = "portrait"   // portrait works best for backgrounds
    ): PexelsResponse

    /**
     * User-initiated free-text search (separate from category browsing).
     * Uses landscape + portrait both — no orientation filter.
     */
    @GET("v1/search")
    suspend fun userSearch(
        @Header("Authorization") apiKey: String,
        @Query("query") query: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): PexelsResponse
}