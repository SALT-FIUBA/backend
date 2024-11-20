package io.kauth.client.tuya

import kotlinx.serialization.Serializable

@Serializable
data class TuyaApiResult<R>(
    val result: R? = null,
    val code: Int? = null,
    val msg: String? = null,
    val success: Boolean,
    val t: Long,
    val tid: String
) {
    fun <T> map(transform: (R) -> T): TuyaApiResult<T> {
        return TuyaApiResult(
            result = result?.let { transform(it) },
            code = code,
            msg = msg,
            success = success,
            t = t,
            tid = tid
        )
    }
}
