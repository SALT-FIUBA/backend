package io.kauth.service.accessrequest

import kotlinx.serialization.Serializable
import io.kauth.abstractions.reducer.Reducer
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import java.util.UUID

//ManualReservation
object AccessRequest {

    @Serializable
    sealed interface Status {

        @Serializable
        object Pending : Status

        @Serializable
        data class Accepted(
            val acceptedAt: Instant,
            val acceptedBy: String
        ): Status

        @Serializable
        data class PendingAccept(
            val acceptedAt: Instant,
            val acceptedBy: String
        ): Status

        @Serializable
        data class Confirmed(
            val confirmedAt: Instant,
            val confirmedBy: String
        ): Status

        @Serializable
        data class PendingConfirmation(
            val confirmedAt: Instant,
            val confirmedBy: String
        ): Status

        @Serializable
        data class Rejected(
            val rejectedAt: Instant,
            val rejectedBy: String
        ): Status

    }

    @Serializable
    data class State(
        @Contextual
        val occasionId: UUID,
        val categoryName: String,
        val description: String,
        val createdAt: Instant,
        val userId: String,
        val status: Status,
        val places: Int
    )

    @Serializable
    sealed interface Command {
        @Serializable
        data class CreateRequest(
            @Contextual
            val occasionId: UUID,
            val categoryName: String,
            val userId: String,
            val createAt: Instant,
            val description: String,
            val places: Int
        ) : Command

        @Serializable
        data class AcceptRequest(
            val acceptAt: Instant,
            val acceptBy: String
        ) : Command

        @Serializable
        data class AcceptRequestResult(
            val accepted: Boolean,
            val reason: String,
            val at: Instant
        ) : Command

        @Serializable
        data class ConfirmRequest(
            val confirmAt: Instant,
            val confirmBy: String
        ) : Command

        @Serializable
        data class ConfirmRequestResult(
            val confirmed: Boolean,
            val reason: String,
            val at: Instant
        ) : Command

    }

    @Serializable
    sealed interface Event {
        @Serializable
        data class RequestCreated(
            @Contextual
            val occasionId: UUID,
            val userId: String,
            val createdAt: Instant,
            val categoryName: String,
            val description: String,
            val places: Int
        ) : Event

        @Serializable
        data class RequestPendingAccept(
            val acceptedAt: Instant,
            val acceptedBy: String
        ) : Event

        @Serializable
        data class RequestAccepted(
            val acceptedAt: Instant,
            val reason: String
        ) : Event

        @Serializable
        data class RequestRejected(
            val rejectedAt: Instant,
            val reason: String
        ) : Event

        @Serializable
        data class RequestConfirmed(
            val confirmedAt: Instant,
            val reason: String
        ) : Event

        @Serializable
        data class RequestPendingConfirmation(
            val confirmedAt: Instant,
            val confirmedBy: String
        ) : Event

    }

    @Serializable
    sealed interface Error : Event {
        @Serializable
        data object RequestAlreadyExists : Error

        @Serializable
        data object RequestNotFound : Error

        @Serializable
        data class InvalidTransition(val message: String) : Error
    }

    val reducer: Reducer<State?, Event> = Reducer { state, event ->
        when (event) {
            is Event.RequestCreated -> State(
                occasionId = event.occasionId,
                userId = event.userId,
                status = Status.Pending,
                createdAt = event.createdAt,
                categoryName = event.categoryName,
                description = event.description,
                places = event.places
            )

            is Event.RequestAccepted -> {
                state?.copy(status = Status.Accepted(event.acceptedAt, event.reason))
            }

            is Event.RequestConfirmed -> {
                state?.copy(status = Status.Confirmed(event.confirmedAt, event.reason))
            }

            is Error -> state

            is Event.RequestRejected -> {
                state?.copy(status = Status.Rejected(event.rejectedAt, event.reason))
            }

            is Event.RequestPendingAccept -> {
                state?.copy(status = Status.PendingAccept(event.acceptedAt, event.acceptedBy))
            }

            is Event.RequestPendingConfirmation -> {
                state?.copy(status = Status.PendingConfirmation(event.confirmedAt, event.confirmedBy))
            }
        }
    }

    val commandHandler: CommandMonad<Command, State?, Event, Output> = CommandMonad.Do { exit ->
        val state = !getState
        when (val cmd = command) {
            is Command.CreateRequest -> {
                if (state != null) {
                    !emitEvents(Error.RequestAlreadyExists)
                    !exit(Failure("Request already exists"))
                }
                !emitEvents(
                    Event.RequestCreated(
                        cmd.occasionId,
                        cmd.userId,
                        cmd.createAt,
                        cmd.categoryName,
                        cmd.description,
                        cmd.places
                    )
                )
                Ok
            }

            is Command.AcceptRequestResult -> {

                if (state == null) {
                    !emitEvents(Error.RequestNotFound)
                    !exit(Failure("Request not found"))
                }

                if (state.status !is Status.PendingAccept) {
                    !emitEvents(Error.InvalidTransition("Can only accept Pending requests"))
                    !exit(Failure("Invalid transition"))
                }

                if (cmd.accepted) {
                    !emitEvents(Event.RequestAccepted(cmd.at, cmd.reason))
                } else {
                    !emitEvents(Event.RequestRejected(cmd.at, cmd.reason))
                }
                Ok

            }
            is Command.ConfirmRequestResult -> {
                if (state == null) {
                    !emitEvents(Error.RequestNotFound)
                    !exit(Failure("Request not found"))
                }
                if (state.status !is Status.PendingConfirmation) {
                    !emitEvents(Error.InvalidTransition("Can only confirm pending confirmation requests"))
                    !exit(Failure("Invalid transition"))
                }
                if (cmd.confirmed) {
                    !emitEvents(Event.RequestConfirmed(cmd.at, cmd.reason))
                } else {
                    !emitEvents(Event.RequestRejected(cmd.at, cmd.reason))
                }
                Ok
            }
            is Command.AcceptRequest -> {
                if (state == null) {
                    !emitEvents(Error.RequestNotFound)
                    !exit(Failure("Request not found"))
                }
                if (state.status != Status.Pending) {
                    !emitEvents(Error.InvalidTransition("Can only accept Pending requests"))
                    !exit(Failure("Invalid transition"))
                }
                !emitEvents(
                    Event.RequestPendingAccept(
                        cmd.acceptAt,
                        cmd.acceptBy
                    )
                )
                Ok
            }
            is Command.ConfirmRequest -> {
                if (state == null) {
                    !emitEvents(Error.RequestNotFound)
                    !exit(Failure("Request not found"))
                }
                if (state.status !is Status.Accepted) {
                    !emitEvents(Error.InvalidTransition("Can only confirm Accepted requests"))
                    !exit(Failure("Invalid transition"))
                }
                !emitEvents(Event.RequestPendingConfirmation(cmd.confirmAt, cmd.confirmBy))
                Ok
            }
        }
    }
}
