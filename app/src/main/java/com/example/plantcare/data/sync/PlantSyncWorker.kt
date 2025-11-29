package com.example.plantcare.data.sync

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.plantcare.data.local.PlantDao
import com.example.plantcare.data.local.PlantEntity
import com.example.plantcare.data.local.SyncState
import com.example.plantcare.data.remote.PlantRemoteDataSource
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

class PlantSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun plantDao(): PlantDao
        fun plantRemoteDataSource(): PlantRemoteDataSource
        fun firebaseStorage(): FirebaseStorage
    }

    private val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, SyncWorkerEntryPoint::class.java)
    private val plantDao = entryPoint.plantDao()
    private val remote = entryPoint.plantRemoteDataSource()
    private val storage = entryPoint.firebaseStorage()
    private val appContext = context.applicationContext

    override suspend fun doWork(): Result {
        val pending = plantDao.getPlantsBySyncState()
        if (pending.isEmpty()) return Result.success()

        val errors = coroutineScope {
            pending.map { entity ->
                async {
                    runCatching {
                        val prepared = uploadPhotoIfNeeded(entity)
                        remote.upsertPlant(prepared)
                        plantDao.upsertPlant(
                            prepared.copy(
                                sync_state = SyncState.SYNCED,
                                last_sync_error = null
                            )
                        )
                        null
                    }.getOrElse { throwable ->
                        plantDao.updateSyncState(entity.id, SyncState.FAILED, throwable.message)
                        throwable
                    }
                }
            }.awaitAll().filterNotNull()
        }
        return if (errors.isEmpty()) Result.success() else Result.retry()
    }

    private suspend fun uploadPhotoIfNeeded(entity: PlantEntity): PlantEntity {
        val photo = entity.user_photo_url
        if (photo.isBlank() || photo.startsWith("http") || photo.startsWith("gs://")) return entity

        val uri = Uri.parse(photo)
        val input = appContext.contentResolver.openInputStream(uri) ?: return entity
        input.use {
            val ref = storage.reference.child("users/${entity.userId}/plants/${entity.id}/user.jpg")
            ref.putStream(it).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            return entity.copy(user_photo_url = downloadUrl)
        }
    }

    companion object {
        const val UNIQUE_NAME = "plant_sync"
    }
}

