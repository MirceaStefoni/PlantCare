package com.example.plantcare.ui.outdoor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plantcare.domain.model.OutdoorCheck
import com.example.plantcare.domain.model.Plant
import com.example.plantcare.domain.repository.PlantRepository
import com.google.android.gms.location.FusedLocationProviderClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OutdoorCheckUiState(
    val plant: Plant? = null,
    val cityQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val latest: OutdoorCheck? = null,
    val history: List<OutdoorCheck> = emptyList()
)

@HiltViewModel
class OutdoorCheckViewModel @Inject constructor(
    private val repository: PlantRepository,
    private val fusedLocation: FusedLocationProviderClient
) : ViewModel() {

    private val _state = MutableStateFlow(OutdoorCheckUiState())
    val state: StateFlow<OutdoorCheckUiState> = _state.asStateFlow()

    fun load(plantId: String) {
        if (plantId.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(plant = repository.getPlant(plantId)) }
        }
        viewModelScope.launch {
            repository.observeOutdoorChecks(plantId).collect { list ->
                _state.update { it.copy(history = list, latest = list.firstOrNull()) }
            }
        }
    }

    fun setCityQuery(value: String) {
        _state.update { it.copy(cityQuery = value) }
    }

    fun runFromCity(plantId: String) {
        val query = _state.value.cityQuery.trim()
        if (query.isBlank()) {
            _state.update { it.copy(errorMessage = "Please enter a city") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                repository.runOutdoorEnvironmentCheckFromCity(plantId, query)
            }.onSuccess { check ->
                _state.update { it.copy(isLoading = false, latest = check) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Outdoor check failed") }
            }
        }
    }

    @Suppress("MissingPermission")
    fun runFromCurrentLocation(plantId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                fusedLocation.lastLocation
                    .addOnSuccessListener { loc ->
                        if (loc == null) {
                            _state.update { it.copy(isLoading = false, errorMessage = "Location unavailable. Enter a city instead.") }
                            return@addOnSuccessListener
                        }
                        viewModelScope.launch {
                            runCatching {
                                repository.runOutdoorEnvironmentCheckFromCoordinates(
                                    plantId = plantId,
                                    latitude = loc.latitude,
                                    longitude = loc.longitude,
                                    cityName = null
                                )
                            }.onSuccess { check ->
                                _state.update { it.copy(isLoading = false, latest = check) }
                            }.onFailure { e ->
                                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Outdoor check failed") }
                            }
                        }
                    }
                    .addOnFailureListener {
                        _state.update { it.copy(isLoading = false, errorMessage = "Location failed. Enter a city instead.") }
                    }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = "Location failed. Enter a city instead.") }
            }
        }
    }
}


