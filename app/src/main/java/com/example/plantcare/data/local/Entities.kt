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
    tableName = "light_measurements",
    indices = [Index("plant_id")],
    foreignKeys = [ForeignKey(
        entity = PlantEntity::class,
        parentColumns = ["id"],
        childColumns = ["plant_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class LightMeasurementEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "plant_id") val plantId: String,
    val lux_value: Double,
    val assessment_label: String,
    val assessment_level: String,
    val ideal_min_lux: Double?,
    val ideal_max_lux: Double?,
    val ideal_description: String?,
    val adequacy_percent: Int?,
    val recommendations: String?,
    val time_of_day: String?,
    val measured_at: Long,
    val sync_state: SyncState = SyncState.PENDING,
    val last_sync_error: String? = null
)

