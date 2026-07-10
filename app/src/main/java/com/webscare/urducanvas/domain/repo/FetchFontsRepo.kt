package com.webscare.urducanvas.domain.repo

import com.webscare.urducanvas.common.sealed.Response
import com.webscare.urducanvas.data.model.FontsResponse
import kotlinx.coroutines.flow.Flow

interface FetchFontsRepo {
    fun fetchFonts(): Flow<Response<FontsResponse>>
}
