package com.example.plantcare.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plantcare.domain.model.Plant
import com.example.plantcare.domain.repository.PlantRepository
import com.example.plantcare.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PlantRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private var currentUserId: String? = null

    private val _plants = MutableStateFlow<List<Plant>>(emptyList())
    val plants: StateFlow<List<Plant>> = _plants.asStateFlow()

    // Start as true - we're loading until we know for sure
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Track if we have a valid session to distinguish between "loading" and "no session"
    private val _hasSession = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            authRepository.sessionFlow
                .flatMapLatest { session ->
                    currentUserId = session?.user?.id
                    val uid = session?.user?.id
                    
                    if (uid == null) {
                        // No session - but we might still be loading the session
                        // Keep loading true until we're sure there's no session
                        _hasSession.value = false
                        _plants.value = emptyList()
                        // Don't set loading to false here - let it stay true briefly
                        // The session flow will emit again if user logs in
                        flowOf(emptyList<Plant>())
                    } else {
                        // Valid session - start loading plants
                        _hasSession.value = true
                        _isLoading.value = true
                        flow {
                            try {
                                repository.syncFromRemote(uid)
                            } catch (e: Exception) {
                                // Sync failed, but we can still show local data
                            }
                            emitAll(repository.observePlants(uid))
                        }
                    }
                }
                .collectLatest { list ->
                    _plants.value = list
                    // Only set loading to false if we have a valid session
                    // This prevents showing "No Plants Yet" before we know if user is logged in
                    if (_hasSession.value) {
                        _isLoading.value = false
                    }
                }
        }
        
        // Also observe session to handle the "no session" case properly
        viewModelScope.launch {
            // Give a small delay for the session to be restored from storage
            kotlinx.coroutines.delay(500)
            // If after 500ms we still don't have a session, stop loading
            if (!_hasSession.value) {
                _isLoading.value = false
            }
        }
    }

    fun addPlantFromUri(uri: String, name: String, scientific: String?) {
        val userId = currentUserId ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            repository.addOrUpdate(
                Plant(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    commonName = name.ifBlank { "Unknown" },
                    scientificName = scientific,
                    nickname = null,
                    location = null,
                    userPhotoUrl = uri,
                    referencePhotoUrl = null,
                    addedMethod = "photo",
                    notes = null,
                    acquiredDate = null,
                    wateringFrequency = null,
                    lightRequirements = null,
                    healthStatus = null,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    fun addPlantByName(name: String) {
        val userId = currentUserId ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            repository.addOrUpdate(
                Plant(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    commonName = name,
                    scientificName = null,
                    nickname = null,
                    location = null,
                    userPhotoUrl = "", // will be filled after photo API or user update
                    referencePhotoUrl = null,
                    addedMethod = "name",
                    notes = null,
                    acquiredDate = null,
                    wateringFrequency = null,
                    lightRequirements = null,
                    healthStatus = null,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    fun deletePlant(plantId: String) {
        viewModelScope.launch { repository.delete(plantId) }
    }
}
