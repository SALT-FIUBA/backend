package io.kauth.service.notification

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.exception.ApiException
import io.kauth.monad.apicall.ApiCall
import io.kauth.monad.apicall.apiCallGetService
import io.kauth.monad.apicall.toApiCall
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.getService
import io.kauth.service.notification.NotificationProjection.toNotificationProjection
import io.kauth.util.not
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

object NotificationApi {
    object Command {
        fun sendNotification(
            resource: String,
            id: UUID,
            channel: Notification.Channel,
            recipient: String,
            content: String,
            sender: String
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
                        createdAt = Clock.System.now(),
                        resource = resource
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
    object Query {
        fun readState(id: UUID) = AppStack.Do {
            val service = !getService<NotificationService.Interface>()
            !service.query.readState(id)
        }
        fun queryNotifications(
            resource: String? = null,
            channel: Notification.Channel? = null,
            recipient: String? = null,
            status: Notification.Status? = null
        ) = AppStack.Do {
            !appStackDbQuery {
                val query = NotificationProjection.NotificationTable.selectAll()
                    .let { if (resource != null) it.andWhere { NotificationProjection.NotificationTable.resource eq resource } else it }
                    .let { if (channel != null) it.andWhere { NotificationProjection.NotificationTable.channel eq channel.name } else it }
                    .let { if (recipient != null) it.andWhere { NotificationProjection.NotificationTable.recipient eq recipient } else it }
                    .let { if (status != null) it.andWhere { NotificationProjection.NotificationTable.status eq status.name } else it }
                query.map { it.toNotificationProjection }
            }
        }
    }
}
