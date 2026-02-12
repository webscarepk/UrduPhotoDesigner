package com.example.urduphotodesigner.data.repository

import android.content.ContentValues.TAG
import android.util.Log
import com.example.urduphotodesigner.common.sealed.Response
import com.example.urduphotodesigner.data.model.TemplatesResponse
import com.example.urduphotodesigner.data.remote.EndPointsInterface
import com.example.urduphotodesigner.domain.repo.FetchTemplatesRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import javax.inject.Inject

class FetchTemplatesRepoImpl @Inject constructor(
    private val api: EndPointsInterface
) : FetchTemplatesRepo {

    override fun fetchTemplates(): Flow<Response<TemplatesResponse>> = channelFlow {
        try {
            trySend(Response.Loading)
            val response = api.getAllTemplates()

            Log.e(TAG, "fetchTemplates: $response")
            trySend(Response.Success(response))
        } catch (e: Exception) {
            Log.e(TAG, "fetchTemplates: $e")
            if (e.message?.contains("Connection reset") == true){
                trySend(Response.Error("Unstable Internet Connection!"))
            }else{
                trySend(Response.Error("Unexpected Error Occurred ${e.message}"))
            }
        }
    }
}
