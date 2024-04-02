package io.kauth.service.mqtt

import MQTTClient
import io.kauth.client.eventStore.EventStoreClient
import io.kauth.client.eventStore.append
import io.kauth.client.eventStore.model.StreamRevision
import io.kauth.client.eventStore.stream
import io.kauth.monad.stack.*
import io.kauth.service.AppService
import io.kauth.service.inboxPattern.InboxPatternService
import io.kauth.util.Async
import io.kauth.util.IO
import io.kauth.util.io
import io.kauth.util.not
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import mqtt.MQTTVersion
import mqtt.Subscription
import mqtt.packets.Qos
import mqtt.packets.mqttv5.ReasonCode
import mqtt.packets.mqttv5.SubscriptionOptions
import kotlin.time.Duration.Companion.seconds

object MqttConnectorService: AppService {

    @Serializable
    data class MqttConnectorData(
        val data: JsonElement,
        val packetId: UInt?
    )

    data class Interface(
        val subscribe: (subscriptions: List<Subscription>) -> IO<Unit>,
        val disconnect: Async<Unit>
    )

    data class Config(
        val brokerAddress: String,
        val brokerPort: Integer
    )

    val getConfig = AuthStack.Do {
        val ktor = !authStackKtor
        //TODO
    }

        override val start =
        AuthStack.Do {

            val log = !authStackLog
            val json = !authStackJson
            val inputPatterService = !getService<InboxPatternService.Interface>()
            val client = !getService<EventStoreClient>()

            //Clients configs
            val mqtt = MQTTClient(
                MQTTVersion.MQTT5,
                "localhost",
                1883,
                null,
                clientId = "salt",
                userName = "mati",
                password = "1234".toByteArray().toUByteArray()
            ) { message ->


                val topicName = message.topicName.replace("/", "-")
                val data = message.payload
                    ?.toByteArray()
                    ?.decodeToString()
                    ?.let { value -> json.decodeFromString<JsonElement>(value) }

                if (data != null) {

                    val sequenceId =
                        try {
                            data.jsonObject["sequenceId"]?.jsonPrimitive?.contentOrNull
                        } catch (e: Throwable) {
                            null
                        }

                    val mqttData = MqttConnectorData(data, message.packetId)

                    val append = stream<MqttConnectorData>(client, "mqtt-connector-event-${topicName}")
                        .append(mqttData, StreamRevision.AnyRevision)

                    if (sequenceId != null) {
                        val result = !with(inputPatterService) { append.idempotency(sequenceId) }.io
                        //TODO: if error ver que hacer? ack?
                        log.info("Append result $result")
                    } else {
                        !append.io
                    }
                }
            }

            log.info("Mqtt client connected: ${mqtt.connackReceived}")

            val job = launch(Dispatchers.IO) {
                while(isActive && mqtt.running) {
                    mqtt.step()
                }
            }

            launch(Dispatchers.IO) {
                while (isActive) {
                    delay(10.seconds)
                    log.info("Sending message.....")
                    mqtt.publish(false, Qos.AT_LEAST_ONCE, "test/topic", """ { "sequenceId": "12347", "data": "chau" } """.toByteArray().toUByteArray())
                    log.info("....Ok")
                }
            }

            mqtt.subscribe(listOf(Subscription("test/topic", SubscriptionOptions(Qos.AT_LEAST_ONCE))))

            !registerService(
                Interface(
                    subscribe = { subs -> IO { mqtt.subscribe(subs) } },
                    disconnect = Async {
                        mqtt.disconnect(ReasonCode.SUCCESS)
                        job.cancelAndJoin()
                    }
                )
            )

        }

}