package io.kauth.service.publisher

import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.client.eventStore.EventStoreClientPersistenceSubs
import io.kauth.client.eventStore.subscribeToStream
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.authStackLog
import io.kauth.monad.stack.getService
import io.kauth.service.mqtt.MqttConnectorService
import io.kauth.util.Async
import io.kauth.util.not
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.*


object PublisherEventHandler {


    val eventHandler = AppStack.Do {

        val log = !authStackLog

        val streamName = "\$ce-publisher"
        val consumerGroup = "publisher-event-consumer"

        /* TODO: Event Handler
            + Se encarga de guardar en la db los eventos
            + Se encarga de mandar eventos a una queue de integracion
         */

        val client = !getService<EventStoreClientPersistenceSubs>()


        with(log) {
            !client.subscribeToStream<Publisher.Event>(streamName, consumerGroup) { event ->
                Async {
                    val mqtt = !getService<MqttConnectorService.Interface>()

                    log.info(event.id.toString()) //esto te da idempotence
                    log.info(event.value.toString())

                    if(event.value is Publisher.Event.Publish) {

                        val topic = event.value.data.jsonObject["topic"]?.jsonPrimitive?.content ?: return@Async
                        val data = event.value.data.jsonObject["data"] ?: return@Async

                        try {

                            log.info("Send message to topic $topic")

                            !mqtt.mqtt.publish(
                                topic,
                                data
                            )

                            !PublisherApi.result(
                                idempotence = event.id,
                                id = UUID.fromString(event.retrieveId("publisher")),
                                result = "Ok"
                            )

                        } catch (e: Exception) {

                            !PublisherApi.result(
                                idempotence = event.id,
                                id = UUID.fromString(event.retrieveId("publisher")),
                                result = e.message ?: ""
                            )

                        }


                    }

                }
            }
        }

    }

}