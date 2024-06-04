package io.kauth.service.mqtt.subscription

import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.StateMonad
import kotlinx.serialization.Serializable
import mqtt.Subscription
import mqtt.packets.Qos
import mqtt.packets.mqttv5.SubscriptionOptions

//TODO esto tambien te sirve de RELACION TOPIC-RESOURCE
//PENSAR BIEN COMO HACER PARA OBTENER TODOS
object Subscription {

    @Serializable
    data class SubsData(
        val topic: String,
        val resource: String
    )

    //TODO pensar si esta bien tener una lista o mejor tener
    //el nombre del topic en la key
    //Aca neceisto un resource ? created by ? etc
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

    fun handleSubscribe(
        command: Event.Subscribe
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        val state = !getState ?: State(emptyList())
        if (state.data.isEmpty()) {
            !exit(Failure("No topics to subscribe"))
        }
        !emitEvents(command)
        Ok
    }

    fun stateMachine(
        command: Event
    ) = when (command) {
        is Event.Add -> handleAdd(command)
        is Event.Subscribe -> handleSubscribe(command)
        else -> TODO("Not implemented")
    }

}
