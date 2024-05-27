package io.kauth.service.publisher

import io.kauth.abstractions.command.CommandHandler
import io.kauth.abstractions.result.Output
import io.kauth.client.eventStore.EventStoreClient
import io.kauth.client.eventStore.commandHandler
import io.kauth.client.eventStore.computeStateResult
import io.kauth.client.eventStore.stream
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.getService
import io.kauth.monad.stack.registerService
import io.kauth.service.AppService
import io.kauth.service.publisher.Publisher.asCommand
import io.kauth.util.Async
import java.util.*


object PublisherService : AppService {

    val STREAM_PREFIX = "publisher-"
    val SNAPSHOT_STREAM_PREFIX = "publisher_snapshot-"
    val UUID.streamName get() = STREAM_PREFIX + this.toString()
    val UUID.snapshotName get() = SNAPSHOT_STREAM_PREFIX + this.toString()

    data class Command(
        val handle: (id: UUID) -> CommandHandler<Publisher.Command, Output>
    )

    data class Query(
        val readState: (id: UUID) -> Async<Publisher.State?>
    )

    data class Interface(
        val command: Command,
        val query: Query,
    )

    override val start =
        AppStack.Do {

            val client = !getService<EventStoreClient>()

            val commands = Command(
                handle = { id ->
                    stream<Publisher.Event, Publisher.State>(client, id.streamName, id.snapshotName)
                        .commandHandler(Publisher::stateMachine) { it.asCommand }
                }
            )

            val query = Query(
                readState = { id ->
                    stream<Publisher.Event,Publisher.State>(client, id.streamName, id.snapshotName)
                        .computeStateResult(Publisher::stateMachine) { it.asCommand }
                }
            )

            !registerService(
                Interface(
                    query = query,
                    command = commands,
                )
            )

            !PublisherApiRest.api

            !PublisherEventHandler.eventHandler

        }
}