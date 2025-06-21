package io.kauth.service.organism

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

object Organism {

    data class OrganismRole(
        val role: Role,
        val organismId: UUID
    ) {
        val string: String = "role:${role}:organism:${organismId}"
        companion object {
            fun formString(organismRole: String) =
                kotlin.runCatching {
                    val (role, roleId, organism, organismId) = organismRole.split(":")
                    OrganismRole(
                        Role.fromString(roleId) ?: error("Invalid role"),
                        UUID.fromString(organismId))
                }.getOrNull()
        }
    }

    //EL role podria ser (organismo)-(operador|supervisor)
    @Serializable
    enum class Role {
        supervisor,
        operators,
        write,
        read;

        companion object {
            fun fromString(value: String) =
                kotlin.runCatching { Role.valueOf(value) }.getOrNull()
        }
    }

    @Serializable
    enum class InternalRole {
        admin
    }

    @Serializable
    data class State(
        val tag: String,
        val name: String,
        val description: String,
        val createdBy: String?,
        val createdAt: Instant,
        val supervisors: List<UserInfo> = emptyList(),
        val operators: List<UserInfo> = emptyList(),
        val deleted: Boolean = false // Soft delete flag
    )

    @Serializable
    data class UserInfo(
        @Contextual
        val id: UUID,
        @Contextual
        val addedBy: UUID?,
        val addedAt: Instant
    )

    @Serializable
    sealed interface Command {

        @Serializable
        data class CreateOrganism(
            val tag: String,
            val name: String,
            val description: String,
            val createdBy: String?,
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

        @Serializable
        data class DeleteOrganism(
            val deletedBy: String?,
            val deletedAt: Instant
        ): Command

        @Serializable
        data class EditOrganism(
            val tag: String? = null,
            val name: String? = null,
            val description: String? = null,
            val editedBy: String?,
            val editedAt: Instant
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

        @Serializable
        data class OrganismDeleted(
            val deletedBy: String?,
            val deletedAt: Instant
        ): Event

        @Serializable
        data class OrganismEdited(
            val tag: String,
            val name: String,
            val description: String,
            val editedBy: String?,
            val editedAt: Instant
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

    val handleDeletedEvent get() = Reducer<State?, Event.OrganismDeleted> { state, event ->
        state?.copy(deleted = true)
    }

    val handleEditedEvent get() = Reducer<State?, Event.OrganismEdited> { state, event ->
        state?.copy(
            tag = event.tag,
            name = event.name,
            description = event.description
        )
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

    val handleDeleteOrganism get() = CommandMonad.Do<Command.DeleteOrganism, State?, Event, Output> { exit ->
        val state = !getState
        if (state == null) {
            !emitEvents(Error.OrganismDoesNotExists)
            !exit(Failure("Organism does not exists"))
        }
        if (state.deleted) {
            !emitEvents(Error.InvalidCommand("Organism already deleted"))
            !exit(Failure("Organism already deleted"))
        }
        !emitEvents(Event.OrganismDeleted(command.deletedBy, command.deletedAt))
        Ok
    }

    val handleEditOrganism get() = CommandMonad.Do<Command.EditOrganism, State?, Event, Output> { exit ->
        val state = !getState
        if (state == null) {
            !emitEvents(Error.OrganismDoesNotExists)
            !exit(Failure("Organism does not exists"))
        }
        if (state.deleted) {
            !emitEvents(Error.InvalidCommand("Organism is deleted"))
            !exit(Failure("Organism is deleted"))
        }
        val newTag = command.tag ?: state.tag
        val newName = command.name ?: state.name
        val newDescription = command.description ?: state.description
        if (newTag.isEmpty()) {
            !emitEvents(Error.InvalidCommand("Invalid tag"))
            !exit(Failure("Invalid empty tag"))
        }
        if (newName.isEmpty()) {
            !emitEvents(Error.InvalidCommand("Invalid name"))
            !exit(Failure("Invalid empty name"))
        }
        if (newDescription.isEmpty()) {
            !emitEvents(Error.InvalidCommand("Invalid description"))
            !exit(Failure("Invalid empty description"))
        }
        if (newTag == state.tag && newName == state.name && newDescription == state.description) {
            !emitEvents(Error.InvalidCommand("No changes provided"))
            !exit(Failure("No changes provided"))
        }
        !emitEvents(
            Event.OrganismEdited(
                tag = newTag,
                name = newName,
                description = newDescription,
                editedBy = command.editedBy,
                editedAt = command.editedAt
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
                is Command.DeleteOrganism -> handleDeleteOrganism
                is Command.EditOrganism -> handleEditOrganism
            }
        }

    val eventReducer: Reducer<State?, Event>
        get() =
            reducerOf(
                Event.OrganismCreated::class to handleCreatedEvent,
                Event.SupervisorAdded::class to handleSupervisorEvent,
                Event.OperatorAdded::class to handleOperatorEvent,
                Event.OrganismDeleted::class to handleDeletedEvent,
                Event.OrganismEdited::class to handleEditedEvent
            )

}