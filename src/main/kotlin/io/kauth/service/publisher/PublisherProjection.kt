package io.kauth.service.publisher

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackSqlProjector
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.sql.Table
import java.util.*
import io.kauth.service.publisher.Publisher.Channel.Mqtt
import io.kauth.service.publisher.Publisher.Event
import io.kauth.service.salt.DeviceProjection.DeviceTable
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.upsert

object PublisherProjection {

    object Publisher: Table() {
        val id = text("id").uniqueIndex()
        val data = text("message")
        val resource = text("resource")
        val channel = text("channel")
        val mqttTopic = text("mqtt_topic").nullable()
        val resultSuccess = text("result_success").nullable()
        val resultError = text("result_error").nullable()
    }

    @Serializable
    data class Projection(
        val id: String,
        val data: String,
        val resource: String,
        val channel: String,
        val mqttTopic: String?,
        val resultSuccess: String?,
        val resultError: String?
    )

    val ResultRow.toPublisherProjection get() =
        Projection(
            this[Publisher.id],
            this[Publisher.data],
            this[Publisher.resource],
            this[Publisher.channel],
            this[Publisher.mqttTopic],
            this[Publisher.resultSuccess],
            this[Publisher.resultError],
        )

    val sqlEventHandler = appStackSqlProjector<Event>(
        streamName = "\$ce-publisher",
        consumerGroup = "publisher-sql-projection",
        tables = listOf(Publisher)
    ) { event ->
        AppStack.Do {
            val publishId = UUID.fromString(event.retrieveId("publisher"))
            val state = !PublisherApi.readState(publishId) ?: return@Do
            !appStackDbQuery {
                Publisher.upsert() {
                    it[id] = publishId.toString()
                    it[data] = serialization.encodeToString(state.data)
                    it[resource] = state.resource
                    it[channel] = when(state.channel) {
                        is Mqtt -> "mqtt"
                    }
                    it[mqttTopic] = (state.channel as Mqtt?)?.topic
                    it[resultSuccess] = state.result?.data
                    it[resultError] = state.result?.error
                }
            }

        }
    }

}