package io.kauth.service.iotdevice

import io.kauth.abstractions.forever
import io.kauth.abstractions.repeatForever
import io.kauth.client.eventStore.model.retrieveId
import io.kauth.client.pulsar.auth.MqAuth
import io.kauth.client.tuya.Tuya
import io.kauth.client.tuya.queryProperties
import io.kauth.client.tuya.sendCommand
import io.kauth.client.tuya.sendProperties
import io.kauth.monad.stack.*
import io.kauth.service.iotdevice.decryptor.decrypt
import io.kauth.service.iotdevice.model.iotdevice.CapabilitySchema
import io.kauth.service.iotdevice.model.iotdevice.Integration
import io.kauth.service.iotdevice.model.tuya.EncryptModel
import io.kauth.service.iotdevice.model.tuya.TuyaEvent
import io.kauth.service.iotdevice.model.tuya.TuyaPulsarMessage
import io.kauth.service.mqtt.MqttConnectorService
import io.kauth.service.mqtt.subscription.SubscriptionApi
import io.kauth.service.publisher.Publisher
import io.kauth.service.publisher.PublisherApi
import io.kauth.service.reservation.ReservationApi
import io.kauth.util.not
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.apache.pulsar.client.api.PulsarClient
import org.apache.pulsar.client.api.SubscriptionType
import java.util.*
import io.kauth.util.Async


object IoTDeviceEventHandler {
    //Pulsar event listener
    val pulsarEventHandler = AppStack.Do {

        val config = !findConfig<IotDeviceConfig>(IoTDeviceService.name) ?: return@Do

        val logger = !authStackLog
        val json = !authStackJson

        //Este cliente es una dependencia!
        val client = PulsarClient.builder()
            .serviceUrl(config.tuya.pulsarHost)
            .allowTlsInsecureConnection(true)
            .authentication(
                MqAuth(
                    config.tuya.clientId,
                    config.tuya.clientSecret

            ))
            .build()

        //Aca creo el consumer y me pongo a escuchar
        val consumer = client.newConsumer()
            .subscriptionName("${config.tuya.clientId}-sub")
            .topic(
                "${config.tuya.clientId}/out/event-test",
                "${config.tuya.clientId}/out/event"
            )
            .subscriptionType(SubscriptionType.Failover)
            .autoUpdatePartitions(false)
            .subscribeAsync()
            .await()

        ktor.launch {
            //TODO: Error handling
            while (isActive) {
                logger.info("Waiting for tuya events!")
                val message = consumer.receiveAsync().await()
                val encryptModel = message.getProperty("em")
                val tuyaMessage =
                    json.decodeFromString<TuyaPulsarMessage>(message.data.toString(charset = Charsets.UTF_8))
                val model = EncryptModel.fromString(encryptModel) ?: continue
                val data = model.decrypt(tuyaMessage.data, config.tuya.clientSecret.substring(8, 24))

                val tuyaEvent = json.decodeFromString<TuyaEvent>(data)

                val tuyaClient = !getService<Tuya.Client>()

                val devId = tuyaEvent.devId ?: tuyaEvent.bizData?.devId ?: error("Unknown tuya event")

                val status = !tuyaClient.queryProperties(devId)

                val iotDevice = !ReservationApi.readIfTaken("device-${devId}")

                if (iotDevice != null) {
                    //FIX THIS
                    try {
                        !IoTDeviceApi.setCapValues(
                            UUID.fromString(iotDevice),
                            status.result?.properties?.map { it.code to json.encodeToString(it.value) } ?: emptyList()
                        )
                    } catch (error: Throwable) {
                        logger.error("Error updating value !! $iotDevice, $status, ${error.message}")
                    }
                } else {
                    logger.error("IoT device id not found for device-${devId} ${iotDevice}")
                }
                consumer.acknowledgeAsync(message).await()
            }
            logger.info("DONE LISTENING")
        }

    }

    //MQTT event listener
    val mqttEventHandler = appStackEventHandler<MqttConnectorService.MqttData<JsonElement>>(
        streamName = "\$ce-mqtt",
        consumerGroup = "iotdevice-mqtt-event-handler",
    ) { event ->
        AppStack.Do {

            val topic = event.value.topic
            val subscriptionData = !SubscriptionApi.readState(topic) ?: return@Do
            val deviceId =
                UUID.fromString(subscriptionData.resource.retrieveId(IoTDeviceService.STREAM_NAME) ?: return@Do)
            val device = !IoTDeviceApi.Query.readState(deviceId) ?: return@Do
            val message = event.value.message

            val integration = device.integration

            if (integration !is Integration.Tasmota) return@Do

            runCatching {
                val decodedMessage = serialization.encodeToString(message)

                val topics =
                    integration.capsSchema.map { it.key }.plus(integration.topics.status).plus(integration.topics.state)

                if (topic in topics) {
                    !IoTDeviceApi.setCapValue(deviceId, topic, decodedMessage)
                }

            }

        }
    }

    val mqttDeviceEventHandler = appStackEventHandler<IoTDevice.Event>(
        streamName = "\$ce-${IoTDeviceService.STREAM_NAME}",
        consumerGroup = "iotdevice-event-consumer",
    ) { event ->
        AppStack.Do {

            val json = !authStackJson

            val id = event.retrieveId(IoTDeviceService.STREAM_NAME)?.let { UUID.fromString(it) } ?: return@Do
            val state = !IoTDeviceApi.Query.readState(id) ?: return@Do

            if (state.integration is Integration.Tuya) {
                //TODO usar publisher para tener idempotencia
                val tuyaClient = !getService<Tuya.Client>()
                val deviceId = state.integration.deviceId
                if (event.value is IoTDevice.Event.SendCommand) {
                    !tuyaClient.sendProperties(
                        deviceId,
                        event.value.commands.groupBy { it.uri }
                            .mapValues { json.decodeFromString(it.value.first().command) }
                    )
                }
            }


            if (state.integration is Integration.Tasmota) {

                if (event.value is IoTDevice.Event.Registered) {
                    val topics =
                        state.integration.capsSchema.entries.filter{ it.value.access == CapabilitySchema.Access.readonly }.map { it.key }
                            .plus(state.integration.topics.status)
                            .plus(state.integration.topics.state)
                    !topics
                        .map {
                            SubscriptionApi
                                .subscribeToTopic(it, event.streamName)
                                .catching()
                        }
                        .sequential()
                }


                if (event.value is IoTDevice.Event.SendCommand) {
                    //TODO: Aca podria usar el publisher
                    //El publisher podria tener batchSend
                    if (event.value.commands.isEmpty()) {
                        return@Do
                    }
                    val fst = event.value.commands.get(0)
                    !PublisherApi.publish(
                        messageId = event.id,
                        message = json.encodeToJsonElement(fst.command),
                        resource = event.streamName,
                        channel = Publisher.Channel.Mqtt(fst.uri)
                    )
                }


            }
        }
    }

    val start = AppStack.Do {
        !mqttEventHandler
        !mqttDeviceEventHandler
        !pulsarEventHandler
    }

}