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
import com.example.plantcare.domain.model.CareInstructions
import com.example.plantcare.domain.model.Plant
import com.example.plantcare.domain.repository.PlantRepository
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

    override suspend fun getCareGuide(plantId: String, forceRefresh: Boolean): CareInstructions? {
        val cached = careDao.getCare(plantId)
        if (cached != null) return cached.toDomain()

        val plant = plantDao.getPlantById(plantId) ?: return null

        val remoteExisting = runCatching { remote.fetchCareInstruction(plant.userId, plantId) }.getOrNull()
        if (remoteExisting != null) {
            careDao.upsertCare(remoteExisting)
            return remoteExisting.toDomain()
        }

        val now = System.currentTimeMillis()

        val prompt = """
            You are a strict JSON generator. Produce COMPLETE and VALID JSON only—no prose.
            Describe care for ${plant.common_name} (${plant.scientific_name ?: "unknown scientific name"}).
            Schema (keys fixed, values are strings):
            {
              "watering_info": "...",
              "light_info": "...",
              "temperature_info": "...",
              "humidity_info": "...",
              "soil_info": "...",
              "fertilization_info": "...",
              "pruning_info": "...",
              "common_issues": "...",
              "seasonal_tips": "..."
            }
            Formatting rules:
            - Each value must be plain text separated by newline characters (\n).
            - Start every bullet with "- " (hyphen + space). Do not include other bullet markers.
            - Do NOT use Markdown (**bold**, quotes, code fences) or additional JSON fields.
            - Escape any double quotes inside values as \".
            - Do NOT include stray quotation marks outside JSON.
            - No trailing commas.
            - Return the JSON minified (single block) with double quotes around keys and values.
        """.trimIndent()

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
                ?: return null
            val jsonString = text.extractJsonPayload().normalizeJsonPayload()
            val json = Gson().fromJson(jsonString, JsonObject::class.java)

            val entity = CareInstructionsEntity(
                id = plantId,
                plantId = plantId,
                watering_info = json.cleaned("watering_info"),
                light_info = json.cleaned("light_info"),
                temperature_info = json.cleaned("temperature_info"),
                humidity_info = json.cleaned("humidity_info"),
                soil_info = json.cleaned("soil_info"),
                fertilization_info = json.cleaned("fertilization_info"),
                pruning_info = json.cleaned("pruning_info"),
                common_issues = json.cleaned("common_issues"),
                seasonal_tips = json.cleaned("seasonal_tips"),
                fetched_at = now
            )
            careDao.upsertCare(entity)
            runCatching { remote.upsertCareInstructions(plant.userId, entity) }
            entity.toDomain()
        } catch (e: Exception) {
            Log.e("PlantRepo", "Failed to fetch care guide", e)
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

private val CARE_GUIDE_KEYS = listOf(
    "watering_info",
    "light_info",
    "temperature_info",
    "humidity_info",
    "soil_info",
    "fertilization_info",
    "pruning_info",
    "common_issues",
    "seasonal_tips"
)

private fun String.normalizeJsonPayload(): String {
        val normalized = StringBuilder("{")
    CARE_GUIDE_KEYS.forEachIndexed { index, key ->
        val value = extractValueForKey(key)
            val sanitized = (value ?: "- No data.").sanitizeCareText().escapeJson()
        normalized.append("\"").append(key).append("\":\"").append(sanitized).append("\"")
        if (index != CARE_GUIDE_KEYS.lastIndex) normalized.append(",")
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
