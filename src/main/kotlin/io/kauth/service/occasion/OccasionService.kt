package io.kauth.service.occasion

import io.kauth.abstractions.command.CommandHandler
import io.kauth.abstractions.result.Output
import io.kauth.client.eventStore.EventStoreClient
import io.kauth.client.eventStore.commandHandler
import io.kauth.client.eventStore.computeStateResult
import io.kauth.client.eventStore.stream
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.getService
import io.kauth.monad.stack.registerService
import io.kauth.service.EventStoreService
import io.kauth.util.Async
import java.util.UUID

object OccasionService : EventStoreService {

    override val name = "occasion"

    data class Command(
        val handle: (id: UUID) -> CommandHandler<Occasion.Command, Output>
    )

    data class Query(
        val readState: (id: UUID) -> Async<Occasion.State?>
    )

    data class Interface(
        val command: Command,
        val query: Query,
    )

    override val start = AppStack.Do {

        val client = !getService<EventStoreClient>()

        val commands = Command(
            handle = { id ->
                stream<Occasion.Event, Occasion.State>(client, id.streamName, id.snapshotName)
                    .commandHandler(Occasion.commandStateMachine, Occasion.eventReducer)
            }
        )

        val query = Query(
            readState = { id ->
                stream<Occasion.Event, Occasion.State>(client, id.streamName, id.snapshotName)
                    .computeStateResult(Occasion.eventReducer)
            }
        )

        !registerService(
            Interface(
                command = commands,
                query = query
            )
        )

        !OccasionProjection.sqlEventHandler

        !OccasionApiRest.api

        !OccasionEventHandler.start

    }
}
