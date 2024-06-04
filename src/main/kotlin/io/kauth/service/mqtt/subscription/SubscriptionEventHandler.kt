package io.kauth.service.mqtt.subscription

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackEventHandler
import io.kauth.monad.stack.getService
import io.kauth.service.mqtt.MqttConnectorService
import io.kauth.service.mqtt.subscription.Subscription.toMqttSubs
import io.kauth.util.not

object SubscriptionEventHandler {

    val subscriptionHandler = appStackEventHandler<Subscription.Event>(
        streamName = "\$ce-subscription",
        consumerGroup = "subscription-event-consumer",
    ) { event ->
        AppStack.Do {
            val mqtt = !getService<MqttConnectorService.Interface>()
            when(event.value) {
                is Subscription.Event.Add -> {
                    !mqtt.mqtt.subscribe(event.value.data.map { it.toMqttSubs })
                }
                is Subscription.Event.Subscribe -> {
                    val topics = !SubscriptionApi.readState() ?: return@Do
                    !mqtt.mqtt.subscribe(topics.data.map { it.toMqttSubs })
                }
                is Subscription.Event.Remove -> {
                    //TODO Unsubscribe
                }
            }
        }
    }

}