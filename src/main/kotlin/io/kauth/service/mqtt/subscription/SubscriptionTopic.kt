package io.kauth.service.mqtt.subscription

import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.StateMonad
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

//TODO esto tambien te sirve de RELACION TOPIC-RESOURCE
//PENSAR BIEN COMO HACER PARA OBTENER TODOS
object SubscriptionTopic {

    @Serializable
    data class State(
        val resource: String,
        val createdAt: Instant? = null,
        val lastSubscribedAt: Instant? = null
    )

    @Serializable
    sealed interface Event {
        @Serializable
        data class Subscribed(val at: Instant): Event
        @Serializable
        data class Add(
            val resource: String,
            val createdAt: Instant? = null
        ): Event
        @Serializable
        data object Remove: Event
    }

    fun handleAdd(
        command: Event.Add
    ) = StateMonad.Do<State?, Event, Output> { exit ->

        val state = !getState

        if(state != null) {
            !exit(Failure("Already subscribed"))
        }

        !setState(
            State(
                resource = command.resource,
                createdAt = command.createdAt
            )
        )

        !emitEvents(command)

        Ok
    }

    fun handleSubscribed(
        command: Event.Subscribed
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        val state = !getState ?: !exit(Failure("Topic subscription does not exists"))
        !setState(state.copy(lastSubscribedAt = command.at))
        !emitEvents(command)
        Ok
    }

    fun handleRemove(
        command: Event.Remove
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        val state = !getState ?: !exit(Failure("Topic subscription does not exists to remove"))
        !setState(null)
        !emitEvents(command)
        Ok
    }

    fun stateMachine(
        command: Event
    ) = when (command) {
        is Event.Add -> handleAdd(command)
        is Event.Subscribed -> handleSubscribed(command)
        is Event.Remove -> handleRemove(command)
    }

}
