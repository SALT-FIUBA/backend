package io.kauth.service.iotdevice

import io.kauth.abstractions.command.CommandHandler
import io.kauth.abstractions.result.Output
import io.kauth.client.eventStore.EventStoreClient
import io.kauth.client.eventStore.commandHandler
import io.kauth.client.eventStore.computeStateResult
import io.kauth.client.eventStore.stream
import io.kauth.client.tuya.Tuya
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.findConfig
import io.kauth.monad.stack.getService
import io.kauth.monad.stack.registerService
import io.kauth.service.AppService
import io.kauth.service.auth.AuthConfig
import io.kauth.service.auth.AuthService
import io.kauth.util.Async
import io.kauth.util.not
import java.util.*

object IoTDeviceService : AppService {

    override val name: String
        get() = "iotdevice"

    val STREAM_NAME = "iotdevice"
    val STREAM_PREFIX = "$STREAM_NAME-"
    val SNAPSHOT_STREAM_PREFIX = "iotdevice_snapshot-"
    val UUID.streamName get() = STREAM_PREFIX + this.toString()
    val UUID.snapshotName get() = SNAPSHOT_STREAM_PREFIX + this.toString()

    data class Command(
        val handle: (id: UUID) -> CommandHandler<IoTDevice.Command, Output>
    )

    data class Query(
        val readState: (id: UUID) -> Async<IoTDevice.State?>
    )

    data class Interface(
        val command: Command,
        val query: Query,
    )

    override val start =
        AppStack.Do {

            val config = !findConfig<IotDeviceConfig>(name) ?: return@Do

            val client = !getService<EventStoreClient>()

            !registerService(!Tuya.newClient(ktor, config.tuya.clientId, config.tuya.clientSecret))

            val commands = Command(
                handle = { id ->
                    stream<IoTDevice.Event, IoTDevice.State>(client, id.streamName, id.snapshotName)
                        .commandHandler(IoTDevice.commandStateMachine, IoTDevice.eventReducer)
                }
            )

            val query = Query(
                readState = { id ->
                    stream<IoTDevice.Event, IoTDevice.State>(client, id.streamName, id.snapshotName)
                        .computeStateResult(IoTDevice.eventReducer)
                }
            )

            !registerService(
                Interface(
                    query = query,
                    command = commands,
                )
            )

            !IoTDeviceApiRest.api

            !IoTDeviceEventHandler.start

            !IoTDeviceProjection.sqlEventHandler

        }
}