package io.kauth.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*

class ByteString(
    val byteArray: ByteArray
) {

    override fun equals(other: Any?): Boolean =
        other === this || other is ByteString && this.byteArray.contentEquals(other.byteArray)

    companion object {
    }
}

object ByteStringBase64Serializer : KSerializer<ByteString> {

    val delegated = String.serializer()

    override val descriptor: SerialDescriptor
        get() = delegated.descriptor

    override fun deserialize(decoder: Decoder): ByteString {
        return ByteString(Base64.getDecoder().decode(delegated.deserialize(decoder)))
    }

    override fun serialize(encoder: Encoder, value: ByteString) {
        delegated.serialize(encoder, Base64.getEncoder().encodeToString(value.byteArray))
    }

}