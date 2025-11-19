package com.example.plantcare.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plantcare.domain.model.AuthSession
import com.example.plantcare.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _session = MutableStateFlow<AuthSession?>(null)
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionFlow.collectLatest {
                _session.value = it
                if (it != null) {
                    val now = System.currentTimeMillis() / 1000L
                    if (it.tokens.expiresAtEpochSeconds - now < 60) {
                        authRepository.refreshTokens()
                    }
                }
            }
        }
        // Try to hydrate from Firebase if a user session exists but DataStore is empty
        viewModelScope.launch {
            try { authRepository.refreshTokens() } catch (_: Exception) {} finally { _ready.value = true }
        }
    }

    fun clearError() { _error.value = null }

    fun signIn(email: String, password: String, rememberMe: Boolean) {
        if (!isValidEmail(email)) { _error.value = "Invalid email"; return }
        if (!isValidPassword(password)) { _error.value = "Password must be 8+ chars incl. letter & digit"; return }
        viewModelScope.launch {
            _loading.value = true
            val res = authRepository.signInWithEmail(email, password, rememberMe)
            _loading.value = false
            if (res.isFailure) _error.value = res.exceptionOrNull()?.message
        }
    }

    fun signUp(email: String, password: String, displayName: String) {
        if (!isValidEmail(email)) { _error.value = "Invalid email"; return }
        if (!isValidPassword(password)) { _error.value = "Password must be 8+ chars incl. letter & digit"; return }
        if (displayName.length < 2) { _error.value = "Display name too short"; return }
        viewModelScope.launch {
            _loading.value = true
            val res = authRepository.signUpWithEmail(email, password, displayName)
            _loading.value = false
            if (res.isFailure) _error.value = res.exceptionOrNull()?.message
        }
    }

    fun sendReset(email: String) {
        if (!isValidEmail(email)) { _error.value = "Invalid email"; return }
        viewModelScope.launch {
            _loading.value = true
            val res = authRepository.sendPasswordReset(email)
            _loading.value = false
            if (res.isFailure) _error.value = res.exceptionOrNull()?.message
        }
    }

    fun googleSignIn(idToken: String, rememberMe: Boolean) {
        viewModelScope.launch {
            _loading.value = true
            val res = authRepository.signInWithGoogle(idToken, rememberMe)
            _loading.value = false
            if (res.isFailure) _error.value = res.exceptionOrNull()?.message
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    private fun isValidEmail(email: String): Boolean =
        email.contains('@') && email.contains('.') && email.length in 5..254

    private fun isValidPassword(password: String): Boolean {
        if (password.length < 8) return false
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        return hasLetter && hasDigit
    }
}


