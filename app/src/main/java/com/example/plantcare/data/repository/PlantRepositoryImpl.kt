package com.example.plantcare.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.example.plantcare.data.local.CareDao
import com.example.plantcare.data.local.CareInstructionsEntity
import com.example.plantcare.data.local.PlantDao
import com.example.plantcare.data.local.PlantEntity
import com.example.plantcare.data.local.LightMeasurementDao
import com.example.plantcare.data.local.LightMeasurementEntity
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
import com.example.plantcare.domain.model.Plant
import com.example.plantcare.domain.model.LightEnvironment
import com.example.plantcare.domain.model.LightMeasurement
import com.example.plantcare.domain.model.inferLightEnvironment
import com.example.plantcare.domain.repository.PlantRepository
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

import com.example.plantcare.BuildConfig

import android.util.Log

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import retrofit2.HttpException
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.roundToInt

class PlantRepositoryImpl(
    private val plantDao: PlantDao,
    private val userDao: UserDao,
    private val careDao: CareDao,
    private val lightMeasurementDao: LightMeasurementDao,
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
        careDao.deleteByPlantId(plantId)
        lightMeasurementDao.deleteByPlantId(plantId)
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

        val remoteMeasurements = runCatching { remote.fetchLightMeasurements(userId) }.getOrElse { emptyList() }
        remoteMeasurements.forEach { measurement ->
            lightMeasurementDao.upsertMeasurement(measurement.copy(sync_state = SyncState.SYNCED, last_sync_error = null))
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

    override fun observeLightMeasurements(plantId: String): Flow<List<LightMeasurement>> =
        lightMeasurementDao.observeMeasurements(plantId).map { list -> list.map { it.toDomain() } }

    override suspend fun getLightMeasurements(plantId: String): List<LightMeasurement> =
        lightMeasurementDao.getMeasurements(plantId).map { it.toDomain() }

    override suspend fun evaluateLightConditions(
        plantId: String,
        luxValue: Double,
        timeOfDay: String,
        measurementTimestamp: Long
    ): LightMeasurement? {
        if (luxValue.isNaN() || luxValue.isInfinite()) return null
        val plant = plantDao.getPlantById(plantId) ?: return null
        val prompt = buildLightMonitorPrompt(plant, luxValue, timeOfDay, measurementTimestamp)

        return runCatching {
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
            val jsonString = text.extractJsonPayload().trim().ifEmpty { error("Empty Gemini payload") }
            val json = Gson().fromJson(jsonString, JsonObject::class.java)
            val level = json.stringOrNull("assessment_level") ?: "unknown"
            val idealMin = json.doubleOrNull("ideal_min_lux")
            val idealMax = json.doubleOrNull("ideal_max_lux")
            val entity = LightMeasurementEntity(
                id = UUID.randomUUID().toString(),
                plantId = plantId,
                lux_value = luxValue,
                assessment_label = json.stringOrNull("assessment_label") ?: resolveAssessmentLabel(level),
                assessment_level = level,
                ideal_min_lux = idealMin,
                ideal_max_lux = idealMax,
                ideal_description = json.stringOrNull("ideal_description"),
                adequacy_percent = calculateAdequacyPercent(luxValue, idealMin, idealMax)
                    ?: json.intOrNull("adequacy_percent"),
                recommendations = json.recommendationsAsBullets(),
                time_of_day = json.stringOrNull("time_of_day") ?: timeOfDay,
                measured_at = measurementTimestamp,
                sync_state = SyncState.PENDING,
                last_sync_error = null
            )
            lightMeasurementDao.upsertMeasurement(entity)
            runCatching { remote.upsertLightMeasurement(plant.userId, entity) }
            entity.toDomain()
        }.onFailure { Log.e("PlantRepo", "Failed to evaluate light for $plantId", it) }.getOrNull()
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
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
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

private fun LightMeasurementEntity.toDomain(): LightMeasurement = LightMeasurement(
    id = id,
    plantId = plantId,
    luxValue = lux_value,
    assessmentLabel = assessment_label,
    assessmentLevel = assessment_level,
    idealMinLux = ideal_min_lux,
    idealMaxLux = ideal_max_lux,
    idealDescription = ideal_description,
    adequacyPercent = adequacy_percent,
    recommendations = recommendations,
    timeOfDay = time_of_day,
    measuredAt = measured_at
)

private fun LightMeasurement.toEntity(): LightMeasurementEntity = LightMeasurementEntity(
    id = id,
    plantId = plantId,
    lux_value = luxValue,
    assessment_label = assessmentLabel,
    assessment_level = assessmentLevel,
    ideal_min_lux = idealMinLux,
    ideal_max_lux = idealMaxLux,
    ideal_description = idealDescription,
    adequacy_percent = adequacyPercent,
    recommendations = recommendations,
    time_of_day = timeOfDay,
    measured_at = measuredAt,
    sync_state = SyncState.PENDING,
    last_sync_error = null
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

private fun buildLightMonitorPrompt(
    plant: PlantEntity,
    luxValue: Double,
    timeOfDay: String,
    measuredAt: Long
): String {
    val luxDisplay = String.format(Locale.US, "%,.0f", luxValue.coerceAtLeast(0.0))
    val timestamp = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(measuredAt))
    val environment = inferLightEnvironment(plant.location, plant.light_requirements)
    val environmentLine = when (environment) {
        LightEnvironment.INDOOR -> "Detected environment: Indoor setup based on the plant photo/location history."
        LightEnvironment.OUTDOOR -> "Detected environment: Outdoor growing conditions (balcony / patio / garden)."
        LightEnvironment.UNKNOWN -> "Detected environment: Not explicitly known; default to indoor-safe assumptions."
    }
    val environmentGuidance = when (environment) {
        LightEnvironment.INDOOR -> """
            - Compare readings against indoor light availability only. Ignore outdoor full-sun lux numbers.
            - Recommend indoor-friendly adjustments (better window exposure, sheer curtains, supplemental grow light, reflective surfaces).
        """.trimIndent()
        LightEnvironment.OUTDOOR -> """
            - Outdoor readings are acceptable; you may reference direct sunlight ranges when relevant.
        """.trimIndent()
        LightEnvironment.UNKNOWN -> """
            - When in doubt, favor indoor-safe adjustments unless the lux reading clearly indicates outdoor full sun.
        """.trimIndent()
    }
    return """
        You are an indoor horticulture expert evaluating ambient light for ${plant.common_name} (${plant.scientific_name ?: "unknown scientific name"}).
        Current measurement: $luxDisplay lux.
        Measurement timestamp (local device time): $timestamp.
        Time-of-day context: $timeOfDay.
        $environmentLine
        Plant location field (if provided by the user): ${plant.location ?: "not specified"}.
        Return STRICT JSON with these keys and types:
        {
            "assessment_label": "Human readable summary such as Adequate Light",
            "assessment_level": "too_low | adequate | too_high",
            "ideal_min_lux": 0,
            "ideal_max_lux": 0,
            "ideal_description": "≤12 words describing the target lighting (e.g., Bright Indirect Light)",
            "adequacy_percent": 0,
            "recommendations": [
                "Short actionable step 1",
                "Short actionable step 2",
                "Optional step 3"
            ],
            "time_of_day": "Normalize to Morning / Afternoon / Evening / Night"
        }
        Guidance:
        - Base lux range on the plant's natural habitat and needs.
        - Recommendations must be pragmatic (move closer to window, add sheer curtain, rotate weekly, consider grow light, etc.).
        - Use the timestamp + time-of-day context to decide if darkness is expected (e.g., night cycle) before flagging issues.
        - Provide 2-3 recommendation strings; keep each under 18 words.
        $environmentGuidance
        - JSON only. No markdown, explanations, or extra keys.
    """.trimIndent()
}

private fun resolveAssessmentLabel(level: String?): String =
    when (level?.lowercase()) {
        "too_low" -> "Too Low"
        "too_high" -> "Too High"
        "adequate" -> "Adequate Light"
        else -> "Light Assessment"
    }

private fun calculateAdequacyPercent(
    measuredLux: Double,
    idealMin: Double?,
    idealMax: Double?
): Int? {
    val normalizedMin = idealMin?.takeIf { it > 0 }
    val normalizedMax = idealMax?.takeIf { it > 0 }

    val (minLux, maxLux) = when {
        normalizedMin != null && normalizedMax != null -> {
            if (normalizedMin <= normalizedMax) normalizedMin to normalizedMax else normalizedMax to normalizedMin
        }
        normalizedMin != null -> normalizedMin to (normalizedMin * 1.4)
        normalizedMax != null -> (normalizedMax * 0.6) to normalizedMax
        else -> return null
    }

    if (minLux <= 0 || maxLux <= 0) return null

    return when {
        measuredLux < minLux -> ((measuredLux / minLux) * 100).roundToInt().coerceIn(0, 100)
        measuredLux > maxLux -> ((maxLux / measuredLux) * 100).roundToInt().coerceIn(0, 100)
        else -> 100
    }
}

private suspend fun <T> retryOnTimeout(
    attempts: Int = 3,
    block: suspend () -> T
): T {
    var remaining = attempts
    var lastError: Exception? = null
    var attemptNumber = 0
    while (remaining > 0) {
        try {
            return withContext(Dispatchers.IO) {
                withTimeout(30_000L) { block() }
            }
        } catch (e: SocketTimeoutException) {
            lastError = e
            remaining--
            attemptNumber++
            if (remaining == 0) throw e
            Log.w("PlantRepo", "SocketTimeout, retrying... (attempt $attemptNumber)")
        } catch (e: TimeoutCancellationException) {
            lastError = SocketTimeoutException("timeout")
            remaining--
            attemptNumber++
            if (remaining == 0) throw SocketTimeoutException("timeout")
            Log.w("PlantRepo", "Coroutine timeout, retrying... (attempt $attemptNumber)")
        } catch (e: HttpException) {
            // Handle 503 Service Unavailable with exponential backoff
            if (e.code() == 503) {
                lastError = e
                remaining--
                attemptNumber++
                if (remaining == 0) throw e
                val delayMs = (1000L * (1 shl (attemptNumber - 1))).coerceAtMost(8000L) // 1s, 2s, 4s, max 8s
                Log.w("PlantRepo", "HTTP 503 Service Unavailable, retrying in ${delayMs}ms... (attempt $attemptNumber)")
                delay(delayMs)
            } else {
                throw e
            }
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

private fun JsonObject.doubleOrNull(key: String): Double? =
    takeIf { has(key) }?.get(key)?.let { element ->
        if (!element.isJsonPrimitive) return null
        runCatching { element.asDouble }.getOrElse {
            runCatching { element.asLong.toDouble() }.getOrNull()
        }
    }

private fun JsonObject.intOrNull(key: String): Int? =
    takeIf { has(key) }?.get(key)?.let { element ->
        if (!element.isJsonPrimitive) return null
        runCatching { element.asInt }.getOrElse {
            runCatching { element.asDouble.toInt() }.getOrNull()
        }
    }

private fun JsonObject.recommendationsAsBullets(key: String = "recommendations"): String? {
    if (!has(key)) return null
    val element = get(key)
    val joined = when {
        element.isJsonArray -> element.asJsonArray
            .mapNotNull { item -> if (item.isJsonPrimitive) item.asString else null }
            .joinToString("\n")
        element.isJsonPrimitive -> element.asString
        else -> null
    } ?: return null
    return joined.sanitizeCareText().takeIf { it.isNotBlank() }
}

private fun String.indexOfNextNonWhitespace(start: Int): Int {
    var i = start
    while (i < length) {
        val c = this[i]
        if (!c.isWhitespace()) return i
        i++
    }
    return -1
}
