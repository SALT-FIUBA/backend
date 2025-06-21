package io.kauth.service.train

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

object Train {

    @Serializable
    data class State(
        @Contextual
        val organism: UUID,
        val seriesNumber: String,
        val name: String,
        val description: String,
        val createdBy: String?,
        val createdAt: Instant,
        val deleted: Boolean = false
    )

    @Serializable
    sealed interface Command {
        @Serializable
        data class CreateTrain(
            val seriesNumber: String,
            val name: String,
            val description: String,
            val createdBy: String?,
            val createdAt: Instant,
            @Contextual
            val organism: UUID
        ): Command
        @Serializable
        data class DeleteTrain(
            val deletedBy: String?,
            val deletedAt: Instant
        ): Command
        @Serializable
        data class EditTrain(
            val seriesNumber: String? = null,
            val name: String? = null,
            val description: String? = null,
            val editedBy: String?,
            val editedAt: Instant
        ): Command
    }

    @Serializable
    sealed interface Event {
        @Serializable
        data class TrainCreated(
            val state: State
        ): Event
        @Serializable
        data class TrainDeleted(
            val deletedBy: String?,
            val deletedAt: Instant
        ): Event
        @Serializable
        data class TrainEdited(
            val seriesNumber: String,
            val name: String,
            val description: String,
            val editedBy: String?,
            val editedAt: Instant
        ): Event
    }

    @Serializable
    sealed interface Error : Event {
        @Serializable
        data object TrainAlreadyExists : Error

        @Serializable
        data class InvalidCommand(val message: String): Error
    }

    //Reducers
    val handleCreatedEvent get() = Reducer<State?, Event.TrainCreated> { _, event ->
        event.state
    }
    val handleDeletedEvent get() = Reducer<State?, Event.TrainDeleted> { state, event ->
        state?.copy(deleted = true)
    }
    val handleEditedEvent get() = Reducer<State?, Event.TrainEdited> { state, event ->
        state?.copy(
            seriesNumber = event.seriesNumber,
            name = event.name,
            description = event.description
        )
    }

    val handleCreate get() = CommandMonad.Do<Command.CreateTrain, State?, Event, Output> { exit ->

        val state = !getState

        if(state != null) {
            !emitEvents(Error.TrainAlreadyExists)
            !exit(Failure("Organism already exists"))
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
            Event.TrainCreated(
                State(
                    organism = command.organism,
                    seriesNumber = command.seriesNumber,
                    name = command.name,
                    description = command.description,
                    createdBy = command.createdBy,
                    createdAt = command.createdAt
                )
            )
        )

        Ok
    }

    val handleDeleteTrain get() = CommandMonad.Do<Command.DeleteTrain, State?, Event, Output> { exit ->
        val state = !getState
        if (state == null) {
            !emitEvents(Error.InvalidCommand("Train does not exist"))
            !exit(Failure("Train does not exist"))
        }
        if (state.deleted) {
            !emitEvents(Error.InvalidCommand("Train already deleted"))
            !exit(Failure("Train already deleted"))
        }
        !emitEvents(Event.TrainDeleted(command.deletedBy, command.deletedAt))
        Ok
    }

    val handleEditTrain get() = CommandMonad.Do<Command.EditTrain, State?, Event, Output> { exit ->
        val state = !getState
        if (state == null) {
            !emitEvents(Error.InvalidCommand("Train does not exist"))
            !exit(Failure("Train does not exist"))
        }
        if (state.deleted) {
            !emitEvents(Error.InvalidCommand("Train is deleted"))
            !exit(Failure("Train is deleted"))
        }
        val newSeriesNumber = command.seriesNumber ?: state.seriesNumber
        val newName = command.name ?: state.name
        val newDescription = command.description ?: state.description
        if (newSeriesNumber.isEmpty()) {
            !emitEvents(Error.InvalidCommand("Invalid seriesNumber"))
            !exit(Failure("Invalid empty seriesNumber"))
        }
        if (newName.isEmpty()) {
            !emitEvents(Error.InvalidCommand("Invalid name"))
            !exit(Failure("Invalid empty name"))
        }
        if (newDescription.isEmpty()) {
            !emitEvents(Error.InvalidCommand("Invalid description"))
            !exit(Failure("Invalid empty description"))
        }
        if (newSeriesNumber == state.seriesNumber && newName == state.name && newDescription == state.description) {
            !emitEvents(Error.InvalidCommand("No changes provided"))
            !exit(Failure("No changes provided"))
        }
        !emitEvents(
            Event.TrainEdited(
                seriesNumber = newSeriesNumber,
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
                is Command.CreateTrain -> handleCreate
                is Command.DeleteTrain -> handleDeleteTrain
                is Command.EditTrain -> handleEditTrain
            }
        }

    val eventReducer: Reducer<State?, Event>
        get() =
            reducerOf(
                Event.TrainCreated::class to handleCreatedEvent,
                Event.TrainDeleted::class to handleDeletedEvent,
                Event.TrainEdited::class to handleEditedEvent
            )

}