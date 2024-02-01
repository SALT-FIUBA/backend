package io.kauth.util

import kotlinx.serialization.Serializable


@Serializable
sealed interface ApiResponse {
    @Serializable
    data class Success<out T>(val value: T) : ApiResponse
    @Serializable
    data class Error(val message: String) : ApiResponse
}