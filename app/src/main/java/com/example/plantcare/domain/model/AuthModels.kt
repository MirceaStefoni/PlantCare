package com.example.plantcare.domain.model

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long
)

data class AuthSession(
    val user: User,
    val tokens: AuthTokens
)


