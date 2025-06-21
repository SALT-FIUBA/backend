package io.kauth.service.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthConfig(
    val secret: String,
    val frontend: String,
    val google: AuthGoogleConfig
)

@Serializable
data class AuthGoogleConfig(
    val clientId: String,
    val secret: String,
    val redirectUri: String,
)