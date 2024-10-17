package io.kauth.service.mqttdevice

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.authStackJwt
import io.kauth.monad.stack.authStackLog
import io.kauth.monad.stack.getService
import io.kauth.service.reservation.ReservationApi
import io.kauth.util.not
import kotlinx.datetime.Clock
import java.util.*

object MqttDeviceApi {

    fun register(
        name: String,
        resource: String,
        topics: MqttDevice.Topics
    ) = AppStack.Do {
        //TODO Check on subscription api that topic is not in use!
        val jwt = !authStackJwt
        val log = !authStackLog
        log.info("Create device $resource")
        val deviceId = !ReservationApi.takeIfNotTaken("device-${name}") { UUID.randomUUID().toString() }
        val service = !getService<MqttDeviceService.Interface>()
        !service.command
            .handle(UUID.fromString(deviceId))
            .throwOnFailureHandler(
                MqttDevice.Command.Register(
                    createdBy = jwt.payload.id,
                    createdAt = Clock.System.now(),
                    topics = topics,
                    resource = resource,
                    name = name
                ),
            )
        deviceId
    }

    fun sendCommand(deviceId: UUID, data: String) = AppStack.Do {
        val service = !getService<MqttDeviceService.Interface>()
        !service.command
            .handle(deviceId)
            .throwOnFailureHandler(
                MqttDevice.Command.SendCommand(
                    data = data
                ),
            )
        deviceId
    }

    fun setStatus(deviceId: UUID, status: String) = AppStack.Do {
        val service = !getService<MqttDeviceService.Interface>()
        !service.command
            .handle(deviceId)
            .throwOnFailureHandler(
                MqttDevice.Command.SetStatus(
                    status = status
                ),
            )
        deviceId
    }

    fun setState(deviceId: UUID, status: String) = AppStack.Do {
        val service = !getService<MqttDeviceService.Interface>()
        !service.command
            .handle(deviceId)
            .throwOnFailureHandler(
                MqttDevice.Command.SetState(
                    status = status
                ),
            )
        deviceId
    }

    fun readState(id: UUID) = AppStack.Do {
        val authService = !getService<MqttDeviceService.Interface>()
        !authService.query.readState(id)
    }

}