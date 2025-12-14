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
import com.example.plantcare.data.local.OutdoorCheckDao
import com.example.plantcare.data.local.OutdoorCheckEntity
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
import com.example.plantcare.data.remote.openweather.OpenWeatherGeoService
import com.example.plantcare.data.remote.openweather.OpenWeatherService
import com.example.plantcare.data.sync.PlantSyncWorker
import com.example.plantcare.domain.model.CareGuideFields
import com.example.plantcare.domain.model.CareInstructions
import com.example.plantcare.domain.model.HealthAnalysis
import com.example.plantcare.domain.model.HealthIssue
import com.example.plantcare.domain.model.HealthRecommendation
import com.example.plantcare.domain.model.HealthRecommendationsResult
import com.example.plantcare.domain.model.HealthScoreResult
import com.example.plantcare.domain.model.OutdoorCheck
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.IllegalStateException

class PlantRepositoryImpl(
    private val plantDao: PlantDao,
    private val userDao: UserDao,
    private val careDao: CareDao,
    private val outdoorCheckDao: OutdoorCheckDao,
    private val remote: PlantRemoteDataSource,
    private val workManager: WorkManager,
    private val geminiService: GeminiService,
    private val openWeatherGeoService: OpenWeatherGeoService,
    private val openWeatherService: OpenWeatherService,
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
        outdoorCheckDao.deleteByPlantId(plantId)
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

        val remoteOutdoor = runCatching { remote.fetchOutdoorChecks(userId) }.getOrElse { emptyList() }
        remoteOutdoor.forEach { check ->
            outdoorCheckDao.upsertCheck(check.copy(sync_state = SyncState.SYNCED, last_sync_error = null))
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
            Analyze "$plantName" health. Image 1: full plant. Image 2: PROBLEM AREA user is worried about.
            
            Be STRICT - user shows problem area because something IS wrong. Check for: discoloration, wilting, pests, fungus, nutrient issues, water stress.
            
            Score: 80-100=healthy, 60-79=mild issues, 40-59=moderate, 20-39=severe, 0-19=critical.
            
            Return JSON only: {"health_score": 0-100, "health_status": "Healthy/Fair/Poor", "status_description": "one sentence"}
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
            Identify issues in "$plantName". Image 1: full plant. Image 2: PROBLEM AREA.
            
            User IS concerned - find what's wrong. Check: leaf damage, pests, fungus, nutrient deficiency, water stress.
            
            Severity: Low=cosmetic, Medium=needs attention, High=serious threat.
            
            Return JSON only: {"issues": [{"name": "issue name", "severity": "Low/Medium/High", "description": "brief cause and what you see"}]}
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
            Provide treatment for "$plantName". Image 1: full plant. Image 2: PROBLEM AREA.
            
            Give 2-3 actionable recommendations. Types: Treatment, Watering, Light, Fertilizer, Pest Control.
            Also give 2 prevention tips.
            
            Return JSON only: {"recommendations": [{"type": "category", "title": "action", "description": "brief how-to"}], "prevention_tips": ["tip1", "tip2"]}
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

    override fun observeOutdoorChecks(plantId: String) =
        outdoorCheckDao.observeChecks(plantId).map { list -> list.map { it.toDomain() } }

    override suspend fun getOutdoorChecks(plantId: String): List<OutdoorCheck> =
        outdoorCheckDao.getChecks(plantId).map { it.toDomain() }

    override suspend fun runOutdoorEnvironmentCheckFromCity(
        plantId: String,
        cityQuery: String
    ): OutdoorCheck {
        val plant = plantDao.getPlantById(plantId) ?: throw IllegalStateException("Plant not found")
        val geo = openWeatherGeoService.directGeocode(
            query = cityQuery.trim(),
            limit = 1,
            apiKey = BuildConfig.OPENWEATHER_API_KEY
        ).firstOrNull() ?: throw IllegalStateException("City not found")
        return runOutdoorEnvironmentCheckFromCoordinates(
            plantId = plantId,
            latitude = geo.lat,
            longitude = geo.lon,
            cityName = geo.name ?: cityQuery
        )
    }

    override suspend fun runOutdoorEnvironmentCheckFromCoordinates(
        plantId: String,
        latitude: Double,
        longitude: Double,
        cityName: String?
    ): OutdoorCheck {
        val plant = plantDao.getPlantById(plantId) ?: throw IllegalStateException("Plant not found")

        // Best-effort reverse geocode so history shows a city even for GPS checks.
        val resolvedCityName = cityName ?: runCatching {
            openWeatherGeoService.reverseGeocode(
                lat = latitude,
                lon = longitude,
                limit = 1,
                apiKey = BuildConfig.OPENWEATHER_API_KEY
            ).firstOrNull()?.name
        }.getOrNull()

        val weather = retryOnTimeout {
            openWeatherService.currentWeather(
                lat = latitude,
                lon = longitude,
                apiKey = BuildConfig.OPENWEATHER_API_KEY
            )
        }
        val forecast = runCatching {
            retryOnTimeout {
                openWeatherService.forecast5d3h(
                    lat = latitude,
                    lon = longitude,
                    apiKey = BuildConfig.OPENWEATHER_API_KEY
                )
            }
        }.getOrNull()

        val tempC = weather.main?.temp ?: 0.0
        val feelsC = weather.main?.feelsLike ?: tempC
        val humidity = weather.main?.humidity ?: 0
        val windKmh = ((weather.wind?.speed ?: 0.0) * 3.6)
        val description = weather.weather?.firstOrNull()?.description
        val minNext24 = forecast?.list
            ?.mapNotNull { it.main?.tempMin }
            ?.minOrNull()

        // OpenWeather "weather" endpoints don't include UV index. Keep optional.
        val uvIndex: Double? = null

        val prompt = buildOutdoorPrompt(
            plantName = plant.common_name,
            cityName = resolvedCityName ?: weather.name,
            tempC = tempC,
            feelsLikeC = feelsC,
            humidityPercent = humidity,
            windKmh = windKmh,
            uvIndex = uvIndex,
            minTempNext24hC = minNext24,
            weatherDescription = description
        )

        val gemini = retryOnTimeout {
            geminiService.generateContent(
                apiKey = BuildConfig.GEMINI_API_KEY,
                req = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt))))
                )
            )
        }

        val text = gemini.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Empty response from Gemini")
        val jsonString = text.extractJsonPayload()

        val parsed = parseOutdoorJson(jsonString)

        val now = System.currentTimeMillis()
        val entity = OutdoorCheckEntity(
            id = UUID.randomUUID().toString(),
            plantId = plantId,
            city_name = resolvedCityName ?: weather.name,
            latitude = latitude,
            longitude = longitude,
            temp_c = tempC,
            feels_like_c = feelsC,
            humidity_percent = humidity,
            wind_kmh = windKmh,
            uv_index = uvIndex,
            min_temp_next_24h_c = minNext24,
            weather_description = description,
            verdict = parsed.verdict,
            verdict_color = parsed.verdictColor,
            analysis = parsed.analysis,
            warnings_json = Gson().toJson(parsed.warnings),
            recommendations_json = Gson().toJson(parsed.recommendations),
            checked_at = now,
            sync_state = SyncState.PENDING,
            last_sync_error = null
        )

        outdoorCheckDao.upsertCheck(entity)

        // Best-effort immediate Firestore sync; Worker will retry on failures.
        runCatching {
            remote.upsertOutdoorCheck(plant.userId, entity)
            outdoorCheckDao.upsertCheck(entity.copy(sync_state = SyncState.SYNCED, last_sync_error = null))
        }.onFailure {
            scheduleSync()
        }

        return entity.toDomain()
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
                // Use .use{} to ensure stream is closed even if readBytes() throws
                context.contentResolver.openInputStream(Uri.parse(imageUrl))?.use { inputStream ->
                    inputStream.readBytes()
                }
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

