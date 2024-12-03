package io.kauth.service.iotdevice.model.iotdevice

import kotlinx.serialization.Serializable

@Serializable
data class DeviceCommand(
    val command: String,
    val uri: String
)

