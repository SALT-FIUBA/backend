package io.kauth.service.mqtt.subscription

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.util.not
import io.kauth.monad.stack.*

object SubscriptionApi {

    fun subscribeToTopics() = AppStack.Do {
        val service = !getService<SubscriptionService.Interface>()
        !service.command
            .handle()
            .throwOnFailureHandler(Subscription.Event.Subscribe)
    }

    fun subscribe(
        data: List<Subscription.SubsData>
    ) = AppStack.Do {
        val service = !getService<SubscriptionService.Interface>()
        !service.command
            .handle()
            .throwOnFailureHandler(Subscription.Event.Add(data))
    }

    fun unsubscribe(
        data: String
    ) = AppStack.Do {
        val service = !getService<SubscriptionService.Interface>()
        !service.command
            .handle()
            .throwOnFailureHandler(Subscription.Event.Remove(data))
    }

    fun readState() = AppStack.Do {
        val authService = !getService<SubscriptionService.Interface>()
        !authService.query.readState()
    }

    fun readState(topic: String) = AppStack.Do {
        val authService = !getService<SubscriptionService.Interface>()
        val response = !authService.query.readState()
        response?.data?.find { it.topic == topic }
    }

}