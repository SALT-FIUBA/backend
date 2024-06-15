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
        data class Added(
            val resource: String,
            val createdAt: Instant? = null
        ): Event
        @Serializable
        data object Removed: Event
    }

    @Serializable
    sealed interface Command {
        @Serializable
        data class Subscribed(val at: Instant): Command
        @Serializable
        data class Add(
            val resource: String,
            val createdAt: Instant? = null
        ): Command
        @Serializable
        data object Remove: Command
    }

    fun handleAdded(
        event: Event.Added
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        !setState(
            State(
                resource = event.resource,
                createdAt = event.createdAt
            )
        )
        Ok
    }

    fun handleSubscribedEvent(
        event: Event.Subscribed
    ) = StateMonad.Do<State?,Event, Output> { exit ->
        val state = !getState
        !setState(state?.copy(lastSubscribedAt = event.at))
        Ok
    }

    fun handleRemoved(
        command: Event.Removed
    ) = StateMonad.Do<State?,Event, Output> { exit ->
        !setState(null)
        Ok
    }

    fun handleAdd(
        command: Command.Add
    ) = StateMonad.Do<State?,Event, Output> { exit ->

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

        !emitEvents(Event.Added(command.resource, command.createdAt))

        Ok
    }

    fun handleSubscribed(
        command: Command.Subscribed
    ) = StateMonad.Do<State?,Event, Output> { exit ->
        val state = !getState ?: !exit(Failure("Topic subscription does not exists"))
        !setState(state.copy(lastSubscribedAt = command.at))
        !emitEvents(Event.Subscribed(command.at))
        Ok
    }

    fun handleRemove(
        command: Command.Remove
    ) = StateMonad.Do<State?,Event, Output> { exit ->
        val state = !getState ?: !exit(Failure("Topic subscription does not exists to remove"))
        !setState(null)
        !emitEvents(Event.Removed)
        Ok
    }

    fun stateMachine(
        command: Command
    ) = when (command) {
        is Command.Add -> handleAdd(command)
        is Command.Subscribed -> handleSubscribed(command)
        is Command.Remove -> handleRemove(command)
    }

    fun eventStateMachine(
        event: Event
    ) = when(event) {
        is Event.Subscribed -> handleSubscribedEvent(event)
        is Event.Removed -> handleRemoved(event)
        is Event.Added -> handleAdded(event)
    }

}
