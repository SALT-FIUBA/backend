package io.kauth.service.mqtt.subscription

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.util.not
import io.kauth.monad.stack.*
import kotlinx.datetime.Clock

object SubscriptionApi {

    fun subscribeToAllTopics() = AppStack.Do {
        val service = !getService<SubscriptionService.Interface>()
        !service.command
            .handle()
            .throwOnFailureHandler(Subscription.Command.Subscribe)
    }

    fun addTopic(
        data: List<Subscription.SubsData>
    ) = AppStack.Do {
        val service = !getService<SubscriptionService.Interface>()
        !service.command
            .handle()
            .throwOnFailureHandler(Subscription.Command.Add(data))
    }

    fun removeTopic(
        data: String
    ) = AppStack.Do {
        val service = !getService<SubscriptionService.Interface>()
        !service.command
            .handle()
            .throwOnFailureHandler(Subscription.Command.Remove(data))
    }

    fun subscribeToTopic(
        topic: String,
        resource: String
    ) = AppStack.Do {
        val service = !getService<SubscriptionService.Interface>()
        !service.command
            .handleTopic(topic)
            .throwOnFailureHandler(SubscriptionTopic.Command.Add(resource))
    }

    fun subscribedToTopic(
        topic: String,
    ) = AppStack.Do {
        val service = !getService<SubscriptionService.Interface>()
        !service.command
            .handleTopic(topic)
            .throwOnFailureHandler(SubscriptionTopic.Command.Subscribed(Clock.System.now()))
    }

    fun unsubscribeToTopic(
        topic: String
    ) = AppStack.Do {
        val service = !getService<SubscriptionService.Interface>()
        !service.command
            .handleTopic(topic)
            .throwOnFailureHandler(SubscriptionTopic.Command.Remove)
    }

    fun readState() = AppStack.Do {
        val authService = !getService<SubscriptionService.Interface>()
        val topics = !authService.query.readState()
        topics
    }

    fun readState(topic: String) = AppStack.Do {
        val authService = !getService<SubscriptionService.Interface>()
       !authService.query.readStateTopic(topic)
    }

}