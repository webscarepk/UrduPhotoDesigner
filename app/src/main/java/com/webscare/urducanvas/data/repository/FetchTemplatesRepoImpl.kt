package com.webscare.urducanvas.data.repository

import android.content.ContentValues.TAG
import android.util.Log
import com.webscare.urducanvas.common.sealed.Response
import com.webscare.urducanvas.data.model.TemplatesResponse
import com.webscare.urducanvas.data.remote.EndPointsInterface
import com.webscare.urducanvas.domain.repo.FetchTemplatesRepo
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
