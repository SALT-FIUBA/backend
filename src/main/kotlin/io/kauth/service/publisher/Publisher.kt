package io.kauth.service.publisher

import io.kauth.abstractions.reducer.Reducer
import io.kauth.abstractions.reducer.reducerOf
import io.kauth.abstractions.result.AppResult
import io.kauth.abstractions.result.Failure
import io.kauth.abstractions.result.Ok
import io.kauth.abstractions.result.Output
import io.kauth.monad.state.CommandMonad
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

//JsonPublisher
//Algo mas general recibe como data: ByteArray y un Codec<ByteArray, E> donde E podria ser Json o lo que sea
object Publisher {

    @Serializable
    sealed class Channel {
        @Serializable
        data class Mqtt(
            val topic: String
        ) : Channel()

        @Serializable
        data class Tuya(
            val code: String
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

    val handlePublishedEvent
        get() = Reducer<State?, Event.Publish> { state, event ->
            State(
                data = event.data,
                channel = event.channel,
                resource = event.resource,
                result = null
            )
        }

    val handleSetStatusEvent get() = Reducer<State?, Event.PublishResult> { state, event ->
        state?.copy(result = event.result)
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

    val eventReducer
        get() =
            reducerOf(
                Event.Publish::class to handlePublishedEvent,
                Event.PublishResult::class to handleSetStatusEvent
            )

}