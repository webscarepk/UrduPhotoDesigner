package com.webscare.urducanvas.domain.repo

import com.example.urduphotodesigner.common.sealed.Response
import com.example.urduphotodesigner.data.model.ImageResponse
import kotlinx.coroutines.flow.Flow

interface FetchImagesRepo {
    fun fetchImages(): Flow<Response<ImageResponse>>
}