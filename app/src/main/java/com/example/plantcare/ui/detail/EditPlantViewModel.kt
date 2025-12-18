package com.example.plantcare.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.FileProvider
import com.example.plantcare.domain.model.Plant
import com.example.plantcare.domain.repository.PlantRepository
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

@HiltViewModel
class EditPlantViewModel @Inject constructor(
    private val repository: PlantRepository,
    private val storage: FirebaseStorage,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "EditPlantVM"
    }

    private val _plant = MutableStateFlow<Plant?>(null)
    val plant: StateFlow<Plant?> = _plant.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    // Pending photo change (not saved yet, just selected)
    // Store as File path for more reliable access
    private val _pendingPhotoFile = MutableStateFlow<File?>(null)
    val pendingPhotoUri: StateFlow<Uri?>
        get() = MutableStateFlow(_pendingPhotoFile.value?.let { Uri.fromFile(it) }).asStateFlow()

    private val _pendingPhotoPreview = MutableStateFlow<Uri?>(null)
    val pendingPhotoPreview: StateFlow<Uri?> = _pendingPhotoPreview.asStateFlow()

    fun loadPlant(plantId: String) {
        viewModelScope.launch {
            _plant.value = repository.getPlant(plantId)
            Log.d(TAG, "Plant loaded: ${_plant.value?.commonName}, photo: ${_plant.value?.userPhotoUrl}")
        }
    }

    /**
     * Called when user selects a new photo.
     * Only stores the file locally - does NOT upload or save until Save is pressed.
     */
    fun selectPhoto(photoUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Copy to app storage for reliable access
                val file = copyToAppStorage(photoUri)
                if (file != null && file.exists()) {
                    _pendingPhotoFile.value = file
                    _pendingPhotoPreview.value = Uri.fromFile(file)
                    Log.d(TAG, "Photo selected and copied to: ${file.absolutePath}, size: ${file.length()} bytes")
                } else {
                    Log.e(TAG, "Failed to copy photo to app storage")
                    withContext(Dispatchers.Main) {
                        _saveError.value = "Failed to process selected photo"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error selecting photo", e)
                withContext(Dispatchers.Main) {
                    _saveError.value = "Error: ${e.message}"
                }
            }
        }
    }

    /**
     * Clears the pending photo selection
     */
    fun clearPendingPhoto() {
        _pendingPhotoFile.value = null
        _pendingPhotoPreview.value = null
    }

    /**
     * Called when Save button is pressed.
     * Uploads photo (if changed) and saves all changes to database.
     */
    fun savePlant(
        plantId: String,
        commonName: String,
        scientificName: String?,
        nickname: String?,
        location: String?,
        acquiredDate: Long?,
        notes: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val currentPlant = _plant.value ?: return@launch
            _isSaving.value = true
            _saveError.value = null

            try {
                // Determine final photo URL
                val pendingFile = _pendingPhotoFile.value
                val finalPhotoUrl = if (pendingFile != null && pendingFile.exists()) {
                    // Upload new photo to Firebase Storage
                    Log.d(TAG, "Uploading new photo from: ${pendingFile.absolutePath}")
                    val downloadUrl = uploadPhotoToFirebase(
                        currentPlant.userId,
                        currentPlant.id,
                        pendingFile
                    )

                    if (downloadUrl != null) {
                        Log.d(TAG, "Photo uploaded successfully: $downloadUrl")
                        downloadUrl
                    } else {
                        Log.e(TAG, "Failed to upload photo to Firebase")
                        _saveError.value = "Failed to upload photo. Please try again."
                        _isSaving.value = false
                        return@launch
                    }
                } else {
                    // Keep existing photo
                    Log.d(TAG, "No new photo, keeping existing: ${currentPlant.userPhotoUrl}")
                    currentPlant.userPhotoUrl
                }

                // Create updated plant with all changes
                val updatedPlant = currentPlant.copy(
                    commonName = commonName,
                    scientificName = scientificName,
                    nickname = nickname,
                    location = location,
                    acquiredDate = acquiredDate,
                    notes = notes,
                    userPhotoUrl = finalPhotoUrl,
                    updatedAt = System.currentTimeMillis()
                )

                Log.d(TAG, "Saving plant with photo URL: $finalPhotoUrl")

                // Save to repository AND immediately sync to Firestore
                // This ensures data persists even if user logs out before WorkManager runs
                repository.addOrUpdateAndSync(updatedPlant)
                Log.d(TAG, "Plant saved and synced to Firestore")

                // Clear pending photo
                _pendingPhotoFile.value = null
                _pendingPhotoPreview.value = null
                _plant.value = updatedPlant

                withContext(Dispatchers.Main) {
                    onSuccess()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error saving plant", e)
                _saveError.value = "Failed to save: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    private suspend fun uploadPhotoToFirebase(userId: String, plantId: String, file: File): String? {
        return withContext(Dispatchers.IO) {
            try {
                val storagePath = "users/$userId/plants/$plantId/user.jpg"
                Log.d(TAG, "Uploading to Firebase Storage: $storagePath")
                Log.d(TAG, "File exists: ${file.exists()}, size: ${file.length()} bytes")

                if (!file.exists() || file.length() == 0L) {
                    Log.e(TAG, "File does not exist or is empty")
                    return@withContext null
                }

                val ref = storage.reference.child(storagePath)

                // Use FileInputStream for more reliable upload
                FileInputStream(file).use { inputStream ->
                    val uploadTask = ref.putStream(inputStream).await()
                    Log.d(TAG, "Upload completed: ${uploadTask.bytesTransferred} bytes transferred")
                }

                // Get the download URL after upload completes
                val downloadUrl = ref.downloadUrl.await().toString()
                Log.d(TAG, "Download URL obtained: $downloadUrl")

                downloadUrl
            } catch (e: Exception) {
                Log.e(TAG, "Firebase upload failed", e)
                e.printStackTrace()
                null
            }
        }
    }

    private suspend fun copyToAppStorage(source: Uri): File? {
        return withContext(Dispatchers.IO) {
            try {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?: context.filesDir

                val dest = File(dir, "plant_pending_${System.currentTimeMillis()}.jpg")

                context.contentResolver.openInputStream(source)?.use { input ->
                    dest.outputStream().use { output ->
                        val bytes = input.copyTo(output)
                        Log.d(TAG, "Copied $bytes bytes to ${dest.absolutePath}")
                    }
                } ?: run {
                    Log.e(TAG, "Could not open input stream for: $source")
                    return@withContext null
                }

                if (dest.exists() && dest.length() > 0) {
                    dest
                } else {
                    Log.e(TAG, "File was not created properly")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error copying to app storage", e)
                null
            }
        }
    }

    fun deletePlant(plantId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.delete(plantId)
            onSuccess()
        }
    }

    fun clearError() {
        _saveError.value = null
    }
}
