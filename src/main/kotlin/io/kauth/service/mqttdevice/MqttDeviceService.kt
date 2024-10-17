package io.kauth.service.mqttdevice

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

object MqttDeviceService : AppService {

    val STREAM_NAME = "mqttdevice"
    val STREAM_PREFIX = "$STREAM_NAME-"
    val SNAPSHOT_STREAM_PREFIX = "mqttdevice_snapshot-"
    val UUID.streamName get() = STREAM_PREFIX + this.toString()
    val UUID.snapshotName get() = SNAPSHOT_STREAM_PREFIX + this.toString()

    data class Command(
        val handle: (id: UUID) -> CommandHandler<MqttDevice.Command, Output>
    )

    data class Query(
        val readState: (id: UUID) -> Async<MqttDevice.State?>
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
                    stream<MqttDevice.Event, MqttDevice.State>(client, id.streamName, id.snapshotName)
                        .commandHandler(MqttDevice.commandStateMachine, MqttDevice.eventReducer)
                }
            )

            val query = Query(
                readState = { id ->
                    stream<MqttDevice.Event, MqttDevice.State>(client, id.streamName, id.snapshotName)
                        .computeStateResult(MqttDevice.eventReducer)
                }
            )

            !registerService(
                Interface(
                    query = query,
                    command = commands,
                )
            )

            !MqttDeviceApiRest.api

            !MqttDeviceEventHandler.start

        }
}