package io.kauth.client.google

import io.kauth.client.tuya.TuyaDataCenter
import io.kauth.util.Async
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Job
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.forms.*
import io.ktor.serialization.kotlinx.json.*

object Google {

    data class Client(
        val http: HttpClient,
        val json: Json,
        val clientId: String,
        val clientSecret: String,
        val redirectUri: String,
    )

    fun newClient(
        clientId: String,
        clientSecret: String,
        redirectUri: String,
    ) = Async {
        val json = Json {
            ignoreUnknownKeys = true
        }
        val http = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        Client(
            http = http,
            json = json,
            clientId = clientId,
            clientSecret = clientSecret,
            redirectUri = redirectUri
        )
    }

}

@Serializable
data class GoogleTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("id_token") val idToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("refresh_token") val refreshToken: String? = null
)

fun Google.Client.exchangeCodeForToken(code: String) = Async {
    val r = http.post("https://oauth2.googleapis.com/token") {
        contentType(ContentType.Application.FormUrlEncoded)
        setBody(
            FormDataContent(Parameters.build {
                append("code", code)
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("redirect_uri", redirectUri)
                append("grant_type", "authorization_code")
            })
        )
    }
    r.body<GoogleTokenResponse>()
}

@Serializable
data class GoogleUserInfo(
    val sub: String,
    val name: String,
    val given_name: String,
    val family_name: String,
    val picture: String,
    val email: String,
    val email_verified: Boolean,
    val locale: String? = null
)

fun Google.Client.fetchUserData(accessToken: String) = Async {
    http.get("https://www.googleapis.com/oauth2/v3/userinfo") {
        headers {
            append(HttpHeaders.Authorization, "Bearer $accessToken")
        }
    }.body<GoogleUserInfo>()
}