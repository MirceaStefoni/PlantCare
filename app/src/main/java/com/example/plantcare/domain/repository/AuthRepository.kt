package com.example.plantcare.domain.repository

import com.example.plantcare.domain.model.AuthSession
import com.example.plantcare.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val sessionFlow: Flow<AuthSession?>

    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<AuthSession>
    suspend fun signInWithEmail(email: String, password: String, rememberMe: Boolean): Result<AuthSession>
    suspend fun sendPasswordReset(email: String): Result<Unit>

    suspend fun signInWithGoogle(idToken: String, rememberMe: Boolean): Result<AuthSession>
    suspend fun refreshTokens(): Result<AuthSession>
    suspend fun logout(): Result<Unit>

    suspend fun updateProfile(displayName: String?, profilePhotoLocalPath: String?): Result<User>
    suspend fun deleteAccount(): Result<Unit>
}


