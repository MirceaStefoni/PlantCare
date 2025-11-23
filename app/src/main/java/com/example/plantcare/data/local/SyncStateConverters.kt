package com.example.plantcare.data.local

import androidx.room.TypeConverter

class SyncStateConverters {
    @TypeConverter
    fun fromSyncState(state: SyncState?): String? = state?.name

    @TypeConverter
    fun toSyncState(value: String?): SyncState =
        value?.let { runCatching { SyncState.valueOf(it) }.getOrNull() } ?: SyncState.PENDING
}

