package com.example.plantcare.ui.light

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plantcare.data.sensor.LightSampleResult
import com.example.plantcare.data.sensor.LightSensorSampler
import com.example.plantcare.domain.model.LightEnvironment
import com.example.plantcare.domain.model.LightMeasurement
import com.example.plantcare.domain.model.inferLightEnvironment
import com.example.plantcare.domain.repository.PlantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltViewModel
class LightMonitorViewModel @Inject constructor(
    private val repository: PlantRepository,
    private val sensorSampler: LightSensorSampler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val plantId: String = savedStateHandle.get<String>("plantId") ?: ""
    private val _state = MutableStateFlow(LightMonitorUiState(plantId = plantId, sensorAvailable = sensorSampler.hasSensor()))
    val state: StateFlow<LightMonitorUiState> = _state.asStateFlow()

    private var timerJob: Job? = null

    init {
        if (plantId.isBlank()) {
            _state.update { it.copy(errorMessage = "Missing plant reference", measurementPhase = MeasurementPhase.Error) }
        } else {
            viewModelScope.launch {
                val plant = repository.getPlant(plantId)
                _state.update {
                    it.copy(
                        plantName = plant?.commonName,
                        plantScientificName = plant?.scientificName,
                        referencePhotoUrl = plant?.referencePhotoUrl ?: plant?.userPhotoUrl,
                        environment = inferLightEnvironment(plant?.location, plant?.lightRequirements)
                    )
                }
            }
            viewModelScope.launch {
                repository.observeLightMeasurements(plantId).collectLatest { list ->
                    _state.update {
                        it.copy(
                            history = list,
                            latestMeasurement = list.firstOrNull(),
                            measurementPhase = if (it.measurementPhase == MeasurementPhase.Idle && list.isNotEmpty()) MeasurementPhase.Ready else it.measurementPhase
                        )
                    }
                }
            }
        }
    }

    fun startMeasurement() {
        val current = _state.value
        if (!current.sensorAvailable) {
            _state.update { it.copy(errorMessage = "This device does not have an ambient light sensor.", measurementPhase = MeasurementPhase.Error) }
            return
        }
        if (current.measurementPhase == MeasurementPhase.Sampling || current.measurementPhase == MeasurementPhase.Evaluating) return
        if (plantId.isBlank()) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    measurementPhase = MeasurementPhase.Sampling,
                    errorMessage = null,
                    averagedLux = null,
                    currentLux = null,
                    elapsedMillis = 0L
                )
            }
            val duration = LightSensorSampler.DEFAULT_DURATION_MS
            val startTime = SystemClock.elapsedRealtime()
            timerJob?.cancel()
            timerJob = launch {
                while (true) {
                    val elapsed = SystemClock.elapsedRealtime() - startTime
                    _state.update { state ->
                        state.copy(
                            elapsedMillis = elapsed.coerceAtMost(duration),
                            remainingMillis = (duration - elapsed).coerceAtLeast(0L)
                        )
                    }
                    if (elapsed >= duration) break
                    delay(80L)
                }
            }

            when (val result = sensorSampler.sample(durationMillis = duration) { reading ->
                _state.update { it.copy(currentLux = reading.toDouble()) }
            }) {
                is LightSampleResult.SensorMissing -> {
                    _state.update {
                        it.copy(
                            measurementPhase = MeasurementPhase.Error,
                            sensorAvailable = false,
                            errorMessage = "Light sensor unavailable on this device."
                        )
                    }
                }
                is LightSampleResult.Failed -> {
                    _state.update {
                        it.copy(
                            measurementPhase = MeasurementPhase.Error,
                            errorMessage = "Unable to capture any light readings. Try again."
                        )
                    }
                }
                is LightSampleResult.Success -> {
                    _state.update {
                        it.copy(
                            measurementPhase = MeasurementPhase.Evaluating,
                            averagedLux = result.averageLux,
                            currentLux = result.averageLux,
                            elapsedMillis = duration,
                            remainingMillis = 0L
                        )
                    }
                    val measurementTimestamp = System.currentTimeMillis()
                    val measurement = repository.evaluateLightConditions(
                        plantId = plantId,
                        luxValue = result.averageLux,
                        timeOfDay = resolveTimeOfDay(),
                        measurementTimestamp = measurementTimestamp
                    )

                    _state.update {
                        it.copy(
                            measurementPhase = if (measurement != null) MeasurementPhase.Ready else MeasurementPhase.Error,
                            latestMeasurement = measurement ?: it.latestMeasurement,
                            errorMessage = if (measurement == null) "Failed to evaluate light conditions." else null
                        )
                    }
                }
            }
            timerJob?.cancel()
        }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null, measurementPhase = MeasurementPhase.Idle) }
    }

    private fun resolveTimeOfDay(): String {
        val hour = SimpleDateFormat("H", Locale.getDefault()).format(Date()).toInt()
        return when (hour) {
            in 5..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..20 -> "Evening"
            else -> "Night"
        }
    }
}

data class LightMonitorUiState(
    val plantId: String,
    val plantName: String? = null,
    val plantScientificName: String? = null,
    val referencePhotoUrl: String? = null,
    val measurementPhase: MeasurementPhase = MeasurementPhase.Idle,
    val currentLux: Double? = null,
    val averagedLux: Double? = null,
    val elapsedMillis: Long = 0L,
    val remainingMillis: Long = LightSensorSampler.DEFAULT_DURATION_MS,
    val history: List<LightMeasurement> = emptyList(),
    val latestMeasurement: LightMeasurement? = null,
    val sensorAvailable: Boolean = true,
    val errorMessage: String? = null,
    val environment: LightEnvironment = LightEnvironment.UNKNOWN
)

enum class MeasurementPhase { Idle, Sampling, Evaluating, Ready, Error }

