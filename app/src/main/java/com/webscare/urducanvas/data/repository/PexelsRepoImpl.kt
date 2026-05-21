package com.webscare.urducanvas.data.repository

import android.util.Log
import com.webscare.urducanvas.common.sealed.Response
import com.webscare.urducanvas.common.utils.PexelsCategories
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.data.local.AppDatabase
import com.webscare.urducanvas.data.mapper.toImageEntity
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.data.model.StockPhotoMeta
import com.webscare.urducanvas.data.remote.PexelsApi
import com.webscare.urducanvas.domain.repo.PexelsRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import javax.inject.Inject

private const val TAG = "PexelsRepo"
private const val SEARCH_META_PREFIX = "search:"

class PexelsRepoImpl @Inject constructor(
    private val api: PexelsApi,
    private val db: AppDatabase
) : PexelsRepo {

    // ── Category browsing ─────────────────────────────────────────────────────

    override fun loadNextPage(superQuery: String): Flow<Response<List<ImageEntity>>> = channelFlow {
        trySend(Response.Loading)
        try {
            val superQueryDef = PexelsCategories.BY_QUERY[superQuery]
                ?: throw IllegalArgumentException("Unknown super-query: $superQuery")

            val meta = db.stockPhotoMetaDao().getMeta(superQuery)

            if (meta != null && !meta.hasMore) {
                Log.d(TAG, "loadNextPage: no more pages for '$superQuery'")
                trySend(Response.Success(emptyList()))
                return@channelFlow
            }

            val nextPage = (meta?.lastPage ?: 0) + 1
            Log.d(TAG, "loadNextPage: fetching '$superQuery' page $nextPage")

            val response = api.searchPhotos(
                apiKey = Constants.PEXELS_API_KEY,
                query  = superQuery,
                page   = nextPage
            )

            val entities: List<ImageEntity> = response.photos.map { photo ->
                val subcategory = PexelsCategories.classify(photo.alt, superQueryDef)
                photo.toImageEntity(subcategory)
            }

            // Save to Room for offline / next launch
            db.imagesDao().insertImages(entities)

            val hasMore = response.next_page != null &&
                    response.photos.size >= response.per_page

            db.stockPhotoMetaDao().upsert(
                StockPhotoMeta(
                    superQuery   = superQuery,
                    lastPage     = nextPage,
                    cachedAt     = meta?.cachedAt ?: System.currentTimeMillis(),
                    totalResults = response.total_results,
                    hasMore      = hasMore
                )
            )

            Log.d(TAG, "loadNextPage: ${entities.size} images for '$superQuery' p$nextPage hasMore=$hasMore")
            // Return entities so VM can appendItems() directly — no RV jump
            trySend(Response.Success(entities))

        } catch (e: Exception) {
            Log.e(TAG, "loadNextPage failed for '$superQuery': $e")
            trySend(Response.Error(when {
                e.message?.contains("Connection reset") == true      -> "Unstable Internet Connection!"
                e.message?.contains("Unable to resolve host") == true -> "No Internet Connection"
                else -> "Failed to load images: ${e.message}"
            }))
        }
    }

    // ── User search — saves results to Room under query as category name ──────

    override fun search(query: String, page: Int): Flow<Response<List<ImageEntity>>> = channelFlow {
        trySend(Response.Loading)
        try {
            val response = api.userSearch(
                apiKey = Constants.PEXELS_API_KEY,
                query  = query,
                page   = page
            )

            // Normalise query → tab name: "mountain sunset" → "Mountain sunset"
            val categoryName = query.trim().lowercase()
                .replaceFirstChar { it.uppercase() }

            val entities = response.photos.map { photo ->
                photo.toImageEntity(categoryName)
            }

            // Save to Room — results persist and appear as a new tab automatically
            if (entities.isNotEmpty()) {
                db.imagesDao().insertImages(entities)

                val metaKey = "$SEARCH_META_PREFIX$categoryName"
                val existingMeta = db.stockPhotoMetaDao().getMeta(metaKey)
                db.stockPhotoMetaDao().upsert(
                    StockPhotoMeta(
                        superQuery   = metaKey,
                        lastPage     = page,
                        cachedAt     = existingMeta?.cachedAt ?: System.currentTimeMillis(),
                        totalResults = response.total_results,
                        hasMore      = response.next_page != null
                    )
                )
            }

            Log.d(TAG, "search '$query' p$page: ${entities.size} saved under '$categoryName'")
            trySend(Response.Success(entities))

        } catch (e: Exception) {
            Log.e(TAG, "search failed: $e")
            trySend(Response.Error(e.message ?: "Search failed"))
        }
    }

    // ── Paginate an existing search query (page 2+) ───────────────────────────

    override fun searchNextPage(query: String): Flow<Response<List<ImageEntity>>> {
        val categoryName = query.trim().lowercase().replaceFirstChar { it.uppercase() }
        val metaKey = "$SEARCH_META_PREFIX$categoryName"
        return channelFlow {
            trySend(Response.Loading)
            try {
                val meta = db.stockPhotoMetaDao().getMeta(metaKey)
                val nextPage = (meta?.lastPage ?: 0) + 1

                if (meta != null && meta.hasMore == false) {
                    trySend(Response.Success(emptyList()))
                    return@channelFlow
                }

                val response = api.userSearch(
                    apiKey = Constants.PEXELS_API_KEY,
                    query  = query,
                    page   = nextPage
                )

                val entities = response.photos.map { photo ->
                    photo.toImageEntity(categoryName)
                }

                if (entities.isNotEmpty()) {
                    db.imagesDao().insertImages(entities)
                    db.stockPhotoMetaDao().upsert(
                        StockPhotoMeta(
                            superQuery   = metaKey,
                            lastPage     = nextPage,
                            cachedAt     = meta?.cachedAt ?: System.currentTimeMillis(),
                            totalResults = response.total_results,
                            hasMore      = response.next_page != null
                        )
                    )
                }

                trySend(Response.Success(entities))
            } catch (e: Exception) {
                trySend(Response.Error(e.message ?: "Failed"))
            }
        }
    }

    // ── Local Room search — zero API cost ─────────────────────────────────────

    override suspend fun searchLocal(query: String): List<ImageEntity> =
        db.imagesDao().searchPexelsImages("%${query.trim()}%")

    // ── Search meta ───────────────────────────────────────────────────────────

    override suspend fun getSearchMeta(query: String): StockPhotoMeta? {
        val categoryName = query.trim().lowercase().replaceFirstChar { it.uppercase() }
        return db.stockPhotoMetaDao().getMeta("$SEARCH_META_PREFIX$categoryName")
    }

    // ── Category meta ─────────────────────────────────────────────────────────

    override suspend fun getMeta(superQuery: String): StockPhotoMeta? =
        db.stockPhotoMetaDao().getMeta(superQuery)

    // ── No-op — cache kept forever ────────────────────────────────────────────

    override suspend fun invalidateIfStale(superQuery: String, maxAgeMs: Long) {
        // Intentionally empty — we never delete cached Pexels images
    }
}