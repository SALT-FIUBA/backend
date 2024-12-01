package io.kauth.client.tuya

import io.kauth.util.not
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Duration.Companion.seconds

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

fun main() {

    runBlocking {

        val client = !Tuya.newClient(this)

        delay(5.seconds)
        /*

        val result = !client.queryProperties("eb906f4ba762eb801da5ii")

        println(result)

        val sp = !client.querySpecification("eb906f4ba762eb801da5ii")

        println(sp.result)

        val status = !client.queryProperties("eb906f4ba762eb801da5ii")
        println(status)

         */

        val model = !client.queryDataModel("eb906f4ba762eb801da5ii")

        println(model)


        /*
        val status = !client.queryProperties("eb906f4ba762eb801da5ii")
        println(status)
         */

        /*

        //Esta es otra forma de controlar al device
        val command = !client.sendProperties(
            "eb906f4ba762eb801da5ii",
            mapOf(
                "suck" to JsonPrimitive("strong"),
                "mop_bump" to JsonPrimitive(100),
                "power_go" to JsonPrimitive(false),
            )
        )


        println(command)

         */


    }
}
