package com.webscare.urducanvas.data.repository

import com.webscare.urducanvas.common.sealed.Response
import com.webscare.urducanvas.data.local.CanvasSizeDao
import com.webscare.urducanvas.data.model.CanvasSizeEntity
import com.webscare.urducanvas.data.model.CanvasSizeResponse
import com.webscare.urducanvas.data.remote.EndPointsInterface
import com.webscare.urducanvas.domain.repo.CanvasSizeRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import javax.inject.Inject

class CanvasSizeRepoImpl @Inject constructor(
    private val api: EndPointsInterface,
    private val dao: CanvasSizeDao
) : CanvasSizeRepo {

    override fun fetchAndStoreSizes(): Flow<Response<CanvasSizeResponse>> = channelFlow {
        try {
            trySend(Response.Loading)
            val response = api.getCanvasSizes()
            val entities = response.sizes.map {
                CanvasSizeEntity(id = it.id, name = it.name, width = it.width, height = it.height)
            }
            dao.insertAll(entities)
            trySend(Response.Success(response))
        } catch (e: Exception) {
            if (e.message?.contains("Connection reset") == true){
                trySend(Response.Error("Unstable Internet Connection!"))
            }else{
                trySend(Response.Error("Unexpected Error Occurred ${e.message}"))
            }
        }
    }

    override fun getLocalSizes(): Flow<List<CanvasSizeEntity>> = dao.getAllSizes()
}