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

    init {
        viewModelScope.launch {
            authRepository.sessionFlow
                .flatMapLatest { session ->
                    currentUserId = session?.user?.id
                    val uid = session?.user?.id
                    if (uid == null) {
                        _plants.value = emptyList()
                        flowOf(emptyList())
                    } else {
                        flow {
                            repository.syncFromRemote(uid)
                            emitAll(repository.observePlants(uid))
                        }
                    }
                }
                .collectLatest { list -> _plants.value = list }
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
                    userPhotoUrl = uri,
                    referencePhotoUrl = null,
                    addedMethod = "photo",
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
                    userPhotoUrl = "", // will be filled after photo API or user update
                    referencePhotoUrl = null,
                    addedMethod = "name",
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


