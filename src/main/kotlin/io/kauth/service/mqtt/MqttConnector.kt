package io.kauth.service.mqtt

import MQTTClient
import io.kauth.client.eventStore.EventStoreClient
import io.kauth.client.eventStore.append
import io.kauth.client.eventStore.model.StreamRevision
import io.kauth.client.eventStore.stream
import io.kauth.monad.stack.*
import io.kauth.service.AppService
import io.kauth.service.auth.Auth
import io.kauth.util.Async
import io.kauth.util.IO
import io.kauth.util.io
import io.kauth.util.not
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.whileSelect
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import mqtt.MQTTVersion
import mqtt.Subscription
import mqtt.packets.Qos
import mqtt.packets.mqttv5.ReasonCode
import mqtt.packets.mqttv5.SubscriptionOptions
import kotlin.time.Duration.Companion.seconds

object MqttConnector: AppService {

    @Serializable
    data class MqttConnectorData(
        val data: JsonElement
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

    override val name: String
        get() = "MqttConnectorService"

    override val start =
        AuthStack.Do {

            val log = !authStackLog
            val json = !authStackJson
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
            ) { it ->
                //TODO: Ver que ganaritas tenemos aca
                //Exactly once? idempotencia? Mensajes repetidos?
                val topicName = it.topicName.replace("/","-")
                val data = it.payload
                    ?.toByteArray()
                    ?.decodeToString()
                    ?.let { value -> json.decodeFromString<JsonElement>(value) }
                if(data != null) {
                    //TODO ver que hacer con la revision en este caso...
                    // Si corro este servicio de manera concurrente se me duplicarian los mensajes
                    !stream<MqttConnectorData>(client, "mqtt-connector-event-${topicName}").append(MqttConnectorData(data), StreamRevision.AnyRevision).io
                }
            }

            log.info("Mqtt client connected: ${mqtt.connackReceived}")

            val job = launch(Dispatchers.IO) {
                while(isActive && mqtt.running) {
                    mqtt.step()
                }
            }

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