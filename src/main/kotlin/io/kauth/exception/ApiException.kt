package io.kauth.exception

import io.kauth.util.IO
import kotlinx.serialization.Serializable

@Serializable
data class ApiException(val error: String) : Exception(error)
operator fun ApiException.not(): Nothing = throw this

fun allowIf(
    condition: Boolean,
    message: () -> String? = { null }
) = IO {
    if(!condition) {
        !ApiException(message() ?: "UnAuthorized")
    }
}