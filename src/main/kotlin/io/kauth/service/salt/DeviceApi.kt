package io.kauth.service.salt

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.util.not
import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.apicall.*
import io.kauth.monad.stack.*
import io.kauth.service.mqtt.MqttApi
import io.kauth.service.organism.OrganismApi
import io.kauth.service.salt.DeviceService.streamName
import io.kauth.service.publisher.Publisher
import io.kauth.service.publisher.PublisherApi
import io.kauth.service.reservation.ReservationApi
import io.kauth.service.salt.DeviceProjection.toDeviceProjection
import io.kauth.service.train.TrainApi
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.*

object DeviceApi {

    fun sendCommand(
        deviceId: UUID,
        messageId: UUID,
        cmd: Device.Mqtt.SaltCmd
    ) = AppStack.Do {
        val state = !Query.readState(deviceId) ?: return@Do messageId
        val commandTopic = state.topics?.command ?: error("No command topic");
        !PublisherApi.publish(
            messageId = messageId,
            message = cmd,
            resource = deviceId.streamName,
            channel = Publisher.Channel.Mqtt(commandTopic)
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

    fun setState(
        deviceId: UUID,
        state: Device.Mqtt.SaltState
    ) = AppStack.Do {
        val service = !getService<DeviceService.Interface>()
        !service.command
            .handle(deviceId)
            .throwOnFailureHandler(
                Device.Command.SetState(
                    state = state
                ),
            )
        deviceId
    }

    fun create(
        trainId: UUID,
        seriesNumber: String,
        ports: List<String>,
        topics: Device.Topics
    ) = ApiCall.Do {

        val jwt = !apiCallJwt
        val log = !apiCallLog

        log.info("Create device $seriesNumber")

        !TrainApi.Query.readState(trainId).toApiCall() ?: !ApiException("Train does not exists")

        val deviceId = !ReservationApi.takeIfNotTaken("device-${seriesNumber}") { UUID.randomUUID().toString() }.toApiCall()

        val service = !apiCallGetService<DeviceService.Interface>()

        !service.command
            .handle(UUID.fromString(deviceId))
            .throwOnFailureHandler(
                Device.Command.Create(
                    organismId = null,
                    trainId = trainId,
                    seriesNumber = seriesNumber,
                    ports = ports,
                    createdBy = jwt.payload.id,
                    createdAt = Clock.System.now(),
                    topics = topics
                ),
            )
            .toApiCall()

        deviceId

    }

    fun delete(
        deviceId: UUID
    ) = ApiCall.Do {
        val jwt = jwt ?: !ApiException("UnAuth")
        val service = !apiCallGetService<DeviceService.Interface>()
        !service.command
            .handle(deviceId)
            .throwOnFailureHandler(
                Device.Command.Delete(
                    deletedBy = jwt.payload.id,
                    deletedAt = Clock.System.now()
                ),
            ).toApiCall()
        deviceId
    }

    fun editTrainId(
        deviceId: UUID,
        trainId: UUID
    ) = ApiCall.Do {
        val jwt = !apiCallJwt
        val log = !apiCallLog
        log.info("Edit device $deviceId trainId to $trainId")
        val service = !apiCallGetService<DeviceService.Interface>()
        !service.command
            .handle(deviceId)
            .throwOnFailureHandler(
                Device.Command.EditDevice(
                    trainId = trainId,
                    editedBy = jwt.payload.id,
                    editedAt = Clock.System.now()
                ),
            ).toApiCall()
        deviceId
    }

    fun edit(
        deviceId: UUID,
        trainId: UUID? = null,
        ports: List<String>? = null
    ) = ApiCall.Do {
        val jwt = !apiCallJwt
        val log = !apiCallLog
        log.info("Edit device $deviceId trainId=$trainId ports=$ports")
        val service = !apiCallGetService<DeviceService.Interface>()
        !service.command
            .handle(deviceId)
            .throwOnFailureHandler(
                Device.Command.EditDevice(
                    trainId = trainId,
                    ports = ports,
                    editedBy = jwt.payload.id,
                    editedAt = Clock.System.now()
                ),
            ).toApiCall()
        deviceId
    }

    object Query {

        fun readState(id: UUID) = AppStack.Do {
            val authService = !getService<DeviceService.Interface>()
            !authService.query.readState(id)
        }

        fun messages(id: UUID) = AppStack.Do {
            val state = !readState(id) ?: error("Not found")
            val topics = state.topics ?: error("No topics")
            !MqttApi.Query.list(
                listOf(topics.command, topics.state, topics.status)
                    .map { "mqtt-${it}" }
            )
        }

        fun commands(id: UUID) =
            PublisherApi.getByResource("${DeviceService.STREAM_NAME}-${id}")

        fun get(id: String) = AppStack.Do {
            !appStackDbQueryNeon {
                DeviceProjection.DeviceTable
                    .selectAll()
                    .where { DeviceProjection.DeviceTable.id eq id }
                    .singleOrNull()?.toDeviceProjection
            }
        }

        fun list(
            trainId: String? = null
        ) = AppStack.Do {
            !appStackDbQueryNeon {
                DeviceProjection.DeviceTable.selectAll()
                    .where {
                        trainId?.let {  DeviceProjection.DeviceTable.trainId.eq(it) } ?: Op.TRUE and (DeviceProjection.DeviceTable.deleted eq false)
                    }
                    .map { it.toDeviceProjection }
            }
        }

    }


}
