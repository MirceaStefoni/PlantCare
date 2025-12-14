package com.example.plantcare.di

import android.content.Context
import androidx.work.WorkManager
import com.example.plantcare.data.local.AppDatabase
import com.example.plantcare.data.remote.GeminiService
import com.example.plantcare.data.remote.PlantRemoteDataSource
import com.example.plantcare.data.remote.openweather.OpenWeatherGeoService
import com.example.plantcare.data.remote.openweather.OpenWeatherService
import com.example.plantcare.data.repository.PlantRepositoryImpl
import com.example.plantcare.domain.repository.PlantRepository
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import com.google.android.gms.location.FusedLocationProviderClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideFusedLocation(@ApplicationContext context: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @Provides
    @Singleton
    fun providePlantRepository(
        db: AppDatabase,
        remote: PlantRemoteDataSource,
        workManager: WorkManager,
        geminiService: GeminiService,
        openWeatherGeoService: OpenWeatherGeoService,
        openWeatherService: OpenWeatherService,
        client: OkHttpClient,
        @ApplicationContext context: Context
    ): PlantRepository =
        PlantRepositoryImpl(
            plantDao = db.plantDao(),
            userDao = db.userDao(),
            careDao = db.careDao(),
            outdoorCheckDao = db.outdoorCheckDao(),
            remote = remote,
            workManager = workManager,
            geminiService = geminiService,
            openWeatherGeoService = openWeatherGeoService,
            openWeatherService = openWeatherService,
            client = client,
            context = context
        )
}


