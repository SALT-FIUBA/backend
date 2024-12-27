package io.kauth.service.deviceproject

import io.kauth.abstractions.reducer.Reducer
import io.kauth.abstractions.reducer.reducerOf
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

object DeviceProject {

    @Serializable
    data class State(
        val name: String,
        val createdBy: String,
        val owners: List<String>,
        val createdAt: Instant,
        val enabled: Boolean = true
    )

    @Serializable
    sealed interface Command {

        @Serializable
        data class CreateProject(
            val createdAt: Instant,
            val createdBy: String,
            val name: String,
            val owners: List<String> //Acessos?
        ) : Command

        @Serializable
        data class SetEnabled(
            val enabled: Boolean
        ) : Command

    }

    @Serializable
    sealed interface Event {
        @Serializable
        data class ProjectCreated(val init: State) : Event

        @Serializable
        data class EnabledSet(val enabled: Boolean) : Event
    }

    @Serializable
    sealed interface Error : Event {
        @Serializable
        data class UnknownError(val message: String): Error
    }

    val createdEventHandler get() = Reducer<State?, Event.ProjectCreated> { _, event ->
        event.init
    }

    val enabledEventHandler get() = Reducer<State?, Event.EnabledSet> { state, event ->
        state?.copy(enabled = event.enabled)
    }

    val createHandler get() = CommandMonad.Do<Command.CreateProject, State?, Event, Output> { exit ->
        val state = !getState
        val command = !getCommand
        if (state != null) {
            !emitEvents(Error.UnknownError("Project already exists"))
            !exit(Failure("Project already exists"))
        }
        val init = State(
            owners = command.owners,
            createdBy = command.createdBy,
            createdAt = command.createdAt,
            name = command.name
        )
        !emitEvents(Event.ProjectCreated(init))
        Ok
    }

    val enabledHandler get() = CommandMonad.Do<Command.SetEnabled, State?, Event, Output> { exit ->
        val state = !getState
        if (state == null) {
            !emitEvents(Error.UnknownError("Project does not exists"))
            !exit(Failure("Project does not exists"))
        }
        val command = !getCommand
        !emitEvents(Event.EnabledSet(command.enabled))
        Ok
    }

    val commandStateMachine get() =
        CommandMonad.Do<Command, State?, Event, Output> {
            val command = !getCommand
            when(command) {
                is Command.CreateProject -> !createHandler
                is Command.SetEnabled -> !enabledHandler
            }
        }

    val eventReducer get(): Reducer<State?, Event> =
        reducerOf(
            Event.ProjectCreated::class to createdEventHandler,
            Event.EnabledSet::class to enabledEventHandler,
        )

}