package com.example.plantcare.ui.detail

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plantcare.domain.model.HealthIssue
import com.example.plantcare.domain.model.HealthRecommendationsResult
import com.example.plantcare.domain.model.HealthScoreResult
import com.example.plantcare.domain.model.Plant
import com.example.plantcare.domain.repository.PlantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HealthAnalysisViewModel @Inject constructor(
    private val repository: PlantRepository
) : ViewModel() {

    private val _plant = MutableStateFlow<Plant?>(null)
    val plant: StateFlow<Plant?> = _plant.asStateFlow()

    private val _affectedAreaPhotoUri = MutableStateFlow<Uri?>(null)
    val affectedAreaPhotoUri: StateFlow<Uri?> = _affectedAreaPhotoUri.asStateFlow()

    // Separate loading states for each chunk
    private val _isLoadingScore = MutableStateFlow(false)
    val isLoadingScore: StateFlow<Boolean> = _isLoadingScore.asStateFlow()

    private val _isLoadingIssues = MutableStateFlow(false)
    val isLoadingIssues: StateFlow<Boolean> = _isLoadingIssues.asStateFlow()

    private val _isLoadingRecommendations = MutableStateFlow(false)
    val isLoadingRecommendations: StateFlow<Boolean> = _isLoadingRecommendations.asStateFlow()

    // Results for each chunk
    private val _scoreResult = MutableStateFlow<HealthScoreResult?>(null)
    val scoreResult: StateFlow<HealthScoreResult?> = _scoreResult.asStateFlow()

    private val _issuesResult = MutableStateFlow<List<HealthIssue>?>(null)
    val issuesResult: StateFlow<List<HealthIssue>?> = _issuesResult.asStateFlow()

    private val _recommendationsResult = MutableStateFlow<HealthRecommendationsResult?>(null)
    val recommendationsResult: StateFlow<HealthRecommendationsResult?> = _recommendationsResult.asStateFlow()

    // Error states
    private val _scoreError = MutableStateFlow<String?>(null)
    val scoreError: StateFlow<String?> = _scoreError.asStateFlow()

    private val _issuesError = MutableStateFlow<String?>(null)
    val issuesError: StateFlow<String?> = _issuesError.asStateFlow()

    private val _recommendationsError = MutableStateFlow<String?>(null)
    val recommendationsError: StateFlow<String?> = _recommendationsError.asStateFlow()

    // Track if analysis has started
    private val _analysisStarted = MutableStateFlow(false)
    val analysisStarted: StateFlow<Boolean> = _analysisStarted.asStateFlow()

    // Analyzed timestamp
    private val _analyzedAt = MutableStateFlow<Long?>(null)
    val analyzedAt: StateFlow<Long?> = _analyzedAt.asStateFlow()

    fun loadPlant(plantId: String) {
        viewModelScope.launch {
            _plant.value = repository.getPlant(plantId)
        }
    }

    fun setAffectedAreaPhoto(uri: Uri) {
        _affectedAreaPhotoUri.value = uri
    }

    fun clearAffectedAreaPhoto() {
        _affectedAreaPhotoUri.value = null
    }

    fun analyzeHealth(plantId: String) {
        val currentPlant = _plant.value ?: return
        val affectedUri = _affectedAreaPhotoUri.value ?: return

        _analysisStarted.value = true
        _analyzedAt.value = System.currentTimeMillis()

        // Clear previous results
        _scoreResult.value = null
        _issuesResult.value = null
        _recommendationsResult.value = null
        _scoreError.value = null
        _issuesError.value = null
        _recommendationsError.value = null
        
        // Set all loading states to true IMMEDIATELY to prevent glitchy UI
        _isLoadingScore.value = true
        _isLoadingIssues.value = true
        _isLoadingRecommendations.value = true

        val plantPhotoUrl = currentPlant.userPhotoUrl
        val affectedAreaUriString = affectedUri.toString()
        val plantName = currentPlant.commonName

        // Sequential calls to avoid overloading connection pools and causing timeouts
        viewModelScope.launch {
            // Step 1: Health Score (loading already set above)
            try {
                _scoreResult.value = repository.analyzeHealthScore(
                    plantPhotoUrl, affectedAreaUriString, plantName
                )
            } catch (e: Exception) {
                _scoreError.value = e.message ?: "Failed to analyze health score"
            } finally {
                _isLoadingScore.value = false
            }

            // Step 2: Issues (only after score completes)
            _isLoadingIssues.value = true
            try {
                _issuesResult.value = repository.analyzeHealthIssues(
                    plantPhotoUrl, affectedAreaUriString, plantName
                )
            } catch (e: Exception) {
                _issuesError.value = e.message ?: "Failed to analyze issues"
            } finally {
                _isLoadingIssues.value = false
            }

            // Step 3: Recommendations (only after issues complete)
            _isLoadingRecommendations.value = true
            try {
                _recommendationsResult.value = repository.analyzeHealthRecommendations(
                    plantPhotoUrl, affectedAreaUriString, plantName
                )
            } catch (e: Exception) {
                _recommendationsError.value = e.message ?: "Failed to get recommendations"
            } finally {
                _isLoadingRecommendations.value = false
            }
        }
    }

    fun resetAnalysis() {
        _analysisStarted.value = false
        _scoreResult.value = null
        _issuesResult.value = null
        _recommendationsResult.value = null
        _scoreError.value = null
        _issuesError.value = null
        _recommendationsError.value = null
        _affectedAreaPhotoUri.value = null
        _analyzedAt.value = null
    }

    fun retryScore() {
        val currentPlant = _plant.value ?: return
        val affectedUri = _affectedAreaPhotoUri.value ?: return

        viewModelScope.launch {
            _isLoadingScore.value = true
            _scoreError.value = null
            try {
                _scoreResult.value = repository.analyzeHealthScore(
                    currentPlant.userPhotoUrl,
                    affectedUri.toString(),
                    currentPlant.commonName
                )
            } catch (e: Exception) {
                _scoreError.value = e.message ?: "Failed to analyze health score"
            } finally {
                _isLoadingScore.value = false
            }
        }
    }

    fun retryIssues() {
        val currentPlant = _plant.value ?: return
        val affectedUri = _affectedAreaPhotoUri.value ?: return

        viewModelScope.launch {
            _isLoadingIssues.value = true
            _issuesError.value = null
            try {
                _issuesResult.value = repository.analyzeHealthIssues(
                    currentPlant.userPhotoUrl,
                    affectedUri.toString(),
                    currentPlant.commonName
                )
            } catch (e: Exception) {
                _issuesError.value = e.message ?: "Failed to analyze issues"
            } finally {
                _isLoadingIssues.value = false
            }
        }
    }

    fun retryRecommendations() {
        val currentPlant = _plant.value ?: return
        val affectedUri = _affectedAreaPhotoUri.value ?: return

        viewModelScope.launch {
            _isLoadingRecommendations.value = true
            _recommendationsError.value = null
            try {
                _recommendationsResult.value = repository.analyzeHealthRecommendations(
                    currentPlant.userPhotoUrl,
                    affectedUri.toString(),
                    currentPlant.commonName
                )
            } catch (e: Exception) {
                _recommendationsError.value = e.message ?: "Failed to get recommendations"
            } finally {
                _isLoadingRecommendations.value = false
            }
        }
    }
}
