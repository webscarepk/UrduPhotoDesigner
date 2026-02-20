package com.webscare.urducanvas.data.repository

import com.example.urduphotodesigner.data.local.AppDatabase
import com.example.urduphotodesigner.data.model.*
import com.example.urduphotodesigner.domain.repo.TrendsRepo
import com.example.urduphotodesigner.domain.repo.TemplatesRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TrendsRepoImpl @Inject constructor(
    private val appDatabase: com.webscare.urducanvas.data.local.AppDatabase,
    private val templatesRepo: com.webscare.urducanvas.domain.repo.TemplatesRepo // to reuse merge logic
) : com.webscare.urducanvas.domain.repo.TrendsRepo {

    override fun fetchCachedTrends(): Flow<List<com.webscare.urducanvas.data.model.TrendWithTemplates>> {
        return appDatabase.trendDao().getTrendsWithTemplates()
    }

    override suspend fun insertTrends(response: com.webscare.urducanvas.data.model.TrendResponse) {
        val latestTrendIds = response.trends.map { it.id }.toSet()

        // Delete old trends and crossRefs not in API
        appDatabase.trendDao().deleteTrendsNotIn(latestTrendIds)

        // Insert templates with merge
        response.trends.flatMap { it.templates }.forEach { template ->
            templatesRepo.insertTemplates(template)
        }

        // Insert/replace trends
        val trendEntities = response.trends.map {
            _root_ide_package_.com.webscare.urducanvas.data.model.TrendEntity(
                it.id,
                it.name,
                it.is_active
            )
        }
        appDatabase.trendDao().insertTrends(trendEntities)

        // Insert/replace cross refs
        val crossRefs = response.trends.flatMap { trend ->
            trend.templates.map { t ->
                _root_ide_package_.com.webscare.urducanvas.data.model.TrendTemplateCrossRef(
                    trend.id,
                    t.id
                )
            }
        }
        appDatabase.trendDao().insertCrossRefs(crossRefs)
    }
}
