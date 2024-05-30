package io.kauth.abstractions.result

import kotlinx.serialization.Serializable

@Serializable
data class AppResult<out O>(
    val data: O?,
    val error: String? = null
)