package io.kauth.service.iotdevice.model.tuya

import kotlinx.serialization.Serializable

@Serializable
data class TuyaEvent(
    val devId: String? = null,
    val bizData: TuyaBizData? = null
)

@Serializable
data class TuyaBizData(
    val devId: String? = null
)

