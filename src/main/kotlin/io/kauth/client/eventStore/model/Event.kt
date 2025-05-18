package io.kauth.client.eventStore.model

import io.kauth.serializer.UUIDSerializer
import io.kauth.service.AppService
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.concurrent.thread

//Los eventos tienen un ID en el stream
//A su vez el stream tiene un nombre con el ID de la entidad
@Serializable
data class Event<V>(
    val streamName: String,
    val revision: Long,
    @Serializable(with = UUIDSerializer::class)
    val id: UUID, //Esto se usa para idempotencia ?
    val value: V,
    //Evaluar metadata Generica ?
    val metadata: EventMetadata
) {
    fun retrieveId(serviceName: String): String? =
        streamName.retrieveId(serviceName)

    fun retrieveId(serviceName: AppService): String? =
        streamName.retrieveId(serviceName.name)
}

fun String.retrieveId(serviceName: String): String? {
    return "$serviceName-(?<id>.+)"
        .toRegex()
        .matchEntire(this)
        ?.groups
        ?.get("id")
        ?.value
}
