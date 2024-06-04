package io.kauth.service.mqtt.subscription

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackSqlProjector
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert
import java.util.*


object SubscriptionProjection {

    object MqttSubscriptions: Table("mqtt_subscriptions") {
        val topic = text("topic").uniqueIndex()
        val resource = text("resource")
        val createdAt = timestamp("created_at")
    }

    val sqlEventHandler = appStackSqlProjector<Subscription.Event>(
        streamName = "\$ce-${SubscriptionService.STREAM_NAME}",
        consumerGroup = "subscription-sql-projection-consumer",
        tables = listOf(MqttSubscriptions)
    ) { event ->
        AppStack.Do {
            when(event.value) {
                is Subscription.Event.Add -> {
                    !appStackDbQuery {
                        event.value.data.forEach { data ->
                            MqttSubscriptions.upsert() {
                                it[topic] = data.topic
                                it[createdAt] = event.metadata.timestamp
                                it[resource] = data.resource
                            }
                        }
                    }
                }
                else -> {

                }
            }

        }
    }

}