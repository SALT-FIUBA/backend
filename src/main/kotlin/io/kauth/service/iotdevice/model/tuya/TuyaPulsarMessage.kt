package io.kauth.service.iotdevice.model.tuya

import kotlinx.serialization.Serializable

@Serializable
data class TuyaPulsarMessage(
    val data: String,
    val protocol: Int,
    val pv: String,
    val sign: String,
    val t: Long
)
