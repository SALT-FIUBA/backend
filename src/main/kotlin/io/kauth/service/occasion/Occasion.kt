package io.kauth.service.occasion

import io.kauth.abstractions.reducer.Reducer
import io.kauth.abstractions.reducer.reducerOf
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

object Occasion {

    @Serializable
    sealed interface OccasionType {

        @Serializable
        data class UniqueDate(
            val date: LocalDateTime? = null
        ) : OccasionType

        @Serializable
        data class RecurringEvent(
            val startDateTime: LocalDateTime? = null,
            val endDateTime: LocalDateTime,
            val weekdays: List<DayOfWeek>
        ) : OccasionType

    }

    @Serializable
    data class Category(
        val name: String,
        val capacity: Int
    )

    @Serializable
    data class CategoryState(
        val name: String,
        val capacity: Int,
        val places: List<Places> = emptyList()
    )

    @Serializable
    data class Places(
        val takenAt: Instant,
        val accessRequestId: String
    )

    @Serializable
    data class State(
        val categories: List<CategoryState>,
        val date: LocalDate? = null,
        val description: String,
        val owners: List<String>? = null,
        val createdAt: Instant,
        val name: String? = null,
        val disabled: Boolean = false,
        @Contextual
        val fanPageId: UUID? = null,
        val totalCapacity: Int? = null,
        val occasionType: OccasionType? = null,
        val startDateTime: LocalDateTime? = null,
        val endDateTime: LocalDateTime? = null,
    )

    @Serializable
    sealed interface Command {
        @Serializable
        data class CreateOccasion(
            val categories: List<Category>,
            val date: LocalDate? = null,
            val description: String,
            val owners: List<String>? = null,
            val createdAt: Instant,
            val name: String? = null,
            @Contextual
            val fanPageId: UUID? = null,
            val totalCapacity: Int? = null,
            val occasionType: OccasionType? = null,
            val startDateTime: LocalDateTime? = null,
            val endDateTime: LocalDateTime? = null,
        ) : Command

        @Serializable
        data class Visibility(
            val disabled: Boolean
        ) : Command

        @Serializable
        data class TakePlace(
            val categoryName: String,
            val accessRequestId: String,
            val takenAt: Instant,
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

        @Serializable
        data class PlaceTaken(
            val categoryName: String,
            val accessRequestId: String,
            val takenAt: Instant
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

    val handlePlaceTaken: Reducer<State?, Event.PlaceTaken> = Reducer { state, event ->
        val category = state?.categories?.find { it.name == event.categoryName }
        if (category != null) {
            val updatedCategory = category.copy(places = category.places + Places(event.takenAt, event.accessRequestId))
            state.copy(categories = state.categories.map { if (it.name == event.categoryName) updatedCategory else it })
        } else {
            state
        }
    }

    val handleCreate: CommandMonad<Command.CreateOccasion, State?, Event, Output> = CommandMonad.Do { exit ->
        val state = !getState

        if (state != null) {
            !emitEvents(Error.OccasionAlreadyExists)
            !exit(Failure("Occasion already exists"))
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
                    categories = command.categories.map { CategoryState(it.name, it.capacity) },
                    date = command.date,
                    description = command.description,
                    owners = emptyList(),
                    createdAt = command.createdAt,
                    name = command.name,
                    fanPageId = command.fanPageId,
                    totalCapacity = command.totalCapacity,
                    occasionType = command.occasionType,
                    startDateTime = command.startDateTime,
                    endDateTime = command.endDateTime,
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

    val handleTakePlace: CommandMonad<Command.TakePlace, State?, Event, Output> = CommandMonad.Do { exit ->
        val state = !getState
        if (state == null) {
            !emitEvents(Error.InvalidCommand("Occasion does not exist"))
            !exit(Failure("Occasion does not exist"))
        }

        val category = state.categories.find { it.name == command.categoryName }

        if(category == null) {
            !emitEvents(Error.InvalidCommand("Category does not exist"))
            !exit(Failure("Category does not exist"))
        }

        if (category.places.any { it.accessRequestId == command.accessRequestId }) {
            !emitEvents(Error.InvalidCommand("Place already taken by ${command.accessRequestId}"))
            !exit(Failure("Place already taken"))
        }

        if (category.places.size >= category.capacity) {
            !emitEvents(Error.InvalidCommand("No more places available"))
            !exit(Failure("No more places available"))
        }

        !emitEvents(Event.PlaceTaken(command.categoryName, command.accessRequestId, command.takenAt))

        Ok
    }

    val commandStateMachine: CommandMonad<Command, State?, Event, Output> = CommandMonad.Do { exit ->
        val command = !getCommand
        !when (command) {
            is Command.CreateOccasion -> handleCreate
            is Command.Visibility -> hanndlVisibility
            is Command.TakePlace -> handleTakePlace
        }
    }

    val eventReducer: Reducer<State?, Event> = reducerOf(
        Event.OccasionCreated::class to handleCreatedEvent,
        Event.VisibilityChanged::class to handleVisibilityEvent,
        Event.PlaceTaken::class to handlePlaceTaken
    )
}
