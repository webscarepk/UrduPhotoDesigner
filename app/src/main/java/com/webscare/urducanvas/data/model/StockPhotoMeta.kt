package com.webscare.urducanvas.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Side-table that tracks freshness + pagination state per Pexels super-query.
 * The actual image data lives in the existing [ImageEntity] / "images" table
 * with parent_category = "Backgrounds" and category = subcategory name.
 *
 * We never touch ImageEntity's schema — zero migrations required there.
 */
@Entity(tableName = "stock_photo_meta")
data class StockPhotoMeta(
    /** e.g. "nature backgrounds", "abstract backgrounds" */
    @PrimaryKey val superQuery: String,

    /** Last page successfully fetched for this super-query */
    val lastPage: Int = 0,

    /** Epoch millis when this super-query was first cached */
    val cachedAt: Long = System.currentTimeMillis(),

    /** Total results Pexels reports — lets us know when we've hit the end */
    val totalResults: Int = 0,

    /** Whether there are more pages available */
    val hasMore: Boolean = true,
)
