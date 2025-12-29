package com.example.plantcare.data.remote.openweather

import com.google.gson.annotations.SerializedName

data class GeoDirectResult(
    val name: String?,
    val lat: Double,
    val lon: Double,
    val country: String?,
    val state: String?
)

// Reverse geocoding result (coords -> place name). OpenWeather returns similar fields.
data class GeoReverseResult(
    val name: String?,
    val lat: Double,
    val lon: Double,
    val country: String?,
    val state: String?
)

data class WeatherResponse(
    val name: String?,
    val coord: Coord?,
    val weather: List<WeatherCondition>?,
    val main: MainBlock?,
    val wind: WindBlock?
)

data class Coord(
    val lat: Double,
    val lon: Double
)

data class WeatherCondition(
    val main: String?,
    val description: String?
)

data class MainBlock(
    val temp: Double?,
    @SerializedName("feels_like") val feelsLike: Double?,
    val humidity: Int?
)

data class WindBlock(
    val speed: Double? // meters/sec
)

data class ForecastResponse(
    val list: List<ForecastItem>?
)

data class ForecastItem(
    val dt: Long?,
    val main: ForecastMain?
)

data class ForecastMain(
    @SerializedName("temp_min") val tempMin: Double?
)


