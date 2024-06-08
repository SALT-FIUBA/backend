package io.kauth.service.mqtt.subscription

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackEventHandler
import io.kauth.monad.stack.appStackSqlProjector
import io.kauth.service.mqtt.subscription.SubscriptionService.STREAM_NAME
import io.kauth.service.mqtt.subscription.SubscriptionService.TOPIC_STREAM_NAME
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.json.json
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert


object SubscriptionProjection {

    object MqttSubscriptions: Table("mqtt_subscriptions") {
        val topic = text("topic").uniqueIndex()
        val resource = text("resource")
        val createdAt = timestamp("created_at")
        val lastSubscriberAt = timestamp("last_subscribed_at").nullable()
    }

    val sqlEventHandler = appStackSqlProjector<SubscriptionTopic.Event>(
        streamName = "\$ce-${TOPIC_STREAM_NAME}",
        consumerGroup = "subscription-topic-sql-projection-consumer",
        tables = listOf(MqttSubscriptions)
    ) { event ->
        AppStack.Do {
            !appStackDbQuery {
                val mqttTopic = event.retrieveId(TOPIC_STREAM_NAME) ?: return@appStackDbQuery
                val state = !SubscriptionApi.readState(mqttTopic)

                if(state == null) {
                    MqttSubscriptions.deleteWhere() {
                        topic.eq(mqttTopic)
                    }
                } else {
                    MqttSubscriptions.upsert() {
                        it[topic] = mqttTopic
                        //TODO@Mati: CreatedAt
                        it[createdAt] = state.createdAt ?: event.metadata.timestamp
                        it[resource] = state.resource
                        it[lastSubscriberAt] = state.lastSubscribedAt
                    }
                }


            }
        }
    }

}