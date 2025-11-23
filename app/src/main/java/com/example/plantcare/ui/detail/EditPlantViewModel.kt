package com.example.plantcare.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plantcare.domain.model.Plant
import com.example.plantcare.domain.repository.PlantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditPlantViewModel @Inject constructor(
    private val repository: PlantRepository
) : ViewModel() {

    private val _plant = MutableStateFlow<Plant?>(null)
    val plant: StateFlow<Plant?> = _plant.asStateFlow()

    fun loadPlant(plantId: String) {
        viewModelScope.launch {
            _plant.value = repository.getPlant(plantId)
        }
    }

    fun updatePlant(
        plantId: String,
        commonName: String,
        scientificName: String?,
        nickname: String?,
        location: String?,
        acquiredDate: Long?,
        notes: String?
    ) {
        viewModelScope.launch {
            val currentPlant = _plant.value ?: return@launch
            val updatedPlant = currentPlant.copy(
                commonName = commonName,
                scientificName = scientificName,
                nickname = nickname,
                location = location,
                acquiredDate = acquiredDate,
                notes = notes,
                updatedAt = System.currentTimeMillis()
            )
            repository.addOrUpdate(updatedPlant)
        }
    }
    
    fun deletePlant(plantId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.delete(plantId)
            onSuccess()
        }
    }
}

