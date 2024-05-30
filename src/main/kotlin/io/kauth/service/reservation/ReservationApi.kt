package io.kauth.service.reservation

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.authStackLog
import io.kauth.monad.stack.getService
import io.kauth.util.IO
import io.kauth.util.not
import java.util.*


object ReservationApi {

    fun take(id: String, ownerId: String) = AppStack.Do {
        val service = !getService<ReservationService.Interface>()
        !service.command.handle(id).throwOnFailureHandler(
            Reservation.Command.Take(ownerId = ownerId),
        )
    }

    fun release(id: String) = AppStack.Do {
        val service = !getService<ReservationService.Interface>()
        !service.command.handle(id).throwOnFailureHandler(
            Reservation.Command.Release,
        )
    }

    fun readState(id: String) = AppStack.Do {
        val service = !getService<ReservationService.Interface>()
        !service.query.readState(id)
    }

    fun takeIfNotTaken(
        id: String,
        getId: IO<String>
    ) = AppStack.Do {

        val log = !authStackLog

        val resource = !readState(id)

        if(resource == null || !resource.taken) {
            val ownerId = !getId
            !take(id, ownerId)
            return@Do ownerId
        }

        log.error("$id is already taken")

        return@Do resource.ownerId

    }

}
