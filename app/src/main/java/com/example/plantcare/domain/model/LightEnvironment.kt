package com.example.plantcare.domain.model

enum class LightEnvironment {
    INDOOR,
    OUTDOOR,
    UNKNOWN
}

fun inferLightEnvironment(location: String?, lightRequirements: String?): LightEnvironment {
    val combined = listOfNotNull(location, lightRequirements)
        .joinToString(" ")
        .lowercase()
        .trim()

    if (combined.isBlank()) return LightEnvironment.UNKNOWN

    val outdoorSignals = listOf(
        "outdoor",
        "garden",
        "balcony",
        "patio",
        "deck",
        "terrace",
        "yard",
        "courtyard",
        "full sun",
        "direct sun"
    )
    if (outdoorSignals.any { combined.contains(it) }) return LightEnvironment.OUTDOOR

    val indoorSignals = listOf(
        "indoor",
        "inside",
        "apartment",
        "living room",
        "bedroom",
        "office",
        "desk",
        "shelf",
        "windowsill",
        "low light",
        "bright indirect",
        "north window",
        "indoor plant"
    )
    if (indoorSignals.any { combined.contains(it) }) return LightEnvironment.INDOOR

    return LightEnvironment.UNKNOWN
}

