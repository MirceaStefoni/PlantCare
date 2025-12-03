package com.example.plantcare.domain.model

object CareGuideFields {
    const val WATERING = "watering_info"
    const val LIGHT = "light_info"
    const val TEMPERATURE = "temperature_info"
    const val HUMIDITY = "humidity_info"
    const val SOIL = "soil_info"
    const val FERTILIZATION = "fertilization_info"
    const val PRUNING = "pruning_info"
    const val ISSUES = "common_issues"
    const val SEASONAL = "seasonal_tips"

    val ALL = listOf(
        WATERING,
        LIGHT,
        TEMPERATURE,
        HUMIDITY,
        SOIL,
        FERTILIZATION,
        PRUNING,
        ISSUES,
        SEASONAL
    )
}

