package com.example.urduphotodesigner.data.repository

import android.content.ContentValues.TAG
import android.util.Log
import com.example.urduphotodesigner.common.sealed.Response
import com.example.urduphotodesigner.data.model.FontsResponse
import com.example.urduphotodesigner.data.remote.EndPointsInterface
import com.example.urduphotodesigner.domain.repo.FetchFontsRepo
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
