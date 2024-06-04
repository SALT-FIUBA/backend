package io.kauth.service.salt

import io.kauth.monad.state.StateMonad
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.abstractions.state.StateMachineHandler
import io.kauth.abstractions.state.StateMachinieHandler
import io.kauth.abstractions.state.buildStateMachine
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

    val Event.asCommand get() =
        when(this) {
            is Event.Created -> Command.Create(
                organismId = device.organismId,
                seriesNumber = device.seriesNumber,
                ports = device.ports,
                createdBy = device.createdBy,
                createdAt = device.createdAt,
                topics = device.topics
            )
            is Event.StatusSet -> Command.SetStatus(
                status = status
            )
        }

    val commandRouter get() =
        object : StateMachinieHandler<Command, State, Event, Output> {
            override fun Command.handle() =
                when(this) {
                    is Command.SetStatus -> with(setStatusHandler) { this@handle.handle() }
                    is Command.Create -> with(createHandler) { this@handle.handle() }
                }
            }

    val setStatusHandler get() =
        object : StateMachinieHandler<Command.SetStatus, State, Event, Output> {
            override fun Command.SetStatus.handle() = StateMonad.Do<State?, Event, Output> { exit ->
                val state = !getState ?: !exit(Failure("Device does not exists"))
                !setState(state.copy(status = status))
                !emitEvents(Event.StatusSet(status))
                Ok
            }
        }

    val createHandler
        get() =
            StateMachineHandler { command: Command.Create, exit ->

                val state = !getState

                if (state != null) {
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

                !setState(data)
                !emitEvents(Event.Created(data))

                Ok
            }

    fun stateMachine(command: Command) = buildStateMachine(command, commandRouter)

}
