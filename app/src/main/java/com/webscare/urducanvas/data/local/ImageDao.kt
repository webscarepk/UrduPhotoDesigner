package com.webscare.urducanvas.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.webscare.urducanvas.data.model.ImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImage(imageEntity: ImageEntity)

    // Upsert — inserts if not exists, replaces if exists.
    // Used for Pexels images where we need to update is_recent = true
    // regardless of whether the row was already inserted.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImage(imageEntity: ImageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<ImageEntity>)

    // Your own images: newest first (id DESC)
    @Query("SELECT * FROM images WHERE id < 10000000 ORDER BY id DESC")
    fun getOwnImages(): Flow<List<ImageEntity>>

    // Pexels images: oldest first (id ASC) so paginated pages append at the END.
    @Query("SELECT * FROM images WHERE id >= 10000000 ORDER BY id ASC")
    fun getPexelsImages(): Flow<List<ImageEntity>>

    // Combined — own images first (id DESC within own), Pexels last (id ASC within Pexels).
    // CASE gives own images a sort_group of 0 and Pexels a sort_group of 1,
    // so own images always appear before Pexels in the combined list.
    // Within each group the secondary sort preserves the correct order.
    @Query("""
        SELECT * FROM images
        ORDER BY
            CASE WHEN id < 10000000 THEN 0 ELSE 1 END ASC,
            CASE WHEN id < 10000000 THEN -id ELSE id END ASC
    """)
    fun getAllImages(): Flow<List<ImageEntity>>

    @Delete
    suspend fun delete(imageEntity: ImageEntity)

    @Update
    suspend fun update(imageEntity: ImageEntity)

    // ── Mark recent ───────────────────────────────────────────────────────────
    // Updates is_recent = true for any image by id.
    // Works for ALL images including Pexels search results.
    // Using a direct query avoids the IGNORE/REPLACE ambiguity of @Insert/@Update.
    @Query("UPDATE images SET is_recent = 1 WHERE id = :id")
    suspend fun markAsRecent(id: Int)

    // ── Pexels search ─────────────────────────────────────────────────────────

    @Query("""
        SELECT * FROM images
        WHERE id >= 10000000
        AND (
            alt_text LIKE :query
            OR category LIKE :query
            OR file_name LIKE :query
        )
        ORDER BY id ASC
        LIMIT 80
    """)
    suspend fun searchPexelsImages(query: String): List<ImageEntity>

    // ── Pexels management ─────────────────────────────────────────────────────

    @Query("DELETE FROM images WHERE category = :category AND parent_category = 'Backgrounds' AND id >= 10000000")
    suspend fun deletePexelsByCategory(category: String)

    @Query("DELETE FROM images WHERE id >= 10000000")
    suspend fun deleteAllPexelsImages()

    @Query("SELECT COUNT(*) FROM images WHERE category = :category AND id >= 10000000")
    suspend fun countPexelsByCategory(category: String): Int
}