package com.example.plantcare.di

import android.content.Context
import androidx.room.Room
import com.example.plantcare.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "plantcare.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideUserDao(db: AppDatabase) = db.userDao()

    @Provides
    fun providePlantDao(db: AppDatabase) = db.plantDao()

    @Provides
    fun provideCareDao(db: AppDatabase) = db.careDao()

    @Provides
    fun provideLightMeasurementDao(db: AppDatabase) = db.lightMeasurementDao()
    fun provideHealthAnalysisDao(db: AppDatabase) = db.healthAnalysisDao()

    @Provides
    fun provideOutdoorCheckDao(db: AppDatabase) = db.outdoorCheckDao()
}
