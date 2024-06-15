package io.kauth.service.publisher

import io.kauth.abstractions.result.AppResult
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import io.kauth.monad.state.EventMonad
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

object Publisher {

    @Serializable
    sealed class Channel {
        @Serializable
        data class Mqtt(
            val topic: String
        ) : Channel()
    }

    @Serializable
    sealed interface Command {

        @Serializable
        data class Publish(
            val channel: Channel,
            val resource: String,
            val data: JsonElement
        ): Command

        @Serializable
        data class PublishResult(
            val result: AppResult<String>
        ): Command

    }

    //SOLO TENGO QUE USAR EVENTOS, Los comando son al pedo ?
    @Serializable
    sealed interface Event {

        @Serializable
        data class Publish(
            val data: JsonElement,
            val resource: String,
            val channel: Channel
        ): Event

        @Serializable
        data class PublishResult(
            val result: AppResult<String>
        ): Event

    }

    @Serializable
    data class State(
        val data: JsonElement,
        val resource: String,
        val channel: Channel,
        val result: AppResult<String>?
    )

    val handlePublishedEvent get() =EventMonad.Do<State?, Event.Publish, Output> { exit ->
        val event = !getEvent
        !setState(
            State(
                data = event.data,
                channel = event.channel,
                resource = event.resource,
                result = null
            )
        )
        Ok
    }

    val handleSetStatusEvent get() = EventMonad.Do<State?, Event.PublishResult, Output> { exit ->
        val state = !getState
        !setState(state?.copy(result = event.result))
        Ok
    }

    val handleSetStatus get() = CommandMonad.Do<Command.PublishResult, State?, Event, Output> { exit ->
        val state = !getState ?: !exit(Failure("Message does not exists"))
        if(state.result != null) {
            !exit(Failure("Already finished"))
        }
        !emitEvents(Event.PublishResult(command.result))
        Ok
    }

    val handlePublish get() = CommandMonad.Do<Command.Publish, State?, Event, Output> { exit ->
        val state = !getState
        if(state != null) {
            !exit(Failure("Message already exists."))
        }
        !emitEvents(Event.Publish(command.data, command.resource, command.channel))
        Ok
    }

    val stateMachine get() =
        CommandMonad.Do<Command, State?, Event, Output> { exit ->
            val command = !getCommand
            !when (command) {
                is Command.Publish -> handlePublish
                is Command.PublishResult -> handleSetStatus
            }
        }

    val eventStateMachine get() =
        EventMonad.Do<State?, Event, Output> { exit ->
            val event = !getEvent
            !when(event) {
                is Event.Publish -> handlePublishedEvent
                is Event.PublishResult -> handleSetStatusEvent
            }
        }

}