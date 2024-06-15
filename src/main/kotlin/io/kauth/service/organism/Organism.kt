package io.kauth.service.organism

import io.kauth.monad.state.StateMonad
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

object Organism {

    @Serializable
    data class State(
        val tag: String,
        val name: String,
        val description: String,
        val createdBy: String,
        val createdAt: Instant
    )

    @Serializable
    sealed interface Command {

        @Serializable
        data class CreateOrganism(
            val tag: String,
            val name: String,
            val description: String,
            val createdBy: String,
            val createdAt: Instant
        ): Command

    }

    @Serializable
    sealed interface Event {

        @Serializable
        data class OrganismCreated(
            val organism: State
        ): Event

    }

    sealed interface Error : Event {
        object OrganismAlreadyExists : Error
    }


    fun handleCreatedEvent(
        event: Event.OrganismCreated
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        !setState(event.organism)
        Ok
    }


    fun handleCreate(
        command: Command.CreateOrganism
    ) = StateMonad.Do<State?, Event, Output> { exit ->

        val state = !getState

        if(state != null) {
            !emitEvents(Error.OrganismAlreadyExists)
            !exit(Failure("Organism already exists"))
        }

        !emitEvents(
            Event.OrganismCreated(
                State(
                    tag = command.tag,
                    name = command.name,
                    description = command.description,
                    createdBy = command.createdBy,
                    createdAt = command.createdAt
                )
            )
        )

        Ok
    }


    fun commandStateMachine(
        command: Command
    ) = when (command) {
        is Command.CreateOrganism -> handleCreate(command)
    }

    fun eventStateMachine(
        event: Event
    ) = when (event) {
        is Event.OrganismCreated -> handleCreatedEvent(event)
        else -> StateMonad.noOp<State?, Output>(Ok)
    }

}