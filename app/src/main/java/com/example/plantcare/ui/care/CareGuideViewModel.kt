package com.example.plantcare.ui.care

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plantcare.domain.model.CareInstructions
import com.example.plantcare.domain.repository.PlantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CareGuideUiState(
    val plantId: String = "",
    val plantName: String = "",
    val scientificName: String? = null,
    val instructions: CareInstructions? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class CareGuideViewModel @Inject constructor(
    private val repository: PlantRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val plantId: String = savedStateHandle.get<String>("plantId")
        ?: error("CareGuideViewModel requires plantId argument")

    private val _state = MutableStateFlow(CareGuideUiState(plantId = plantId))
    val state: StateFlow<CareGuideUiState> = _state.asStateFlow()

    init {
        load(forceRefresh = false)
    }

    fun refresh() {
        load(forceRefresh = true)
    }

    private fun load(forceRefresh: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val plant = repository.getPlant(plantId)
                val guide = repository.getCareGuide(plantId, forceRefresh)
                Pair(plant, guide)
            }.onSuccess { (plant, guide) ->
                _state.value = CareGuideUiState(
                    plantId = plantId,
                    plantName = plant?.commonName ?: "Your plant",
                    scientificName = plant?.scientificName,
                    instructions = guide,
                    isLoading = false,
                    errorMessage = null
                )
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Unable to load care guide"
                    )
                }
            }
        }
    }
}

