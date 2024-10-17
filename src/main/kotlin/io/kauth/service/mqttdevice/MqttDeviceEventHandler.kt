package io.kauth.service.mqttdevice

import io.kauth.client.eventStore.model.retrieveId
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackEventHandler
import io.kauth.monad.stack.sequential
import io.kauth.service.mqtt.MqttConnectorService
import io.kauth.service.mqtt.subscription.SubscriptionApi
import io.kauth.service.publisher.Publisher
import io.kauth.service.publisher.PublisherApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.*

object MqttDeviceEventHandler {

    val mqttEventHandler  = appStackEventHandler<MqttConnectorService.MqttData<JsonElement>>(
        streamName = "\$ce-mqtt",
        consumerGroup = "mqtt-device-event-handler",
    ) { event ->
        AppStack.Do {

            val topic = event.value.topic
            val subscriptionData = !SubscriptionApi.readState(topic) ?: return@Do
            val deviceId =
                UUID.fromString(subscriptionData.resource.retrieveId(MqttDeviceService.STREAM_NAME) ?: return@Do)
            val device = !MqttDeviceApi.readState(deviceId) ?: return@Do

            val message = event.value.message

            runCatching {
                val decodedMessage = serialization.decodeFromJsonElement<String>(message)
                when (topic) {
                    device.topics.status -> !MqttDeviceApi.setStatus(deviceId, decodedMessage)
                    device.topics.state -> !MqttDeviceApi.setState(deviceId, decodedMessage)
                    else -> {}
                }
            }

        }
    }

    val mqttDeviceEventHandler = appStackEventHandler<MqttDevice.Event>(
        streamName = "\$ce-${MqttDeviceService.STREAM_NAME}",
        consumerGroup = "device-event-consumer",
    ) { event ->
        AppStack.Do {

            if (event.value is MqttDevice.Event.Registered) {
                kotlin.runCatching {
                    val data = event.value.device.topics
                    !listOfNotNull(data.status, data.state, data.telemetry)
                        .map { SubscriptionApi.subscribeToTopic(it, event.streamName) }
                        .sequential()
                }
            }

            if (event.value is MqttDevice.Event.SendCommand) {
                !PublisherApi.publish(
                    event.id,
                    event.value.command,
                    event.streamName,
                    Publisher.Channel.Mqtt(topic = event.value.topic)
                )
            }

        }
    }

    val start = AppStack.Do {
        !mqttEventHandler
        !mqttDeviceEventHandler
    }

}