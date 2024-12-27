package io.kauth.service.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthConfig(
    val secret: String
)