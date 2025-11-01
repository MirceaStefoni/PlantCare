package com.example.plantcare.domain.model

data class User(
    val id: String,
    val email: String,
    val displayName: String?,
    val profilePhotoUrl: String?
)

data class Plant(
    val id: String,
    val userId: String,
    val commonName: String,
    val scientificName: String?,
    val userPhotoUrl: String,
    val referencePhotoUrl: String?,
    val addedMethod: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class CareInstructions(
    val plantId: String,
    val wateringInfo: String?,
    val lightInfo: String?,
    val temperatureInfo: String?,
    val humidityInfo: String?,
    val soilInfo: String?,
    val fertilizationInfo: String?,
    val pruningInfo: String?,
    val commonIssues: String?,
    val seasonalTips: String?,
    val fetchedAt: Long
)


