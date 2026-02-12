package com.example.urduphotodesigner.data.repository

import android.content.ContentValues.TAG
import android.util.Log
import com.example.urduphotodesigner.common.sealed.Response
import com.example.urduphotodesigner.data.model.ImageResponse
import com.example.urduphotodesigner.data.remote.EndPointsInterface
import com.example.urduphotodesigner.domain.repo.FetchImagesRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import javax.inject.Inject

class FetchImagesRepoImpl @Inject constructor(
    private val api: EndPointsInterface
) : FetchImagesRepo {

    override fun fetchImages(): Flow<Response<ImageResponse>> = channelFlow {
        try {
            trySend(Response.Loading)
            val response = api.getAllImages()

            Log.e(TAG, "fetchImages: $response")
            trySend(Response.Success(response))
        } catch (e: Exception) {
            Log.e(TAG, "fetchImages: $e")
            if (e.message?.contains("Connection reset") == true) {
                trySend(Response.Error("Unstable Internet Connection!"))
            } else {
                trySend(Response.Error("Unexpected Error Occurred ${e.message}"))
            }
        }
    }
}
