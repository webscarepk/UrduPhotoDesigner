package com.webscare.urducanvas.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trends")
data class TrendEntity(@PrimaryKey val id: Int, val name: String, val is_active: Boolean)
