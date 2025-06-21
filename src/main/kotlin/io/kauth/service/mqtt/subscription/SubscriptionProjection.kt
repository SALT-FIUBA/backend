package io.kauth.service.mqtt.subscription

import io.kauth.monad.stack.*
import io.kauth.service.mqtt.subscription.SubscriptionService.TOPIC_STREAM_NAME
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert


object SubscriptionProjection {

    object MqttSubscriptions: Table("mqtt_subscriptions") {
        val topic = text("topic").uniqueIndex()
        val resource = text("resource")
        val createdAt = timestamp("created_at")
        val lastSubscriberAt = timestamp("last_subscribed_at").nullable()
    }

    val sqlEventHandler = appStackSqlProjectorNeon<SubscriptionTopic.Event>(
        streamName = "\$ce-${TOPIC_STREAM_NAME}",
        consumerGroup = "subscription-topic-sql-projection-consumer",
        tables = listOf(MqttSubscriptions)
    ) { event ->
        AppStack.Do {
            !appStackDbQueryNeon {
                val mqttTopic = event.retrieveId(TOPIC_STREAM_NAME) ?: return@appStackDbQueryNeon
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