package io.kauth.service.mqtt

import io.kauth.client.eventStore.EventStoreClient
import io.kauth.client.eventStore.append
import io.kauth.client.eventStore.model.StreamRevision
import io.kauth.client.eventStore.stream
import io.kauth.monad.stack.*
import io.kauth.serializer.UUIDSerializer
import io.kauth.service.AppService
import io.kauth.service.mqtt.subscription.SubscriptionApi
import io.kauth.service.mqtt.subscription.SubscriptionService
import io.kauth.util.Async
import io.kauth.util.not
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.*


object MqttConnectorService : AppService {

    val STREAM_NAME = "mqtt"

    //pensar esto
    @Serializable
    data class MqttData<out T>(
        val topic: String = "",
        val message: T,
        @Serializable(with = UUIDSerializer::class)
        val idempotence: UUID? = null
    )

    data class Interface(
        val mqtt: MqttRequesterHiveMQ,
    )

    val getConfig = AppStack.Do {
        Config(
            brokerAddress = "007f5e0286aa4c36ba410312d36d42f0.s1.eu.hivemq.cloud",
            brokerPort = 8883,
            clientId = "tasmota",
            username = "tasmota",
            password = "Password123"
        )
    }

    data class Config(
        val brokerAddress: String,
        val brokerPort: Int,
        val clientId: String,
        val username: String,
        val password: String
    )

    override val start =
        AppStack.Do {

            val log = !authStackLog
            val json = !authStackJson
            val config = !getConfig
            val client = !getService<EventStoreClient>()

            val mqtt = !mqttRequesterHiveMQNew(
                config.username,
                config.password,
                config.brokerAddress,
                config.brokerPort,
                json,
                ctx
            ) { message ->
                Async {
                    try {
                        val topicName = message.topic.toString()
                        val data = message.payloadAsBytes
                            .decodeToString()
                            .let { value -> json.decodeFromString<JsonElement>(value) }
                        !stream<MqttData<JsonElement>>(client, "$STREAM_NAME-${topicName}")
                            .append(MqttData(topicName, data), StreamRevision.AnyRevision)
                    } catch (e: Throwable) {
                        log.error("MQTT subscription loop error", e)
                    }
                }
            }

            !MqttProjection.sqlEventHandler

            !registerService(
                Interface(
                    mqtt = mqtt,
                )
            )

            !MqttConnectorApiRest.api

            !SubscriptionService.start

            try {
                val subscription = !SubscriptionApi.readState("discovery/+/config")
                if(subscription == null) {
                    !SubscriptionApi.subscribeToTopic(topic = "discovery/+/config", resource = "MqttService") // -> For discovery
                }
            } catch (e: Throwable) {
                log.debug("Error subscribing to discovery topic", e)
            }

            try {
                !SubscriptionApi.subscribeToAllTopics()
            } catch (e: Throwable) {
                log.debug("Subscription error", e)
            }

        }
}