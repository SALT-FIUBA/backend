package io.kauth.service.salt

import io.kauth.client.eventStore.model.retrieveId
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackEventHandler
import io.kauth.monad.stack.sequential
import io.kauth.service.mqtt.MqttConnectorService
import io.kauth.service.mqtt.subscription.SubscriptionApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

object DeviceEventHandler {

    val mqttConsumer  = appStackEventHandler<MqttConnectorService.MqttData<JsonElement>>(
        streamName = "\$ce-mqtt",
        consumerGroup = "device-state-mqtt-consumer",
    ) { event ->
        AppStack.Do {

            val topic = event.retrieveId(MqttConnectorService.STREAM_NAME) ?: return@Do
            val subscriptionData = !SubscriptionApi.readState(topic) ?: return@Do
            val deviceId = UUID.fromString(subscriptionData.resource.retrieveId(DeviceService.STREAM_NAME) ?: return@Do)
            val device = !DeviceApi.Query.readState(deviceId)

            val message = event.value.message

            runCatching {
                if(topic == device?.topics?.status && message.jsonPrimitive.isString) {
                    !DeviceApi.setStatus(deviceId, message.jsonPrimitive.content)
                } else if (topic == device?.topics?.state) {
                    val state = serialization.decodeFromJsonElement<Device.Mqtt.SaltState>(message)
                    println(state)
                } else {

                }
            }

        }
    }

    val deviceTopicSubscriptionHandler = appStackEventHandler<Device.Event>(
        streamName = "\$ce-${DeviceService.STREAM_NAME}",
        consumerGroup = "device-event-consumer",
    ) { event ->
        AppStack.Do {
            when(event.value) {
                is Device.Event.Created -> {
                    val data = event.value.device.topics
                    !listOfNotNull(data?.status, data?.command, data?.state)
                        .map { SubscriptionApi.subscribeToTopic(it, event.streamName) }
                        .sequential()
                }
                else -> {}
            }
        }
    }

}