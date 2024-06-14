package io.kauth.service.mqtt

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackSqlProjector
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.upsert
import java.util.UUID

object MqttProjection {

    object MqttData: Table("mqtt_messages") {
        val id = text("id").uniqueIndex()
        val data = text("data")
        val topic = text("topic")
    }

    val sqlEventHandler = appStackSqlProjector<MqttConnectorService.MqttMessage>(
        streamName = "\$ce-mqtt",
        consumerGroup = "mqtt-data-sql-projection",
        tables = listOf(MqttData)
    ) { event ->
        AppStack.Do {
            !appStackDbQuery {
                MqttData.upsert() {
                    it[id] = UUID.randomUUID().toString()
                    it[data] = serialization.encodeToString(event.value.message)
                    it[topic] = event.streamName
                }
            }
        }
    }

}