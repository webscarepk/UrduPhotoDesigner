package com.webscare.urducanvas.di

import com.webscare.urducanvas.data.repository.PexelsRepoImpl
import com.webscare.urducanvas.domain.repo.PexelsRepo
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PexelsRepoModule {
 
    @Binds
    @Singleton
    abstract fun bindPexelsRepo(impl: PexelsRepoImpl): PexelsRepo
}