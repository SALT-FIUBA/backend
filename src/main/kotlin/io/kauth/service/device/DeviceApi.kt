package io.kauth.service.device

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.monad.stack.AuthStack
import io.kauth.monad.stack.authStackLog
import io.kauth.monad.stack.getService
import io.kauth.util.not
import io.kauth.abstractions.result.throwOnFailure
import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.authStackJwt
import io.kauth.service.organism.OrganismApi
import io.kauth.service.reservation.ReservationApi
import kotlinx.datetime.Clock
import java.util.*

object DeviceApi {

    fun create(
        organismId: String,
        seriesNumber: String,
        ports: List<String>,
    ) = AuthStack.Do {

        val jwt = !authStackJwt
        val log = !authStackLog

        log.info("Create device $seriesNumber")

        val service = !getService<DeviceService.Interface>()

        !OrganismApi.readState(UUID.fromString(organismId)) ?: !ApiException("Organism does not exists")

        val deviceId = !ReservationApi.takeIfNotTaken("device-${seriesNumber}") { UUID.randomUUID().toString() }

        !service.command
            .handle(UUID.fromString(deviceId))
            .throwOnFailureHandler(
                Device.Command.Create(
                    organismId = organismId,
                    seriesNumber = seriesNumber,
                    ports = ports,
                    createdBy = jwt.payload.id,
                    createdAt = Clock.System.now()
                )
            )

        deviceId

    }

    fun readState(id: UUID) = AuthStack.Do {
        val authService = !getService<DeviceService.Interface>()
        !authService.query.readState(id)
    }

}