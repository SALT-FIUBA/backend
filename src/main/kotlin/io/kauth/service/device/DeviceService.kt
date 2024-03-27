package io.kauth.service.device

import io.kauth.abstractions.command.CommandHandler
import io.kauth.client.eventStore.EventStoreClient
import io.kauth.client.eventStore.commandHandler
import io.kauth.client.eventStore.computeStateResult
import io.kauth.client.eventStore.stream
import io.kauth.monad.stack.AuthStack
import io.kauth.monad.stack.getService
import io.kauth.monad.stack.registerService
import io.kauth.service.AppService
import io.kauth.service.device.Device.asCommand
import io.kauth.util.Async
import io.kauth.abstractions.result.Output
import java.util.*

object DeviceService : AppService {

    val STREAM_PREFIX = "device-"
    val SNAPSHOT_STREAM_PREFIX = "device_snapshot-"
    val UUID.streamName get() = STREAM_PREFIX + this.toString()
    val UUID.snapshotName get() = SNAPSHOT_STREAM_PREFIX + this.toString()

    data class Command(
        val handle: (id: UUID) -> CommandHandler<Device.Command, Output>
    )

    data class Query(
        val readState: (id: UUID) -> Async<Device.State?>
    )

    data class Interface(
        val command: Command,
        val query: Query,
    )

    override val name: String get() = "DeviceService"

    override val start =
        AuthStack.Do {

            val client = !getService<EventStoreClient>()

            val commands = Command(
                handle = { id ->
                    stream<Device.Event, Device.State>(client, id.streamName, id.snapshotName)
                        .commandHandler(Device::stateMachine) { it.asCommand }
                }
            )

            val query = Query(
                readState = { id ->
                    stream<Device.Event, Device.State>(client, id.streamName, id.snapshotName)
                        .computeStateResult(Device::stateMachine) { it.asCommand }
                }
            )

            !registerService(
                Interface(
                    query = query,
                    command = commands,
                )
            )

            !DeviceApiRest.api

        }

}