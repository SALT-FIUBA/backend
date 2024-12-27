package io.kauth.service.iotdevice

import kotlinx.serialization.Serializable

@Serializable
data class IotDeviceConfig(
    val tuya: TuyaConfig
) {
    @Serializable
    data class TuyaConfig(
        val clientId: String,
        val clientSecret: String,
        val pulsarHost: String
    )
}
