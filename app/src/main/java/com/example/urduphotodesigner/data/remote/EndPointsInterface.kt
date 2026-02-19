package com.example.urduphotodesigner.data.remote

import com.example.urduphotodesigner.common.utils.Constants
import com.example.urduphotodesigner.data.model.FontsResponse
import com.example.urduphotodesigner.data.model.ImageResponse
import com.example.urduphotodesigner.data.model.LoginResponse
import com.example.urduphotodesigner.data.model.RegistrationResponse
import com.example.urduphotodesigner.data.model.TemplatesResponse
import com.example.urduphotodesigner.data.model.TrendResponse
import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface EndPointsInterface {
    @GET("fonts")
    suspend fun getAllFonts(
        @Header("X-API-KEY") apiKey: String = Constants.X_API_KEY
    ): FontsResponse

    @GET("images")
    suspend fun getAllImages(
        @Header("X-API-KEY") apiKey: String = Constants.X_API_KEY
    ): ImageResponse

    @FormUrlEncoded
    @POST("login/user")
    suspend fun loginUser(
        @Field("email") email: String,
        @Field("password") password: String,
        @Header("X-API-KEY") apiKey: String = Constants.X_API_KEY
    ): LoginResponse

    @FormUrlEncoded
    @POST("register/user")
    suspend fun signUpUser(
        @Field("name") name: String,
        @Field("email") email: String,
        @Field("password") password: String,
        @Header("X-API-KEY") apiKey: String = Constants.X_API_KEY
    ): RegistrationResponse

    @GET("templates")
    suspend fun getAllTemplates(
        @Header("X-API-KEY") apiKey: String = Constants.X_API_KEY
    ): TemplatesResponse

    @GET("trends_with_templates")
    suspend fun getTrendsWithTemplates(
        @Header("X-API-KEY") apiKey: String = Constants.X_API_KEY
    ): TrendResponse

    @GET("template/json/{id}")
    suspend fun getTemplateJson(
        @Header("X-API-KEY") apiKey: String = Constants.X_API_KEY,
        @Path("id") id: String
    ): retrofit2.Response<ResponseBody>
}