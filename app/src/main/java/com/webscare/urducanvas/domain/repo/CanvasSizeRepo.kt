package com.webscare.urducanvas.domain.repo

import com.webscare.urducanvas.common.sealed.Response
import com.webscare.urducanvas.data.model.CanvasSizeResponse
import kotlinx.coroutines.flow.Flow

interface CanvasSizeRepo {
    fun fetchAndStoreSizes(): Flow<Response<CanvasSizeResponse>>
    fun getLocalSizes(): Flow<List<com.webscare.urducanvas.data.model.CanvasSizeEntity>>
}