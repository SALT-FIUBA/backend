package io.kauth.service.device

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.util.not
import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.*
import io.kauth.service.device.DeviceService.streamName
import io.kauth.service.organism.OrganismApi
import io.kauth.service.publisher.Publisher
import io.kauth.service.publisher.PublisherApi
import io.kauth.service.reservation.ReservationApi
import kotlinx.datetime.Clock
import java.util.*

object DeviceApi {

    fun sendCommand(
        deviceId: UUID,
        messageId: UUID,
        message: String,
        topic: String
    ) = AppStack.Do {
        !PublisherApi.publish(
            messageId = messageId,
            message = message,
            resource = deviceId.streamName,
            channel = Publisher.Channel.Mqtt(topic)
        )
        messageId
    }

    fun create(
        organismId: UUID,
        seriesNumber: String,
        ports: List<String>,
    ) = AppStack.Do {

        val jwt = !authStackJwt
        val log = !authStackLog

        log.info("Create device $seriesNumber")

        !OrganismApi.readState(organismId) ?: !ApiException("Organism does not exists")

        val deviceId = !ReservationApi.takeIfNotTaken("device-${seriesNumber}") { UUID.randomUUID().toString() }

        val service = !getService<DeviceService.Interface>()

        !service.command
            .handle(UUID.fromString(deviceId))
            .throwOnFailureHandler(
                Device.Command.Create(
                    organismId = organismId,
                    seriesNumber = seriesNumber,
                    ports = ports,
                    createdBy = jwt.payload.id,
                    createdAt = Clock.System.now()
                ),
            )

        deviceId

    }

    fun readState(id: UUID) = AppStack.Do {
        val authService = !getService<DeviceService.Interface>()
        !authService.query.readState(id)
    }

}