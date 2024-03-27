package io.kauth.service.auth.jwt

import kotlinx.serialization.Serializable

data class Jwt(
    val payload: Payload
) {
    @Serializable
    data class Payload(
        val id: String,
        val email: String,
        val roles: List<String>
    )
}
