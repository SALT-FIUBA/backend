package io.kauth.client.brevo

import io.kauth.util.Async
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object Brevo {

    val baseUrl = "https://api.sendinblue.com/v3/"

    fun newClient(
        apiKey: String
    ): Async<Client> = Async {

        val http = HttpClient(CIO)
        val json = Json {
            ignoreUnknownKeys = true
        }

        Client(
            http = http,
            json = json,
            apiKey = apiKey,
        )

    }

    @Serializable
    data class BrevoRequest(
        val to: List<BrevoUser>,
        val sender: BrevoUser? = null,
        val subject: String? = null,
        val htmlContent: String? = null,
        val templateId: Long? = null,
        val params: Map<String, String>? = null,
        val headers: Map<String, String>? = null,
    )

    @Serializable
    data class BrevoUser(
        val name: String,
        val email: String
    )

    @Serializable
    data class BrevoResponse(
        val messageId: String,
    )

    data class Client(
        val http: HttpClient,
        val json: Json,
        val apiKey: String
    ) {

        fun request(
            uri: String,
            body: BrevoRequest
        ) = Async<BrevoResponse> {
            val response = http.post(baseUrl + uri) {
                this.headers {
                    append("api-key", apiKey)
                    append("Content-Type", "application/json;charset=UTF-8")
                }
                setBody(json.encodeToString(body))
            }
            json.decodeFromString<BrevoResponse>(response.bodyAsText())
        }

    }
}

fun Brevo.Client.sendEmail(
    to: List<Brevo.BrevoUser>,
    sender: Brevo.BrevoUser,
    subject: String,
    htmlContent: String
) = request(
    "smtp/email",
    Brevo.BrevoRequest(
        to = to,
        sender = sender,
        subject = subject,
        htmlContent = htmlContent
    )
)