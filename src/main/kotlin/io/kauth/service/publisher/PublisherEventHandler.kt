package io.kauth.service.publisher

import io.kauth.abstractions.result.AppResult
import io.kauth.client.eventStore.EventStoreClientPersistenceSubs
import io.kauth.client.eventStore.subscribeToStream
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.authStackLog
import io.kauth.monad.stack.getService
import io.kauth.service.mqtt.MqttConnectorService
import io.kauth.util.Async
import io.kauth.util.not
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*


object PublisherEventHandler {

    val eventHandler = AppStack.Do {

        val log = !authStackLog

        val streamName = "\$ce-publisher"
        val consumerGroup = "publisher-event-consumer"

        val client = !getService<EventStoreClientPersistenceSubs>()

        !client.subscribeToStream<Publisher.Event>(streamName, consumerGroup) { event ->
            Async {
                if (event.value is Publisher.Event.Publish) {

                    val mqtt = !getService<MqttConnectorService.Interface>()
                    val publishId = UUID.fromString(event.retrieveId("publisher"))

                    val data = event.value.data

                    val result = try {

                        val state = !PublisherApi.readState(publishId)
                        val topic = event.value.channel as Publisher.Channel.Mqtt

                        if (state?.result?.data != null) {
                            log.info("Already published $publishId")
                            return@Async
                        }

                        log.info("Publishing message")

                        !mqtt.mqtt.publish(topic.topic, data, publishId)

                        AppResult("Ok")

                    } catch (e: Exception) {
                        log.error("Publish error", e)
                        AppResult(data = null, error = e.message ?: "error")
                    }

                    !PublisherApi.result(
                        idempotence = event.id,
                        id = publishId,
                        result = result
                    )

                }

            }
        }

    }

}