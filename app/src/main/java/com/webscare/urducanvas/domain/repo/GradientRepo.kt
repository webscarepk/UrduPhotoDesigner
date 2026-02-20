package com.webscare.urducanvas.domain.repo

import com.example.urduphotodesigner.common.canvas.model.GradientItem
import kotlinx.coroutines.flow.Flow

interface GradientRepo {
  fun getAllGradients(): Flow<List<GradientItem>>
  suspend fun seedDefaultGradients(defaults: List<GradientItem>)
  suspend fun insertNewGradient(gradient: GradientItem)
  suspend fun deleteGradientById(id: Long)
  suspend fun updateGradient(gradient: GradientItem)
}