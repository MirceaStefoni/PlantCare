package com.example.plantcare.data.auth

import com.example.plantcare.data.local.AppDatabase
import com.example.plantcare.data.local.SyncState
import com.example.plantcare.data.local.UserEntity
import com.example.plantcare.domain.model.AuthSession
import com.example.plantcare.domain.model.AuthTokens
import com.example.plantcare.domain.model.User
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class FakeAuthService(private val db: AppDatabase) {
    private val emailToPasswordHash: MutableMap<String, String> = mutableMapOf()

    private fun hashPassword(password: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(password.toByteArray())
        return bytes.joinToString(separator = "") { b -> "%02x".format(b) }
    }

    private fun generateTokens(): AuthTokens {
        val access = UUID.randomUUID().toString()
        val refresh = UUID.randomUUID().toString()
        val expires = (System.currentTimeMillis() / 1000L) + 3600L
        return AuthTokens(accessToken = access, refreshToken = refresh, expiresAtEpochSeconds = expires)
    }

    private fun toDomain(user: UserEntity): User = User(
        id = user.id,
        email = user.email,
        displayName = user.display_name,
        profilePhotoUrl = user.profile_photo_url
    )

    suspend fun signUp(email: String, password: String, displayName: String): Result<AuthSession> {
        delay(400)
        val existing = db.userDao().findByEmail(email)
        if (existing != null) return Result.failure(IllegalStateException("Email already in use"))
        val now = System.currentTimeMillis()
        val userId = UUID.randomUUID().toString()
        val entity = UserEntity(
            id = userId,
            email = email,
            display_name = displayName,
            profile_photo_url = null,
            created_at = now,
            updated_at = now,
            sync_state = SyncState.SYNCED,
            last_sync_error = null
        )
        db.userDao().upsert(entity)
        emailToPasswordHash[email] = hashPassword(password)
        val session = AuthSession(user = toDomain(entity), tokens = generateTokens())
        return Result.success(session)
    }

    suspend fun signIn(email: String, password: String): Result<AuthSession> {
        delay(300)
        val user = db.userDao().findByEmail(email) ?: return Result.failure(IllegalArgumentException("Invalid credentials"))
        val hash = emailToPasswordHash[email]
        if (hash == null || hash != hashPassword(password)) return Result.failure(IllegalArgumentException("Invalid credentials"))
        val session = AuthSession(user = toDomain(user), tokens = generateTokens())
        return Result.success(session)
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        delay(300)
        val user = db.userDao().findByEmail(email) ?: return Result.failure(IllegalArgumentException("No account for that email"))
        return Result.success(Unit)
    }

    suspend fun signInWithGoogle(idToken: String): Result<AuthSession> {
        delay(300)
        val fakeEmail = "user_${idToken.take(6)}@example.com"
        val existing = db.userDao().findByEmail(fakeEmail)
        val userEntity = existing ?: run {
            val now = System.currentTimeMillis()
            val entity = UserEntity(
                id = UUID.randomUUID().toString(),
                email = fakeEmail,
                display_name = "Google User",
                profile_photo_url = null,
                created_at = now,
                updated_at = now,
                sync_state = SyncState.SYNCED,
                last_sync_error = null
            )
            db.userDao().upsert(entity)
            entity
        }
        val session = AuthSession(user = toDomain(userEntity), tokens = generateTokens())
        return Result.success(session)
    }

    suspend fun refresh(current: AuthSession): Result<AuthSession> {
        delay(150)
        val refreshed = current.copy(tokens = generateTokens())
        return Result.success(refreshed)
    }

    suspend fun updateProfile(userId: String, displayName: String?, photoUrl: String?): Result<User> {
        val existing = db.userDao().getById(userId) ?: return Result.failure(IllegalStateException("User not found"))
        val updated = existing.copy(
            display_name = displayName ?: existing.display_name,
            profile_photo_url = photoUrl ?: existing.profile_photo_url,
            updated_at = System.currentTimeMillis(),
            sync_state = SyncState.PENDING
        )
        db.userDao().upsert(updated)
        return Result.success(toDomain(updated))
    }

    suspend fun deleteAccount(userId: String): Result<Unit> {
        db.userDao().deleteById(userId)
        return Result.success(Unit)
    }
}


