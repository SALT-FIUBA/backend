package io.kauth.service.mqtt.subscription

import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import io.kauth.monad.state.EventMonad
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

    val handleAdded get() = EventMonad.Do<State?, Event.Added, Output> { exit ->
        val event = !getEvent
        !setState(
            State(
                resource = event.resource,
                createdAt = event.createdAt
            )
        )
        Ok
    }

    val handleSubscribedEvent get() = EventMonad.Do<State?,Event.Subscribed, Output> { exit ->
        val event = !getEvent
        val state = !getState
        !setState(state?.copy(lastSubscribedAt = event.at))
        Ok
    }

    val handleRemoved get() = EventMonad.Do<State?,Event.Removed, Output> { exit ->
        !setState(null)
        Ok
    }

    val handleAdd get() = CommandMonad.Do<Command.Add, State?,Event, Output> { exit ->

        val state = !getState

        if(state != null) {
            !exit(Failure("Already subscribed"))
        }

        !emitEvents(Event.Added(command.resource, command.createdAt))

        Ok
    }

    val handleSubscribed get() = CommandMonad.Do<Command.Subscribed, State?,Event, Output> { exit ->
        val command = !getCommand
        !getState ?: !exit(Failure("Topic subscription does not exists"))
        !emitEvents(Event.Subscribed(command.at))
        Ok
    }

    val handleRemove get() = CommandMonad.Do<Command.Remove, State?,Event, Output> { exit ->
        val state = !getState ?: !exit(Failure("Topic subscription does not exists to remove"))
        !emitEvents(Event.Removed)
        Ok
    }

    val stateMachine get() =
        CommandMonad.Do<Command, State?,Event, Output> { exit ->
            val command = !getCommand
            !when (command) {
                is Command.Add -> handleAdd
                is Command.Subscribed -> handleSubscribed
                is Command.Remove -> handleRemove
            }
        }


    val eventStateMachine get() =
        EventMonad.Do<State?,Event, Output> { exit ->
            val event = !getEvent
            !when(event) {
                is Event.Subscribed -> handleSubscribedEvent
                is Event.Removed -> handleRemoved
                is Event.Added -> handleAdded
            }
        }

}
