package io.kauth.service.device

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

    @Serializable
    data class State(
        @Contextual
        val organismId: UUID,
        val seriesNumber: String,
        val ports: List<String>,
        val status: String?,
        val createdBy: String,
        val createdAt: Instant,
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
            val createdAt: Instant
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
                createdAt = device.createdAt
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
                        createdAt = command.createdAt
                    )

                !setState(data)
                !emitEvents(Event.Created(data))

                Ok
            }

    fun stateMachine(command: Command) = buildStateMachine(command, commandRouter)

}
