package com.example.plantcare.data.session

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.plantcare.domain.model.AuthSession
import com.example.plantcare.domain.model.AuthTokens
import com.example.plantcare.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session")

class SessionManager(private val appContext: Context) {
    private object Keys {
        val accessToken = stringPreferencesKey("accessToken")
        val refreshToken = stringPreferencesKey("refreshToken")
        val expiresAt = longPreferencesKey("expiresAt")
        val rememberMe = booleanPreferencesKey("rememberMe")

        val userId = stringPreferencesKey("userId")
        val email = stringPreferencesKey("email")
        val displayName = stringPreferencesKey("displayName")
        val photoUrl = stringPreferencesKey("photoUrl")

        val failedAttempts = intPreferencesKey("failedAttempts")
        val lockoutUntilEpochSeconds = longPreferencesKey("lockoutUntil")
    }

    val sessionFlow: Flow<AuthSession?> = appContext.sessionDataStore.data.map { prefs ->
        val access = prefs[Keys.accessToken]
        val refresh = prefs[Keys.refreshToken]
        val expires = prefs[Keys.expiresAt] ?: 0L
        val userId = prefs[Keys.userId]
        val email = prefs[Keys.email]
        if (access.isNullOrBlank() || userId.isNullOrBlank() || email.isNullOrBlank()) {
            null
        } else {
            val tokens = AuthTokens(access, refresh ?: "", expires)
            val user = User(
                id = userId,
                email = email,
                displayName = prefs[Keys.displayName],
                profilePhotoUrl = prefs[Keys.photoUrl]
            )
            AuthSession(user = user, tokens = tokens)
        }
    }

    suspend fun saveSession(session: AuthSession, remember: Boolean) {
        appContext.sessionDataStore.edit { prefs ->
            prefs[Keys.accessToken] = session.tokens.accessToken
            prefs[Keys.refreshToken] = session.tokens.refreshToken
            prefs[Keys.expiresAt] = session.tokens.expiresAtEpochSeconds
            prefs[Keys.rememberMe] = remember

            prefs[Keys.userId] = session.user.id
            prefs[Keys.email] = session.user.email
            prefs[Keys.displayName] = session.user.displayName ?: ""
            prefs[Keys.photoUrl] = session.user.profilePhotoUrl ?: ""
        }
    }

    suspend fun clearSession() {
        appContext.sessionDataStore.edit { prefs ->
            prefs.remove(Keys.accessToken)
            prefs.remove(Keys.refreshToken)
            prefs.remove(Keys.expiresAt)
            prefs.remove(Keys.userId)
            prefs.remove(Keys.email)
            prefs.remove(Keys.displayName)
            prefs.remove(Keys.photoUrl)
            prefs.remove(Keys.rememberMe)
        }
    }

    suspend fun getRememberMe(): Boolean {
        return appContext.sessionDataStore.data.map { it[Keys.rememberMe] ?: true }.first()
    }

    val lockoutInfo: Flow<Pair<Int, Long>> = appContext.sessionDataStore.data.map { prefs ->
        (prefs[Keys.failedAttempts] ?: 0) to (prefs[Keys.lockoutUntilEpochSeconds] ?: 0L)
    }

    suspend fun recordFailedAttempt(maxAttempts: Int, lockoutSeconds: Long, nowEpochSeconds: Long): Pair<Int, Long> {
        var resultAttempts = 0
        var lockoutUntil = 0L
        appContext.sessionDataStore.edit { prefs ->
            val attempts = (prefs[Keys.failedAttempts] ?: 0) + 1
            prefs[Keys.failedAttempts] = attempts
            resultAttempts = attempts
            if (attempts >= maxAttempts) {
                lockoutUntil = nowEpochSeconds + lockoutSeconds
                prefs[Keys.lockoutUntilEpochSeconds] = lockoutUntil
                prefs[Keys.failedAttempts] = 0
            }
        }
        return resultAttempts to lockoutUntil
    }

    suspend fun clearLockout() {
        appContext.sessionDataStore.edit { prefs ->
            prefs.remove(Keys.failedAttempts)
            prefs.remove(Keys.lockoutUntilEpochSeconds)
        }
    }
}


