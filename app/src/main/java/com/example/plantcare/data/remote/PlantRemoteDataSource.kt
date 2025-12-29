package com.example.plantcare.data.remote

import com.example.plantcare.data.local.CareInstructionsEntity
import com.example.plantcare.data.local.LightMeasurementEntity
import com.example.plantcare.data.local.OutdoorCheckEntity
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
    private fun userDoc(userId: String) = firestore.collection("users").document(userId)

    private fun plantsCollection(userId: String) =
        userDoc(userId).collection("plants")

    private fun careCollection(userId: String) =
        userDoc(userId).collection("care_guides")

    private fun lightCollection(userId: String) =
        userDoc(userId).collection("light_measurements")

    private fun outdoorChecksCollection(userId: String) =
        userDoc(userId).collection("outdoor_checks")

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

        runCatching { careCollection(userId).document(plantId).delete().await() }
        runCatching { deleteOutdoorChecksForPlant(userId, plantId) }
        runCatching { deleteLightMeasurementsForPlant(userId, plantId) }

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
        runCatching {
            val careDocs = careCollection(userId).get().await()
            careDocs.documents.forEach { doc -> runCatching { doc.reference.delete().await() } }
        }
        runCatching {
            val outdoorDocs = outdoorChecksCollection(userId).get().await()
            outdoorDocs.documents.forEach { doc -> runCatching { doc.reference.delete().await() } }
            val lightDocs = lightCollection(userId).get().await()
            lightDocs.documents.forEach { doc -> runCatching { doc.reference.delete().await() } }
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

    suspend fun upsertCareInstructions(userId: String, care: CareInstructionsEntity) {
        careCollection(userId)
            .document(care.plantId)
            .set(
                mapOf(
                    "wateringInfo" to care.watering_info,
                    "lightInfo" to care.light_info,
                    "temperatureInfo" to care.temperature_info,
                    "humidityInfo" to care.humidity_info,
                    "soilInfo" to care.soil_info,
                    "fertilizationInfo" to care.fertilization_info,
                    "pruningInfo" to care.pruning_info,
                    "commonIssues" to care.common_issues,
                    "seasonalTips" to care.seasonal_tips,
                    "fetchedAt" to care.fetched_at
                )
            )
            .await()
    }

    suspend fun fetchCareInstructions(userId: String): List<CareInstructionsEntity> {
        val snapshot = careCollection(userId).get().await()
        return snapshot.documents.mapNotNull { doc ->
            val plantId = doc.id
            CareInstructionsEntity(
                id = plantId,
                plantId = plantId,
                watering_info = doc.getString("wateringInfo"),
                light_info = doc.getString("lightInfo"),
                temperature_info = doc.getString("temperatureInfo"),
                humidity_info = doc.getString("humidityInfo"),
                soil_info = doc.getString("soilInfo"),
                fertilization_info = doc.getString("fertilizationInfo"),
                pruning_info = doc.getString("pruningInfo"),
                common_issues = doc.getString("commonIssues"),
                seasonal_tips = doc.getString("seasonalTips"),
                fetched_at = doc.getLong("fetchedAt") ?: System.currentTimeMillis(),
                sync_state = SyncState.SYNCED,
                last_sync_error = null
            )
        }
    }

    suspend fun fetchCareInstruction(userId: String, plantId: String): CareInstructionsEntity? {
        val doc = careCollection(userId).document(plantId).get().await()
        if (!doc.exists()) return null
        return CareInstructionsEntity(
            id = plantId,
            plantId = plantId,
            watering_info = doc.getString("wateringInfo"),
            light_info = doc.getString("lightInfo"),
            temperature_info = doc.getString("temperatureInfo"),
            humidity_info = doc.getString("humidityInfo"),
            soil_info = doc.getString("soilInfo"),
            fertilization_info = doc.getString("fertilizationInfo"),
            pruning_info = doc.getString("pruningInfo"),
            common_issues = doc.getString("commonIssues"),
            seasonal_tips = doc.getString("seasonalTips"),
            fetched_at = doc.getLong("fetchedAt") ?: System.currentTimeMillis(),
            sync_state = SyncState.SYNCED,
            last_sync_error = null
        )
    }

    suspend fun upsertLightMeasurement(userId: String, entity: LightMeasurementEntity) {
        lightCollection(userId)
            .document(entity.id)
            .set(
                mapOf(
                    "plantId" to entity.plantId,
                    "luxValue" to entity.lux_value,
                    "assessmentLabel" to entity.assessment_label,
                    "assessmentLevel" to entity.assessment_level,
                    "idealMinLux" to entity.ideal_min_lux,
                    "idealMaxLux" to entity.ideal_max_lux,
                    "idealDescription" to entity.ideal_description,
                    "adequacyPercent" to entity.adequacy_percent,
                    "recommendations" to entity.recommendations,
                    "timeOfDay" to entity.time_of_day,
                    "measuredAt" to entity.measured_at
                )
            )
            .await()
    }

    suspend fun fetchLightMeasurements(userId: String): List<LightMeasurementEntity> {
        val snapshot = lightCollection(userId).get().await()
        return snapshot.documents.mapNotNull { doc ->
            val plantId = doc.getString("plantId") ?: return@mapNotNull null
            val lux = doc.getDouble("luxValue") ?: return@mapNotNull null
            LightMeasurementEntity(
                id = doc.id,
                plantId = plantId,
                lux_value = lux,
                assessment_label = doc.getString("assessmentLabel") ?: "Unknown",
                assessment_level = doc.getString("assessmentLevel") ?: "unknown",
                ideal_min_lux = doc.getDouble("idealMinLux"),
                ideal_max_lux = doc.getDouble("idealMaxLux"),
                ideal_description = doc.getString("idealDescription"),
                adequacy_percent = doc.getLong("adequacyPercent")?.toInt(),
                recommendations = doc.getString("recommendations"),
                time_of_day = doc.getString("timeOfDay"),
                measured_at = doc.getLong("measuredAt") ?: System.currentTimeMillis(),
                sync_state = SyncState.SYNCED,
                last_sync_error = null
            )
        }
    }

    suspend fun deleteLightMeasurementsForPlant(userId: String, plantId: String) {
        val docs = lightCollection(userId).whereEqualTo("plantId", plantId).get().await()
        docs.documents.forEach { doc -> runCatching { doc.reference.delete().await() } }
    }

    suspend fun upsertOutdoorCheck(userId: String, entity: OutdoorCheckEntity) {
        outdoorChecksCollection(userId)
            .document(entity.id)
            .set(
                mapOf(
                    "plantId" to entity.plantId,
                    "cityName" to entity.city_name,
                    "latitude" to entity.latitude,
                    "longitude" to entity.longitude,
                    "tempC" to entity.temp_c,
                    "feelsLikeC" to entity.feels_like_c,
                    "humidityPercent" to entity.humidity_percent,
                    "windKmh" to entity.wind_kmh,
                    "uvIndex" to entity.uv_index,
                    "minTempNext24hC" to entity.min_temp_next_24h_c,
                    "weatherDescription" to entity.weather_description,
                    "verdict" to entity.verdict,
                    "verdictColor" to entity.verdict_color,
                    "analysis" to entity.analysis,
                    "warningsJson" to entity.warnings_json,
                    "recommendationsJson" to entity.recommendations_json,
                    "checkedAt" to entity.checked_at
                )
            )
            .await()
    }

    suspend fun fetchOutdoorChecks(userId: String): List<OutdoorCheckEntity> {
        val snapshot = outdoorChecksCollection(userId).get().await()
        return snapshot.documents.mapNotNull { doc ->
            val plantId = doc.getString("plantId") ?: return@mapNotNull null
            OutdoorCheckEntity(
                id = doc.id,
                plantId = plantId,
                city_name = doc.getString("cityName"),
                latitude = doc.getDouble("latitude") ?: return@mapNotNull null,
                longitude = doc.getDouble("longitude") ?: return@mapNotNull null,
                temp_c = doc.getDouble("tempC") ?: 0.0,
                feels_like_c = doc.getDouble("feelsLikeC") ?: 0.0,
                humidity_percent = (doc.getLong("humidityPercent") ?: 0L).toInt(),
                wind_kmh = doc.getDouble("windKmh") ?: 0.0,
                uv_index = doc.getDouble("uvIndex"),
                min_temp_next_24h_c = doc.getDouble("minTempNext24hC"),
                weather_description = doc.getString("weatherDescription"),
                verdict = doc.getString("verdict") ?: "Acceptable",
                verdict_color = doc.getString("verdictColor") ?: "yellow",
                analysis = doc.getString("analysis") ?: "",
                warnings_json = doc.getString("warningsJson") ?: "[]",
                recommendations_json = doc.getString("recommendationsJson") ?: "[]",
                checked_at = doc.getLong("checkedAt") ?: System.currentTimeMillis(),
                sync_state = SyncState.SYNCED,
                last_sync_error = null
            )
        }
    }

    suspend fun deleteOutdoorChecksForPlant(userId: String, plantId: String) {
        val docs = outdoorChecksCollection(userId).whereEqualTo("plantId", plantId).get().await()
        docs.documents.forEach { doc -> runCatching { doc.reference.delete().await() } }
    }
}

