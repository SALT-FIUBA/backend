package io.kauth.service.iotdevice.model.iotdevice

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Integration {
    @Serializable
    @SerialName("Tasmota")
    data class Tasmota(
        val topics: TasmotaTopics,
        val capsSchema: Map<String, CapabilitySchema<TasmotaCapability>>
    ) : Integration

    @Serializable
    @SerialName("Tuya")
    data class Tuya(
        val deviceId: String,
        val capsSchema: Map<String, CapabilitySchema<TuyaCapabilityType>>
    ) : Integration
}

@Serializable
sealed interface IntegrationCapability

@Serializable
data class CapabilitySchema<out E : IntegrationCapability>(
    val cap: E, //type!
    //Cosas que estan en todas las Capabilties
    val name: String,
    val access: Access
) {
    @Serializable
    enum class Access {
        writeread,
        writeonly,
        readonly
    }
}

@Serializable
@SerialName("TasmotaCapability")
sealed interface TasmotaCapability : IntegrationCapability {
    @Serializable
    @SerialName("DHT11")
    data class DHT11(val node: String): TasmotaCapability

    @Serializable
    @SerialName("Relay")
    data object Relay: TasmotaCapability
}

@Serializable
@SerialName("TuyaCapability")
sealed interface TuyaCapabilityType : IntegrationCapability {
    @Serializable
    @SerialName("BooleanCap")
    object BooleanCap : TuyaCapabilityType
    @Serializable
    @SerialName("ValueCap")
    data class ValueCap(val unit: String, val step: Int, val max: Int, val min: Int) : TuyaCapabilityType
    @Serializable
    @SerialName("EnumCap")
    data class EnumCap(val values: List<String>) : TuyaCapabilityType
    @Serializable
    @SerialName("BitMapCap")
    data class BitMapCap(val values: List<String>) : TuyaCapabilityType
    @Serializable
    @SerialName("StringCap")
    data class StringCap(val maxLen: Int) : TuyaCapabilityType
}

@Serializable
@SerialName("TasmotaTopics")
data class TasmotaTopics(
    val state: String,
    val status: String, //En general Online|Offline
    val telemetry: String
)

