package com.example.plantcare.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plantcare.domain.model.Plant
import com.example.plantcare.domain.repository.PlantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PlantRepository
) : ViewModel() {

    private val currentUserId = "local-user" // TODO integrate with auth session

    private val _plants = MutableStateFlow<List<Plant>>(emptyList())
    val plants: StateFlow<List<Plant>> = _plants.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observePlants(currentUserId).collectLatest { list ->
                _plants.value = list
            }
        }
    }

    fun addPlantFromUri(uri: String, name: String, scientific: String?) {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            repository.addOrUpdate(
                Plant(
                    id = UUID.randomUUID().toString(),
                    userId = currentUserId,
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
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            repository.addOrUpdate(
                Plant(
                    id = UUID.randomUUID().toString(),
                    userId = currentUserId,
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


