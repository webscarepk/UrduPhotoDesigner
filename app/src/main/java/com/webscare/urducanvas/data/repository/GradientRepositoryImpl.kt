package com.webscare.urducanvas.data.repository

import android.content.ContentValues.TAG
import android.util.Log
import com.webscare.urducanvas.common.canvas.model.GradientItem
import com.webscare.urducanvas.data.mapper.toDomain
import com.webscare.urducanvas.data.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.collections.map

class GradientRepositoryImpl(
    private val dao: com.webscare.urducanvas.data.local.GradientDao,
) : com.webscare.urducanvas.domain.repo.GradientRepo {

    override fun getAllGradients(): Flow<List<GradientItem>> = dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun seedDefaultGradients(defaults: List<com.webscare.urducanvas.common.canvas.model.GradientItem>) {
        if (dao.count() == 0) {
            val entities = defaults.map { it.toEntity() }
            entities.forEach {
                Log.d(TAG, "seedDefaultGradients: TRUE")
                dao.insert(it)
            }
        }
    }

    override suspend fun insertNewGradient(gradient: com.webscare.urducanvas.common.canvas.model.GradientItem) {
        dao.insert(gradient.toEntity())
    }

    override suspend fun deleteGradientById(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun updateGradient(gradient: com.webscare.urducanvas.common.canvas.model.GradientItem) {
        dao.updateGradient(gradient.toEntity())
    }
}
