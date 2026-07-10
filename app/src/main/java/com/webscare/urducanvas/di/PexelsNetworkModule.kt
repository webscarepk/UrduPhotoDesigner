package com.webscare.urducanvas.di

import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.data.remote.PexelsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PexelsNetworkModule {

    @Provides
    @Singleton
    @Named("pexels")
    fun providePexelsOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            },
        )
        .build()

    @Provides
    @Singleton
    @Named("pexels")
    fun providePexelsRetrofit(@Named("pexels") okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(Constants.PEXELS_BASE_URL) // "https://api.pexels.com/"
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun providePexelsApi(@Named("pexels") retrofit: Retrofit): PexelsApi = retrofit.create(PexelsApi::class.java)
}