private data class OutdoorParsed(
    val verdict: String,
    val verdictColor: String,
    val analysis: String,
    val warnings: List<String>,
    val recommendations: List<String>
)

private fun buildOutdoorPrompt(
    plantName: String,
    cityName: String?,
    tempC: Double,
    feelsLikeC: Double,
    humidityPercent: Int,
    windKmh: Double,
    uvIndex: Double?,
    minTempNext24hC: Double?,
    weatherDescription: String?
): String {
    val city = cityName ?: "Unknown location"
    val uv = uvIndex?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "unknown"
    val min24 = minTempNext24hC?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "unknown"
    val desc = weatherDescription ?: "unknown"
    return """
        Act as an expert botanist. Evaluate if the current outdoor weather conditions in $city are suitable for the plant: $plantName.

        Weather Data provided:
        - Current Temp: ${String.format(java.util.Locale.US, "%.1f", tempC)}°C (Feels like: ${String.format(java.util.Locale.US, "%.1f", feelsLikeC)}°C)
        - Humidity: $humidityPercent%
        - Wind Speed: ${String.format(java.util.Locale.US, "%.1f", windKmh)} km/h
        - UV Index: $uv
        - Forecast Low next 24h: $min24°C
        - Weather Description: $desc

        Output rules:
        - Be concise and user-facing.
        - analysis MUST be 1-2 sentences, max 240 characters.
        - warnings: 0-3 items, each <= 16 words.
        - recommendations: 2-3 items, each <= 18 words.

        Return STRICT JSON only (no markdown, no extra keys, no code fences):
        {
          "verdict": "Ideal|Acceptable|Risky|Dangerous",
          "verdict_color": "green|yellow|red",
          "analysis": "string",
          "warnings": ["string"],
          "recommendations": ["string"]
        }
    """.trimIndent()
}

