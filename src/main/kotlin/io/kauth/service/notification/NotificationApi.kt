package io.kauth.service.notification

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.exception.ApiException
import io.kauth.monad.apicall.ApiCall
import io.kauth.monad.apicall.apiCallGetService
import io.kauth.monad.apicall.toApiCall
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.getService
import kotlinx.datetime.Instant
import java.util.UUID

object NotificationApi {
    object Command {
        fun sendNotification(
            id: UUID,
            channel: Notification.Channel,
            recipient: String,
            content: String,
            sender: String,
            createdAt: Instant
        ) = AppStack.Do {
            val service = !getService<NotificationService.Interface>()
            !service.command
                .handle(id)
                .throwOnFailureHandler(
                    Notification.Command.SendNotification(
                        channel = channel,
                        recipient = recipient,
                        content = content,
                        sender = sender,
                        createdAt = createdAt
                    )
                )
            id.toString()
        }
        fun sendResult(
            id: UUID,
            sentAt: Instant?,
            success: Boolean,
            error: String? = null
        ) = AppStack.Do {
            val service = !getService<NotificationService.Interface>()
            !service.command
                .handle(id)
                .throwOnFailureHandler(
                    Notification.Command.SendResult(
                        sentAt = sentAt,
                        success = success,
                        error = error
                    )
                )
            id.toString()
        }
    }
}

