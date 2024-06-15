package io.kauth.service.salt

import io.kauth.monad.state.StateMonad
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
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
            SALT_CMD_ORDER_STOP,
            SALT_CMD_ORDER_DRIFT,
            SALT_CMD_ORDER_ISOLATED,
            SALT_CMD_ORDER_AUTOMATIC,
            SALT_CMD_ORDER_COUNT
        }

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
        val organismId: UUID,
        val seriesNumber: String,
        val ports: List<String>,
        val status: String?,
        val createdBy: String,
        val createdAt: Instant,
        val topics: Topics? = null
    )

    @Serializable
    sealed interface Command {

        @Serializable
        data class Create(
            @Contextual
            val organismId: UUID,
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

    }

    @Serializable
    sealed interface Error : Event {
        @Serializable
        data class DeviceAlreadyExists(val seriesNumber: String): Error
        @Serializable
        object DeviceDoesNotExists: Error
    }

    fun createdEventHandler(
        event: Event.Created
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        !setState(event.device)
        Ok
    }

    fun statusSetEventHandler(
        event: Event.StatusSet
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        !setState(state?.copy(status = event.status))
        Ok
    }

    fun setStatusHandler(command: Command.SetStatus) = StateMonad.Do<State?, Event, Output> { exit ->
        val state = !getState
        if (state == null) {
            !emitEvents(Error.DeviceDoesNotExists)
            !exit(Failure("Device already exists"))
        }
        !emitEvents(Event.StatusSet(command.status))
        Ok
    }

    fun createCommandHandler(command: Command.Create) =  StateMonad.Do<State?, Event, Output> { exit ->
        val state = !getState

        if (state != null) {
            !emitEvents(Error.DeviceAlreadyExists(command.seriesNumber))
            !exit(Failure("Device already exists"))
        }

        val data =
            State(
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

    fun commandStateMachine(command: Command) =
        when(command) {
            is Command.Create -> createCommandHandler(command)
            is Command.SetStatus -> setStatusHandler(command)
        }

    fun eventStateMachine(event: Event) =
        when(event) {
            is Event.Created -> createdEventHandler(event)
            is Event.StatusSet -> statusSetEventHandler(event)
            else -> StateMonad.noOp(Ok)
        }
}
