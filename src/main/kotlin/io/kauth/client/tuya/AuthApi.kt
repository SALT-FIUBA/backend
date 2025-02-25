package io.kauth.client.tuya

import io.kauth.util.not
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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

/*
fun main() {


    runBlocking {

        val client = !Tuya.newClient(this, "vjsswmh3wqerd5keuj4m", "4a24bb8b298e48d8a7040316cd8b8bf7")

        val model = !client.queryDataModel("eb1e2a58ac996a0800mt92")

        println(model)
    }



}

 */