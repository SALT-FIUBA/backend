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
import io.kauth.util.Async
import java.util.*


object PublisherService : AppService {

    override val name: String
        get() = "publisher"

    val STREAM_PREFIX = "publisher-"
    val UUID.streamName get() = STREAM_PREFIX + this.toString()

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
                    stream<Publisher.Event, Publisher.State>(client, id.streamName)
                        .commandHandler(Publisher.stateMachine, Publisher.eventReducer)
                }
            )

            val query = Query(
                readState = { id ->
                    stream<Publisher.Event,Publisher.State>(client, id.streamName)
                        .computeStateResult(Publisher.eventReducer)
                }
            )

            !registerService(
                Interface(
                    query = query,
                    command = commands,
                )
            )

            !PublisherApiRest.api

            !PublisherEventHandler.mqttEventHandler

            !PublisherProjection.sqlEventHandler

        }
}