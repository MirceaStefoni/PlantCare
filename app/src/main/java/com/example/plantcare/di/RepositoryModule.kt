package com.example.plantcare.di

import android.content.Context
import androidx.work.WorkManager
import com.example.plantcare.data.local.AppDatabase
import com.example.plantcare.data.remote.GeminiService
import com.example.plantcare.data.remote.PlantRemoteDataSource
import com.example.plantcare.data.repository.PlantRepositoryImpl
import com.example.plantcare.domain.repository.PlantRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun providePlantRepository(
        db: AppDatabase,
        remote: PlantRemoteDataSource,
        workManager: WorkManager,
        geminiService: GeminiService,
        client: OkHttpClient,
        @ApplicationContext context: Context
    ): PlantRepository =
        PlantRepositoryImpl(db.plantDao(), db.userDao(), remote, workManager, geminiService, client, context)
}


