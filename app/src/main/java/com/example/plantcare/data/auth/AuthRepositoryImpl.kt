package com.example.plantcare.data.auth

import com.example.plantcare.data.local.AppDatabase
import com.example.plantcare.data.session.SessionManager
import com.example.plantcare.domain.model.AuthSession
import com.example.plantcare.domain.model.User
import com.example.plantcare.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AuthRepositoryImpl(
    private val db: AppDatabase,
    private val fakeService: FakeAuthService,
    private val sessionManager: SessionManager
) : AuthRepository {
    override val sessionFlow: Flow<AuthSession?> = sessionManager.sessionFlow

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<AuthSession> {
        val res = fakeService.signUp(email, password, displayName)
        return res.onSuccess { session -> sessionManager.saveSession(session, remember = true) }
    }

    override suspend fun signInWithEmail(email: String, password: String, rememberMe: Boolean): Result<AuthSession> {
        val now = System.currentTimeMillis() / 1000L
        val lock = sessionManager.lockoutInfo.first()
        if (lock.second > now) {
            return Result.failure(IllegalStateException("Account is locked. Try again later."))
        }
        val res = fakeService.signIn(email, password)
        return if (res.isSuccess) {
            sessionManager.clearLockout()
            val s = res.getOrThrow()
            sessionManager.saveSession(s, remember = rememberMe)
            Result.success(s)
        } else {
            sessionManager.recordFailedAttempt(maxAttempts = 5, lockoutSeconds = 60, nowEpochSeconds = now)
            res
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = fakeService.sendPasswordReset(email)

    override suspend fun signInWithGoogle(idToken: String, rememberMe: Boolean): Result<AuthSession> {
        val res = fakeService.signInWithGoogle(idToken)
        return res.onSuccess { s -> sessionManager.saveSession(s, remember = rememberMe) }
    }

    override suspend fun refreshTokens(): Result<AuthSession> {
        val current = sessionManager.sessionFlow.first() ?: return Result.failure(IllegalStateException("No session"))
        val res = fakeService.refresh(current)
        return res.onSuccess { s -> sessionManager.saveSession(s, remember = true) }
    }

    override suspend fun logout(): Result<Unit> {
        sessionManager.clearSession()
        return Result.success(Unit)
    }

    override suspend fun updateProfile(displayName: String?, profilePhotoLocalPath: String?): Result<User> {
        val current = sessionManager.sessionFlow.first() ?: return Result.failure(IllegalStateException("No session"))
        val res = fakeService.updateProfile(current.user.id, displayName, photoUrl = profilePhotoLocalPath)
        return res
    }

    override suspend fun deleteAccount(): Result<Unit> {
        val current = sessionManager.sessionFlow.first() ?: return Result.failure(IllegalStateException("No session"))
        val res = fakeService.deleteAccount(current.user.id)
        if (res.isSuccess) {
            db.clearAllTables()
            sessionManager.clearSession()
        }
        return res
    }
}


