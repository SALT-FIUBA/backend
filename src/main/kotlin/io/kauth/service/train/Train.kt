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
    }

    @Serializable
    sealed interface Event {
        @Serializable
        data class TrainCreated(
            val state: State
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

    val commandStateMachine get() =
        CommandMonad.Do<Command, State?, Event, Output> { exit ->
            val command = !getCommand
            !when (command) {
                is Command.CreateTrain -> handleCreate
            }
        }

    val eventReducer: Reducer<State?, Event>
        get() =
            reducerOf(
                Event.TrainCreated::class to handleCreatedEvent,
            )

}