package io.kauth.service.mqtt.subscription

import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import io.kauth.monad.state.EventMonad
import kotlinx.serialization.Serializable
import mqtt.Subscription
import mqtt.packets.Qos
import mqtt.packets.mqttv5.SubscriptionOptions

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

    val SubsData.toMqttSubs get() =
        Subscription(topic, SubscriptionOptions(Qos.AT_LEAST_ONCE))


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

    val handleAdded get() = EventMonad.Do<State?, Event.Added, Output> { exit ->
        val event = !getEvent
        val state = !getState ?: State(emptyList())
        !setState(state.copy(state.data + event.data))
        Ok
    }

    val handleRemoved get() = EventMonad.Do<State?, Event.Removed, Output> { exit ->
        val event = !getEvent
        val state = !getState
        !setState(state?.copy(data = state.data.filter { it.topic != event.topic }))
        Ok
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

    val eventStateMachine get() =
        EventMonad.Do<State?, Event, Output> { exit ->
            val event = !getEvent
            when(event) {
                is Event.Removed -> !handleRemoved
                is Event.Added -> !handleAdded
                else -> Ok
            }
        }

}
