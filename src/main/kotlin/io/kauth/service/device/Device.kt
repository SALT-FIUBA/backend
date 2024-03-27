package io.kauth.service.device

import io.kauth.monad.state.StateMonad
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

object Device {

    @Serializable
    data class State(
        val organismId: String,
        val seriesNumber: String,
        val ports: List<String>,
        val status: String?,
        val createdBy: String,
        val createdAt: Instant
    )

    @Serializable
    sealed interface Command {

        @Serializable
        data class Create(
            val organismId: String,
            val seriesNumber: String,
            val ports: List<String>,
            val createdBy: String,
            val createdAt: Instant
        ): Command

    }

    @Serializable
    sealed interface Event {

        @Serializable
        data class Created(
            val device: State
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
        }

    fun handleCreate(
        command: Command.Create
    ) = StateMonad.Do<State?, Event, Output> { exit ->

        val state = !getState

        if(state != null) {
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


    fun stateMachine(
        command: Command
    ) = when (command) {
        is Command.Create -> handleCreate(command)
    }

}
