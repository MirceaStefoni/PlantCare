package com.example.plantcare.data.remote

import com.example.plantcare.data.local.PlantEntity
import com.example.plantcare.data.local.SyncState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlantRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
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
                    "userPhotoUrl" to entity.user_photo_url,
                    "referencePhotoUrl" to entity.reference_photo_url,
                    "addedMethod" to entity.added_method,
                    "createdAt" to entity.created_at,
                    "updatedAt" to entity.updated_at
                )
            )
            .await()
    }

    suspend fun deletePlant(userId: String, plantId: String) {
        plantsCollection(userId).document(plantId).delete().await()
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
                user_photo_url = doc.getString("userPhotoUrl") ?: "",
                reference_photo_url = doc.getString("referencePhotoUrl"),
                added_method = doc.getString("addedMethod") ?: "name",
                created_at = doc.getLong("createdAt") ?: now,
                updated_at = doc.getLong("updatedAt") ?: now,
                sync_state = SyncState.SYNCED,
                last_sync_error = null
            )
        }
    }
}

