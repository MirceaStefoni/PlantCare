package com.example.plantcare.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.plantcare.data.local.CareDao
import com.example.plantcare.data.local.CareInstructionsEntity
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
import com.example.plantcare.domain.model.CareGuideFields
import com.example.plantcare.domain.model.CareInstructions
import com.example.plantcare.domain.model.HealthAnalysis
import com.example.plantcare.domain.model.HealthIssue
import com.example.plantcare.domain.model.HealthRecommendation
import com.example.plantcare.domain.model.HealthRecommendationsResult
import com.example.plantcare.domain.model.HealthScoreResult
import com.example.plantcare.domain.model.Plant
import com.example.plantcare.domain.repository.PlantRepository
import com.google.gson.JsonArray
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.SocketTimeoutException
import java.util.UUID

import com.example.plantcare.BuildConfig

import android.util.Log

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import okhttp3.OkHttpClient
import okhttp3.Request

class PlantRepositoryImpl(
    private val plantDao: PlantDao,
    private val userDao: UserDao,
    private val careDao: CareDao,
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

    override suspend fun addOrUpdateAndSync(plant: Plant) {
        ensureUser(plant.userId)
        val entity = plant.toEntity()
        
        // Save to local DB first
        plantDao.upsertPlant(entity)
        
        // Immediately sync to Firestore (don't wait for WorkManager)
        try {
            remote.upsertPlant(entity)
            // Mark as synced in local DB
            plantDao.upsertPlant(entity.copy(sync_state = SyncState.SYNCED, last_sync_error = null))
            Log.d("PlantRepo", "Plant saved and synced to Firestore: ${plant.id}")
        } catch (e: Exception) {
            Log.e("PlantRepo", "Failed to sync to Firestore, will retry via WorkManager", e)
            // Keep as PENDING, WorkManager will retry
            scheduleSync()
        }
    }

    override suspend fun delete(plantId: String) {
        val entity = plantDao.getPlantById(plantId)
        plantDao.deleteById(plantId)
        careDao.deleteByPlantId(plantId)
        runCatching { entity?.let { remote.deletePlant(it.userId, plantId) } }
    }

    override suspend fun syncFromRemote(userId: String) {
        ensureUser(userId)
        val remotePlants = runCatching { remote.fetchPlants(userId) }.getOrElse { emptyList() }
        remotePlants.forEach { entity ->
            plantDao.upsertPlant(entity.copy(sync_state = SyncState.SYNCED, last_sync_error = null))
        }

        val remoteCare = runCatching { remote.fetchCareInstructions(userId) }.getOrElse { emptyList() }
        remoteCare.forEach { care ->
            careDao.upsertCare(care.copy(sync_state = SyncState.SYNCED, last_sync_error = null))
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
            val response = retryOnTimeout(attempts = 2) {
                geminiService.generateContent(
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
            }
            
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

    override suspend fun getCareGuide(plantId: String, forceRefresh: Boolean): CareInstructions? {
        if (!forceRefresh) {
            careDao.getCare(plantId)?.let { return it.toDomain() }
        }

        val plant = plantDao.getPlantById(plantId) ?: return null

        val remoteExisting = runCatching { remote.fetchCareInstruction(plant.userId, plantId) }.getOrNull()
        if (remoteExisting != null) {
            careDao.upsertCare(remoteExisting.copy(sync_state = SyncState.SYNCED, last_sync_error = null))
            return remoteExisting.toDomain()
        }

        if (forceRefresh) {
            careDao.deleteByPlantId(plantId)
        }

        return null
    }

    override suspend fun generateCareGuideChunk(
        plantId: String,
        keys: List<String>,
        focus: String
    ): Map<String, String?> {
        require(keys.isNotEmpty()) { "Care guide chunk requires at least one key" }
        val plant = plantDao.getPlantById(plantId) ?: error("Plant not found for care guide: $plantId")
        val prompt = buildCareGuidePrompt(plant, keys, focus)

        return try {
            val response = retryOnTimeout {
                geminiService.generateContent(
                    apiKey = BuildConfig.GEMINI_API_KEY,
                    req = GenerateContentRequest(
                        contents = listOf(Content(parts = listOf(Part(text = prompt))))
                    )
                )
            }
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: error("Gemini response missing content")
            val jsonString = text.extractJsonPayload().normalizeJsonPayload(keys)
            val json = Gson().fromJson(jsonString, JsonObject::class.java)
            keys.associateWith { key -> json.cleaned(key) }
        } catch (e: Exception) {
            Log.e("PlantRepo", "Failed to fetch care chunk for $keys", e)
            throw e
        }
    }

    override suspend fun saveCareGuide(
        plantId: String,
        values: Map<String, String?>
    ): CareInstructions? {
        val plant = plantDao.getPlantById(plantId) ?: return null
        val now = System.currentTimeMillis()
        val entity = CareInstructionsEntity(
            id = plantId,
            plantId = plantId,
            watering_info = values[CareGuideFields.WATERING],
            light_info = values[CareGuideFields.LIGHT],
            temperature_info = values[CareGuideFields.TEMPERATURE],
            humidity_info = values[CareGuideFields.HUMIDITY],
            soil_info = values[CareGuideFields.SOIL],
            fertilization_info = values[CareGuideFields.FERTILIZATION],
            pruning_info = values[CareGuideFields.PRUNING],
            common_issues = values[CareGuideFields.ISSUES],
            seasonal_tips = values[CareGuideFields.SEASONAL],
            fetched_at = now,
            sync_state = SyncState.SYNCED,
            last_sync_error = null
        )
        careDao.upsertCare(entity)
        runCatching { remote.upsertCareInstructions(plant.userId, entity) }
        return entity.toDomain()
    }

    // Legacy method - kept for reference but no longer used
    private suspend fun analyzeHealthWithPhotos(
        plantId: String,
        plantPhotoUrl: String,
        affectedAreaUri: String,
        plantName: String
    ): HealthAnalysis {
        Log.d("PlantRepo", "Starting health analysis for $plantId")
        
        // Read plant photo
        val plantPhotoBase64 = readImageAsBase64(plantPhotoUrl)
            ?: throw Exception("Failed to read plant photo")
        
        // Read affected area photo
        val affectedPhotoBase64 = readImageAsBase64(affectedAreaUri)
            ?: throw Exception("Failed to read affected area photo")
        
        val prompt = """
            You are a plant health expert. Analyze these two images of a plant called "$plantName".
            
            Image 1: The overall plant photo
            Image 2: A close-up of the affected/problematic area
            
            Please analyze the plant's health and return a JSON object with the following structure:
            {
                "health_score": 0-100 (integer representing overall health, 100 being perfectly healthy),
                "health_status": "Healthy" or "Fair" or "Poor",
                "status_description": "Brief description of the plant's current state",
                "issues": [
                    {
                        "name": "Issue name (e.g., 'Yellow Leaves', 'Root Rot', 'Spider Mites')",
                        "severity": "Low" or "Medium" or "High",
                        "description": "Detailed description of the issue"
                    }
                ],
                "recommendations": [
                    {
                        "type": "Category (e.g., 'Watering', 'Light', 'Fertilizer', 'Treatment')",
                        "title": "Short action title",
                        "description": "Detailed recommendation"
                    }
                ],
                "prevention_tips": [
                    "Tip 1 for preventing future issues",
                    "Tip 2 for maintaining plant health"
                ]
            }
            
            Be thorough but concise. If the plant looks healthy, still provide general care tips.
            Return ONLY the JSON object, no additional text.
        """.trimIndent()

        try {
            Log.d("PlantRepo", "Sending health analysis request to Gemini...")
            val response = retryOnTimeout(attempts = 2) {
                geminiService.generateContent(
                    apiKey = BuildConfig.GEMINI_API_KEY,
                    req = GenerateContentRequest(
                        contents = listOf(
                            Content(
                                parts = listOf(
                                    Part(text = prompt),
                                    Part(
                                        inline_data = InlineData(
                                            mime_type = "image/jpeg",
                                            data = plantPhotoBase64
                                        )
                                    ),
                                    Part(
                                        inline_data = InlineData(
                                            mime_type = "image/jpeg",
                                            data = affectedPhotoBase64
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            }

            Log.d("PlantRepo", "Health analysis response received. Parsing...")
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("Empty response from Gemini")
            Log.d("PlantRepo", "Raw health analysis response: $text")

            // Extract JSON from response
            val jsonString = text.substringAfter("```json").substringBefore("```").trim()
                .ifEmpty { text.trim() }

            val json = Gson().fromJson(jsonString, JsonObject::class.java)

            // Parse issues
            val issues = mutableListOf<HealthIssue>()
            json.getAsJsonArray("issues")?.forEach { element ->
                val issueObj = element.asJsonObject
                issues.add(
                    HealthIssue(
                        name = issueObj.get("name")?.asString ?: "Unknown Issue",
                        severity = issueObj.get("severity")?.asString ?: "Medium",
                        description = issueObj.get("description")?.asString
                    )
                )
            }

            // Parse recommendations
            val recommendations = mutableListOf<HealthRecommendation>()
            json.getAsJsonArray("recommendations")?.forEach { element ->
                val recObj = element.asJsonObject
                recommendations.add(
                    HealthRecommendation(
                        type = recObj.get("type")?.asString ?: "General",
                        title = recObj.get("title")?.asString ?: "Recommendation",
                        description = recObj.get("description")?.asString ?: ""
                    )
                )
            }

            // Parse prevention tips
            val preventionTips = mutableListOf<String>()
            json.getAsJsonArray("prevention_tips")?.forEach { element ->
                preventionTips.add(element.asString)
            }

            return HealthAnalysis(
                id = UUID.randomUUID().toString(),
                plantId = plantId,
                photoUrl = affectedAreaUri,
                healthStatus = json.get("health_status")?.asString ?: "Unknown",
                healthScore = json.get("health_score")?.asInt ?: 50,
                statusDescription = json.get("status_description")?.asString ?: "Analysis complete",
                issues = issues,
                recommendations = recommendations,
                preventionTips = preventionTips,
                analyzedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e("PlantRepo", "Health analysis failed: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    // Chunked Health Analysis - Part 1: Health Score
    override suspend fun analyzeHealthScore(
        plantPhotoUrl: String,
        affectedAreaUri: String,
        plantName: String
    ): HealthScoreResult {
        Log.d("PlantRepo", "Analyzing health score for $plantName")
        
        val plantPhotoBase64 = readImageAsBase64(plantPhotoUrl)
            ?: throw Exception("Failed to read plant photo")
        val affectedPhotoBase64 = readImageAsBase64(affectedAreaUri)
            ?: throw Exception("Failed to read affected area photo")
        
        val prompt = """
            You are a plant health expert. Analyze these two images of a plant called "$plantName".
            Image 1: The overall plant photo. Image 2: A close-up of the affected/problematic area.
            
            Provide ONLY the overall health assessment. Return a JSON object:
            {
                "health_score": 0-100 (integer, 100 = perfectly healthy),
                "health_status": "Healthy" or "Fair" or "Poor",
                "status_description": "One sentence describing the plant's current state"
            }
            Return ONLY the JSON, no additional text.
        """.trimIndent()

        val response = retryOnTimeout(attempts = 2) {
            geminiService.generateContent(
                apiKey = BuildConfig.GEMINI_API_KEY,
                req = GenerateContentRequest(
                    contents = listOf(
                        Content(parts = listOf(
                            Part(text = prompt),
                            Part(inline_data = InlineData(mime_type = "image/jpeg", data = plantPhotoBase64)),
                            Part(inline_data = InlineData(mime_type = "image/jpeg", data = affectedPhotoBase64))
                        ))
                    )
                )
            )
        }

        val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response from Gemini")
        val jsonString = text.substringAfter("```json").substringBefore("```").trim().ifEmpty { text.trim() }
        val json = Gson().fromJson(jsonString, JsonObject::class.java)

        return HealthScoreResult(
            healthScore = json.get("health_score")?.asInt ?: 50,
            healthStatus = json.get("health_status")?.asString ?: "Unknown",
            statusDescription = json.get("status_description")?.asString ?: "Analysis complete"
        )
    }

    // Chunked Health Analysis - Part 2: Issues
    override suspend fun analyzeHealthIssues(
        plantPhotoUrl: String,
        affectedAreaUri: String,
        plantName: String
    ): List<HealthIssue> {
        Log.d("PlantRepo", "Analyzing health issues for $plantName")
        
        val plantPhotoBase64 = readImageAsBase64(plantPhotoUrl)
            ?: throw Exception("Failed to read plant photo")
        val affectedPhotoBase64 = readImageAsBase64(affectedAreaUri)
            ?: throw Exception("Failed to read affected area photo")
        
        val prompt = """
            You are a plant health expert. Analyze these two images of a plant called "$plantName".
            Image 1: The overall plant. Image 2: Close-up of the affected area.
            
            Identify any health issues. Return a JSON object:
            {
                "issues": [
                    {
                        "name": "Issue name (e.g., 'Yellow Leaves', 'Root Rot')",
                        "severity": "Low" or "Medium" or "High",
                        "description": "Detailed description of the issue and what causes it"
                    }
                ]
            }
            If the plant is healthy, return an empty issues array. Return ONLY the JSON.
        """.trimIndent()

        val response = retryOnTimeout(attempts = 2) {
            geminiService.generateContent(
                apiKey = BuildConfig.GEMINI_API_KEY,
                req = GenerateContentRequest(
                    contents = listOf(
                        Content(parts = listOf(
                            Part(text = prompt),
                            Part(inline_data = InlineData(mime_type = "image/jpeg", data = plantPhotoBase64)),
                            Part(inline_data = InlineData(mime_type = "image/jpeg", data = affectedPhotoBase64))
                        ))
                    )
                )
            )
        }

        val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response from Gemini")
        val jsonString = text.substringAfter("```json").substringBefore("```").trim().ifEmpty { text.trim() }
        val json = Gson().fromJson(jsonString, JsonObject::class.java)

        val issues = mutableListOf<HealthIssue>()
        json.getAsJsonArray("issues")?.forEach { element ->
            val obj = element.asJsonObject
            issues.add(HealthIssue(
                name = obj.get("name")?.asString ?: "Unknown Issue",
                severity = obj.get("severity")?.asString ?: "Medium",
                description = obj.get("description")?.asString
            ))
        }
        return issues
    }

    // Chunked Health Analysis - Part 3: Recommendations & Prevention
    override suspend fun analyzeHealthRecommendations(
        plantPhotoUrl: String,
        affectedAreaUri: String,
        plantName: String
    ): HealthRecommendationsResult {
        Log.d("PlantRepo", "Analyzing recommendations for $plantName")
        
        val plantPhotoBase64 = readImageAsBase64(plantPhotoUrl)
            ?: throw Exception("Failed to read plant photo")
        val affectedPhotoBase64 = readImageAsBase64(affectedAreaUri)
            ?: throw Exception("Failed to read affected area photo")
        
        val prompt = """
            You are a plant health expert. Analyze these two images of a plant called "$plantName".
            Image 1: The overall plant. Image 2: Close-up of the affected area.
            
            Provide care recommendations and prevention tips. Return a JSON object:
            {
                "recommendations": [
                    {
                        "type": "Category (Watering, Light, Fertilizer, Treatment, etc.)",
                        "title": "Short action title",
                        "description": "Detailed recommendation on what to do"
                    }
                ],
                "prevention_tips": [
                    "Tip 1 for preventing future issues",
                    "Tip 2 for maintaining plant health"
                ]
            }
            Return ONLY the JSON.
        """.trimIndent()

        val response = retryOnTimeout(attempts = 2) {
            geminiService.generateContent(
                apiKey = BuildConfig.GEMINI_API_KEY,
                req = GenerateContentRequest(
                    contents = listOf(
                        Content(parts = listOf(
                            Part(text = prompt),
                            Part(inline_data = InlineData(mime_type = "image/jpeg", data = plantPhotoBase64)),
                            Part(inline_data = InlineData(mime_type = "image/jpeg", data = affectedPhotoBase64))
                        ))
                    )
                )
            )
        }

        val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response from Gemini")
        val jsonString = text.substringAfter("```json").substringBefore("```").trim().ifEmpty { text.trim() }
        val json = Gson().fromJson(jsonString, JsonObject::class.java)

        val recommendations = mutableListOf<HealthRecommendation>()
        json.getAsJsonArray("recommendations")?.forEach { element ->
            val obj = element.asJsonObject
            recommendations.add(HealthRecommendation(
                type = obj.get("type")?.asString ?: "General",
                title = obj.get("title")?.asString ?: "Recommendation",
                description = obj.get("description")?.asString ?: ""
            ))
        }

        val preventionTips = mutableListOf<String>()
        json.getAsJsonArray("prevention_tips")?.forEach { element ->
            preventionTips.add(element.asString)
        }

        return HealthRecommendationsResult(
            recommendations = recommendations,
            preventionTips = preventionTips
        )
    }

    private suspend fun readImageAsBase64(imageUrl: String): String? {
        return try {
            val bytes = if (imageUrl.startsWith("http")) {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(imageUrl).build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) throw Exception("Failed to download image: ${response.code}")
                    response.body?.bytes()
                }
            } else {
                val inputStream = context.contentResolver.openInputStream(Uri.parse(imageUrl))
                val data = inputStream?.readBytes()
                inputStream?.close()
                data
            }
            bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        } catch (e: Exception) {
            Log.e("PlantRepo", "Error reading image: ${e.message}")
            e.printStackTrace()
            null
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
            ExistingWorkPolicy.APPEND_OR_REPLACE,
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

private fun CareInstructionsEntity.toDomain(): CareInstructions = CareInstructions(
    plantId = plantId,
    wateringInfo = watering_info,
    lightInfo = light_info,
    temperatureInfo = temperature_info,
    humidityInfo = humidity_info,
    soilInfo = soil_info,
    fertilizationInfo = fertilization_info,
    pruningInfo = pruning_info,
    commonIssues = common_issues,
    seasonalTips = seasonal_tips,
    fetchedAt = fetched_at
)

private fun String.extractJsonPayload(): String =
    substringAfter("```json", this)
        .substringBefore("```", this)
        .trim()
        .ifEmpty { this.trim() }

private fun JsonObject.stringOrNull(key: String): String? =
    takeIf { has(key) && !get(key).isJsonNull }?.get(key)?.asString

private fun JsonObject.cleaned(key: String): String? =
    stringOrNull(key)?.sanitizeCareText()?.takeIf { it.isNotBlank() }

private fun String.normalizeJsonPayload(keys: List<String>): String {
    val normalized = StringBuilder("{")
    keys.forEachIndexed { index, key ->
        val value = extractValueForKey(key)
        val sanitized = (value ?: "- No data.").sanitizeCareText().escapeJson()
        normalized.append("\"").append(key).append("\":\"").append(sanitized).append("\"")
        if (index != keys.lastIndex) normalized.append(",")
    }
    normalized.append("}")
    return normalized.toString()
}

private fun String.escapeJson(): String =
    this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\n")

private fun buildCareGuidePrompt(
    plant: PlantEntity,
    keys: List<String>,
    focus: String
): String {
    val schemaBlock = keys.joinToString(separator = ",\n") { "\"$it\": \"...\"" }
    val focusLine = focus.takeIf { it.isNotBlank() }?.let { "\nFocus: $it" } ?: ""
    return """
        You are a strict JSON generator. Return VALID JSON only—no prose.
        Plant: ${plant.common_name} (${plant.scientific_name ?: "unknown scientific name"}).$focusLine
        Schema (keys fixed, values are strings):
        {
            $schemaBlock
        }
        Formatting rules:
        - Provide newline-separated guidance with each line starting with "- ".
        - Plain text only. No Markdown, no extra keys, no code fences.
        - Escape any internal double quotes as \".
        - Include only these keys: ${keys.joinToString()}.
        - Return compact JSON on a single line without trailing commas.
    """.trimIndent()
}

private suspend fun <T> retryOnTimeout(
    attempts: Int = 3,
    block: suspend () -> T
): T {
    var remaining = attempts
    var lastError: Exception? = null
    while (remaining > 0) {
        try {
            return withContext(Dispatchers.IO) {
                withTimeout(30_000L) { block() }
            }
        } catch (e: SocketTimeoutException) {
            lastError = e
            remaining--
            if (remaining == 0) throw e
        } catch (e: TimeoutCancellationException) {
            lastError = SocketTimeoutException("timeout")
            remaining--
            if (remaining == 0) throw SocketTimeoutException("timeout")
        } catch (e: Exception) {
            throw e
        }
    }
    throw lastError ?: RuntimeException("Unknown error")
}

private fun String.extractValueForKey(key: String): String? {
    val pattern = Regex("\"$key\"\\s*:\\s*\"")
    val match = pattern.find(this) ?: return null
    var index = match.range.last + 1
    val builder = StringBuilder()
    var escaped = false
    while (index < length) {
        val c = this[index]
        if (!escaped && c == '\\') {
            escaped = true
        } else {
            if (!escaped && c == '"') break
            builder.append(c)
            escaped = false
        }
        index++
    }
    return builder.toString().replace("\\n", "\n")
}

private fun String.sanitizeCareText(): String =
    trim()
        .split(Regex("(?i)(?:\\n|\\r|n-)"))
        .map { rawLine ->
            val noBold = rawLine.replace("**", "").trim()
            when {
                noBold.isBlank() -> ""
                else -> "- " + noBold.trimStart('-', '•', ' ', '\t')
            }
        }
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .trim()

private fun String.indexOfNextNonWhitespace(start: Int): Int {
    var i = start
    while (i < length) {
        val c = this[i]
        if (!c.isWhitespace()) return i
        i++
    }
    return -1
}
