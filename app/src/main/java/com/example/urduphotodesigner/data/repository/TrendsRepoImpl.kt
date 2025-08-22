package com.example.urduphotodesigner.data.repository

import com.example.urduphotodesigner.data.local.AppDatabase
import com.example.urduphotodesigner.data.model.*
import com.example.urduphotodesigner.domain.repo.TrendsRepo
import com.example.urduphotodesigner.domain.repo.TemplatesRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TrendsRepoImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val templatesRepo: TemplatesRepo // to reuse merge logic
) : TrendsRepo {

    override fun fetchCachedTrends(): Flow<List<TrendWithTemplates>> {
        return appDatabase.trendDao().getTrendsWithTemplates()
    }

    override suspend fun insertTrends(response: TrendResponse) {
        val latestTrendIds = response.trends.map { it.id }.toSet()

        // Delete old trends and crossRefs not in API
        appDatabase.trendDao().deleteTrendsNotIn(latestTrendIds)

        // Insert templates with merge
        response.trends.flatMap { it.templates }.forEach { template ->
            templatesRepo.insertTemplates(template)
        }

        // Insert/replace trends
        val trendEntities = response.trends.map { TrendEntity(it.id, it.name, it.is_active) }
        appDatabase.trendDao().insertTrends(trendEntities)

        // Insert/replace cross refs
        val crossRefs = response.trends.flatMap { trend ->
            trend.templates.map { t -> TrendTemplateCrossRef(trend.id, t.id) }
        }
        appDatabase.trendDao().insertCrossRefs(crossRefs)
    }
}
