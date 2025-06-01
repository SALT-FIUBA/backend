package io.kauth.service.notification

import io.kauth.monad.apicall.KtorCall
import io.kauth.monad.apicall.runApiCall
import io.kauth.monad.apicall.toApiCall
import io.kauth.monad.stack.AppStack
import io.kauth.util.not
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

object NotificationApiRest {

    @Serializable
    data class SendNotificationRequest(
        @Contextual val id: UUID,
        val channel: Notification.Channel,
        val recipient: String,
        val content: String,
        val sender: String,
        @Contextual val createdAt: Instant
    )

    @Serializable
    data class SendResultRequest(
        @Contextual val id: UUID,
        @Contextual val sentAt: Instant?,
        val success: Boolean,
        val error: String? = null
    )

    val api = AppStack.Do {
        ktor.routing {
            route("notification") {
                post("/send") {
                    val request = call.receive<SendNotificationRequest>()
                    val result = !KtorCall(this@Do.ctx, call).runApiCall(
                        NotificationApi.Command.sendNotification(
                            id = request.id,
                            channel = request.channel,
                            recipient = request.recipient,
                            content = request.content,
                            sender = request.sender,
                            createdAt = request.createdAt
                        ).toApiCall()
                    )
                    call.respond(HttpStatusCode.Created, result)
                }
                post("/result") {
                    val request = call.receive<SendResultRequest>()
                    val result = !KtorCall(this@Do.ctx, call).runApiCall(
                        NotificationApi.Command.sendResult(
                            id = request.id,
                            sentAt = request.sentAt,
                            success = request.success,
                            error = request.error
                        ).toApiCall()
                    )
                    call.respond(HttpStatusCode.OK, result)
                }
            }
        }
    }
}

