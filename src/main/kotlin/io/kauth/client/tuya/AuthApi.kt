package io.kauth.client.tuya

import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Token(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken:  String,
    @SerialName("expire_time")
    val expireTime: Int
)

fun Tuya.Client.getToken() =
    this.request<Unit, Token>(
        method = HttpMethod.Get,
        uri = "/v1.0/token?grant_type=1"
    )

fun Tuya.Client.refreshToken(refresh: String) =
    this.request<Unit, Token>(
        method = HttpMethod.Get,
        uri = "/v1.0/token/${refresh}"
    )