package io.kauth.service.organism

import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import io.kauth.monad.state.EventMonad
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

    val handleCreatedEvent get() = EventMonad.Do<State?, Event.OrganismCreated, Output> { exit ->
        val event = !getEvent
        !setState(event.organism)
        Ok
    }

    val handleCreate get() = CommandMonad.Do<Command.CreateOrganism, State?, Event, Output> { exit ->

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

    val commandStateMachine get() =
        CommandMonad.Do<Command, State?, Event, Output> { exit ->
            val command = !getCommand
            !when (command) {
                is Command.CreateOrganism -> handleCreate
            }
        }

    val eventStateMachine get() =
        EventMonad.Do<State?, Event, Output> { exit ->
            val event = !getEvent
            when (event) {
                is Event.OrganismCreated -> !handleCreatedEvent
                else -> Ok
            }
        }

}