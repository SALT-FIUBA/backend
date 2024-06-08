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
    sealed interface Event {

        @Serializable
        object Subscribe: Event

        @Serializable
        data class Add(
            val data: List<SubsData>
        ): Event

        @Serializable
        data class Remove(
            val topic: String
        ): Event

    }

    fun handleAdd(
        command: Event.Add
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        val state = !getState ?: State(emptyList())
        if(command.data.map { it.topic }.any { topic -> topic in state.data.map { it.topic } }) {
            !exit(Failure("Already subscribed ${command.data}"))
        }
        !setState(state.copy(state.data + command.data))
        !emitEvents(command)
        Ok
    }

    fun handleRemove(
        command: Event.Remove
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        val state = !getState ?: !exit(Failure("No topics to remove"))
        !setState(state.copy(data = state.data.filter { it.topic != command.topic }))
        !emitEvents(command)
        Ok
    }

    fun handleSubscribe(
        command: Event.Subscribe
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        !getState ?: !exit(Failure("No topics to subscribe"))
        !emitEvents(command)
        Ok
    }

    fun stateMachine(
        command: Event
    ) = when (command) {
        is Event.Add -> handleAdd(command)
        is Event.Subscribe -> handleSubscribe(command)
        is Event.Remove -> handleRemove(command)
    }

}