private fun parseOutdoorJson(raw: String): OutdoorParsed {
    val cleaned = raw.trim()
    return runCatching {
        val json = Gson().fromJson(cleaned, JsonObject::class.java)
        val verdict = json.get("verdict")?.asString ?: "Acceptable"
        val color = json.get("verdict_color")?.asString ?: "yellow"
        val analysis = json.get("analysis")?.asString.orEmpty().shortenAnalysis()
        val warnings = json.getAsJsonArray("warnings")?.mapNotNull { it.asString } ?: emptyList()
        val recs = json.getAsJsonArray("recommendations")?.mapNotNull { it.asString } ?: emptyList()
        OutdoorParsed(verdict, color, analysis, warnings, recs)
    }.getOrElse {
        // Fallback: attempt to salvage minimal info if Gemini produced malformed JSON.
        val fallbackVerdict = Regex("\"verdict\"\\s*:\\s*\"([^\"]+)\"").find(cleaned)?.groupValues?.getOrNull(1) ?: "Acceptable"
        val fallbackColor = Regex("\"verdict_color\"\\s*:\\s*\"([^\"]+)\"").find(cleaned)?.groupValues?.getOrNull(1) ?: "yellow"
        val fallbackAnalysis = Regex("\"analysis\"\\s*:\\s*\"([\\s\\S]*?)\"\\s*(,|})")
            .find(cleaned)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\n", "\n")
            .orEmpty()
            .shortenAnalysis()
        OutdoorParsed(
            verdict = fallbackVerdict,
            verdictColor = fallbackColor,
            analysis = fallbackAnalysis,
            warnings = emptyList(),
            recommendations = emptyList()
        )
    }
}

private fun String.shortenAnalysis(maxChars: Int = 240): String {
    val oneLine = this
        .replace("\r", "\n")
        .replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (oneLine.length <= maxChars) return oneLine
    val slice = oneLine.take(maxChars)
    val lastPeriod = slice.lastIndexOf('.')
    return if (lastPeriod >= 40) slice.take(lastPeriod + 1).trim() else (slice.trimEnd() + "…")
}

private fun OutdoorCheckEntity.toDomain(): OutdoorCheck {
    val warnings = runCatching { Gson().fromJson(warnings_json, Array<String>::class.java)?.toList() }.getOrNull() ?: emptyList()
    val recs = runCatching { Gson().fromJson(recommendations_json, Array<String>::class.java)?.toList() }.getOrNull() ?: emptyList()
    return OutdoorCheck(
        id = id,
        plantId = plantId,
        cityName = city_name,
        latitude = latitude,
        longitude = longitude,
        tempC = temp_c,
        feelsLikeC = feels_like_c,
        humidityPercent = humidity_percent,
        windKmh = wind_kmh,
        uvIndex = uv_index,
        minTempNext24hC = min_temp_next_24h_c,
        weatherDescription = weather_description,
        verdict = verdict,
        verdictColor = verdict_color,
        analysis = analysis,
        warnings = warnings,
        recommendations = recs,
        checkedAt = checked_at
    )
}

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
