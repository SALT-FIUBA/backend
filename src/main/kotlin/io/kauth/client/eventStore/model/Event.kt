package io.kauth.client.eventStore.model

import io.kauth.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

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

    fun retrieveId(serviceName: String): String? {
        return "$serviceName-(?<id>.+)"
            .toRegex()
            .matchEntire(streamName)
            ?.groups
            ?.get("id")
            ?.value
    }

}
