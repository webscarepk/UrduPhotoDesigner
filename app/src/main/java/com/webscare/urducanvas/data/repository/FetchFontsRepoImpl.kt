package com.webscare.urducanvas.data.repository

import android.content.ContentValues.TAG
import android.util.Log
import com.webscare.urducanvas.common.sealed.Response
import com.webscare.urducanvas.data.model.FontsResponse
import com.webscare.urducanvas.data.remote.EndPointsInterface
import com.webscare.urducanvas.domain.repo.FetchFontsRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import javax.inject.Inject

class FetchFontsRepoImpl @Inject constructor(
    private val api: EndPointsInterface
) : FetchFontsRepo {

    override fun fetchFonts(): Flow<Response<FontsResponse>> = channelFlow {
        try {
            trySend(Response.Loading)
            val response = api.getAllFonts()

            Log.e(TAG, "fetchFontsAPI: $response")
            trySend(Response.Success(response))
        } catch (e: Exception) {
            Log.e(TAG, "fetchFontsAPI: $e")
            if (e.message?.contains("Connection reset") == true){
                trySend(Response.Error("Unstable Internet Connection!"))
            }else{
                trySend(Response.Error("Unexpected Error Occurred ${e.message}"))
            }
        }
    }
}
