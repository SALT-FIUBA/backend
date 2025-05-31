package io.kauth.service.occasion

import io.kauth.abstractions.reducer.Reducer
import io.kauth.abstractions.reducer.reducerOf
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import kotlinx.datetime.*
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
    data class CategoryState(
        val name: String,
        val capacity: Int,
        val reservedPlaces: List<PlaceState>,
        val confirmedPlaces: List<PlaceState>
    )

    @Serializable
    data class PlaceState(
        val takenAt: Instant,
        val resource: String,
        val places: Int
    )

    @Serializable
    enum class Status {
        open,
        completed,
        cancelled
    }

    @Serializable
    data class State(
        val status: Status,
        @Contextual
        val fanPageId: UUID,
        val name: String,
        val description: String,
        val resource: String,
        val startDateTime: Instant,
        val endDateTime: Instant,
        val categories: List<CategoryState>,
        val disabled: Boolean,
        val createdAt: Instant,
        val location: String?
    )

    @Serializable
    sealed interface Command {

        @Serializable
        data class CreateOccasion(
            val resource: String,
            @Contextual
            val fanPageId: UUID,
            val name: String,
            val description: String,
            val categories: List<Category>,
            val startDateTime: Instant,
            val endDateTime: Instant,
            val createdAt: Instant,
            val createdBy: String,
            val location: String?
        ) : Command

        @Serializable
        data class Visibility(
            val disabled: Boolean
        ) : Command

        @Serializable
        data class ReservePlace(
            val categoryName: String,
            val resource: String,
            val takenAt: Instant,
            val places: Int
        ) : Command

        @Serializable
        data class ConfirmPlace(
            val resource: String,
            val confirmedAt: Instant,
        ) : Command

        @Serializable
        data class Cancel(
            val cancelledAt: Instant
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
        data class PlaceReserved(
            val categoryName: String? = null,
            val resource: String,
            val takenAt: Instant,
            val places: Int
        ) : Event

        @Serializable
        data class PlaceConfirmed(
            val resource: String,
            val confirmedAt: Instant,
        ) : Event

        @Serializable
        data class Cancelled(
            val cancelledAt: Instant,
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

        @Serializable
        data class CategoryFull(
            val name: String,
            val resource: String
        ) : Error
    }

    val handleCreatedEvent: Reducer<State?, Event.OccasionCreated> = Reducer { _, event ->
        event.occasion
    }

    val handleVisibilityEvent: Reducer<State?, Event.VisibilityChanged> = Reducer { state, event ->
        state?.copy(disabled = event.disabled)
    }

    val handlePlaceReserved: Reducer<State?, Event.PlaceReserved> = Reducer { state, event ->
        val category = state?.categories?.find { it.name == event.categoryName }
        if (category != null) {
            val updatedCategory =
                category.copy(
                    reservedPlaces = category.reservedPlaces + PlaceState(
                        event.takenAt,
                        event.resource,
                        event.places
                    )
                )
            state.copy(categories = state.categories.map { if (it.name == event.categoryName) updatedCategory else it })
        } else {
            state
        }
    }

    val handlePlaceConfirmed: Reducer<State?, Event.PlaceConfirmed> = Reducer { state, event ->
        val category = state?.categories?.find { it.reservedPlaces.any { it.resource == event.resource } }
        if (category != null) {
            val reservation = category.reservedPlaces.find { it.resource == event.resource }
            if (reservation == null) {
                return@Reducer state
            }
            val updatedCategory =
                category.copy(
                    reservedPlaces = category.reservedPlaces.filter { it.resource != event.resource },
                    confirmedPlaces = category.confirmedPlaces + PlaceState(
                        event.confirmedAt,
                        event.resource,
                        reservation.places
                    )
                )
            val newCategories = state.categories.map { if (it.name == category.name) updatedCategory else it }
            // Check if all categories are completed (full)
            val allFull = newCategories.all { cat ->
                val conf = cat.confirmedPlaces.sumOf { it.places }
                val res = cat.reservedPlaces.sumOf { it.places }
                (cat.capacity - conf - res) <= 0
            }
            val newStatus = if (allFull) Status.completed else state.status
            state.copy(categories = newCategories, status = newStatus)
        } else {
            state
        }
    }

    val handleCancelled: Reducer<State?, Event.Cancelled> = Reducer { state, _ ->
        state?.copy(status = Status.cancelled)
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

        if (command.startDateTime > command.endDateTime) {
            !emitEvents(Error.InvalidCommand("Start date cannot be after end date"))
            !exit(Failure("Start date cannot be after end date"))
        }
        if (command.startDateTime < Clock.System.now()) {
            !emitEvents(Error.InvalidCommand("Start date cannot be before created date"))
            !exit(Failure("Start date cannot be before now"))
        }

        if (command.categories.isEmpty()) {
            !emitEvents(Error.InvalidCommand("Categories cannot be empty"))
            !exit(Failure("Categories cannot be empty"))
        }

        if (command.categories.any {it.capacity == 0}) {
            !emitEvents(Error.InvalidCommand("Category capacity cannot be 0"))
            !exit(Failure("Category capacity cannot be 0"))
        }

        !emitEvents(
            Event.OccasionCreated(
                State(
                    categories = command.categories.map {
                        CategoryState(
                            it.name,
                            it.capacity,
                            emptyList(),
                            emptyList()
                        )
                    },
                    description = command.description,
                    name = command.name,
                    fanPageId = command.fanPageId,
                    startDateTime = command.startDateTime,
                    endDateTime = command.endDateTime,
                    disabled = false,
                    resource = command.resource,
                    createdAt = command.createdAt,
                    location = command.location,
                    status = Status.open
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

    val handleReservePlace: CommandMonad<Command.ReservePlace, State?, Event, Output> = CommandMonad.Do { exit ->
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
        if (category.confirmedPlaces.any { it.resource == command.resource}) {
            !emitEvents(Error.InvalidCommand("Place already taken"))
            !exit(Failure("Place already taken"))
        }
        if (command.takenAt > state.startDateTime) {
            !emitEvents(Error.InvalidCommand("Reservation time cannot be before occasion start time"))
            return@Do Ok
        }
        val confirmed = category.confirmedPlaces.fold(0) {
                acc, it -> acc + it.places
        }
        val reserved = category.reservedPlaces.fold(0) {
                acc, it -> acc + it.places
        }
        val remaining = category.capacity - confirmed - reserved

        if (remaining <= 0) {
            !emitEvents(Error.CategoryFull(category.name, command.resource))
            return@Do Ok
        }
        !emitEvents(Event.PlaceReserved(command.categoryName, command.resource, command.takenAt, command.places))
        Ok
    }

    val handleConfirmPlace: CommandMonad<Command.ConfirmPlace, State?, Event, Output> = CommandMonad.Do { exit ->

        val state = !getState

        if (state == null) {
            !emitEvents(Error.InvalidCommand("Occasion does not exist"))
            !exit(Failure("Occasion does not exist"))
        }
        val category = state.categories.find { it.reservedPlaces.any { it.resource == command.resource } }

        if(category == null) {
            !emitEvents(Error.InvalidCommand("Category does not exist"))
            !exit(Failure("Category does not exist"))
        }

        val reservation = category.reservedPlaces.find { it.resource == command.resource }

        if(reservation == null) {
            !emitEvents(Error.InvalidCommand("Reservation does not exist"))
            !exit(Failure("Reservation does not exist"))
        }

        !emitEvents(
            Event.PlaceConfirmed(
                command.resource,
                command.confirmedAt
            )
        )

        Ok
    }

    val handleCancel: CommandMonad<Command.Cancel, State?, Event, Output> = CommandMonad.Do { exit ->
        val state = !getState
        if (state == null) {
            !emitEvents(Error.InvalidCommand("Occasion does not exist"))
            !exit(Failure("Occasion does not exist"))
        }
        if (state.status == Status.cancelled) {
            !emitEvents(Error.InvalidCommand("Occasion already cancelled"))
            !exit(Failure("Occasion already cancelled"))
        }
        if (state.status == Status.completed) {
            !emitEvents(Error.InvalidCommand("Occasion already completed"))
            !exit(Failure("Occasion already completed"))
        }
        !emitEvents(Event.Cancelled(command.cancelledAt))
        Ok
    }

    val commandStateMachine: CommandMonad<Command, State?, Event, Output> = CommandMonad.Do { exit ->
        val command = !getCommand
        !when (command) {
            is Command.CreateOccasion -> handleCreate
            is Command.Visibility -> hanndlVisibility
            is Command.ReservePlace -> handleReservePlace
            is Command.ConfirmPlace -> handleConfirmPlace
            is Command.Cancel -> handleCancel
        }
    }

    val eventReducer: Reducer<State?, Event> = reducerOf(
        Event.OccasionCreated::class to handleCreatedEvent,
        Event.VisibilityChanged::class to handleVisibilityEvent,
        Event.PlaceReserved::class to handlePlaceReserved,
        Event.PlaceConfirmed::class to handlePlaceConfirmed,
        Event.Cancelled::class to handleCancelled,
    )

}
