package io.kauth.service.organism

import io.kauth.abstractions.reducer.Reducer
import io.kauth.abstractions.reducer.reducerOf
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

object Organism {

    @Serializable
    data class State(
        val tag: String,
        val name: String,
        val description: String,
        val createdBy: String,
        val createdAt: Instant,
        val supervisors: List<UserInfo> = emptyList(),
        val operators: List<UserInfo> = emptyList()
    )

    @Serializable
    data class UserInfo(
        val id: String,
        val addedBy: String,
        val addedAt: Instant
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

        @Serializable
        data class AddSupervisor(
            val user: UserInfo
        ): Command

        @Serializable
        data class AddOperator(
            val user: UserInfo
        ): Command

    }

    @Serializable
    sealed interface Event {

        @Serializable
        data class OrganismCreated(
            val organism: State
        ): Event

        @Serializable
        data class OperatorAdded(
            val user: UserInfo
        ): Event

        @Serializable
        data class SupervisorAdded(
            val user: UserInfo
        ): Event

    }

    @Serializable
    sealed interface Error : Event {
        @Serializable
        data object OrganismAlreadyExists : Error

        @Serializable
        data object OrganismDoesNotExists: Error

        @Serializable
        data class InvalidCommand(val message: String): Error
    }

    //Reducers
    val handleCreatedEvent get() = Reducer<State?, Event.OrganismCreated> { state, event ->
        event.organism
    }

    val handleOperatorEvent get() = Reducer<State?, Event.OperatorAdded> { state, event ->
        state?.copy(operators = state.operators + event.user)
    }

    val handleSupervisorEvent get() = Reducer<State?, Event.SupervisorAdded> { state, event ->
        state?.copy(supervisors = state.supervisors + event.user)
    }

    //event generators
    val handleAddSupervisor get() = CommandMonad.Do<Command.AddSupervisor, State?, Event, Output> { exit ->
        val state = !getState
        if(state == null) {
            !emitEvents(Error.OrganismDoesNotExists)
            !exit(Failure("Organism does not exists"))
        }
        if(state.supervisors.any { it -> it.id == command.user.id }) {
            !emitEvents(Error.InvalidCommand("User ${command.user.id} is supervisor"))
            !exit(Failure("User ${command.user.id} is supervisor"))
        }
        !emitEvents(
            Event.SupervisorAdded(
                user = command.user
            )
        )
        Ok
    }

    val handleAddOperator get() = CommandMonad.Do<Command.AddOperator, State?, Event, Output> { exit ->
        val state = !getState
        if(state == null) {
            !emitEvents(Error.OrganismDoesNotExists)
            !exit(Failure("Organism does not exists"))
        }
        if(state.operators.any { it -> it.id == command.user.id }) {
            !emitEvents(Error.InvalidCommand("User ${command.user.id} is operator"))
            !exit(Failure("User ${command.user.id} is operator"))
        }
        !emitEvents(
            Event.OperatorAdded(
                user = command.user
            )
        )
        Ok
    }

    val handleCreate get() = CommandMonad.Do<Command.CreateOrganism, State?, Event, Output> { exit ->

        val state = !getState

        if(state != null) {
            !emitEvents(Error.OrganismAlreadyExists)
            !exit(Failure("Organism already exists"))
        }

        if (command.tag.isEmpty()) {
            !emitEvents(Error.InvalidCommand("Invalid tag"))
            !exit(Failure("Invalid empty tag"))
        }

        if (command.name.isEmpty()) {
            !emitEvents(Error.InvalidCommand("Invalid name"))
            !exit(Failure("Invalid empty name"))
        }

        if (command.description.isEmpty()) {
            !emitEvents(Error.InvalidCommand("Invalid description"))
            !exit(Failure("Invalid empty description"))
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
                is Command.AddSupervisor -> handleAddSupervisor
                is Command.AddOperator -> handleAddOperator
            }
        }

    val eventReducer: Reducer<State?, Event>
        get() =
            reducerOf(
                Event.OrganismCreated::class to handleCreatedEvent,
                Event.SupervisorAdded::class to handleSupervisorEvent,
                Event.OperatorAdded::class to handleOperatorEvent
            )

}