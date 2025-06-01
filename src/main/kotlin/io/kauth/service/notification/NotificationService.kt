package io.kauth.service.notification

import io.kauth.abstractions.command.CommandHandler
import io.kauth.abstractions.result.Output
import io.kauth.client.brevo.Brevo
import io.kauth.client.eventStore.EventStoreClient
import io.kauth.client.eventStore.commandHandler
import io.kauth.client.eventStore.computeStateResult
import io.kauth.client.eventStore.stream
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.findConfig
import io.kauth.monad.stack.getService
import io.kauth.monad.stack.registerService
import io.kauth.service.EventStoreService
import io.kauth.service.auth.AuthConfig
import io.kauth.service.auth.AuthService
import io.kauth.util.Async
import io.kauth.util.not
import java.util.UUID

object NotificationService : EventStoreService {
    override val name = "notification.v0.1"

    data class Command(
        val handle: (id: UUID) -> CommandHandler<Notification.Command, Output>
    )

    data class Query(
        val readState: (id: UUID) -> Async<Notification.State?>
    )

    data class Interface(
        val command: Command,
        val query: Query,
    )

    override val start = AppStack.Do {
        val client = !getService<EventStoreClient>()

        val config = !findConfig<NotificationConfig>(name) ?: return@Do

        if (config.brevoApiKey != null) {
            !registerService(!Brevo.newClient(config.brevoApiKey))
        }

        val commands = Command(
            handle = { id ->
                stream<Notification.Event, Notification.State>(client, id.streamName, id.snapshotName)
                    .commandHandler(Notification.commandStateMachine, Notification.eventReducer)
            }
        )

        val query = Query(
            readState = { id ->
                stream<Notification.Event, Notification.State>(client, id.streamName, id.snapshotName)
                    .computeStateResult(Notification.eventReducer)
            }
        )

        !registerService(
            Interface(
                command = commands,
                query = query
            )
        )

        !NotificationApiRest.api
        !NotificationEventHandler.start
    }
}

