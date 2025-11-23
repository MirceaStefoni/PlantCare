package com.example.plantcare.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
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
import com.example.plantcare.data.remote.Content
import com.example.plantcare.data.remote.GenerateContentRequest
import com.example.plantcare.data.remote.GeminiService
import com.example.plantcare.data.remote.InlineData
import com.example.plantcare.data.remote.Part
import com.example.plantcare.data.remote.PlantRemoteDataSource
import com.example.plantcare.data.sync.PlantSyncWorker
import com.example.plantcare.domain.model.Plant
import com.example.plantcare.domain.repository.PlantRepository
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

import com.example.plantcare.BuildConfig

import android.util.Log

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class PlantRepositoryImpl(
    private val plantDao: PlantDao,
    private val userDao: UserDao,
    private val remote: PlantRemoteDataSource,
    private val workManager: WorkManager,
    private val geminiService: GeminiService,
    private val client: OkHttpClient,
    @ApplicationContext private val context: Context
) : PlantRepository {

    override fun observePlants(userId: String): Flow<List<Plant>> =
        plantDao.observePlants(userId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getPlants(userId: String): List<Plant> =
        plantDao.getPlants(userId).map { it.toDomain() }

    override suspend fun getPlant(plantId: String): Plant? =
        plantDao.getPlantById(plantId)?.toDomain()

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

    override suspend fun analyzePlant(plantId: String) {
        Log.d("PlantRepo", "Starting analysis for $plantId")
        val entity = plantDao.getPlantById(plantId) ?: run {
            Log.e("PlantRepo", "Plant not found in DB: $plantId")
            return
        }

        // Skip if already analyzed
        if (entity.is_analyzed) {
             Log.d("PlantRepo", "Plant already analyzed (is_analyzed = true). Skipping.")
             return
        }
        
        Log.d("PlantRepo", "Photo URL: ${entity.user_photo_url}")
        val photoUrl = entity.user_photo_url ?: return

        val base64Image = try {
            val bytes = if (photoUrl.startsWith("http")) {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(photoUrl).build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) throw Exception("Failed to download image: ${response.code}")
                    response.body?.bytes()
                }
            } else {
                val inputStream = context.contentResolver.openInputStream(Uri.parse(photoUrl))
                val data = inputStream?.readBytes()
                inputStream?.close()
                data
            }
            
            bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        } catch (e: Exception) {
            Log.e("PlantRepo", "Error reading image: ${e.message}")
            e.printStackTrace()
            null
        } ?: run {
            Log.e("PlantRepo", "Base64 conversion failed")
            return
        }

        val prompt = """
            Analyze this plant image. Return a JSON object with these fields:
            {
                "common_name": "Common name of the plant",
                "scientific_name": "Scientific name",
                "watering_frequency": "Watering frequency (e.g. 'Every 7 days')",
                "light_requirements": "Light requirements (e.g. 'Bright Indirect')",
                "health_status": "Health status (Healthy, Fair, or Poor)"
            }
            Keep answers short.
        """.trimIndent()

        try {
            Log.d("PlantRepo", "Sending request to Gemini...")
            val response = geminiService.generateContent(
                apiKey = BuildConfig.GEMINI_API_KEY,
                req = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = prompt),
                                Part(
                                    inline_data = InlineData(
                                        mime_type = "image/jpeg",
                                        data = base64Image
                                    )
                                )
                            )
                        )
                    )
                )
            )
            
            Log.d("PlantRepo", "Response received. Parsing...")
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            Log.d("PlantRepo", "Raw response text: $text")
            
            if (text != null) {
                // Extract JSON from text (it might be wrapped in ```json ... ```)
                val jsonString = text.substringAfter("```json").substringBefore("```").trim()
                    .ifEmpty { text.trim() } // Fallback if no markdown code blocks
                
                val json = Gson().fromJson(jsonString, JsonObject::class.java)
                
                val updatedEntity = entity.copy(
                    common_name = json.get("common_name")?.asString ?: entity.common_name,
                    scientific_name = json.get("scientific_name")?.asString,
                    watering_frequency = json.get("watering_frequency")?.asString,
                    light_requirements = json.get("light_requirements")?.asString,
                    health_status = json.get("health_status")?.asString,
                    is_analyzed = true,
                    sync_state = SyncState.PENDING
                )
                plantDao.upsertPlant(updatedEntity)
                scheduleSync() // Trigger sync to upload changes to Firestore immediately
                Log.d("PlantRepo", "Database updated with new stats")
            }
        } catch (e: Exception) {
            Log.e("PlantRepo", "API Call failed: ${e.message}")
            e.printStackTrace()
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
    nickname = nickname,
    location = location,
    userPhotoUrl = user_photo_url,
    referencePhotoUrl = reference_photo_url,
    addedMethod = added_method,
    notes = notes,
    acquiredDate = acquired_date,
    wateringFrequency = watering_frequency,
    lightRequirements = light_requirements,
    healthStatus = health_status,
    isAnalyzed = is_analyzed,
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
        nickname = nickname,
        location = location,
        user_photo_url = userPhotoUrl,
        reference_photo_url = referencePhotoUrl,
        added_method = addedMethod,
        notes = notes,
        acquired_date = acquiredDate,
        watering_frequency = wateringFrequency,
        light_requirements = lightRequirements,
        health_status = healthStatus,
        is_analyzed = isAnalyzed,
        created_at = if (createdAt == 0L) now else createdAt,
        updated_at = if (updatedAt == 0L) now else updatedAt,
        sync_state = SyncState.PENDING,
        last_sync_error = null
    )
}
