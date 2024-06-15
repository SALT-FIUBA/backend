package io.kauth.service.mqtt.subscription

import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.StateMonad
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

    fun handleAdded(
        event: Event.Added
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        val state = !getState ?: State(emptyList())
        !setState(state.copy(state.data + event.data))
        Ok
    }

    fun handleRemoved(
        event: Event.Removed
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        val state = !getState
        !setState(state?.copy(data = state.data.filter { it.topic != event.topic }))
        Ok
    }

    fun handleAdd(
        command: Command.Add
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        val state = !getState ?: State(emptyList())
        if(command.data.map { it.topic }.any { topic -> topic in state.data.map { it.topic } }) {
            !exit(Failure("Already subscribed ${command.data}"))
        }
        !emitEvents(Event.Added(command.data))
        Ok
    }

    fun handleRemove(
        command: Command.Remove
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        val state = !getState ?: !exit(Failure("No topics to remove"))
        !emitEvents(Event.Removed(command.topic))
        Ok
    }

    fun handleSubscribe(
        command: Command.Subscribe
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        !getState ?: !exit(Failure("No topics to subscribe"))
        !emitEvents(Event.Subscribed)
        Ok
    }

    fun stateMachine(
        command: Command
    ) = when (command) {
        is Command.Add -> handleAdd(command)
        is Command.Subscribe -> handleSubscribe(command)
        is Command.Remove -> handleRemove(command)
    }

    fun eventStateMachine(
        event: Event
    ) = when(event) {
        is Event.Removed -> handleRemoved(event)
        is Event.Added -> handleAdded(event)
        else -> StateMonad.noOp(Ok)
    }

}
