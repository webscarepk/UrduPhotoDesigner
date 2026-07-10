package com.webscare.urducanvas.domain.repo

import com.webscare.urducanvas.common.sealed.Response
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.data.model.StockPhotoMeta
import kotlinx.coroutines.flow.Flow

interface PexelsRepo {

    /** Load next page of a category super-query. Saves to Room. Returns count inserted. */
    fun loadNextPage(superQuery: String): Flow<Response<List<ImageEntity>>>

    /**
     * API search for [query]. Saves results to Room under the query as a category name.
     * Returns list of saved entities so VM can display them immediately.
     */
    fun search(query: String, page: Int = 1): Flow<Response<List<ImageEntity>>>

    /** Paginate an existing search query (page 2+). */
    fun searchNextPage(query: String): Flow<Response<List<ImageEntity>>>

    /** Search already-cached Pexels images in Room. Zero API cost. */
    suspend fun searchLocal(query: String): List<ImageEntity>

    /** Get pagination meta for a search query (keyed as "search:QueryName"). */
    suspend fun getSearchMeta(query: String): StockPhotoMeta?

    /** No-op — we keep cache forever. Kept for interface compatibility. */
    suspend fun invalidateIfStale(superQuery: String, maxAgeMs: Long = Long.MAX_VALUE)

    /** Get pagination meta for a category super-query. */
    suspend fun getMeta(superQuery: String): StockPhotoMeta?
}
