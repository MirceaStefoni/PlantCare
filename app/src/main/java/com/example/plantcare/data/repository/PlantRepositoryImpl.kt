package com.example.plantcare.data.repository

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.plantcare.data.local.PlantDao
import com.example.plantcare.data.local.PlantEntity
import com.example.plantcare.data.local.SyncState
import com.example.plantcare.data.local.UserDao
import com.example.plantcare.data.local.UserEntity
import com.example.plantcare.data.remote.PlantRemoteDataSource
import com.example.plantcare.data.sync.PlantSyncWorker
import com.example.plantcare.domain.model.Plant
import com.example.plantcare.domain.repository.PlantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class PlantRepositoryImpl(
    private val plantDao: PlantDao,
    private val userDao: UserDao,
    private val remote: PlantRemoteDataSource,
    private val workManager: WorkManager
) : PlantRepository {

    override fun observePlants(userId: String): Flow<List<Plant>> =
        plantDao.observePlants(userId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getPlants(userId: String): List<Plant> =
        plantDao.getPlants(userId).map { it.toDomain() }

    override suspend fun addOrUpdate(plant: Plant) {
        ensureUser(plant.userId)
        val entity = plant.toEntity()
        plantDao.upsertPlant(entity)
        scheduleSync()
    }

    override suspend fun delete(plantId: String) {
        val entity = plantDao.getPlantById(plantId)
        plantDao.deleteById(plantId)
        runCatching { entity?.let { remote.deletePlant(it.userId, plantId) } }
    }

    override suspend fun syncFromRemote(userId: String) {
        ensureUser(userId)
        val remotePlants = runCatching { remote.fetchPlants(userId) }.getOrElse { emptyList() }
        remotePlants.forEach { entity ->
            plantDao.upsertPlant(entity.copy(sync_state = SyncState.SYNCED, last_sync_error = null))
        }
    }

    // Ensures a user row exists before inserting plants tied to that user
    private suspend fun ensureUser(userId: String) {
        if (userDao.userCount(userId) == 0) {
            val now = System.currentTimeMillis()
            userDao.upsert(
                UserEntity(
                    id = userId,
                    email = "local@plantcare.app",
                    display_name = "You",
                    profile_photo_url = null,
                    created_at = now,
                    updated_at = now,
                    sync_state = SyncState.SYNCED,
                    last_sync_error = null
                )
            )
        }
    }

    private fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<PlantSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(
            PlantSyncWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}

private fun PlantEntity.toDomain(): Plant = Plant(
    id = id,
    userId = userId,
    commonName = common_name,
    scientificName = scientific_name,
    userPhotoUrl = user_photo_url,
    referencePhotoUrl = reference_photo_url,
    addedMethod = added_method,
    createdAt = created_at,
    updatedAt = updated_at
)

private fun Plant.toEntity(): PlantEntity {
    val now = System.currentTimeMillis()
    return PlantEntity(
        id = if (id.isBlank()) UUID.randomUUID().toString() else id,
        userId = userId,
        common_name = commonName,
        scientific_name = scientificName,
        user_photo_url = userPhotoUrl,
        reference_photo_url = referencePhotoUrl,
        added_method = addedMethod,
        created_at = if (createdAt == 0L) now else createdAt,
        updated_at = if (updatedAt == 0L) now else updatedAt,
        sync_state = SyncState.PENDING,
        last_sync_error = null
    )
}

 


