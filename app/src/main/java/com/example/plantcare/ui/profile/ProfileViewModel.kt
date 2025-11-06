package com.example.plantcare.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plantcare.data.local.AppDatabase
import com.example.plantcare.domain.model.User
import com.example.plantcare.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val db: AppDatabase,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _createdAt = MutableStateFlow<Long?>(null)
    val createdAt: StateFlow<Long?> = _createdAt.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionFlow.collect { session ->
                _user.value = session?.user
                if (session?.user != null) {
                    val entity = db.userDao().getById(session.user.id)
                    _createdAt.value = entity?.created_at
                } else {
                    _createdAt.value = null
                }
            }
        }
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            _loading.value = true
            val res = authRepository.updateProfile(displayName = name, profilePhotoLocalPath = null)
            _loading.value = false
            if (res.isFailure) _error.value = res.exceptionOrNull()?.message else _user.value = res.getOrNull()
        }
    }

    fun updateAvatar(localPath: String) {
        viewModelScope.launch {
            _loading.value = true
            val res = authRepository.updateProfile(displayName = null, profilePhotoLocalPath = localPath)
            _loading.value = false
            if (res.isFailure) _error.value = res.exceptionOrNull()?.message else _user.value = res.getOrNull()
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    suspend fun deleteAccount(): Result<Unit> = authRepository.deleteAccount()
}


