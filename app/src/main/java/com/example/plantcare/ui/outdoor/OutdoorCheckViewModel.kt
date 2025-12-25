package com.example.plantcare.ui.outdoor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plantcare.domain.model.CityLocation
import com.example.plantcare.domain.model.OutdoorCheck
import com.example.plantcare.domain.model.Plant
import com.example.plantcare.domain.repository.PlantRepository
import com.google.android.gms.location.FusedLocationProviderClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OutdoorCheckUiState(
    val plant: Plant? = null,
    val cityQuery: String = "",
    val citySuggestions: List<CityLocation> = emptyList(),
    val selectedCity: CityLocation? = null,
    val isSearchingCities: Boolean = false,
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

    private var citySearchJob: Job? = null

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
        _state.update {
            it.copy(
                cityQuery = value,
                selectedCity = null, // user is typing again
                errorMessage = null
            )
        }
        debouncedSearchCities(value)
    }

    fun runFromCity(plantId: String) {
        val s = _state.value
        val query = s.cityQuery.trim()
        if (query.isBlank()) {
            _state.update { it.copy(errorMessage = "Please enter a city") }
            return
        }

        val chosen = s.selectedCity
        if (chosen == null) {
            // If ambiguous, force a user pick to avoid wrong cities with same name.
            if (s.citySuggestions.size > 1) {
                _state.update { it.copy(errorMessage = "Multiple matches. Please select the correct city from the list.") }
                return
            }
            if (s.citySuggestions.size == 1) {
                onCitySelected(s.citySuggestions.first())
            } else {
                // No suggestions yet (or query too new). Trigger a search and ask user to pick if multiple.
                debouncedSearchCities(query, immediate = true)
                _state.update { it.copy(errorMessage = "Searching… select the correct city if multiple results appear.") }
                return
            }
        }

        val target = _state.value.selectedCity ?: return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                repository.runOutdoorEnvironmentCheckFromCoordinates(
                    plantId = plantId,
                    latitude = target.latitude,
                    longitude = target.longitude,
                    cityName = formatCityLabel(target)
                )
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

    fun onCitySelected(city: CityLocation) {
        _state.update {
            it.copy(
                selectedCity = city,
                cityQuery = formatCityLabel(city),
                citySuggestions = emptyList(),
                errorMessage = null
            )
        }
    }

    private fun debouncedSearchCities(query: String, immediate: Boolean = false) {
        val q = query.trim()
        citySearchJob?.cancel()

        if (q.length < 2) {
            _state.update { it.copy(citySuggestions = emptyList(), isSearchingCities = false) }
            return
        }

        citySearchJob = viewModelScope.launch {
            if (!immediate) delay(350)
            _state.update { it.copy(isSearchingCities = true) }
            runCatching {
                repository.searchCityLocations(q, limit = 5)
            }.onSuccess { hits ->
                _state.update { it.copy(citySuggestions = hits, isSearchingCities = false) }
            }.onFailure {
                _state.update { it.copy(citySuggestions = emptyList(), isSearchingCities = false) }
            }
        }
    }

    private fun formatCityLabel(city: CityLocation): String {
        val parts = buildList {
            add(city.name)
            city.state?.takeIf { it.isNotBlank() }?.let { add(it) }
            city.country?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        return parts.joinToString(", ")
    }
}


