package com.example.plantcare.ui.care

import com.example.plantcare.domain.model.CareGuideFields

data class CareGuideGroupDefinition(
    val id: String,
    val title: String,
    val description: String,
    val focus: String,
    val keys: List<String>
)

val CareGuideGroups: List<CareGuideGroupDefinition> = listOf(
    CareGuideGroupDefinition(
        id = "hydration_light",
        title = "Hydration & Light",
        description = "Dial in the daily essentials",
        focus = "Provide concise watering cadences and optimal light exposure recommendations.",
        keys = listOf(CareGuideFields.WATERING, CareGuideFields.LIGHT)
    ),
    CareGuideGroupDefinition(
        id = "environment",
        title = "Environment",
        description = "Keep the climate steady",
        focus = "Summarize suitable temperature ranges and humidity adjustments to avoid stress.",
        keys = listOf(CareGuideFields.TEMPERATURE, CareGuideFields.HUMIDITY)
    ),
    CareGuideGroupDefinition(
        id = "nutrition",
        title = "Soil & Feeding",
        description = "Build healthy roots",
        focus = "Explain soil composition and fertilization cadence tailored to the plant.",
        keys = listOf(CareGuideFields.SOIL, CareGuideFields.FERTILIZATION)
    ),
    CareGuideGroupDefinition(
        id = "maintenance",
        title = "Maintenance & Issues",
        description = "Prevent setbacks",
        focus = "Outline pruning cadence and common issues with quick fixes.",
        keys = listOf(CareGuideFields.PRUNING, CareGuideFields.ISSUES)
    ),
    CareGuideGroupDefinition(
        id = "seasonal",
        title = "Seasonal Focus",
        description = "Adjust across the year",
        focus = "Return exactly four concise bullet points labelled Spring, Summer, Autumn, Winter—each line should start with '- Spring:', '- Summer:' etc. Keep to a single actionable sentence per season.",
        keys = listOf(CareGuideFields.SEASONAL)
    )
)

val CareGuideFieldLabels: Map<String, String> = mapOf(
    CareGuideFields.WATERING to "Watering",
    CareGuideFields.LIGHT to "Light Requirements",
    CareGuideFields.TEMPERATURE to "Temperature",
    CareGuideFields.HUMIDITY to "Humidity",
    CareGuideFields.SOIL to "Soil & Potting",
    CareGuideFields.FERTILIZATION to "Fertilization",
    CareGuideFields.PRUNING to "Pruning",
    CareGuideFields.ISSUES to "Common Problems",
    CareGuideFields.SEASONAL to "Seasonal Tips"
)

