package io.kauth.service.salt

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.util.not
import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.*
import io.kauth.service.salt.DeviceService.streamName
import io.kauth.service.organism.OrganismApi
import io.kauth.service.publisher.Publisher
import io.kauth.service.publisher.PublisherApi
import io.kauth.service.reservation.ReservationApi
import io.kauth.service.salt.DeviceProjection.toDeviceProjection
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.selectAll
import java.util.*

object DeviceApi {

    fun sendCommand(
        deviceId: UUID,
        messageId: UUID,
        action: Device.Mqtt.SaltAction
    ) = AppStack.Do {

        val state = !Query.readState(deviceId) ?: return@Do messageId

        !PublisherApi.publish(
            messageId = messageId,
            message = Device.Mqtt.SaltCmd(action = action, config = null),
            resource = deviceId.streamName,
            channel = Publisher.Channel.Mqtt(
                state.topics?.command ?: error("No command topic")
            )
        )

        messageId
    }

    fun setStatus(
        deviceId: UUID,
        status: String
    ) = AppStack.Do {
        val service = !getService<DeviceService.Interface>()
        !service.command
            .handle(deviceId)
            .throwOnFailureHandler(
                Device.Command.SetStatus(
                    status = status
                ),
            )
        deviceId
    }

    fun create(
        organismId: UUID,
        seriesNumber: String,
        ports: List<String>,
        topics: Device.Topics
    ) = AppStack.Do {

        val jwt = !authStackJwt
        val log = !authStackLog

        log.info("Create device $seriesNumber")

        !OrganismApi.Query.readState(organismId) ?: !ApiException("Organism does not exists")

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
                    createdAt = Clock.System.now(),
                    topics = topics
                ),
            )

        deviceId

    }

    object Query {

        fun readState(id: UUID) = AppStack.Do {
            val authService = !getService<DeviceService.Interface>()
            !authService.query.readState(id)
        }

        fun get(id: String) = AppStack.Do {
            !appStackDbQuery {
                DeviceProjection.DeviceTable
                    .selectAll()
                    .where { DeviceProjection.DeviceTable.id eq id }
                    .singleOrNull()?.toDeviceProjection
            }
        }

        fun list() = AppStack.Do {
            !appStackDbQuery {
                DeviceProjection.DeviceTable.selectAll()
                    .map { it.toDeviceProjection }
            }

        }

    }


}