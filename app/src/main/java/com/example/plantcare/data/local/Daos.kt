package com.example.plantcare.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlantDao {
    @Query("SELECT * FROM plants WHERE user_id = :userId ORDER BY created_at DESC")
    suspend fun getPlants(userId: String): List<PlantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlant(plant: PlantEntity)

    @Delete
    suspend fun deletePlant(plant: PlantEntity)
}

@Dao
interface CareDao {
    @Query("SELECT * FROM care_instructions WHERE plant_id = :plantId LIMIT 1")
    suspend fun getCare(plantId: String): CareInstructionsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCare(care: CareInstructionsEntity)
}


