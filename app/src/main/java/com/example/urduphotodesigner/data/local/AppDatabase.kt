package com.example.urduphotodesigner.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.urduphotodesigner.data.model.ExportResult
import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.data.model.GradientEntity
import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.data.model.TrendEntity
import com.example.urduphotodesigner.data.model.TrendTemplateCrossRef

@androidx.room.Database(
    entities = [FontEntity::class, ImageEntity::class, GradientEntity::class, ExportResult::class, TemplateEntity::class, TrendEntity::class, TrendTemplateCrossRef::class],
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