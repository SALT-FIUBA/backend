package io.kauth.exception

import kotlinx.serialization.Serializable

@Serializable
data class ApiException(val error: String) : Exception(error)
operator fun ApiException.not(): Nothing = throw this
