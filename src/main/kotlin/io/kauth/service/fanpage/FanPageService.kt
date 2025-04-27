package io.kauth.service.fanpage

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

object FanPageService : AppService {

    override val name = "fanpage"

    private const val STREAM_PREFIX = "fanpage-"
    private const val SNAPSHOT_STREAM_PREFIX = "fanpage_snapshot-"

    private val UUID.streamName get() = STREAM_PREFIX + this.toString()
    private val UUID.snapshotName get() = SNAPSHOT_STREAM_PREFIX + this.toString()

    data class Command(
        val handle: (id: UUID) -> CommandHandler<FanPage.Command, Output>
    )

    data class Query(
        val readState: (id: UUID) -> Async<FanPage.State?>
    )

    data class Interface(
        val command: Command,
        val query: Query,
    )

    override val start = AppStack.Do {

        val client = !getService<EventStoreClient>()

        val commands = Command(
            handle = { id ->
                stream<FanPage.Event, FanPage.State>(client, id.streamName, id.snapshotName)
                    .commandHandler(FanPage.commandStateMachine, FanPage.eventReducer)
            }
        )

        val query = Query(
            readState = { id ->
                stream<FanPage.Event, FanPage.State>(client, id.streamName, id.snapshotName)
                    .computeStateResult(FanPage.eventReducer)
            }
        )

        !registerService(
            Interface(
                command = commands,
                query = query
            )
        )

        !FanPageApiRest.api

        !FanPageProjection.sqlEventHandler

    }
}