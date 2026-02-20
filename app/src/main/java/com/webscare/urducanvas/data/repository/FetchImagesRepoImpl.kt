package com.webscare.urducanvas.data.repository

import android.content.ContentValues.TAG
import android.util.Log
import com.webscare.urducanvas.common.sealed.Response
import com.webscare.urducanvas.data.model.ImageResponse
import com.webscare.urducanvas.data.remote.EndPointsInterface
import com.webscare.urducanvas.domain.repo.FetchImagesRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import javax.inject.Inject

class FetchImagesRepoImpl @Inject constructor(
    private val api: com.webscare.urducanvas.data.remote.EndPointsInterface
) : com.webscare.urducanvas.domain.repo.FetchImagesRepo {

    override fun fetchImages(): Flow<com.webscare.urducanvas.common.sealed.Response<com.webscare.urducanvas.data.model.ImageResponse>> = channelFlow {
        try {
            trySend(_root_ide_package_.com.webscare.urducanvas.common.sealed.Response.Loading)
            val response = api.getAllImages()

            Log.e(TAG, "fetchImages: $response")
            trySend(_root_ide_package_.com.webscare.urducanvas.common.sealed.Response.Success(response))
        } catch (e: Exception) {
            Log.e(TAG, "fetchImages: $e")
            if (e.message?.contains("Connection reset") == true) {
                trySend(_root_ide_package_.com.webscare.urducanvas.common.sealed.Response.Error("Unstable Internet Connection!"))
            } else {
                trySend(_root_ide_package_.com.webscare.urducanvas.common.sealed.Response.Error("Unexpected Error Occurred ${e.message}"))
            }
        }
    }
}
