package com.example.plantcare.di

import androidx.work.WorkManager
import com.example.plantcare.data.local.AppDatabase
import com.example.plantcare.data.remote.PlantRemoteDataSource
import com.example.plantcare.data.repository.PlantRepositoryImpl
import com.example.plantcare.domain.repository.PlantRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun providePlantRepository(
        db: AppDatabase,
        remote: PlantRemoteDataSource,
        workManager: WorkManager
    ): PlantRepository =
        PlantRepositoryImpl(db.plantDao(), db.userDao(), remote, workManager)
}


