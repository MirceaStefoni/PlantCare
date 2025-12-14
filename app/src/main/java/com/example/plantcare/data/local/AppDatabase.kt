package com.example.plantcare.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        UserEntity::class,
        PlantEntity::class,
        CareInstructionsEntity::class,
        HealthAnalysisEntity::class,
        OutdoorCheckEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(SyncStateConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun plantDao(): PlantDao
    abstract fun careDao(): CareDao
    abstract fun healthAnalysisDao(): HealthAnalysisDao
    abstract fun outdoorCheckDao(): OutdoorCheckDao
}
