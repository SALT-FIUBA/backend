package io.kauth.service.mqtt

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackSqlProjector
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

object MqttProjection {

    object MqttData: Table("mqtt_messages") {
        val id = text("id").uniqueIndex()
        val data = text("data")
        val topic = text("topic")
    }

    val sqlEventHandler = appStackSqlProjector<MqttConnectorService.MqttData<JsonElement>>(
        streamName = "\$ce-mqtt",
        consumerGroup = "mqtt-data-sql-projection",
        tables = listOf(MqttData)
    ) { event ->
        AppStack.Do {
            transaction(db) {
                MqttData.upsert() {
                    it[id] = event.value.idempotence.toString()
                    it[data] = serialization.encodeToString(event.value.data)
                    it[topic] = event.streamName
                }
            }
        }
    }

}