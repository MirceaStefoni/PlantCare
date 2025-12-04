package com.example.plantcare.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users WHERE id = :userId")
    suspend fun userCount(userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface PlantDao {
    @Query("SELECT * FROM plants WHERE user_id = :userId ORDER BY created_at DESC")
    suspend fun getPlants(userId: String): List<PlantEntity>

    @Query("SELECT * FROM plants WHERE id = :plantId LIMIT 1")
    suspend fun getPlantById(plantId: String): PlantEntity?

    @Query("SELECT * FROM plants WHERE user_id = :userId ORDER BY created_at DESC")
    fun observePlants(userId: String): Flow<List<PlantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlant(plant: PlantEntity)

    @Delete
    suspend fun deletePlant(plant: PlantEntity)

    @Query("DELETE FROM plants WHERE id = :plantId")
    suspend fun deleteById(plantId: String)

    @Query("SELECT * FROM plants WHERE sync_state != :state")
    suspend fun getPlantsBySyncState(state: SyncState = SyncState.SYNCED): List<PlantEntity>

    @Query("UPDATE plants SET sync_state = :state, last_sync_error = :error WHERE id = :plantId")
    suspend fun updateSyncState(plantId: String, state: SyncState, error: String?)
}

@Dao
interface CareDao {
    @Query("SELECT * FROM care_instructions WHERE plant_id = :plantId LIMIT 1")
    suspend fun getCare(plantId: String): CareInstructionsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCare(care: CareInstructionsEntity)

    @Query("DELETE FROM care_instructions WHERE plant_id = :plantId")
    suspend fun deleteByPlantId(plantId: String)
}

@Dao
interface LightMeasurementDao {
    @Query("SELECT * FROM light_measurements WHERE plant_id = :plantId ORDER BY measured_at DESC")
    fun observeMeasurements(plantId: String): Flow<List<LightMeasurementEntity>>

    @Query("SELECT * FROM light_measurements WHERE plant_id = :plantId ORDER BY measured_at DESC")
    suspend fun getMeasurements(plantId: String): List<LightMeasurementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeasurement(measurement: LightMeasurementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeasurements(measurements: List<LightMeasurementEntity>)

    @Query("DELETE FROM light_measurements WHERE plant_id = :plantId")
    suspend fun deleteByPlantId(plantId: String)
}


