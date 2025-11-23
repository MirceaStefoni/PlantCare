package com.example.plantcare.data.auth

import com.example.plantcare.data.local.AppDatabase
import com.example.plantcare.data.remote.PlantRemoteDataSource
import com.example.plantcare.data.session.SessionManager
import com.example.plantcare.domain.model.AuthSession
import com.example.plantcare.domain.model.User
import com.example.plantcare.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FirebaseAuthRepositoryImpl(
    private val db: AppDatabase,
    private val service: FirebaseAuthService,
    private val sessionManager: SessionManager,
    private val googleClient: GoogleSignInClient,
    private val plantRemoteDataSource: PlantRemoteDataSource
) : AuthRepository {
    override val sessionFlow: Flow<AuthSession?> = sessionManager.sessionFlow

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<AuthSession> {
        val res = service.signUp(email, password, displayName)
        return res.onSuccess { s -> sessionManager.saveSession(s, remember = true) }
    }

    override suspend fun signInWithEmail(email: String, password: String, rememberMe: Boolean): Result<AuthSession> {
        val res = service.signIn(email, password)
        return res.onSuccess { s -> sessionManager.saveSession(s, remember = rememberMe) }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = service.sendPasswordReset(email)

    override suspend fun signInWithGoogle(idToken: String, rememberMe: Boolean): Result<AuthSession> {
        val res = service.signInWithGoogle(idToken)
        return res.onSuccess { s -> sessionManager.saveSession(s, remember = rememberMe) }
    }

    override suspend fun refreshTokens(): Result<AuthSession> {
        val res = service.refresh()
        if (res.isSuccess) {
            val s = res.getOrThrow()
            sessionManager.saveSession(s, remember = true)
        } else {
            val msg = res.exceptionOrNull()?.message.orEmpty().lowercase()
            if (msg.contains("no session") || msg.contains("no user")) {
                sessionManager.clearSession()
            }
        }
        return res
    }

    override suspend fun logout(): Result<Unit> {
        service.signOut()
        try { googleClient.signOut() } catch (_: Exception) {}
        withContext(Dispatchers.IO) {
            try { db.clearAllTables() } catch (_: Exception) {}
        }
        sessionManager.clearSession()
        return Result.success(Unit)
    }

    override suspend fun updateProfile(displayName: String?, profilePhotoLocalPath: String?): Result<User> =
        service.updateProfile(displayName, profilePhotoLocalPath).onSuccess { updatedUser ->
            val current = sessionManager.sessionFlow.first()
            if (current != null) {
                sessionManager.saveSession(current.copy(user = updatedUser), remember = true)
            }
        }

    override suspend fun deleteAccount(): Result<Unit> {
        val userId = runCatching { sessionManager.sessionFlow.first()?.user?.id }.getOrNull()
        if (userId != null) {
            runCatching { plantRemoteDataSource.deleteAllPlants(userId) }
        }
        val res = service.deleteAccount()
        if (res.isSuccess) {
            withContext(Dispatchers.IO) { db.clearAllTables() }
            try { 
                googleClient.revokeAccess()
            } catch (_: Exception) {}
            service.signOut()
            sessionManager.clearSession()
            return res
        }
        withContext(Dispatchers.IO) { try { db.clearAllTables() } catch (_: Exception) {} }
        try { googleClient.revokeAccess() } catch (_: Exception) {}
        service.signOut()
        sessionManager.clearSession()
        return Result.success(Unit)
    }
}


