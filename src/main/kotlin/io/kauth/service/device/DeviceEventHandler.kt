package io.kauth.service.device

import io.kauth.client.eventStore.EventStoreClientPersistenceSubs
import io.kauth.client.eventStore.subscribeToStream
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.authStackLog
import io.kauth.monad.stack.getService
import io.kauth.service.mqtt.MqttConnectorService
import io.kauth.util.Async
import io.kauth.util.not

object DeviceEventHandler {

    //TODO: Colgarse EN ORDEN! de los eventosMqtt y setear el esttado actual
    //mqtt-device-data-mac o algo asi

    val eventHandler = AppStack.Do {

        val log = !authStackLog

        val streamName = "\$ce-mqtt"
        val consumerGroup = "device-event-consumer"

        /* TODO: Event Handler
            + Se encarga de guardar en la db los eventos
            + Se encarga de mandar eventos a una queue de integracion
         */

        val client = !getService<EventStoreClientPersistenceSubs>()

        with(log) {
            !client.subscribeToStream<MqttConnectorService.MqttConnectorData>(streamName, consumerGroup) { event ->
                Async {

                    //Decode data
                    //Retrieve deviceId
                    //readState
                    //updateStatus

                    log.info(event.id.toString()) //esto te da idempotence
                    log.info(event.value.toString())

                }
            }
        }

    }

}