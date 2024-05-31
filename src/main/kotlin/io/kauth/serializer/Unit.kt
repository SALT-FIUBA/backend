package io.kauth.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder

object UnitSerializer : KSerializer<Unit> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("unit serializer mati", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Unit {
        val value = decoder.decodeString()
        if(value != "unit") error("No unit")
    }
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Unit) {
        encoder.encodeString("unit")
    }
}
