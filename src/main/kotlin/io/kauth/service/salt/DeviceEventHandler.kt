package io.kauth.service.salt

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackEventHandler
import io.kauth.monad.stack.sequential
import io.kauth.service.mqtt.MqttConnectorService
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

            //que pasa si no existe el device ? lo podemos mandar al servicio de discovery
            //necesitamos un servicio de discovery cq apriori no sabemos el organism del device
            //un administardor deberia asignarle uno y asi crearlo...

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
                    !listOfNotNull(data?.status, data?.command, data?.state)
                        .map { SubscriptionApi.subscribeToTopic(it, event.streamName) }
                        .sequential()
                }
                else -> {}
            }
        }
    }

}