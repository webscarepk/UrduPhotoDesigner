package com.webscare.urducanvas.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "canvas_sizes")
data class CanvasSizeEntity(@PrimaryKey val id: Int, val name: String, val width: Float, val height: Float)
