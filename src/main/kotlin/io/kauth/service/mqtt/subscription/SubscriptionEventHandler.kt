package io.kauth.service.mqtt.subscription

import io.kauth.monad.stack.*
import io.kauth.service.mqtt.MqttConnectorService
import io.kauth.service.mqtt.subscription.Subscription.toMqttSubs
import io.kauth.service.mqtt.subscription.SubscriptionService.STREAM_NAME
import io.kauth.service.mqtt.subscription.SubscriptionService.TOPIC_STREAM_NAME
import io.kauth.util.not

object SubscriptionEventHandler {

    val topicListSubscription =
        appStackEventHandler<SubscriptionTopic.Event>(
            streamName = "\$ce-$TOPIC_STREAM_NAME",
            consumerGroup = "subscription-topic-event-consumer",
        ) { event ->
            AppStack.Do {

                val subs = !getService<SubscriptionService.Interface>()
                val topic = event.retrieveId(TOPIC_STREAM_NAME) ?: return@Do
                val state = !subs.query.readStateTopic(topic)

                when(event.value) {
                    is SubscriptionTopic.Event.Add -> {
                        state ?: return@Do
                        !SubscriptionApi.addTopic(
                            listOf(Subscription.SubsData(topic = topic, resource = state.resource))
                        )
                    }
                    is SubscriptionTopic.Event.Remove -> { !SubscriptionApi.removeTopic(topic) }
                    else -> {}
                }

            }
        }

    //INTERACTION WITH MQTT CLIENT
    val subscriptionHandler = appStackEventHandler<Subscription.Event>(
        streamName = "\$ce-$STREAM_NAME",
        consumerGroup = "subscription-event-consumer",
    ) { event ->
        AppStack.Do {
            val mqtt = !getService<MqttConnectorService.Interface>()
            when(event.value) {
                is Subscription.Event.Add -> {
                    val subscribedTopics = !mqtt.mqtt.subscribe(event.value.data.map { it.toMqttSubs })
                    !event.value.data
                        .filter { it.topic in subscribedTopics }
                        .map { SubscriptionApi.subscribedToTopic(it.topic) }
                        .sequential()
                }
                is Subscription.Event.Subscribe -> {
                    val topics = !SubscriptionApi.readState() ?: return@Do
                    val subscribedTopics = !mqtt.mqtt.subscribe(topics.data.map { it.toMqttSubs })
                    !topics.data
                        .filter { it.topic in subscribedTopics }
                        .map { SubscriptionApi.subscribedToTopic(it.topic) }
                        .sequential()
                }
                is Subscription.Event.Remove -> {
                    !mqtt.mqtt.unsubscribe(listOf(event.value.topic))
                }
            }
        }
    }

}