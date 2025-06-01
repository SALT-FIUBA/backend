package io.kauth.service.notification

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackSqlProjector
import io.kauth.service.occasion.OccasionService
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert
import java.util.*

object NotificationProjection {
    object NotificationTable : Table("notifications") {
        val id = text("id").uniqueIndex()
        val resource = text("resource")
        val channel = text("channel")
        val recipient = text("recipient")
        val content = text("content")
        val sender = text("sender")
        val status = text("status")
        val createdAt = timestamp("created_at")
        val sentAt = timestamp("sent_at").nullable()
        val error = text("error").nullable()
    }

    @Serializable
    data class NotificationProjection(
        val resource: String,
        val channel: String,
        val recipient: String,
        val content: String,
        val sender: String,
        val status: String,
        val createdAt: Instant,
        val sentAt: Instant? = null,
        val error: String? = null
    )

    val ResultRow.toNotificationProjection get() = NotificationProjection(
        resource = this[NotificationTable.resource],
        channel = this[NotificationTable.channel],
        recipient = this[NotificationTable.recipient],
        content = this[NotificationTable.content],
        sender = this[NotificationTable.sender],
        status = this[NotificationTable.status],
        createdAt = this[NotificationTable.createdAt],
        sentAt = this[NotificationTable.sentAt],
        error = this[NotificationTable.error]
    )

    val sqlEventHandler = appStackSqlProjector<Notification.Event>(
        streamName = "\$ce-${NotificationService.name}",
        consumerGroup = "${NotificationService.name}-sql-projection",
        tables = listOf(NotificationTable)
    ) { event ->
        AppStack.Do {
            val id = UUID.fromString(event.retrieveId(NotificationService.name))
            val state = !NotificationApi.Query.readState(id) ?: return@Do
            !appStackDbQuery {
                NotificationTable.upsert {
                    it[NotificationTable.id] = id.toString()
                    it[resource] = state.resource
                    it[channel] = state.channel.name
                    it[recipient] = state.recipient
                    it[content] = state.content
                    it[sender] = state.sender
                    it[status] = state.status.name
                    it[createdAt] = state.createdAt
                    it[sentAt] = state.sentAt
                    it[error] = state.error
                }
            }
        }
    }
}

