package com.example.plantcare.data.remote

import com.example.plantcare.data.local.PlantEntity
import com.example.plantcare.data.local.SyncState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlantRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    private fun plantsCollection(userId: String) =
        firestore.collection("users").document(userId).collection("plants")

    suspend fun upsertPlant(entity: PlantEntity) {
        plantsCollection(entity.userId)
            .document(entity.id)
            .set(
                mapOf(
                    "commonName" to entity.common_name,
                    "scientificName" to entity.scientific_name,
                    "nickname" to entity.nickname,
                    "location" to entity.location,
                    "userPhotoUrl" to entity.user_photo_url,
                    "referencePhotoUrl" to entity.reference_photo_url,
                    "addedMethod" to entity.added_method,
                    "notes" to entity.notes,
                    "acquiredDate" to entity.acquired_date,
                    "wateringFrequency" to entity.watering_frequency,
                    "lightRequirements" to entity.light_requirements,
                    "healthStatus" to entity.health_status,
                    "isAnalyzed" to entity.is_analyzed,
                    "createdAt" to entity.created_at,
                    "updatedAt" to entity.updated_at
                )
            )
            .await()
    }

    suspend fun deletePlant(userId: String, plantId: String) {
        // Remove Firestore document first to keep data consistent even if Storage cleanup fails
        try {
            plantsCollection(userId).document(plantId).delete().await()
        } catch (e: Exception) {
            // Log error but proceed to try cleaning up storage
            e.printStackTrace()
        }

        // Best-effort cleanup of the associated Storage folder
        val plantFolder = storage.reference.child("users/$userId/plants/$plantId")
        runCatching { plantFolder.child("user.jpg").delete().await() }
        runCatching { plantFolder.child("reference.jpg").delete().await() }
    }

    suspend fun deleteAllPlants(userId: String) {
        val snapshot = plantsCollection(userId).get().await()
        snapshot.documents.forEach { doc ->
            runCatching { doc.reference.delete().await() }
        }
        val rootFolder = storage.reference.child("users/$userId/plants")
        deleteStorageFolder(rootFolder)
    }

    private suspend fun deleteStorageFolder(folder: StorageReference) {
        val listResult = runCatching { folder.listAll().await() }.getOrElse { return }
        listResult.items.forEach { item -> runCatching { item.delete().await() } }
        listResult.prefixes.forEach { prefix -> deleteStorageFolder(prefix) }
    }

    suspend fun fetchPlants(userId: String): List<PlantEntity> {
        val snapshot = plantsCollection(userId).get().await()
        val now = System.currentTimeMillis()
        return snapshot.documents.mapNotNull { doc ->
            val commonName = doc.getString("commonName") ?: return@mapNotNull null
            PlantEntity(
                id = doc.id,
                userId = userId,
                common_name = commonName,
                scientific_name = doc.getString("scientificName"),
                nickname = doc.getString("nickname"),
                location = doc.getString("location"),
                user_photo_url = doc.getString("userPhotoUrl") ?: "",
                reference_photo_url = doc.getString("referencePhotoUrl"),
                added_method = doc.getString("addedMethod") ?: "name",
                notes = doc.getString("notes"),
                acquired_date = doc.getLong("acquiredDate"),
                watering_frequency = doc.getString("wateringFrequency"),
                light_requirements = doc.getString("lightRequirements"),
                health_status = doc.getString("healthStatus"),
                is_analyzed = doc.getBoolean("isAnalyzed") ?: false,
                created_at = doc.getLong("createdAt") ?: now,
                updated_at = doc.getLong("updatedAt") ?: now,
                sync_state = SyncState.SYNCED,
                last_sync_error = null
            )
        }
    }
}

