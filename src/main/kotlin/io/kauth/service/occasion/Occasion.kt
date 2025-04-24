package io.kauth.service.occasion

import io.kauth.abstractions.reducer.Reducer
import io.kauth.abstractions.reducer.reducerOf
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

object Occasion {

    @Serializable
    data class Category(
        val name: String,
        val capacity: Int
    )

    @Serializable
    data class State(
        val categories: List<Category>,
        val date: LocalDate,
        val description: String,
        val owners: List<String>,
        val createdAt: Instant,
        val name: String? = null
    )

    @Serializable
    sealed interface Command {
        @Serializable
        data class CreateOccasion(
            val categories: List<Category>,
            val date: LocalDate,
            val description: String,
            val owners: List<String>,
            val createdAt: Instant,
            val name: String? = null
        ) : Command

        @Serializable
        data class AddOwners(
            val ownersToAdd: List<String>
        ) : Command
    }

    @Serializable
    sealed interface Event {
        @Serializable
        data class OccasionCreated(
            val occasion: State
        ) : Event

        @Serializable
        data class OwnersAdded(
            val ownersAdded: List<String>
        ) : Event
    }

    @Serializable
    sealed interface Error : Event {
        @Serializable
        data object OccasionAlreadyExists : Error

        @Serializable
        data class InvalidCommand(val message: String) : Error

        @Serializable
        data class OwnersAlreadyExist(val owners: List<String>) : Error
    }

    val handleCreatedEvent: Reducer<State?, Event.OccasionCreated> = Reducer { _, event ->
        event.occasion
    }

    val handleOwnersAddedEvent: Reducer<State?, Event.OwnersAdded> = Reducer { state, event ->
        state?.copy(owners = state.owners + event.ownersAdded)
    }

    val handleCreate: CommandMonad<Command.CreateOccasion, State?, Event, Output> = CommandMonad.Do { exit ->
        val state = !getState

        if (state != null) {
            !emitEvents(Error.OccasionAlreadyExists)
            !exit(Failure("Occasion already exists"))
        }

        if (command.categories.isEmpty()) {
            !emitEvents(Error.InvalidCommand("Categories cannot be empty"))
            !exit(Failure("Categories cannot be empty"))
        }

        if (command.description.isEmpty()) {
            !emitEvents(Error.InvalidCommand("Description cannot be empty"))
            !exit(Failure("Description cannot be empty"))
        }

        if (command.owners.isEmpty()) {
            !emitEvents(Error.InvalidCommand("Owners cannot be empty"))
            !exit(Failure("Owners cannot be empty"))
        }

        !emitEvents(
            Event.OccasionCreated(
                State(
                    categories = command.categories,
                    date = command.date,
                    description = command.description,
                    owners = command.owners,
                    createdAt = command.createdAt,
                    name = command.name
                )
            )
        )

        Ok
    }

    val handleAddOwners: CommandMonad<Command.AddOwners, State?, Event, Output> = CommandMonad.Do { exit ->
        val state = !getState
        if (state == null) {
            !emitEvents(Error.InvalidCommand("Occasion does not exist"))
            !exit(Failure("Occasion does not exist"))
        }

        val existingOwners = state.owners.toSet()
        val duplicates = command.ownersToAdd.filter { it in existingOwners }
        if (duplicates.isNotEmpty()) {
            !emitEvents(Error.OwnersAlreadyExist(duplicates))
            !exit(Failure("Owners already exist: $duplicates"))
        }

        !emitEvents(Event.OwnersAdded(command.ownersToAdd))
        Ok
    }

    val commandStateMachine: CommandMonad<Command, State?, Event, Output> = CommandMonad.Do { exit ->
        val command = !getCommand
        !when (command) {
            is Command.CreateOccasion -> handleCreate
            is Command.AddOwners -> handleAddOwners
        }
    }

    val eventReducer: Reducer<State?, Event> = reducerOf(
        Event.OccasionCreated::class to handleCreatedEvent,
        Event.OwnersAdded::class to handleOwnersAddedEvent
    )
}
