package com.example.plantcare.domain.model

data class User(
    val id: String,
    val email: String,
    val displayName: String?,
    val profilePhotoUrl: String?,
    val profileIconId: Int = 0
)

data class Plant(
    val id: String,
    val userId: String,
    val commonName: String,
    val scientificName: String?,
    val nickname: String?,
    val location: String?,
    val userPhotoUrl: String,
    val referencePhotoUrl: String?,
    val addedMethod: String,
    val notes: String?,
    val acquiredDate: Long?,
    val wateringFrequency: String?,
    val lightRequirements: String?,
    val healthStatus: String?,
    val isAnalyzed: Boolean = false,
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

data class LightMeasurement(
    val id: String,
    val plantId: String,
    val luxValue: Double,
    val assessmentLabel: String,
    val assessmentLevel: String,
    val idealMinLux: Double?,
    val idealMaxLux: Double?,
    val idealDescription: String?,
    val adequacyPercent: Int?,
    val recommendations: String?,
    val timeOfDay: String?,
    val measuredAt: Long
)


