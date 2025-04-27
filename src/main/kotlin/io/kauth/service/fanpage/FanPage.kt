package io.kauth.service.fanpage

import io.kauth.abstractions.reducer.Reducer
import io.kauth.abstractions.reducer.reducerOf
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

object FanPage {

    @Serializable
    data class State(
        val createdBy: String,
        val admins: List<String>,
        val createdAt: Instant,
        val name: String,
        val description: String,
        val profilePhoto: String,
        val location: String,
        val email: String,
        val phone: String,
        val website: String,
    )

    @Serializable
    sealed interface Command {
        @Serializable
        data class Create(
            val createdBy: String,
            val createdAt: Instant,
            val name: String,
            val description: String,
            val profilePhoto: String,
            val location: String,
            val email: String,
            val phone: String,
            val website: String,
        ): Command
        @Serializable
        data class AddAdmins(
            val admins: List<String>
        ): Command
    }

    @Serializable
    sealed interface Event {
        @Serializable
        data class Created(
            val state: State
        ) : Event
        @Serializable
        data class AdminsAdded(
            val admins: List<String>
        ) : Event
    }

    @Serializable
    sealed interface Error : Event {
        @Serializable
        data object AlreadyExists : Error
        @Serializable
        data class InvalidCommand(val message: String) : Error
    }

    val handleCreatedEvent: Reducer<State?, Event.Created> = Reducer { _, event ->
        event.state
    }

    val handleCreate: CommandMonad<Command.Create, State?, Event, Output> = CommandMonad.Do { exit ->
        val state = !getState
        if (state != null) {
            !emitEvents(Error.AlreadyExists)
            !exit(Failure("FanPage already exists"))
        }
        !emitEvents(
            Event.Created(
                State(
                    name = command.name,
                    createdBy = command.createdBy,
                    createdAt = command.createdAt,
                    description = command.description,
                    profilePhoto = command.profilePhoto,
                    location = command.location,
                    email = command.email,
                    phone = command.phone,
                    website = command.website,
                    admins = emptyList()
                )
            )
        )
        Ok
    }


    val commandStateMachine: CommandMonad<Command, State?, Event, Output> = CommandMonad.Do { exit ->
        val command = !getCommand
        !when (command) {
            is Command.Create -> handleCreate
            else -> error("Unhandled command")
        }
    }

    val eventReducer: Reducer<State?, Event> = reducerOf(
        Event.Created::class to handleCreatedEvent,
    )

}