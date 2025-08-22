package com.example.urduphotodesigner.data.repository

import android.util.Log
import com.example.urduphotodesigner.common.sealed.Response
import com.example.urduphotodesigner.data.model.TrendResponse
import com.example.urduphotodesigner.data.remote.EndPointsInterface
import com.example.urduphotodesigner.domain.repo.FetchTrendsRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import javax.inject.Inject

class FetchTrendsRepoImpl @Inject constructor(
    private val api: EndPointsInterface
) : FetchTrendsRepo {

    override fun fetchTrends(): Flow<Response<TrendResponse>> = channelFlow {
        try {
            trySend(Response.Loading)
            val response = api.getTrendsWithTemplates()
            Log.d("FetchTrendsRepo", "Fetched trends: $response")
            trySend(Response.Success(response))
        } catch (e: Exception) {
            val msg = if (e.message?.contains("Connection reset") == true) {
                "Unstable Internet Connection!"
            } else {
                "Unexpected Error Occurred ${e.message}"
            }
            trySend(Response.Error(msg))
        }
    }
}