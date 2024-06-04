package io.kauth.service.salt

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackEventHandler
import io.kauth.service.mqtt.MqttConnectorService
import io.kauth.service.mqtt.subscription.Subscription
import io.kauth.service.mqtt.subscription.SubscriptionApi

object DeviceEventHandler {

    val mqttStateConsumer = appStackEventHandler<MqttConnectorService.MqttData<Device.Mqtt.SaltState>>(
        streamName = "\$ce-mqtt",
        consumerGroup = "device-mqtt-consumer",
    ) { event ->
        AppStack.Do {

            //hay que sacarle el mqtt
            val topic = event.streamName

            val subscriptionData = !SubscriptionApi.readState(topic) ?: return@Do

            //subscriptionData.resource --> Aca deberiamos tener el device-id
            //setear el SaltState

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
                    !SubscriptionApi.subscribe(
                        listOfNotNull(data?.status, data?.command, data?.state)
                            .map { Subscription.SubsData(topic = it, resource = event.streamName) }
                    )
                }
                else -> {}
            }
        }
    }

}