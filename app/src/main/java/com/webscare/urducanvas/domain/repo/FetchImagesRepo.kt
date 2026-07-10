package com.webscare.urducanvas.domain.repo

import com.webscare.urducanvas.common.sealed.Response
import com.webscare.urducanvas.data.model.ImageResponse
import kotlinx.coroutines.flow.Flow

interface FetchImagesRepo {
    fun fetchImages(): Flow<Response<ImageResponse>>
}
