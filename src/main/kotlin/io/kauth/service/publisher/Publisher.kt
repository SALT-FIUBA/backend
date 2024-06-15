package io.kauth.service.publisher

import io.kauth.abstractions.result.AppResult
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.StateMonad
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

    fun handlePublishedEvent(
        event: Event.Publish
    ) = StateMonad.Do<State?, Event, Output> { exit ->
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

    fun handleSetStatusEvent(
        event : Event.PublishResult
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        val state = !getState
        !setState(state?.copy(result = event.result))
        Ok
    }

    fun handleSetStatus(
        command: Command.PublishResult
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        val state = !getState ?: !exit(Failure("Message does not exists"))
        if(state.result != null) {
            !exit(Failure("Already finished"))
        }
        !emitEvents(Event.PublishResult(command.result))
        Ok
    }

    fun handlePublish(
        command: Command.Publish
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        val state = !getState
        if(state != null) {
            !exit(Failure("Message already exists."))
        }
        !emitEvents(Event.Publish(command.data, command.resource, command.channel))
        Ok
    }

    fun stateMachine(
        command: Command
    ) = when (command) {
        is Command.Publish -> handlePublish(command)
        is Command.PublishResult -> handleSetStatus(command)
    }

    fun eventStateMachine(
        event: Event
    ) = when(event) {
        is Event.Publish -> handlePublishedEvent(event)
        is Event.PublishResult -> handleSetStatusEvent(event)
    }

}