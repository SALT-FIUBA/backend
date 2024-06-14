package io.kauth.service.publisher

import io.kauth.abstractions.result.AppResult
import io.kauth.client.eventStore.model.Event
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackEventHandler
import io.kauth.monad.stack.authStackLog
import io.kauth.monad.stack.getService
import io.kauth.service.mqtt.MqttConnectorService
import io.kauth.util.not
import java.util.*

object PublisherEventHandler {

    val mqttEventHandler = appStackEventHandler<Publisher.Event>(
        streamName = "\$ce-publisher",
        consumerGroup = "publisher-event-consumer",
    ) { event ->
        when (event.value) {
            is Publisher.Event.Publish -> handlePublishEvent(event as Event<Publisher.Event.Publish>)
            is Publisher.Event.PublishResult -> AppStack.Do { }
        }
    }

    private fun handlePublishEvent(event: Event<Publisher.Event.Publish>) = AppStack.Do {

        val mqtt = !getService<MqttConnectorService.Interface>()
        val log = !authStackLog
        val publishId = UUID.fromString(event.retrieveId("publisher"))

        val result = try {

            val state = !PublisherApi.readState(publishId)

            val topic = event.value.channel as Publisher.Channel.Mqtt

            if (state?.result?.data != null) {
                log.info("Already published $publishId")
                return@Do
            }

            log.info("Publishing message")

            //SI aca te trabas trabas la lectura de los otros topics
            !mqtt.mqtt.publish(topic.topic, event.value.data)

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