package io.kauth.service.auth

import io.kauth.abstractions.command.CommandHandler
import io.kauth.client.eventStore.*
import io.kauth.monad.stack.getService
import io.kauth.monad.stack.registerService
import io.kauth.service.AppService
import io.kauth.service.auth.Auth.asCommand
import io.kauth.util.Async
import io.kauth.abstractions.result.Output
import io.kauth.monad.stack.AppStack
import java.util.*


object AuthService : AppService {

    val USERS_STREAM_PREFIX = "user-"
    val USER_SNAPSHOT_STREAM_PREFIX = "user_snapshot-"
    val UUID.streamName get() = USERS_STREAM_PREFIX + this.toString()
    val UUID.snapshotName get() = USER_SNAPSHOT_STREAM_PREFIX + this.toString()

    data class Command(
        val handle: (id: UUID) -> CommandHandler<Auth.Command, Output>
    )

    data class Query(
        val readState: (id: UUID) -> Async<Auth.User?>
    )

    data class Interface(
        val command: Command,
        val query: Query,
        val config: Config
    )

    override val start =
        AppStack.Do {

            val client = !getService<EventStoreClient>()

            val commands = Command(
                handle = { id ->
                    stream<Auth.UserEvent, Auth.User>(client, id.streamName, id.snapshotName)
                        .commandHandler(Auth::stateMachine) { it.asCommand }
                }
            )

            val query = Query(
                readState = { id ->
                    stream<Auth.UserEvent, Auth.User>(client, id.streamName, id.snapshotName)
                        .computeStateResult(Auth::stateMachine) { it.asCommand }
                }
            )

            !registerService(
                Interface(
                    query = query,
                    command = commands,
                    config = Config(
                        hashAlgorithm = Auth.HashAlgorithm.Pbkdf2Sha256(iterations = 27500),
                        secret = "supermegasecret"
                    )
                )
            )

            !AuthEventHandler.eventHandler

            !AuthRest.api

        }

        data class Config(
            val hashAlgorithm: Auth.HashAlgorithm,
            val secret: String,
        )

        //readConfig

}


