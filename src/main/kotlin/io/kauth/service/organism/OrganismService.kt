package io.kauth.service.organism

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


object OrganismService : AppService {

    val STREAM_PREFIX = "organism-"
    val SNAPSHOT_STREAM_PREFIX = "organism_snapshot-"
    val UUID.streamName get() = STREAM_PREFIX + this.toString()
    val UUID.snapshotName get() = SNAPSHOT_STREAM_PREFIX + this.toString()

    data class Command(
        val handle: (id: UUID) -> CommandHandler<Organism.Command, Output>
    )

    data class Query(
        val readState: (id: UUID) -> Async<Organism.State?>
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
                    stream<Organism.Event, Organism.State>(client, id.streamName, id.snapshotName)
                        .commandHandler(Organism.commandStateMachine, Organism.eventReducer)
                }
            )

            val query = Query(
                readState = { id ->
                    stream<Organism.Event, Organism.State>(client, id.streamName, id.snapshotName)
                        .computeStateResult(Organism.eventReducer)
                }
            )

            !registerService(
                Interface(
                    query = query,
                    command = commands,
                )
            )

            !OrganismProjection.sqlEventHandler

            !OrganismApiRest.api


        }

}

