package io.kauth.service.reservation

import io.kauth.monad.stack.AuthStack
import io.kauth.monad.stack.getService
import io.kauth.util.not


object ReservationApi {

    fun take(id: String, ownerId: String) = AuthStack.Do {
        val service = !getService<ReservationService.Interface>()
        !service.command.handle(id)(Reservation.Command.Take(ownerId = ownerId))
    }

    fun release(id: String) = AuthStack.Do {
        val service = !getService<ReservationService.Interface>()
        !service.command.handle(id)(Reservation.Command.Release)
    }

    fun readState(id: String) = AuthStack.Do {
        val service = !getService<ReservationService.Interface>()
        !service.query.readState(id)
    }

}
