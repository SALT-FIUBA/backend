package io.kauth.service.salt

import io.kauth.abstractions.reducer.Reducer
import io.kauth.abstractions.reducer.reducerOf
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

object Device {

    object Mqtt {
        @Serializable
        data class SaltConfig(
            val velCtOn: Double?,
            val velCtOff: Double?,
            val velFeOn: Double?,
            val velFeHold: Double?,
            val timeBlinkEnable: Boolean?,
            val timeBlinkDisable: Boolean?,
            val blinkPeriod: Boolean?
        )

        @Serializable
        enum class SaltAction {
            STOP,
            DRIFT,
            ISOLATED,
            AUTOMATIC,
            COUNT
        }

        //Revisar si puede ser sealed interface
        @Serializable
        data class SaltCmd(
            val action: SaltAction?,
            val config: SaltConfig?
        )

        @Serializable
        data class SaltState(
            val config: SaltConfig,
            val currentAction: SaltAction,
            val speed: Double
        )
    }

    @Serializable
    data class Topics(
        val state: String,
        val command: String,
        val status: String
    )

    @Serializable
    data class State(
        @Contextual
        val organismId: UUID? = null,
        @Contextual
        val trainId: UUID? = null,
        val seriesNumber: String,
        val ports: List<String>,
        val status: String?,
        val createdBy: String,
        val createdAt: Instant,
        val topics: Topics? = null,
        val deviceState: Mqtt.SaltState? = null
    )

    //Metadatos del tren en el que se configuró este device
    @Serializable
    data class Metadata(
        val train: String,
    )

    @Serializable
    sealed interface Command {

        @Serializable
        data class Create(
            @Contextual
            val organismId: UUID? = null,
            @Contextual
            val trainId: UUID? = null,
            val seriesNumber: String,
            val ports: List<String>,
            val createdBy: String,
            val createdAt: Instant,
            val topics: Topics? = null
        ): Command

        @Serializable
        data class SetStatus(
            val status: String
        ): Command

        @Serializable
        data class SetState(
            val state: Mqtt.SaltState
        ): Command

    }

    @Serializable
    sealed interface Event {

        @Serializable
        data class Created(
            val device: State
        ): Event

        @Serializable
        data class StatusSet(
            val status: String
        ): Event

        @Serializable
        data class StateSet(
            val state: Mqtt.SaltState
        ): Event

    }

    @Serializable
    sealed interface Error : Event {
        @Serializable
        data class DeviceAlreadyExists(val seriesNumber: String): Error
        @Serializable
        object DeviceDoesNotExists: Error
    }

    val createdEventHandler get() = Reducer<State?, Event.Created> { state, event ->
        event.device
    }

    val statusSetEventHandler get() = Reducer<State?, Event.StatusSet> { state, event ->
        state?.copy(status = event.status)
    }

    val stateSetEventHandler get() = Reducer<State?, Event.StateSet> { state, event ->
        state?.copy(deviceState = event.state)
    }

    val setStatusHandler get() = CommandMonad.Do<Command.SetStatus, State?, Event, Output> { exit ->
        val state = !getState
        val command = !getCommand
        if (state == null) {
            !emitEvents(Error.DeviceDoesNotExists)
            !exit(Failure("Device already exists"))
        }
        !emitEvents(Event.StatusSet(command.status))
        Ok
    }

    val setStateHandler get() = CommandMonad.Do<Command.SetState, State?, Event, Output> { exit ->
        val state = !getState
        val command = !getCommand
        if (state == null) {
            !emitEvents(Error.DeviceDoesNotExists)
            !exit(Failure("Device does not exist"))
        }
        !emitEvents(Event.StateSet(command.state))
        Ok
    }

    val createCommandHandler get() = CommandMonad.Do<Command.Create, State?, Event, Output> { exit ->
        val state = !getState
        val command = !getCommand

        if (state != null) {
            !emitEvents(Error.DeviceAlreadyExists(command.seriesNumber))
            !exit(Failure("Device already exists"))
        }

        val data =
            State(
                trainId = command.trainId,
                organismId = command.organismId,
                seriesNumber = command.seriesNumber,
                ports = command.ports,
                status = null,
                createdBy = command.createdBy,
                createdAt = command.createdAt,
                topics = command.topics
            )

        !emitEvents(Event.Created(data))

        Ok
    }

    val commandStateMachine get() =
        CommandMonad.Do<Command, State?, Event, Output> {
            val command = !getCommand
            when(command) {
                is Command.Create -> !createCommandHandler
                is Command.SetStatus -> !setStatusHandler
                is Command.SetState -> !setStateHandler
            }
        }

    val eventReducer get() =
        reducerOf(
            Event.Created::class to createdEventHandler,
            Event.StatusSet::class to statusSetEventHandler,
            Event.StateSet::class to stateSetEventHandler
        )
}

