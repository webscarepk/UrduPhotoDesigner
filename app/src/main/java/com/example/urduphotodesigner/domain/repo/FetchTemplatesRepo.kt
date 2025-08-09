package com.example.urduphotodesigner.domain.repo

import com.example.urduphotodesigner.common.sealed.Response
import com.example.urduphotodesigner.data.model.TemplatesResponse
import kotlinx.coroutines.flow.Flow

interface FetchTemplatesRepo {
    fun fetchTemplates(): Flow<Response<TemplatesResponse>>
}