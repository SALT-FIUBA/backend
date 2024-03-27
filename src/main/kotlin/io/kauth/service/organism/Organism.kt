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

    val Event.asCommand get() =
        when(this) {
            is Event.OrganismCreated -> Command.CreateOrganism(
                tag = organism.tag,
                name = organism.name,
                description = organism.description,
                createdBy = organism.createdBy,
                createdAt = organism.createdAt
            )
        }

    fun handleCreate(
        command: Command.CreateOrganism
    ) = StateMonad.Do<State?, Event, Output> { exit ->

        val state = !getState

        if(state != null) {
            !exit(Failure("Organism already exists"))
        }

        val data =
            State(
                tag = command.tag,
                name = command.name,
                description = command.description,
                createdBy = command.createdBy,
                createdAt = command.createdAt
            )

        !setState(data)
        !emitEvents(Event.OrganismCreated(data))

        Ok
    }


    fun stateMachine(
        command: Command
    ) = when (command) {
        is Command.CreateOrganism -> handleCreate(command)
    }

}