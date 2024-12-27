package io.kauth.service.deviceproject

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.apicall.*
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.getService
import io.kauth.service.auth.AuthApi.apiCallAuthValidateAdmin
import io.kauth.service.auth.AuthProjection
import io.kauth.service.auth.AuthProjection.toUserProjection
import io.kauth.service.deviceproject.DeviceProjectProjection.toDeviceProjectProjection
import io.kauth.service.deviceproject.DeviceProjectService.streamName
import io.kauth.service.iotdevice.IoTDeviceApi
import io.kauth.service.iotdevice.IoTDeviceService
import io.kauth.service.reservation.ReservationApi
import io.kauth.util.not
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.json.contains
import org.jetbrains.exposed.sql.selectAll
import java.util.*

object DeviceProjectApi {

    fun create(name: String) = ApiCall.Do {
        val jwt = !apiCallJwt
        val service = !apiCallGetService<DeviceProjectService.Interface>()
        val now = Clock.System.now()
        val id = !ReservationApi.takeIfNotTaken("deviceproject-${name}") { UUID.randomUUID().toString() }.toApiCall()
        !service.command
            .handle(UUID.fromString(id))
            .throwOnFailureHandler(
                DeviceProject.Command.CreateProject(
                    createdBy = jwt.payload.id,
                    name = name,
                    owners = listOf(jwt.payload.id),
                    createdAt = now
                ),
            )
            .toApiCall()
        id
    }

    fun addTuyaDevice(projectId: UUID, name: String, tuyaId: String) = ApiCall.Do {
        val jwt = !apiCallJwt
        val project = !Query.readState(projectId).toApiCall() ?: !ApiException("Project does not exists")
        if (jwt.payload.id !in project.owners) {
            !ApiException("Invalid user!")
        }
        !IoTDeviceApi.registerTuyaIntegration(name, projectId.streamName, tuyaId)
    }

    object Query {

        fun readState(projectId: UUID) = AppStack.Do {
            val service = !getService<DeviceProjectService.Interface>()
            !service.query.readState(projectId)
        }

        fun listDevices(projectId: UUID) = ApiCall.Do {
            !readState(projectId).toApiCall() ?: error("Invalid project")
            !IoTDeviceApi.Query.listDevices(projectId.streamName)
        }

        fun list() = ApiCall.Do {
            val jwt = !apiCallJwt
            !apiCallStackDbQuery {
                DeviceProjectProjection.DeviceProjectTable.selectAll()
                    .where {
                        DeviceProjectProjection.DeviceProjectTable.owners.inList(listOf(listOf(jwt.payload.id)))
                    }
                    .map { it.toDeviceProjectProjection }
            }
        }

    }

}