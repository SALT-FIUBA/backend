package io.kauth.service.mqttdevice

import io.kauth.abstractions.reducer.Reducer
import io.kauth.abstractions.reducer.reducerOf
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

object MqttDevice {

    @Serializable
    data class Topics(
        val state: String,
        val command: String,
        val status: String, //En general Online|Offline
        val telemetry: String
    )

    @Serializable
    data class State(
        val name: String,
        val status: String?,
        val state: String?,
        val createdAt: Instant,
        val topics: Topics,
        val createdBy: String,
        val resource: String = "",
    )

    @Serializable
    sealed interface Command {

        @Serializable
        data class Register(
            val resource: String,
            val topics: Topics,
            val createdAt: Instant,
            val createdBy: String,
            val name: String
        ): Command

        @Serializable
        data class SetStatus(
            val status: String
        ): Command

        @Serializable
        data class SetState(
            val status: String
        ): Command

        @Serializable
        data class Telemetry(
            val data: String
        ): Command

        @Serializable
        data class SendCommand(
            val data: String
        ): Command

    }

    @Serializable
    sealed interface Event {
        @Serializable
        data class Registered(
            val device: State
        ): Event
        @Serializable
        data class StatusSet(
            val status: String
        ): Event

        @Serializable
        data class StateSet(
            val state: String
        ): Event

        @Serializable
        data class SendCommand(
            val command: String,
            val topic: String
        ): Event

    }

    @Serializable
    sealed interface Error : Event {
        @Serializable
        data class UnknownError(val message: String): Error
    }

    val createdEventHandler get() = Reducer<State?, Event.Registered> { _, event ->
        event.device
    }

    val statusSetEventHandler get() = Reducer<State?, Event.StatusSet> { state, event ->
        state?.copy(status = event.status)
    }

    val setState get() = Reducer<State?, Event.StateSet> { state, event ->
        state?.copy(state = event.state)
    }

    val sendCommandHandler get() = CommandMonad.Do<Command.SendCommand, State?, Event, Output> { exit ->
        val state = !getState
        val command = !getCommand
        if (state == null) {
            !emitEvents(Error.UnknownError("Device does not exists!"))
            !exit(Failure("Device already exists"))
        }
        !emitEvents(Event.SendCommand(command.data, state.topics.command))
        Ok
    }

    val setStatusHandler get() = CommandMonad.Do<Command.SetStatus, State?, Event, Output> { exit ->
        val state = !getState
        val command = !getCommand
        if (state == null) {
            !emitEvents(Error.UnknownError("Device does not exists!"))
            !exit(Failure("Device already exists"))
        }
        !emitEvents(Event.StatusSet(command.status))
        Ok
    }

    val setStateHandler get() = CommandMonad.Do<Command.SetState, State?, Event, Output> { exit ->
        val state = !getState
        val command = !getCommand
        if (state == null) {
            !emitEvents(Error.UnknownError("Device does not exists!"))
            !exit(Failure("Device already exists"))
        }
        !emitEvents(Event.StateSet(command.status))
        Ok
    }

    val createCommandHandler get() = CommandMonad.Do<Command.Register, State?, Event, Output> { exit ->
        val state = !getState
        val command = !getCommand

        if (state != null) {
            !emitEvents(Error.UnknownError("Device already exists"))
            !exit(Failure("Device already exists"))
        }
        val data =
            State(
                status = null,
                state = null,
                createdBy = command.createdBy,
                createdAt = command.createdAt,
                topics = command.topics,
                name = command.name,
                resource = command.resource
            )
        !emitEvents(Event.Registered(data))
        Ok
    }

    val commandStateMachine get() =
        CommandMonad.Do<Command,State?, Event, Output> {
            val command = !getCommand
            when(command) {
                is Command.Register -> !createCommandHandler
                is Command.SetStatus -> !setStatusHandler
                is Command.SendCommand -> !sendCommandHandler
                is Command.SetState -> !setStateHandler
                is Command.Telemetry -> TODO()
            }
        }

    val eventReducer get() =
        reducerOf(
            Event.Registered::class to createdEventHandler,
            Event.StatusSet::class to statusSetEventHandler,
            Event.StateSet::class to setState
        )

}