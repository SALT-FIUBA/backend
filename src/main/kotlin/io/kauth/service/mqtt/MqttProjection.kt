package io.kauth.service.mqtt

import io.kauth.monad.stack.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert

object MqttProjection {

    object MqttMessages: Table("mqtt_messages") {
        val id = text("id").uniqueIndex()
        val data = text("data")
        val topic = text("topic")
        val timestamp = timestamp("timestamp")
    }

    @Serializable
    data class Projection(
        val id: String,
        val data: String,
        val topic: String,
        val timestamp: Instant
    )

    val ResultRow.toMqttDataProjection get() =
        Projection(
            this[MqttMessages.id],
            this[MqttMessages.data],
            this[MqttMessages.topic],
            this[MqttMessages.timestamp],
        )

    val sqlEventHandler = appStackSqlProjectorNeon<MqttConnectorService.MqttData<JsonElement>>(
        streamName = "\$ce-mqtt",
        consumerGroup = "mqtt-data-sql-projection",
        tables = listOf(MqttMessages)
    ) { event ->
        AppStack.Do {
            !appStackDbQueryNeon {
                MqttMessages.upsert() {
                    it[id] = event.value.idempotence?.toString() ?: event.id.toString()
                    it[data] = serialization.encodeToString(event.value.message)
                    it[topic] = event.streamName
                    it[timestamp] = event.metadata.timestamp
                }
            }
        }
    }

}