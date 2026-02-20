package com.webscare.urducanvas.data.repository

import android.util.Log
import com.example.urduphotodesigner.common.sealed.Response
import com.example.urduphotodesigner.data.model.TrendResponse
import com.example.urduphotodesigner.data.remote.EndPointsInterface
import com.example.urduphotodesigner.domain.repo.FetchTrendsRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import javax.inject.Inject

class FetchTrendsRepoImpl @Inject constructor(
    private val api: com.webscare.urducanvas.data.remote.EndPointsInterface
) : com.webscare.urducanvas.domain.repo.FetchTrendsRepo {

    override fun fetchTrends(): Flow<com.webscare.urducanvas.common.sealed.Response<com.webscare.urducanvas.data.model.TrendResponse>> = channelFlow {
        try {
            trySend(_root_ide_package_.com.webscare.urducanvas.common.sealed.Response.Loading)
            val response = api.getTrendsWithTemplates()
            Log.d("FetchTrendsRepo", "Fetched trends: $response")
            trySend(_root_ide_package_.com.webscare.urducanvas.common.sealed.Response.Success(response))
        } catch (e: Exception) {
            val msg = if (e.message?.contains("Connection reset") == true) {
                "Unstable Internet Connection!"
            } else {
                "Unexpected Error Occurred ${e.message}"
            }
            trySend(_root_ide_package_.com.webscare.urducanvas.common.sealed.Response.Error(msg))
        }
    }
}