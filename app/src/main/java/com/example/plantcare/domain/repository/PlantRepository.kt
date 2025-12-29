package com.example.plantcare.domain.repository

import com.example.plantcare.domain.model.CareInstructions
import com.example.plantcare.domain.model.LightMeasurement
import com.example.plantcare.domain.model.HealthIssue
import com.example.plantcare.domain.model.HealthRecommendationsResult
import com.example.plantcare.domain.model.HealthScoreResult
import com.example.plantcare.domain.model.CityLocation
import com.example.plantcare.domain.model.OutdoorCheck
import com.example.plantcare.domain.model.Plant
import kotlinx.coroutines.flow.Flow

interface PlantRepository {
    fun observePlants(userId: String): Flow<List<Plant>>
    suspend fun getPlant(plantId: String): Plant?
    suspend fun getPlants(userId: String): List<Plant>
    suspend fun addOrUpdate(plant: Plant)
    suspend fun addOrUpdateAndSync(plant: Plant) // Saves to local DB AND immediately syncs to Firestore
    suspend fun delete(plantId: String)
    suspend fun syncFromRemote(userId: String)
    suspend fun analyzePlant(plantId: String)
    suspend fun getCareGuide(plantId: String, forceRefresh: Boolean = false): CareInstructions?
    suspend fun generateCareGuideChunk(
        plantId: String,
        keys: List<String>,
        focus: String
    ): Map<String, String?>
    suspend fun saveCareGuide(plantId: String, values: Map<String, String?>): CareInstructions?

    fun observeLightMeasurements(plantId: String): Flow<List<LightMeasurement>>
    suspend fun getLightMeasurements(plantId: String): List<LightMeasurement>
    suspend fun evaluateLightConditions(
        plantId: String,
        luxValue: Double,
        timeOfDay: String,
        measurementTimestamp: Long
    ): LightMeasurement?

    // Health Analysis - Chunked for better UX
    suspend fun analyzeHealthScore(
        plantPhotoUrl: String,
        affectedAreaUri: String,
        plantName: String
    ): HealthScoreResult
    
    suspend fun analyzeHealthIssues(
        plantPhotoUrl: String,
        affectedAreaUri: String,
        plantName: String
    ): List<HealthIssue>
    
    suspend fun analyzeHealthRecommendations(
        plantPhotoUrl: String,
        affectedAreaUri: String,
        plantName: String
    ): HealthRecommendationsResult

    // Outdoor Environment Check
    fun observeOutdoorChecks(plantId: String): Flow<List<OutdoorCheck>>
    suspend fun getOutdoorChecks(plantId: String): List<OutdoorCheck>
    suspend fun runOutdoorEnvironmentCheckFromCoordinates(
        plantId: String,
        latitude: Double,
        longitude: Double,
        cityName: String? = null
    ): OutdoorCheck

    suspend fun runOutdoorEnvironmentCheckFromCity(
        plantId: String,
        cityQuery: String
    ): OutdoorCheck

    suspend fun searchCityLocations(
        query: String,
        limit: Int = 5
    ): List<CityLocation>
}
