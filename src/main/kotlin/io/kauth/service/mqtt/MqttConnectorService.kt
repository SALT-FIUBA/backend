package io.kauth.service.mqtt

import MQTTClient
import io.kauth.abstractions.state.Var
import io.kauth.abstractions.state.varNew
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
import io.kauth.util.IO
import io.kauth.util.io
import io.kauth.util.not
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import mqtt.MQTTVersion
import mqtt.Subscription
import mqtt.packets.Qos
import mqtt.packets.mqtt.MQTTPublish
import mqtt.packets.mqttv5.ReasonCode
import mqtt.packets.mqttv5.SubscriptionOptions
import java.util.*
import io.kauth.service.mqtt.subscription.Subscription.SubsData

object MqttConnectorService : AppService {

    @Serializable
    data class MqttData<out T>(
        val data: T,
        @Serializable(with = UUIDSerializer::class)
        val idempotence: UUID
    )

    enum class StatusEvent {
        OFFLINE,
        ONLINE
    }

    data class Interface(
        val mqtt: MqttRequester,
    )

    val getConfig = AppStack.Do {
        Config(
            brokerAddress = "localhost",
            brokerPort = 1883,
            clientId = "salt",
            username = "mati",
            password = "1234".toByteArray().toUByteArray()
        )
    }

    data class Config(
        val brokerAddress: String,
        val brokerPort: Int,
        val clientId: String,
        val username: String,
        val password: UByteArray
    )

    @OptIn(ExperimentalUnsignedTypes::class)
    fun mqttClientNew(
        config: Config,
        json: Json,
        onPublish: (messaeg: MQTTPublish) -> Async<Unit>
    ) = Async {
        MQTTClient(
            MQTTVersion.MQTT5,
            config.brokerAddress,
            config.brokerPort,
            null,
            clientId = config.clientId,
            userName = config.username,
            password = config.password,
        ) { message -> !onPublish(message).io }
    }

    override val start =
        AppStack.Do {

            val log = !authStackLog
            val json = !authStackJson
            val config = !getConfig
            val client = !getService<EventStoreClient>()

            //Retry for ever?
            val mqtt = !mqttRequesterNew(
                mqttClientNew(config, json) { message ->
                    Async {
                        try {
                            val topicName = message.topicName.replace("/", "-")
                            val data = message.payload
                                ?.toByteArray()
                                ?.decodeToString()
                                ?.let { value -> json.decodeFromString<MqttData<JsonElement>>(value) }
                            if (data != null) {
                                !stream<MqttData<JsonElement>>(client, "mqtt-${topicName}")
                                    .append(data, StreamRevision.AnyRevision) { it.idempotence }
                            }
                        } catch (e: Throwable) {
                            log.error("MQTT subscription loop error", e)
                        }
                    }
                },
                json,
                ctx
            )

            !mqtt.connect()

            !MqttProjection.sqlEventHandler

            !registerService(
                Interface(
                    mqtt = mqtt,
                )
            )

            !SubscriptionService.start

            try {
                !SubscriptionApi.subscribe(listOf(SubsData(topic = "discovery/+/config", resource = "MqttService"))) // -> For discovery
            } catch (e: Throwable) {
                log.error("Error subscribing to discovery topic", e)
            }

            try {
                !SubscriptionApi.subscribeToTopics()
            } catch (e: Throwable) {
                log.error("Subscription error", e)
            }

        }
}