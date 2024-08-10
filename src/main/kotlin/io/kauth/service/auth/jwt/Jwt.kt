package io.kauth.service.auth.jwt

import kotlinx.serialization.Serializable
import java.util.UUID

data class Jwt(
    val payload: Payload
) {
    @Serializable
    data class Payload(
        val id: String,
        val email: String,
        val roles: List<String>
    ) {
        val uuid get() = UUID.fromString(id)
    }
}
