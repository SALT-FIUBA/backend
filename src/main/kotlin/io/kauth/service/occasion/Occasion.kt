package io.kauth.service.occasion

import io.kauth.abstractions.reducer.Reducer
import io.kauth.abstractions.reducer.reducerOf
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

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
        val owners: List<String>? = null,
        val createdAt: Instant,
        val name: String? = null,
        val disabled: Boolean = false,
        @Contextual
        val fanPageId: UUID? = null
    )

    @Serializable
    sealed interface Command {
        @Serializable
        data class CreateOccasion(
            val categories: List<Category>,
            val date: LocalDate,
            val description: String,
            val owners: List<String>? = null,
            val createdAt: Instant,
            val name: String? = null,
            @Contextual
            val fanPageId: UUID? = null
        ) : Command

        @Serializable
        data class Visibility(
            val disabled: Boolean
        ) : Command
    }

    @Serializable
    sealed interface Event {
        @Serializable
        data class OccasionCreated(
            val occasion: State
        ) : Event

        @Serializable
        data class VisibilityChanged(
            val disabled: Boolean
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

    val handleVisibilityEvent: Reducer<State?, Event.VisibilityChanged> = Reducer { state, event ->
        state?.copy(disabled = event.disabled)
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

        if (command.fanPageId == null) {
            !emitEvents(Error.InvalidCommand("Fanpage cannot be null"))
            !exit(Failure("Fanpage cannot be null"))
        }

        !emitEvents(
            Event.OccasionCreated(
                State(
                    categories = command.categories,
                    date = command.date,
                    description = command.description,
                    owners = emptyList(),
                    createdAt = command.createdAt,
                    name = command.name,
                    fanPageId = command.fanPageId
                )
            )
        )

        Ok
    }

    val hanndlVisibility: CommandMonad<Command.Visibility, State?, Event, Output> = CommandMonad.Do { exit ->
        val state = !getState
        if (state == null) {
            !emitEvents(Error.InvalidCommand("Occasion does not exist"))
            !exit(Failure("Occasion does not exist"))
        }
        !emitEvents(Event.VisibilityChanged(command.disabled))
        Ok
    }

    val commandStateMachine: CommandMonad<Command, State?, Event, Output> = CommandMonad.Do { exit ->
        val command = !getCommand
        !when (command) {
            is Command.CreateOccasion -> handleCreate
            is Command.Visibility -> hanndlVisibility
        }
    }

    val eventReducer: Reducer<State?, Event> = reducerOf(
        Event.OccasionCreated::class to handleCreatedEvent,
        Event.VisibilityChanged::class to handleVisibilityEvent
    )
}
