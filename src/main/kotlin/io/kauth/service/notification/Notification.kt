package io.kauth.service.notification

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

object Notification {
    @Serializable
    enum class Channel {
        email,
        whatsapp
    }

    @Serializable
    enum class Status {
        pending,
        sent,
        failed
    }

    @Serializable
    data class State(
        @Contextual val resource: String,
        val channel: Channel,
        val recipient: String,
        val content: String,
        val sender: String,
        val status: Status,
        val createdAt: Instant,
        val sentAt: Instant? = null,
        val error: String? = null
    )

    @Serializable
    sealed interface Command {
        @Serializable
        data class SendNotification(
            val channel: Channel,
            val recipient: String,
            val content: String,
            val sender: String,
            val createdAt: Instant
        ) : Command

        @Serializable
        data class SendResult(
            val sentAt: Instant?,
            val success: Boolean,
            val error: String? = null
        ) : Command
    }

    @Serializable
    sealed interface Event {
        @Serializable
        data class NotificationSent(
            val sentAt: Instant
        ) : Event

        @Serializable
        data class NotificationFailed(
            val error: String
        ) : Event

        @Serializable
        data class SendNotificationEvent(
            val channel: Channel,
            val recipient: String,
            val content: String,
            val sender: String,
            val createdAt: Instant
        ) : Event
    }

    @Serializable
    sealed interface Error : Event {
        @Serializable
        data class InvalidCommand(val message: String) : Error
    }

    val handleSendNotification: CommandMonad<Command.SendNotification, State?, Event, Output> = CommandMonad.Do { exit ->
        val state = !getState
        if (state != null) {
            !emitEvents(Error.InvalidCommand("Notification already exists"))
            !exit(Failure("Notification already exists"))
        }
        if (command.recipient.isBlank() || command.content.isBlank() || command.sender.isBlank()) {
            !emitEvents(Error.InvalidCommand("Missing required fields"))
            !exit(Failure("Missing required fields"))
        }
        // Emit SendNotificationEvent for async delivery
        !emitEvents(Event.SendNotificationEvent(
            channel = command.channel,
            recipient = command.recipient,
            content = command.content,
            sender = command.sender,
            createdAt = command.createdAt
        ))
        Ok
    }

    val handleSendResult: CommandMonad<Command.SendResult, State?, Event, Output> = CommandMonad.Do { exit ->
        val state = !getState
        if (state == null) {
            !emitEvents(Error.InvalidCommand("Notification does not exist"))
            !exit(Failure("Notification does not exist"))
        }
        if (command.success) {
            !emitEvents(Event.NotificationSent(command.sentAt ?: state.createdAt))
        } else {
            !emitEvents(Event.NotificationFailed(command.error ?: "Unknown error"))
        }
        Ok
    }

    val commandStateMachine: CommandMonad<Command, State?, Event, Output> = CommandMonad.Do { exit ->
        val command = !getCommand
        !when (command) {
            is Command.SendNotification -> handleSendNotification
            is Command.SendResult -> handleSendResult
        }
    }

    val handleNotificationSent: Reducer<State?, Event.NotificationSent> = Reducer { state, event ->
        state?.copy(status = Status.sent, sentAt = event.sentAt, error = null)
            ?: state
    }

    val handleNotificationFailed: Reducer<State?, Event.NotificationFailed> = Reducer { state, event ->
        state?.copy(status = Status.failed, error = event.error)
            ?: state
    }

    val eventReducer: Reducer<State?, Event> = reducerOf(
        Event.NotificationSent::class to handleNotificationSent,
        Event.NotificationFailed::class to handleNotificationFailed
    )
}
