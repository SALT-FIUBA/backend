package io.kauth.client.tuya

import io.kauth.abstractions.forever
import io.kauth.abstractions.repeatForever
import io.kauth.abstractions.state.varNew
import io.kauth.util.Async
import io.kauth.util.IO
import io.kauth.util.map
import io.kauth.util.not
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.seconds

object Tuya {

    data class Credentials(
        val access: String,
        val refresh: String
    )

    fun newClient(
        scope: CoroutineScope,
        clientId: String,
        clientSecret: String
    ): IO<Client> = IO {

        val state = !varNew<Credentials?>(null)

        val http = HttpClient(CIO)
        val json = Json {
            ignoreUnknownKeys = true
        }

        val client = Client(
            http = http,
            json = json,
            clientId = clientId,
            secret = clientSecret,
            dataCenter = TuyaDataCenter.WA,
            accessToken = null,
            refreshJob = null
        )

        val job = scope.launch {
            !repeatForever {
                val actual = !state.get
                val token = if (actual != null) {
                    !client.refreshToken(actual.refresh)
                } else {
                    !client.getToken()
                }
                if (!token.success) {
                    println("Error getting tuya token")
                    throw RuntimeException("Token Error")
                }
                if (token.result != null) {
                    !state.set(Credentials(token.result.accessToken, token.result.refreshToken))
                    val waitFor = (token.result.expireTime / 10).seconds
                    println("WAITING! ${waitFor}")
                    delay(waitFor)
                }
            }
        }

        Client(
            http = http,
            json = json,
            clientId = clientId,
            secret = clientSecret,
            dataCenter = TuyaDataCenter.WA,
            accessToken = state.get.map { it?.access },
            refreshJob = job
        )

    }

    data class Client(
        val accessToken: IO<String?>?,
        val http: HttpClient,
        val json: Json,
        val clientId: String,
        val secret: String,
        val dataCenter: TuyaDataCenter,
        val refreshJob: Job?
    ) {

        inline fun <reified I, reified O> request(
            method: HttpMethod,
            uri: String,
            noinline body: (() -> I)? = null
        ) = Async<TuyaApiResult<O>> {

            val timestamp = Clock.System.now().toEpochMilliseconds()

            val stringBody = if (body != null) json.encodeToString(body()) else null

            val stringToSign = listOf(
                method.value,
                generateSHA256Hash(stringBody?:""),
                "",
                uri
            )

            val tokenApi = uri.contains("token")
            val sign = generateTuyaSignature(
                stringToSign.joinToString("\n"),
                clientId,
                secret,
                timestamp,
                if (tokenApi) null else accessToken?.let { it() }
            )

            val response = http.request(dataCenter.url + uri) {
                val token = accessToken?.let { it() }
                this.method = method
                this.headers {
                    append("t", timestamp.toString())
                    append("client_id", clientId)
                    append("stringToSign", stringToSign.joinToString(""))
                    append("sign", sign)
                    append("sign_method", "HMAC-SHA256")
                    if (stringBody != null) {
                        append("Content-Type", "application/json;charset=UTF-8")
                    }
                    if (!tokenApi) append("access_token", token ?: "")

                }
                if (stringBody != null) {
                    setBody(stringBody)
                }
            }

            val raw = response.bodyAsText()

            json.decodeFromString<TuyaApiResult<O>>(raw)

        }

    }

    @OptIn(ExperimentalStdlibApi::class)
    fun generateTuyaSignature(
        stringToSign: String,
        clientId: String,
        secret: String,
        timestamp: Long,
        accessToken: String?
    ): String {
        val message = clientId + (if(accessToken != null) accessToken else "") + timestamp + stringToSign
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val hash = mac.doFinal(message.toByteArray(Charsets.UTF_8));
        return hash.toHexString(format = HexFormat.UpperCase)
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun generateSHA256Hash(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray())
        return hashBytes.toHexString()
    }


}