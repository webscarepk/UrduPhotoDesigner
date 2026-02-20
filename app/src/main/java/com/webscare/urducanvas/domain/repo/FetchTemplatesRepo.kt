package com.webscare.urducanvas.domain.repo

import com.webscare.urducanvas.common.sealed.Response
import com.webscare.urducanvas.data.model.TemplatesResponse
import kotlinx.coroutines.flow.Flow

interface FetchTemplatesRepo {
    fun fetchTemplates(): Flow<Response<TemplatesResponse>>
}