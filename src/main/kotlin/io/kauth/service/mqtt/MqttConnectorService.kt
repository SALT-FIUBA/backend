package io.kauth.service.mqtt

import MQTTClient
import io.kauth.client.eventStore.EventStoreClient
import io.kauth.client.eventStore.append
import io.kauth.client.eventStore.model.StreamRevision
import io.kauth.client.eventStore.stream
import io.kauth.monad.stack.*
import io.kauth.service.AppService
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
import mqtt.packets.mqttv5.MQTT5Properties
import mqtt.packets.mqttv5.ReasonCode
import mqtt.packets.mqttv5.SubscriptionOptions
import java.util.*

object MqttConnectorService : AppService {

    @Serializable
    data class MqttConnectorData(
        val data: JsonElement,
        val packetId: UInt?
    ) {
        val sequenceId
            get() =
                try {
                    data.jsonObject["sequenceId"]?.jsonPrimitive?.contentOrNull
                } catch (e: Throwable) {
                    null
                }
    }

    data class MqttRequester(
        val serializable: Json,
        val mqtt: MQTTClient
    ) {

        @OptIn(ExperimentalUnsignedTypes::class)
        inline fun <reified T> publish(topic: String, data: T): Async<Unit> = Async {
            val payload = serializable.encodeToString(data).toByteArray().toUByteArray()
            mqtt.publish(
                qos = Qos.AT_LEAST_ONCE,
                payload = payload,
                retain = false,
                topic = topic
            )
            Unit
        }

    }

    data class Interface(
        val mqtt: MqttRequester,
        val subscribe: (subscriptions: List<Subscription>) -> IO<Unit>,
        val disconnect: Async<Unit>
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

    override val start =
        AppStack.Do {

            val log = !authStackLog
            val json = !authStackJson
            val config = !getConfig
            val client = !getService<EventStoreClient>()

            //Clients configs
            val mqtt = MQTTClient(
                MQTTVersion.MQTT5,
                config.brokerAddress,
                config.brokerPort,
                null,
                clientId = config.clientId,
                userName = config.username,
                password = config.password
            ) { message ->

                try {

                    val topicName = message.topicName.replace("/", "-")
                    val data = message.payload
                        ?.toByteArray()
                        ?.decodeToString()
                        ?.let { value -> json.decodeFromString<JsonElement>(value) }

                    if (data != null) {
                        val mqttData = MqttConnectorData(data, message.packetId)
                        !stream<MqttConnectorData>(client, "mqtt-connector-event-${topicName}")
                            .append(
                                mqttData,
                                StreamRevision.AnyRevision
                            ) { mqttConnectorData ->
                                mqttConnectorData.sequenceId?.let { UUID.fromString(it) } ?: UUID.randomUUID()
                            }
                            .io
                    }

                } catch (e: Throwable) {
                    log.error("MQTT subscription loop error", e)
                }


            }

            log.info("Mqtt client connected: ${mqtt.connackReceived}")

            val job = launch(Dispatchers.IO) {
                while (isActive && mqtt.running) {
                    mqtt.step()
                }
            }


            mqtt.subscribe(
                subscriptions = listOf(
                    Subscription(
                        topicFilter = "salt/command/#",
                        options = SubscriptionOptions(qos = Qos.AT_LEAST_ONCE)
                    ),
                    Subscription(
                        topicFilter = "salt/data/#",
                        options = SubscriptionOptions(qos = Qos.AT_LEAST_ONCE)
                    ),
                ),
            )

            !registerService(
                Interface(
                    mqtt = MqttRequester(serialization, mqtt),
                    subscribe = { subs -> IO { mqtt.subscribe(subs) } },
                    disconnect = Async {
                        mqtt.disconnect(ReasonCode.SUCCESS)
                        job.cancelAndJoin()
                    }
                )
            )

        }

}