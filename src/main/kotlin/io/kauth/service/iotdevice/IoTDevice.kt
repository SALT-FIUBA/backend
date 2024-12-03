package io.kauth.service.iotdevice

import io.kauth.abstractions.reducer.Reducer
import io.kauth.abstractions.reducer.reducerOf
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import io.kauth.service.iotdevice.model.iotdevice.DeviceCommand
import io.kauth.service.iotdevice.model.iotdevice.Integration
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

object IoTDevice {

    @Serializable
    data class State(
        val name: String,
        val createdAt: Instant,
        val createdBy: String,
        val resource: String,
        val enabled: Boolean,
        val integration: Integration,
        val capabilitiesValues: Map<String, StateData<String>>
    )

    @Serializable
    data class StateData<T>(
        val value: T,
        val updatedAt: Instant
    )

    @Serializable
    sealed interface Command {

        @Serializable
        data class Register(
            val name: String,
            val resource: String,
            val createdAt: Instant,
            val createdBy: String,
            val integration: Integration
        ): Command

        @Serializable
        data class SetCapabilityValue(
            val key: String,
            val value: String,
            val at: Instant
        ): Command

        @Serializable
        data class SetCapabilityValues(
            val at: Instant,
            val caps: List<Pair<String, String>>
        ): Command

        @Serializable
        data class SendCommand(
            val data: List<DeviceCommand>
        ): Command

        @Serializable
        data class SetEnabled(
            val enabled: Boolean
        ): Command

    }

    @Serializable
    sealed interface Event {
        @Serializable
        data class Registered(
            val device: State
        ): Event

        @Serializable
        data class CapabilitySet(
            val key: String,
            val value: String,
            val at: Instant
        ): Event

        @Serializable
        data class SendCommand(
            val commands: List<DeviceCommand>
        ): Event

        @Serializable
        data class EnabledSet(
            val enabled: Boolean
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

    val enabledSetEventHandler get() = Reducer<State?, Event.EnabledSet> { state, event ->
        state?.copy(enabled = event.enabled)
    }

    val capabilityValueEventHandler get() = Reducer<State?, Event.CapabilitySet> { state, event ->
        val oldValues = state?.capabilitiesValues ?: emptyMap()
        state?.copy(capabilitiesValues = oldValues.plus(event.key to StateData(value = event.value, updatedAt = event.at)))

    }

    val sendCommandHandler get() = CommandMonad.Do<Command.SendCommand, State?, Event, Output> { exit ->
        val state = !getState
        val command = !getCommand
        if (state == null) {
            !emitEvents(Error.UnknownError("Device does not exists!"))
            !exit(Failure("Device already exists"))
        }
        !emitEvents(Event.SendCommand(command.data))
        Ok
    }

    val setCapValueHandler get() = CommandMonad.Do<Command.SetCapabilityValue, State?, Event, Output> { exit ->
        val state = !getState
        val command = !getCommand
        if (state == null) {
            !emitEvents(Error.UnknownError("Device does not exists!"))
            !exit(Failure("Device already exists"))
        }
        !emitEvents(Event.CapabilitySet(command.key, command.value, command.at))
        Ok
    }

    val setCapValuesHandler get() = CommandMonad.Do<Command.SetCapabilityValues, State?, Event, Output> { exit ->
        val state = !getState
        val command = !getCommand
        if (state == null) {
            !emitEvents(Error.UnknownError("Device does not exists!"))
            !exit(Failure("Device already exists"))
        }

        !emitEvents(*command.caps.map { Event.CapabilitySet(it.first, it.second, command.at) }.toTypedArray())
        Ok
    }

    val setEnabled get() = CommandMonad.Do<Command.SetEnabled, State?, Event, Output> { exit ->
        val state = !getState
        val command = !getCommand
        if (state == null) {
            !emitEvents(Error.UnknownError("Device does not exists!"))
            !exit(Failure("Device already exists"))
        }
        !emitEvents(Event.EnabledSet(command.enabled))
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
                createdBy = command.createdBy,
                createdAt = command.createdAt,
                name = command.name,
                resource = command.resource,
                integration = command.integration,
                enabled = true,
                capabilitiesValues = emptyMap()
            )
        !emitEvents(Event.Registered(data))
        Ok
    }

    val commandStateMachine get() =
        CommandMonad.Do<Command,State?, Event, Output> {
            val command = !getCommand
            when(command) {
                is Command.Register -> !createCommandHandler
                is Command.SendCommand -> !sendCommandHandler
                is Command.SetEnabled -> !setEnabled
                is Command.SetCapabilityValue -> !setCapValueHandler
                is Command.SetCapabilityValues -> !setCapValuesHandler
            }
        }

    val eventReducer get() =
        reducerOf(
            Event.Registered::class to createdEventHandler,
            Event.EnabledSet::class to enabledSetEventHandler,
            Event.CapabilitySet::class to capabilityValueEventHandler,
        )

}