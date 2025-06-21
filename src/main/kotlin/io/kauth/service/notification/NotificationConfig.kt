package io.kauth.service.notification

import kotlinx.serialization.Serializable


@Serializable
data class NotificationConfig(
    val brevoApiKey: String? = null,
)