package com.example.plantcare.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [UserEntity::class, PlantEntity::class, CareInstructionsEntity::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(SyncStateConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun plantDao(): PlantDao
    abstract fun careDao(): CareDao
}


