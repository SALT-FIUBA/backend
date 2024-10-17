package io.kauth.service.mqtt.subscription

import io.kauth.abstractions.reducer.Reducer
import io.kauth.abstractions.reducer.reducerOf
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import kotlinx.serialization.Serializable

//TIENE SENTIDO MANTENER ESTO ?
//No puedo computar el estado de la lista te topics a travez del stream ce?
object Subscription {

    @Serializable
    data class SubsData(
        val topic: String,
        val resource: String
    )

    @Serializable
    data class State(
        val data: List<SubsData>
    )

    @Serializable
    sealed interface Command {

        @Serializable
        object Subscribe: Command

        @Serializable
        data class Add(
            val data: List<SubsData>
        ): Command

        @Serializable
        data class Remove(
            val topic: String
        ): Command

    }

    @Serializable
    sealed interface Event {

        @Serializable
        object Subscribed: Event

        @Serializable
        data class Added(
            val data: List<SubsData>
        ): Event

        @Serializable
        data class Removed(
            val topic: String
        ): Event

    }

    val handleAdded get() = Reducer<State?, Event.Added> { state, event ->
        val s = state ?: State(emptyList())
        s.copy(s.data + event.data)
    }

    val handleRemoved get() = Reducer<State?, Event.Removed> { state, event ->
        state?.copy(data = state.data.filter { it.topic != event.topic })
    }

    val handleAdd get() = CommandMonad.Do<Command.Add, State?, Event, Output> { exit ->
        val command = !getCommand
        val state = !getState ?: State(emptyList())
        if(command.data.map { it.topic }.any { topic -> topic in state.data.map { it.topic } }) {
            !exit(Failure("Already subscribed ${command.data}"))
        }
        !emitEvents(Event.Added(command.data))
        Ok
    }

    val handleRemove get() = CommandMonad.Do<Command.Remove, State?, Event, Output> { exit ->
        val command = !getCommand
        val state = !getState ?: !exit(Failure("No topics to remove"))
        !emitEvents(Event.Removed(command.topic))
        Ok
    }

    val handleSubscribe get() = CommandMonad.Do<Command.Subscribe, State?, Event, Output> { exit ->
        !getState ?: !exit(Failure("No topics to subscribe"))
        !emitEvents(Event.Subscribed)
        Ok
    }

    val stateMachine get() =
        CommandMonad.Do<Command, State?, Event, Output> { exit ->
            val command = !getCommand
            !when (command) {
                is Command.Add -> handleAdd
                is Command.Subscribe -> handleSubscribe
                is Command.Remove -> handleRemove
            }
        }

    val eventStateMachine
        get() =
            reducerOf(
                Event.Removed::class to handleRemoved,
                Event.Added::class to handleAdded
            )

}
