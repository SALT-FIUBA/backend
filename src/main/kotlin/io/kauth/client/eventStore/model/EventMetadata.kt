package io.kauth.client.eventStore.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class EventMetadata(
    val timestamp: Instant,
    val snapshottedStreamRevision: StreamRevision? = null
)