package io.kauth.service.accessrequest

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
import java.util.UUID

object AccessRequestService : AppService {

    override val name: String
        get() = "access-request"

    private const val STREAM_PREFIX = "access_request-"
    private const val SNAPSHOT_STREAM_PREFIX = "access_request_snapshot-"

    private val UUID.streamName get() = STREAM_PREFIX + this.toString()
    private val UUID.snapshotName get() = SNAPSHOT_STREAM_PREFIX + this.toString()

    data class Command(
        val handle: (id: UUID) -> CommandHandler<AccessRequest.Command, Output>
    )

    data class Query(
        val readState: (id: UUID) -> Async<AccessRequest.State?>
    )

    data class Interface(
        val command: Command,
        val query: Query,
    )

    override val start = AppStack.Do {
        val client = !getService<EventStoreClient>()

        val commands = Command(
            handle = { id ->
                stream<AccessRequest.Event, AccessRequest.State>(client, id.streamName, id.snapshotName)
                    .commandHandler(AccessRequest.commandHandler, AccessRequest.reducer)
            }
        )

        val query = Query(
            readState = { id ->
                stream<AccessRequest.Event, AccessRequest.State>(client, id.streamName, id.snapshotName)
                    .computeStateResult(AccessRequest.reducer)
            }
        )

        !registerService(
            Interface(
                command = commands,
                query = query
            )
        )

        !AccessRequestProjection.sqlEventHandler

        !AccessRequestApiRest.api
    }
}
