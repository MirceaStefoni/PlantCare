package com.example.plantcare.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SyncState { PENDING, SYNCED, FAILED }

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val display_name: String?,
    val profile_photo_url: String?,
    val profile_icon_id: Int = 0,
    val created_at: Long,
    val updated_at: Long,
    val sync_state: SyncState = SyncState.SYNCED,
    val last_sync_error: String? = null
)

@Entity(
    tableName = "plants",
    indices = [Index("user_id")],
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["user_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class PlantEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val common_name: String,
    val scientific_name: String?,
    val nickname: String? = null,
    val location: String? = null,
    val user_photo_url: String,
    val reference_photo_url: String?,
    val added_method: String,
    val notes: String? = null,
    val acquired_date: Long? = null,
    val watering_frequency: String? = null,
    val light_requirements: String? = null,
    val health_status: String? = null,
    val is_analyzed: Boolean = false,
    val created_at: Long,
    val updated_at: Long,
    val sync_state: SyncState = SyncState.PENDING,
    val last_sync_error: String? = null
)

@Entity(
    tableName = "care_instructions",
    indices = [Index("plant_id")],
    foreignKeys = [ForeignKey(
        entity = PlantEntity::class,
        parentColumns = ["id"],
        childColumns = ["plant_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class CareInstructionsEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "plant_id") val plantId: String,
    val watering_info: String?,
    val light_info: String?,
    val temperature_info: String?,
    val humidity_info: String?,
    val soil_info: String?,
    val fertilization_info: String?,
    val pruning_info: String?,
    val common_issues: String?,
    val seasonal_tips: String?,
    val fetched_at: Long,
    val sync_state: SyncState = SyncState.SYNCED,
    val last_sync_error: String? = null
)

@Entity(
    tableName = "health_analyses",
    indices = [Index("plant_id")],
    foreignKeys = [ForeignKey(
        entity = PlantEntity::class,
        parentColumns = ["id"],
        childColumns = ["plant_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class HealthAnalysisEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "plant_id") val plantId: String,
    val photo_url: String,
    val health_status: String,
    val health_score: Int,
    val status_description: String,
    val issues_json: String, // JSON stored as string
    val recommendations_json: String, // JSON stored as string
    val prevention_tips_json: String, // JSON stored as string
    val analyzed_at: Long,
    val sync_state: SyncState = SyncState.PENDING,
    val last_sync_error: String? = null
)

@Entity(
    tableName = "outdoor_checks",
    indices = [Index("plant_id")],
    foreignKeys = [ForeignKey(
        entity = PlantEntity::class,
        parentColumns = ["id"],
        childColumns = ["plant_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class OutdoorCheckEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "plant_id") val plantId: String,
    val city_name: String?,
    val latitude: Double,
    val longitude: Double,
    val temp_c: Double,
    val feels_like_c: Double,
    val humidity_percent: Int,
    val wind_kmh: Double,
    val uv_index: Double?,
    val min_temp_next_24h_c: Double?,
    val weather_description: String?,
    val verdict: String,
    val verdict_color: String,
    val analysis: String,
    val warnings_json: String,
    val recommendations_json: String,
    val checked_at: Long,
    val sync_state: SyncState = SyncState.PENDING,
    val last_sync_error: String? = null
)
