package com.example.plantcare.domain.repository

import com.example.plantcare.domain.model.CareInstructions
import com.example.plantcare.domain.model.HealthAnalysis
import com.example.plantcare.domain.model.HealthIssue
import com.example.plantcare.domain.model.HealthRecommendationsResult
import com.example.plantcare.domain.model.HealthScoreResult
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
}
