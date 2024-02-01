package io.kauth.client.eventStore.model


data class ReadResult<T>(
    val events: List<Event<T>>,
    val lastStreamPosition: Long,
    val firstStreamPosition: Long
)