package io.kauth.service.reservation

import io.kauth.abstractions.command.CommandHandler
import io.kauth.abstractions.result.Output
import io.kauth.client.eventStore.*
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.getService
import io.kauth.monad.stack.registerService
import io.kauth.service.AppService
import io.kauth.util.Async

object ReservationService : AppService {

    val USERS_STREAM_PREFIX = "reservation-"
    val USER_SNAPSHOT_STREAM_PREFIX = "reservation_snapshot-"
    val String.streamName get() = USERS_STREAM_PREFIX + this.toString()
    val String.snapshotName get() = USER_SNAPSHOT_STREAM_PREFIX + this.toString()

    data class Command(
        val handle: (id: String) -> CommandHandler<Reservation.Command, Output>
    )

    data class Query(
        val readState: (id:String) -> Async<Reservation.Reservation?>
    )

    data class Interface(
        val command: Command,
        val query: Query
    )

    override val start =
        AppStack.Do {

            val client = !getService<EventStoreClient>()

            val commands = Command(
                handle = { id ->
                    stream<Reservation.ResourceEvent, Reservation.Reservation>(client, id.streamName, id.snapshotName)
                        .commandHandler(Reservation.stateMachine, Reservation.eventReducer)
                }
            )

            val query = Query(
                readState = { id ->
                    stream<Reservation.ResourceEvent, Reservation.Reservation>(client, id.streamName, id.snapshotName)
                        .computeStateResult(Reservation.eventReducer)
                }
            )

            !registerService(
                Interface(
                    query = query,
                    command = commands
                )
            )

        }

    //readConfig

}


