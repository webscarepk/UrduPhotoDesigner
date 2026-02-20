package com.webscare.urducanvas.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.webscare.urducanvas.data.model.ExportResult
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.data.model.GradientEntity
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.data.model.TemplateEntity
import com.webscare.urducanvas.data.model.TrendEntity
import com.webscare.urducanvas.data.model.TrendTemplateCrossRef

@androidx.room.Database(
    entities = [_root_ide_package_.com.webscare.urducanvas.data.model.FontEntity::class, _root_ide_package_.com.webscare.urducanvas.data.model.ImageEntity::class, _root_ide_package_.com.webscare.urducanvas.data.model.GradientEntity::class, _root_ide_package_.com.webscare.urducanvas.data.model.ExportResult::class, _root_ide_package_.com.webscare.urducanvas.data.model.TemplateEntity::class, _root_ide_package_.com.webscare.urducanvas.data.model.TrendEntity::class, _root_ide_package_.com.webscare.urducanvas.data.model.TrendTemplateCrossRef::class],
    version = 1
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun fontsDao(): FontDao
    abstract fun imagesDao(): ImageDao
    abstract fun gradientDao(): GradientDao
    abstract fun exportResultsDao(): ExportResultsDao
    abstract fun allTemplatesDao(): AllTemplatesDao
    abstract fun trendDao(): TrendDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null
        private val LOCK = Any()

        operator fun invoke(context: Context) = instance ?: synchronized(LOCK) {
            instance ?: buildDatabase(context).also { instance = it }
        }

        private fun buildDatabase(context: Context) = Room.databaseBuilder(
            context,
            AppDatabase::class.java, "UrduPhotoDesigner.db"
        )
            .allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .build()
    }
}