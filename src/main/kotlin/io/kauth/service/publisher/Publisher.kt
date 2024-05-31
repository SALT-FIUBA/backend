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

    val Event.asCommand get() =
        when(this) {
            is Event.PublishResult -> Command.PublishResult(result)
            is Event.Publish -> Command.Publish(channel, resource, data)
        }

    @Serializable
    data class State(
        val data: JsonElement,
        val resource: String,
        val channel: Channel,
        val result: AppResult<String>?
    )

    fun handleSetStatus(
        command: Command.PublishResult
    ) = StateMonad.Do<State?, Event, Output> { exit ->
        val state = !getState
        if(state == null) {
            !exit(Failure("Message does not exists"))
        }
        if(state.result != null) {
            !exit(Failure("Already finished"))
        }
        !setState(state.copy(result = command.result))
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
        val data =
            State(
                data = command.data,
                channel = command.channel,
                resource = command.resource,
                result = null
            )
        !setState(data)
        //Solo emito 1 evneto en EvenSourcing, los eventos son el estado....
        !emitEvents(Event.Publish(command.data, command.resource, command.channel))
        Ok
    }

    fun stateMachine(
        command: Command
    ) = when (command) {
        is Command.Publish -> handlePublish(command)
        is Command.PublishResult -> handleSetStatus(command)
    }

}