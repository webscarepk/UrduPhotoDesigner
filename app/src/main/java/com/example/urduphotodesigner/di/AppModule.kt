package com.example.urduphotodesigner.di

import android.content.Context
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.datastore.PreferenceDataStoreAPI
import com.example.urduphotodesigner.common.datastore.PreferencesDataStoreHelper
import com.example.urduphotodesigner.common.utils.Constants
import com.example.urduphotodesigner.common.utils.SocketFactoryWithTcpNoDelay
import com.example.urduphotodesigner.data.local.AppDatabase
import com.example.urduphotodesigner.data.local.ExportResultsDao
import com.example.urduphotodesigner.data.local.GradientDao
import com.example.urduphotodesigner.data.remote.EndPointsInterface
import com.example.urduphotodesigner.data.repository.AuthRepositoryImpl
import com.example.urduphotodesigner.data.repository.ExportResultsRepositoryImpl
import com.example.urduphotodesigner.data.repository.FetchFontsRepoImpl
import com.example.urduphotodesigner.data.repository.FetchImagesRepoImpl
import com.example.urduphotodesigner.data.repository.FontsRepoImpl
import com.example.urduphotodesigner.data.repository.GradientRepositoryImpl
import com.example.urduphotodesigner.data.repository.ImagesRepoImpl
import com.example.urduphotodesigner.domain.repo.AuthRepository
import com.example.urduphotodesigner.domain.repo.ExportResultsRepository
import com.example.urduphotodesigner.domain.repo.FetchFontsRepo
import com.example.urduphotodesigner.domain.repo.FetchImagesRepo
import com.example.urduphotodesigner.domain.repo.FontsRepo
import com.example.urduphotodesigner.domain.repo.GradientRepository
import com.example.urduphotodesigner.domain.repo.ImagesRepo
import com.example.urduphotodesigner.domain.usecase.DeleteGradientUseCase
import com.example.urduphotodesigner.domain.usecase.ExportResultsUseCase
import com.example.urduphotodesigner.domain.usecase.GetAllGradientsUseCase
import com.example.urduphotodesigner.domain.usecase.InsertGradientUseCase
import com.example.urduphotodesigner.domain.usecase.SeedGradientsUseCase
import com.example.urduphotodesigner.domain.usecase.UpdateGradientUseCase
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.gson.GsonBuilder
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

        val gson = GsonBuilder()
            .setLenient()
            .create()
        val httpClient: OkHttpClient.Builder = OkHttpClient.Builder()
            .socketFactory(SocketFactoryWithTcpNoDelay())
            .addInterceptor(logging)
            .addInterceptor(Interceptor {
                val original: Request = it.request()
                val originalHttpUrl: HttpUrl = original.url
                val url = originalHttpUrl.newBuilder()
                    .build()
                val requestBuilder: Request.Builder = original.newBuilder()
                    .url(url)
                val request: Request = requestBuilder.build()
                it.proceed(request)
            })
            .readTimeout(120, TimeUnit.SECONDS)
            .connectTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
        return Retrofit.Builder().baseUrl(Constants.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(httpClient.build())
            .build().create(EndPointsInterface::class.java)
    }

    @Provides
    fun providesAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase(context)
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
    fun provideSignInClient(@ApplicationContext context: Context): SignInClient {
        return Identity.getSignInClient(context)
    }

    @Provides
    @Singleton
    fun provideBeginSignInRequest(@ApplicationContext context: Context): BeginSignInRequest {
        return BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(context.getString(R.string.server_client_id)) // Ensure this is correctly set in your strings.xml
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .setAutoSelectEnabled(false)
            .build()
    }

    @Provides
    @Singleton
    fun providePreferenceDataStoreAPI(@ApplicationContext context: Context): PreferenceDataStoreAPI {
        return PreferencesDataStoreHelper(context)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        oneTapClient: SignInClient,
        signInRequest: BeginSignInRequest,
        preferenceDataStoreAPI: PreferenceDataStoreAPI,
        authApiService: EndPointsInterface
    ): AuthRepository {
        return AuthRepositoryImpl(
            oneTapClient,
            signInRequest,
            preferenceDataStoreAPI,
            authApiService
        )
    }

    @Provides
    @Singleton
    fun provideGradientDao(db: AppDatabase): GradientDao =
        db.gradientDao()

    @Provides
    @Singleton
    fun provideGradientRepository(dao: GradientDao): GradientRepository =
        GradientRepositoryImpl(dao)

    @Provides
    @Singleton
    fun provideGetAllUseCase(repo: GradientRepository) = GetAllGradientsUseCase(repo)

    @Provides
    @Singleton
    fun provideSeedUseCase(repo: GradientRepository) = SeedGradientsUseCase(repo)

    @Provides
    @Singleton
    fun provideDeleteUseCase(repo: GradientRepository) = DeleteGradientUseCase(repo)

    @Provides
    @Singleton
    fun provideUpdateUseCase(repo: GradientRepository) = UpdateGradientUseCase(repo)

    @Provides
    @Singleton
    fun provideInsertUseCase(repo: GradientRepository) = InsertGradientUseCase(repo)

    @Provides
    @Singleton
    fun provideExportResultsDao(appDatabase: AppDatabase): ExportResultsDao {
        return appDatabase.exportResultsDao()
    }

    @Provides
    @Singleton
    fun provideExportResultsRepository(exportResultsDao: ExportResultsDao): ExportResultsRepository {
        return ExportResultsRepositoryImpl(exportResultsDao)
    }

    @Provides
    @Singleton
    fun provideExportResultsUseCase(repository: ExportResultsRepository): ExportResultsUseCase {
        return ExportResultsUseCase(repository)
    }
}