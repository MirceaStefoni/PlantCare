package com.example.plantcare.domain.repository

import com.example.plantcare.domain.model.Plant
import kotlinx.coroutines.flow.Flow

interface PlantRepository {
    fun observePlants(userId: String): Flow<List<Plant>>
    suspend fun getPlant(plantId: String): Plant?
    suspend fun getPlants(userId: String): List<Plant>
    suspend fun addOrUpdate(plant: Plant)
    suspend fun delete(plantId: String)
    suspend fun syncFromRemote(userId: String)
    suspend fun analyzePlant(plantId: String)
}


