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

import android.util.Log

@HiltViewModel
class PlantDetailViewModel @Inject constructor(
    private val repository: PlantRepository
) : ViewModel() {

    private val _plant = MutableStateFlow<Plant?>(null)
    val plant: StateFlow<Plant?> = _plant.asStateFlow()

    fun loadPlant(plantId: String) {
        viewModelScope.launch {
            Log.d("PlantDetail", "Loading plant: $plantId")
            val p = repository.getPlant(plantId)
            _plant.value = p
            
            // Trigger analysis if we don't have health status yet
            if (p != null && p.healthStatus == null) {
                Log.d("PlantDetail", "Health status missing, triggering analysis...")
                repository.analyzePlant(plantId)
                // Reload to get updates
                Log.d("PlantDetail", "Reloading plant data after analysis attempt...")
                _plant.value = repository.getPlant(plantId)
            } else {
                 Log.d("PlantDetail", "Plant already analyzed or null. Status: ${p?.healthStatus}")
            }
        }
    }

    fun deletePlant(plantId: String) {
        viewModelScope.launch {
            repository.delete(plantId)
            // Navigation back handled by UI event or callback
        }
    }
}
