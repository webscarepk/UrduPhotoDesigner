package com.webscare.urducanvas.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.webscare.urducanvas.common.canvas.sealed.ImageFilter
import com.webscare.urducanvas.common.datastore.PreferenceDataStoreAPI
import com.webscare.urducanvas.common.datastore.PreferencesDataStoreHelper
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.ImageFilterAdapter
import com.webscare.urducanvas.common.utils.SocketFactoryWithTcpNoDelay
import com.webscare.urducanvas.data.local.AppDatabase
import com.webscare.urducanvas.data.local.CanvasSizeDao
import com.webscare.urducanvas.data.local.ExportResultsDao
import com.webscare.urducanvas.data.local.GradientDao
import com.webscare.urducanvas.data.remote.EndPointsInterface
import com.webscare.urducanvas.data.repository.CanvasSizeRepoImpl
import com.webscare.urducanvas.data.repository.ExportResultsRepositoryImpl
import com.webscare.urducanvas.data.repository.FetchFontsRepoImpl
import com.webscare.urducanvas.data.repository.FetchImagesRepoImpl
import com.webscare.urducanvas.data.repository.FetchTemplatesRepoImpl
import com.webscare.urducanvas.data.repository.FetchTrendsRepoImpl
import com.webscare.urducanvas.data.repository.FontsRepoImpl
import com.webscare.urducanvas.data.repository.GradientRepositoryImpl
import com.webscare.urducanvas.data.repository.ImagesRepoImpl
import com.webscare.urducanvas.data.repository.TemplatesRepoImpl
import com.webscare.urducanvas.data.repository.TrendsRepoImpl
import com.webscare.urducanvas.domain.repo.CanvasSizeRepo
import com.webscare.urducanvas.domain.repo.ExportResultsRepo
import com.webscare.urducanvas.domain.repo.FetchFontsRepo
import com.webscare.urducanvas.domain.repo.FetchImagesRepo
import com.webscare.urducanvas.domain.repo.FetchTemplatesRepo
import com.webscare.urducanvas.domain.repo.FetchTrendsRepo
import com.webscare.urducanvas.domain.repo.FontsRepo
import com.webscare.urducanvas.domain.repo.GradientRepo
import com.webscare.urducanvas.domain.repo.ImagesRepo
import com.webscare.urducanvas.domain.repo.TemplatesRepo
import com.webscare.urducanvas.domain.repo.TrendsRepo
import com.webscare.urducanvas.domain.usecase.DeleteGradientUseCase
import com.webscare.urducanvas.domain.usecase.ExportResultsUseCase
import com.webscare.urducanvas.domain.usecase.GetAllGradientsUseCase
import com.webscare.urducanvas.domain.usecase.InsertGradientUseCase
import com.webscare.urducanvas.domain.usecase.SeedGradientsUseCase
import com.webscare.urducanvas.domain.usecase.UpdateGradientUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun providesWebApiInterface(): EndPointsInterface {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)

        val gson = GsonBuilder().setLenient().create()
        val httpClient: OkHttpClient.Builder =
            OkHttpClient.Builder().socketFactory(SocketFactoryWithTcpNoDelay())
                .addInterceptor(logging).addInterceptor(Interceptor {
                    val original: Request = it.request()
                    val originalHttpUrl: HttpUrl = original.url
                    val url = originalHttpUrl.newBuilder().build()
                    val requestBuilder: Request.Builder = original.newBuilder().url(url)
                    val request: Request = requestBuilder.build()
                    it.proceed(request)
                }).readTimeout(120, TimeUnit.SECONDS).connectTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
        return Retrofit.Builder().baseUrl(Constants.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson)).client(httpClient.build())
            .build().create(EndPointsInterface::class.java)
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder().registerTypeAdapter(ImageFilter::class.java, ImageFilterAdapter())
            .create()
    }

    @Provides
    fun providesAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.Companion(context)
    }

    @Provides
    @Singleton
    fun provideDataStoreHelper(@ApplicationContext context: Context): PreferencesDataStoreHelper {
        return PreferencesDataStoreHelper(context)
    }

    @Provides
    @Singleton
    fun provideFetchFontsRepo(api: EndPointsInterface): FetchFontsRepo {
        return FetchFontsRepoImpl(api)
    }

    @Provides
    @Singleton
    fun provideFetchTemplatesRepo(api: EndPointsInterface): FetchTemplatesRepo {
        return FetchTemplatesRepoImpl(api)
    }

    @Provides
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    @Provides
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideFontsRepo(appDatabase: AppDatabase): FontsRepo {
        return FontsRepoImpl(appDatabase)
    }

    @Provides
    @Singleton
    fun provideTemplatesRepo(appDatabase: AppDatabase): TemplatesRepo {
        return TemplatesRepoImpl(appDatabase)
    }

    @Provides
    @Singleton
    fun provideFetchImagesRepo(api: EndPointsInterface): FetchImagesRepo {
        return FetchImagesRepoImpl(api)
    }

    @Provides
    @Singleton
    fun provideImagesRepo(appDatabase: AppDatabase): ImagesRepo {
        return ImagesRepoImpl(appDatabase)
    }

    @Provides
    @Singleton
    fun provideFetchTrendsRepo(api: EndPointsInterface): FetchTrendsRepo {
        return FetchTrendsRepoImpl(api)
    }

    @Provides
    @Singleton
    fun provideTrendsRepo(appDatabase: AppDatabase, templatesRepo: TemplatesRepo): TrendsRepo {
        return TrendsRepoImpl(appDatabase, templatesRepo)
    }

    @Provides
    @Singleton
    fun providePreferenceDataStoreAPI(@ApplicationContext context: Context): PreferenceDataStoreAPI {
        return PreferencesDataStoreHelper(context)
    }

    @Provides
    @Singleton
    fun provideGradientDao(db: AppDatabase): GradientDao = db.gradientDao()

    @Provides
    @Singleton
    fun provideGradientRepository(dao: GradientDao): GradientRepo = GradientRepositoryImpl(dao)

    @Provides
    @Singleton
    fun provideGetAllUseCase(repo: GradientRepo) = GetAllGradientsUseCase(repo)

    @Provides
    @Singleton
    fun provideSeedUseCase(repo: GradientRepo) = SeedGradientsUseCase(repo)

    @Provides
    @Singleton
    fun provideDeleteUseCase(repo: GradientRepo) = DeleteGradientUseCase(repo)

    @Provides
    @Singleton
    fun provideUpdateUseCase(repo: GradientRepo) = UpdateGradientUseCase(repo)

    @Provides
    @Singleton
    fun provideInsertUseCase(repo: GradientRepo) = InsertGradientUseCase(repo)

    @Provides
    @Singleton
    fun provideExportResultsDao(appDatabase: AppDatabase): ExportResultsDao {
        return appDatabase.exportResultsDao()
    }

    @Provides
    @Singleton
    fun provideExportResultsRepository(exportResultsDao: ExportResultsDao): ExportResultsRepo {
        return ExportResultsRepositoryImpl(exportResultsDao)
    }

    @Provides
    @Singleton
    fun provideExportResultsUseCase(repository: ExportResultsRepo): ExportResultsUseCase {
        return ExportResultsUseCase(repository)
    }

    @Provides
    fun provideCanvasSizeDao(db: AppDatabase): CanvasSizeDao = db.canvasSizeDao()

    @Provides
    @Singleton
    fun provideCanvasSizeRepo(
        api: EndPointsInterface, dao: CanvasSizeDao
    ): CanvasSizeRepo {
        return CanvasSizeRepoImpl(api, dao)
    }
}