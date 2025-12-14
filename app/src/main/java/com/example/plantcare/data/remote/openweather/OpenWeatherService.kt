package com.example.plantcare.data.remote.openweather

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenWeatherGeoService {
    @GET("geo/1.0/direct")
    suspend fun directGeocode(
        @Query("q") query: String,
        @Query("limit") limit: Int = 1,
        @Query("appid") apiKey: String
    ): List<GeoDirectResult>

    @GET("geo/1.0/reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("limit") limit: Int = 1,
        @Query("appid") apiKey: String
    ): List<GeoReverseResult>
}

interface OpenWeatherService {
    @GET("data/2.5/weather")
    suspend fun currentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String = "metric",
        @Query("appid") apiKey: String
    ): WeatherResponse

    @GET("data/2.5/forecast")
    suspend fun forecast5d3h(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String = "metric",
        @Query("cnt") cnt: Int = 8, // ~24 hours (3h intervals)
        @Query("appid") apiKey: String
    ): ForecastResponse
}


