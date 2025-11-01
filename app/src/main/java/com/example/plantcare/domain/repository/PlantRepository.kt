package com.example.plantcare.domain.repository

import com.example.plantcare.domain.model.Plant

interface PlantRepository {
    suspend fun getPlants(userId: String): List<Plant>
    suspend fun addOrUpdate(plant: Plant)
    suspend fun delete(plantId: String)
}


