package com.example.plantcare.ui.care

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plantcare.domain.model.CareGuideFields
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
    val fieldValues: Map<String, String?> = emptyMap(),
    val groupStates: Map<String, CareGroupState> = emptyMap(),
    val isSequentialLoading: Boolean = false,
    val isBootstrapping: Boolean = true,
    val lastUpdated: Long? = null,
    val errorMessage: String? = null
)

data class CareGroupState(
    val status: CareGroupStatus = CareGroupStatus.IDLE,
    val errorMessage: String? = null
)

enum class CareGroupStatus { IDLE, LOADING, READY, ERROR }

@HiltViewModel
class CareGuideViewModel @Inject constructor(
    private val repository: PlantRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val plantId: String = savedStateHandle.get<String>("plantId")
        ?: error("CareGuideViewModel requires plantId argument")

    private val _state = MutableStateFlow(
        CareGuideUiState(
            plantId = plantId,
            fieldValues = blankFieldMap(),
            groupStates = CareGuideGroups.associate { it.id to CareGroupState() }
        )
    )
    val state: StateFlow<CareGuideUiState> = _state.asStateFlow()

    init {
        load(forceRefresh = false)
    }

    fun refresh() {
        load(forceRefresh = true)
    }

    private fun load(forceRefresh: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isBootstrapping = true, errorMessage = null) }
            runCatching {
                val plant = repository.getPlant(plantId)
                val cached = if (forceRefresh) null else repository.getCareGuide(plantId, false)
                Pair(plant, cached)
            }.onSuccess { (plant, cached) ->
                if (plant == null) {
                    _state.update {
                        it.copy(
                            isBootstrapping = false,
                            errorMessage = "Plant not found"
                        )
                    }
                    return@onSuccess
                }

                val shouldGenerate = forceRefresh || cached == null
                val fieldValues = if (shouldGenerate) blankFieldMap() else cached.toFieldMap()
                val groupStates = CareGuideGroups.associate { def ->
                    def.id to CareGroupState(
                        status = if (shouldGenerate) CareGroupStatus.LOADING else CareGroupStatus.READY
                    )
                }

                _state.update {
                    it.copy(
                        plantName = plant.commonName,
                        scientificName = plant.scientificName,
                        instructions = cached,
                        fieldValues = fieldValues,
                        groupStates = groupStates,
                        isSequentialLoading = shouldGenerate,
                        isBootstrapping = false,
                        lastUpdated = cached?.fetchedAt,
                        errorMessage = null
                    )
                }

                if (shouldGenerate) {
                    generateSequential()
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isBootstrapping = false,
                        errorMessage = throwable.message ?: "Unable to load care guide"
                    )
                }
            }
        }
    }

    private suspend fun generateSequential() {
        val mutableValues = state.value.fieldValues.toMutableMap()
        CareGuideGroups.forEach { group ->
            updateGroupState(group.id, CareGroupStatus.LOADING, null)
            try {
                val chunk = repository.generateCareGuideChunk(plantId, group.keys, group.focus)
                chunk.forEach { (key, value) -> mutableValues[key] = value }
                _state.update {
                    it.copy(
                        fieldValues = mutableValues.toMap(),
                        errorMessage = null
                    )
                }
                updateGroupState(group.id, CareGroupStatus.READY, null)
            } catch (e: Exception) {
                updateGroupState(group.id, CareGroupStatus.ERROR, e.message)
                _state.update {
                    it.copy(
                        errorMessage = e.message ?: "Unable to load ${group.title}",
                        isSequentialLoading = false
                    )
                }
                return
            }
        }

        val saved = repository.saveCareGuide(plantId, mutableValues)
        _state.update {
            it.copy(
                instructions = saved,
                fieldValues = mutableValues.toMap(),
                lastUpdated = saved?.fetchedAt,
                isSequentialLoading = false,
                errorMessage = null,
                groupStates = it.groupStates.mapValues { entry ->
                    entry.value.copy(status = CareGroupStatus.READY, errorMessage = null)
                }
            )
        }
    }

    private fun updateGroupState(
        groupId: String,
        status: CareGroupStatus,
        errorMessage: String?
    ) {
        _state.update { current ->
            val updated = current.groupStates.toMutableMap()
            updated[groupId] = CareGroupState(status, errorMessage)
            current.copy(groupStates = updated)
        }
    }
}

private fun CareInstructions?.toFieldMap(): Map<String, String?> =
    this?.let {
        mapOf(
            CareGuideFields.WATERING to it.wateringInfo,
            CareGuideFields.LIGHT to it.lightInfo,
            CareGuideFields.TEMPERATURE to it.temperatureInfo,
            CareGuideFields.HUMIDITY to it.humidityInfo,
            CareGuideFields.SOIL to it.soilInfo,
            CareGuideFields.FERTILIZATION to it.fertilizationInfo,
            CareGuideFields.PRUNING to it.pruningInfo,
            CareGuideFields.ISSUES to it.commonIssues,
            CareGuideFields.SEASONAL to it.seasonalTips
        )
    } ?: blankFieldMap()

private fun blankFieldMap(): Map<String, String?> =
    CareGuideFields.ALL.associateWith { null }

