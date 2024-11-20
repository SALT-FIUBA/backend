package io.kauth.service.iotdevice

import io.kauth.client.eventStore.model.retrieveId
import io.kauth.client.pulsar.auth.MqAuth
import io.kauth.client.tuya.Tuya
import io.kauth.client.tuya.sendCommand
import io.kauth.monad.stack.*
import io.kauth.service.iotdevice.decryptor.decrypt
import io.kauth.service.iotdevice.model.iotdevice.CapabilitySchema
import io.kauth.service.iotdevice.model.iotdevice.Integration
import io.kauth.service.iotdevice.model.tuya.EncryptModel
import io.kauth.service.iotdevice.model.tuya.TuyaPulsarMessage
import io.kauth.service.mqtt.MqttConnectorService
import io.kauth.service.mqtt.subscription.SubscriptionApi
import io.kauth.service.publisher.Publisher
import io.kauth.service.publisher.PublisherApi
import io.kauth.util.not
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.apache.pulsar.client.api.PulsarClient
import org.apache.pulsar.client.api.SubscriptionType
import java.util.*


object IoTDeviceEventHandler {
    //Pulsar event listener
    val pulsarEventHandler = AppStack.Do {
        val logger = !authStackLog
        val json = !authStackJson

        val accessKey = "fd5b028d05b949038a9fd109b45ca534"

        //Este cliente es una dependencia!
        val client = PulsarClient.builder()
            .serviceUrl("pulsar+ssl://mqe.tuyaus.com:7285/")
            .allowTlsInsecureConnection(true)
            .authentication(
                MqAuth(
                    "gh9cknxh5ryxferdnrkc",
                    accessKey

            ))
            .build()

        //Aca creo el consumer y me pongo a escuchar
        val consumer = client.newConsumer()
            .subscriptionName("gh9cknxh5ryxferdnrkc-sub")
            .topic("gh9cknxh5ryxferdnrkc/out/event-test")
            .subscriptionType(SubscriptionType.Failover)
            .autoUpdatePartitions(false)
            .subscribeAsync()
            .await()

        ktor.launch {
            while (isActive) {
                logger.info("LISTENING!")
                val message = consumer.receiveAsync().await()
                //aca voy a tener un tuyaDeviceId y voy a tener que buscar el device
                //tonces tengo que tener un index tuyaDevice -> deviceId
                //ReservationApi.readState("device-${tuyaDeviceId}")
                val encryptModel = message.getProperty("em")
                val tuyaMessage = json.decodeFromString<TuyaPulsarMessage>(message.data.toString(charset = Charsets.UTF_8))
                val model = EncryptModel.fromString(encryptModel) ?: break
                val data = model.decrypt(tuyaMessage.data, accessKey.substring(8, 24))
                logger.info(data)
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
                val tuyaClient = !getService<Tuya.Client>()
                val deviceId = state.integration.deviceId
                if (event.value is IoTDevice.Event.SendCommand) {
                    //TODO: Aca podria usar el publisher
                    !tuyaClient.sendCommand(
                        deviceId,
                        event.value.uri,
                        json.decodeFromString(event.value.command)
                    )
                }
            }


            if (state.integration is Integration.Tasmota) {

                if (event.value is IoTDevice.Event.Registered) {
                    val topics =
                        state.integration.capsSchema.filter { it.permission == CapabilitySchema.Permission.read }.map { it.key }
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
                    !PublisherApi.publish(
                        messageId = event.id,
                        message = json.encodeToJsonElement(event.value.command),
                        resource = event.streamName,
                        channel = Publisher.Channel.Mqtt(event.value.uri)
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