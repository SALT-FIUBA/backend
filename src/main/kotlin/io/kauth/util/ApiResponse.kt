package io.kauth.util

import kotlinx.serialization.Serializable


@Serializable
sealed interface ApiResponse<out T>{
    @Serializable
    data class Success<out T>(val value: T) : ApiResponse<T>
    @Serializable
    data class Error(val message: String) : ApiResponse<Nothing>
}