package com.example.plantcare.data.sync

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.plantcare.data.local.OutdoorCheckDao
import com.example.plantcare.data.local.OutdoorCheckEntity
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
        fun outdoorCheckDao(): OutdoorCheckDao
        fun plantRemoteDataSource(): PlantRemoteDataSource
        fun firebaseStorage(): FirebaseStorage
    }

    private val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, SyncWorkerEntryPoint::class.java)
    private val plantDao = entryPoint.plantDao()
    private val outdoorCheckDao = entryPoint.outdoorCheckDao()
    private val remote = entryPoint.plantRemoteDataSource()
    private val storage = entryPoint.firebaseStorage()
    private val appContext = context.applicationContext

    override suspend fun doWork(): Result {
        val pendingPlants = plantDao.getPendingPlants()
        val pendingOutdoorChecks = outdoorCheckDao.getPendingChecks()

        if (pendingPlants.isEmpty() && pendingOutdoorChecks.isEmpty()) return Result.success()

        val errors = coroutineScope {
            val plantJobs = pendingPlants.map { entity ->
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
            }

            val outdoorJobs = pendingOutdoorChecks.map { check ->
                async {
                    runCatching {
                        val userId = pendingPlants.firstOrNull { it.id == check.plantId }?.userId
                            ?: plantDao.getPlantById(check.plantId)?.userId
                            ?: return@async null
                        remote.upsertOutdoorCheck(userId, check)
                        outdoorCheckDao.upsertCheck(check.copy(sync_state = SyncState.SYNCED, last_sync_error = null))
                        null
                    }.getOrElse { throwable ->
                        outdoorCheckDao.updateSyncState(check.id, SyncState.FAILED, throwable.message)
                        throwable
                    }
                }
            }

            (plantJobs + outdoorJobs).awaitAll().filterNotNull()
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

