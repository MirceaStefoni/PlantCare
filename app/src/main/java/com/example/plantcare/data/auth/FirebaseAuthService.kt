package com.example.plantcare.data.auth

import android.net.Uri
import com.example.plantcare.domain.model.AuthSession
import com.example.plantcare.domain.model.AuthTokens
import com.example.plantcare.domain.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.ktx.userProfileChangeRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FirebaseAuthService(
    private val auth: FirebaseAuth
) {
    suspend fun signUp(email: String, password: String, displayName: String): Result<AuthSession> =
        suspendCancellableCoroutine { cont ->
            auth.fetchSignInMethodsForEmail(email).addOnCompleteListener { checkTask ->
                val methods = checkTask.result?.signInMethods ?: emptyList<String>()
                if (methods.isNotEmpty()) {
                    cont.resume(Result.failure(Exception("Email already in use. Try signing in or use Reset Password.")))
                    return@addOnCompleteListener
                }
                auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        val ex = task.exception
                        val message = when (ex) {
                            is FirebaseAuthUserCollisionException -> "Email already in use. Try signing in or use Reset Password."
                            else -> ex?.message ?: "Sign up failed"
                        }
                        cont.resume(Result.failure(Exception(message)))
                        return@addOnCompleteListener
                    }
                    val user = auth.currentUser
                    if (user == null) {
                        cont.resume(Result.failure(Exception("No user after sign up")))
                        return@addOnCompleteListener
                    }
                    val profile = userProfileChangeRequest { this.displayName = displayName }
                    user.updateProfile(profile).addOnCompleteListener {
                        issueTokenWithIcon(user, cont)
                    }
                }
            }
        }

    suspend fun signIn(email: String, password: String): Result<AuthSession> =
        suspendCancellableCoroutine { cont ->
            auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    cont.resume(Result.failure(task.exception ?: Exception("Sign in failed")))
                    return@addOnCompleteListener
                }
                val user = auth.currentUser
                if (user == null) cont.resume(Result.failure(Exception("No user"))) else issueToken(user, cont)
            }
        }

    suspend fun sendPasswordReset(email: String): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            auth.sendPasswordResetEmail(email).addOnCompleteListener { t ->
                cont.resume(if (t.isSuccessful) Result.success(Unit) else Result.failure(t.exception ?: Exception("Reset failed")))
            }
        }

    suspend fun signInWithGoogle(idToken: String): Result<AuthSession> =
        suspendCancellableCoroutine { cont ->
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    cont.resume(Result.failure(task.exception ?: Exception("Google sign-in failed")))
                    return@addOnCompleteListener
                }
                val user = auth.currentUser
                if (user == null) {
                    cont.resume(Result.failure(Exception("No user")))
                } else {
                    if (user.displayName.isNullOrBlank() && !user.email.isNullOrBlank()) {
                        val fallback = user.email!!.substringBefore('@')
                        val req = userProfileChangeRequest { displayName = fallback }
                        user.updateProfile(req).addOnCompleteListener {
                            issueTokenWithIcon(user, cont)
                        }
                    } else {
                        issueTokenWithIcon(user, cont)
                    }
                }
            }
        }

    suspend fun refresh(): Result<AuthSession> =
        suspendCancellableCoroutine { cont ->
            val user = auth.currentUser
            if (user == null) {
                cont.resume(Result.failure(Exception("No session")))
            } else {
                user.getIdToken(true).addOnCompleteListener { t ->
                    val token = t.result?.token ?: ""
                    cont.resume(Result.success(AuthSession(user = user.toDomain(), tokens = AuthTokens(token, refreshToken = "", expiresAtEpochSeconds = 0L))))
                }
            }
        }

    suspend fun updateProfile(displayName: String?, photoUrl: String?): Result<User> =
        suspendCancellableCoroutine { cont ->
            val user = auth.currentUser
            if (user == null) {
                cont.resume(Result.failure(Exception("No session")))
            } else {
                val req = userProfileChangeRequest {
                    displayName?.let { this.displayName = it }
                    photoUrl?.let { this.photoUri = Uri.parse(it) }
                }
                user.updateProfile(req).addOnCompleteListener {
                    if (it.isSuccessful) cont.resume(Result.success(user.toDomain()))
                    else cont.resume(Result.failure(it.exception ?: Exception("Update failed")))
                }
            }
        }

    suspend fun deleteAccount(): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            val user = auth.currentUser
            if (user == null) {
                cont.resume(Result.failure(Exception("No session")))
            } else {
                user.delete().addOnCompleteListener {
                    cont.resume(if (it.isSuccessful) Result.success(Unit) else Result.failure(it.exception ?: Exception("Delete failed")))
                }
            }
        }

    fun signOut() {
        auth.signOut()
    }

    private fun issueToken(user: FirebaseUser, cont: kotlin.coroutines.Continuation<Result<AuthSession>>) {
        user.getIdToken(false).addOnCompleteListener { t ->
            val token = t.result?.token ?: ""
            cont.resume(Result.success(AuthSession(user = user.toDomain(), tokens = AuthTokens(token, refreshToken = "", expiresAtEpochSeconds = 0L))))
        }
    }

    private fun issueTokenWithIcon(user: FirebaseUser, cont: kotlin.coroutines.Continuation<Result<AuthSession>>) {
        user.getIdToken(false).addOnCompleteListener { t ->
            val token = t.result?.token ?: ""
            val iconId = (user.uid.hashCode() % 6).let { if (it < 0) it + 6 else it }
            cont.resume(Result.success(AuthSession(user = user.toDomain(iconId), tokens = AuthTokens(token, refreshToken = "", expiresAtEpochSeconds = 0L))))
        }
    }

    private fun FirebaseUser.toDomain(iconId: Int = 0): User =
        User(id = uid, email = email.orEmpty(), displayName = displayName, profilePhotoUrl = photoUrl?.toString(), profileIconId = iconId)
}


