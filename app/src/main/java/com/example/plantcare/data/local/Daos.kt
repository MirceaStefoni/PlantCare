package com.example.plantcare.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUser(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users WHERE id = :userId")
    suspend fun userCount(userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: String)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteById(userId: String)
}

@Dao
interface PlantDao {
    @Query("SELECT * FROM plants WHERE user_id = :userId ORDER BY created_at DESC")
    fun observePlants(userId: String): Flow<List<PlantEntity>>

    @Query("SELECT * FROM plants WHERE user_id = :userId ORDER BY created_at DESC")
    suspend fun getPlants(userId: String): List<PlantEntity>

    @Query("SELECT * FROM plants WHERE id = :plantId")
    suspend fun getPlantById(plantId: String): PlantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlant(plant: PlantEntity)

    @Query("DELETE FROM plants WHERE id = :plantId")
    suspend fun deleteById(plantId: String)

    @Query("SELECT * FROM plants WHERE sync_state = :state")
    suspend fun getPlantsBySyncState(state: SyncState): List<PlantEntity>

    @Query("SELECT * FROM plants WHERE sync_state = 'PENDING'")
    suspend fun getPlantsBySyncState(): List<PlantEntity>

    @Query("UPDATE plants SET sync_state = :state, last_sync_error = :error WHERE id = :plantId")
    suspend fun updateSyncState(plantId: String, state: SyncState, error: String?)
}

@Dao
interface CareDao {
    @Query("SELECT * FROM care_instructions WHERE plant_id = :plantId")
    suspend fun getCare(plantId: String): CareInstructionsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCare(care: CareInstructionsEntity)

    @Query("DELETE FROM care_instructions WHERE plant_id = :plantId")
    suspend fun deleteByPlantId(plantId: String)
}

@Dao
interface HealthAnalysisDao {
    @Query("SELECT * FROM health_analyses WHERE plant_id = :plantId ORDER BY analyzed_at DESC")
    fun observeAnalysisHistory(plantId: String): Flow<List<HealthAnalysisEntity>>

    @Query("SELECT * FROM health_analyses WHERE plant_id = :plantId ORDER BY analyzed_at DESC LIMIT 1")
    suspend fun getLatestAnalysis(plantId: String): HealthAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnalysis(analysis: HealthAnalysisEntity)

    @Query("DELETE FROM health_analyses WHERE plant_id = :plantId")
    suspend fun deleteByPlantId(plantId: String)
}
