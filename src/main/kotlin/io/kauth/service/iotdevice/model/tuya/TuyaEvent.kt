package io.kauth.service.iotdevice.model.tuya

import kotlinx.serialization.Serializable

@Serializable
data class TuyaEvent(
    val devId: String
)