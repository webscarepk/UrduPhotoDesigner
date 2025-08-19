package com.example.urduphotodesigner.data.repository

import android.content.ContentValues.TAG
import android.util.Log
import com.example.urduphotodesigner.common.canvas.model.GradientItem
import com.example.urduphotodesigner.data.local.GradientDao
import com.example.urduphotodesigner.data.mapper.toDomain
import com.example.urduphotodesigner.data.mapper.toEntity
import com.example.urduphotodesigner.domain.repo.GradientRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GradientRepositoryImpl(
  private val dao: GradientDao
) : GradientRepo {

  override fun getAllGradients(): Flow<List<GradientItem>> =
    dao.getAll().map { list -> list.map { it.toDomain() } }

  override suspend fun seedDefaultGradients(defaults: List<GradientItem>) {
    if (dao.count() == 0) {
      val entities = defaults.map { it.toEntity() }
      entities.forEach {
        Log.d(TAG, "seedDefaultGradients: TRUE")
        dao.insert(it) }
    }
  }

  override suspend fun insertNewGradient(gradient: GradientItem) {
    dao.insert(gradient.toEntity())
  }

  override suspend fun deleteGradientById(id: Long) {
    dao.deleteById(id)
  }

  override suspend fun updateGradient(gradient: GradientItem) {
    dao.updateGradient(gradient.toEntity())
  }
}