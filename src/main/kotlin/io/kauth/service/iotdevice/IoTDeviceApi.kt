package io.kauth.service.iotdevice

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.client.tuya.SpecificationResponse
import io.kauth.client.tuya.Tuya
import io.kauth.client.tuya.querySpecification
import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.stack.*
import io.kauth.service.auth.AuthApi.appStackAuthValidateAdmin
import io.kauth.service.iotdevice.IoTDeviceProjection.toMqttDeviceProjection
import io.kauth.service.iotdevice.model.iotdevice.*
import io.kauth.service.reservation.ReservationApi
import io.kauth.util.not
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.selectAll
import java.util.*

object IoTDeviceApi {

    fun registerTasmotaIntegration(
        name: String,
        resource: String,
        topics: TasmotaTopics,
        caps: List<CapabilitySchema<TasmotaCapability>>
    ) = AppStack.Do {
        //TODO Check on subscription api that topic is not in use!
        val jwt = !authStackJwt
        val log = !authStackLog
        log.info("Create device $resource")
        val deviceId = !ReservationApi.takeIfNotTaken("device-tasmota-${name}") { UUID.randomUUID().toString() }
        val integration = Integration.Tasmota(topics = topics, capsSchema = caps)
        !register(
            deviceId,
            name,
            resource,
            integration
        )
    }

    fun registerTuyaIntegration(
        name: String,
        resource: String,
        tuyaDeviceId: String
    ) = AppStack.Do {
        //TODO Check on subscription api that topic is not in use!
        val jwt = !authStackJwt
        val log = !authStackLog
        val json = !authStackJson

        log.info("Create device $resource")

        //Crear indice tuyaDeviceId -> deviceId
        val deviceId = !ReservationApi.takeIfNotTaken("device-tuya-${name}") { UUID.randomUUID().toString() }

        //Index tuyaDeviceId -> deviceId
        //esta bien aca? se puede crear desde un evento
        !ReservationApi.takeIfNotTaken("device-${tuyaDeviceId}") { deviceId }

        val tuyaClient = !getService<Tuya.Client>()

        val deviceSpecification = !tuyaClient.querySpecification(tuyaDeviceId)

        if (!deviceSpecification.success) {
            !ApiException("Tuya Client error " + deviceSpecification.msg)
        }

        val result = deviceSpecification.result ?: !ApiException("Tuya Client error, no result")

        val writeCaps = result.functions.map { func ->
            CapabilitySchema(
                permission = CapabilitySchema.Permission.write,
                key = func.code,
                name = func.name,
                cap = when (func.type) {
                    "Integer" -> TuyaCapabilityType.IntegerCap
                    "Enum" -> TuyaCapabilityType.EnumCap(
                        json.decodeFromString<SpecificationResponse.EnumValues>(func.values).range
                    )

                    "Boolean" -> TuyaCapabilityType.BooleanCap
                    "Bitmap" -> TuyaCapabilityType.BitMapCap(
                        json.decodeFromString<SpecificationResponse.BitMapValues>(func.values).label
                    )

                    else -> !ApiException("Unknown tuya type")
                }
            )
        }

        val readCaps = result.status.map { func ->
            CapabilitySchema(
                permission = CapabilitySchema.Permission.read,
                key = func.code,
                name = func.name,
                cap = when (func.type) {
                    "Integer" -> TuyaCapabilityType.IntegerCap
                    "Enum" -> TuyaCapabilityType.EnumCap(
                        json.decodeFromString<SpecificationResponse.EnumValues>(func.values).range
                    )

                    "Boolean" -> TuyaCapabilityType.BooleanCap
                    "Bitmap" -> TuyaCapabilityType.BitMapCap(
                        json.decodeFromString<SpecificationResponse.BitMapValues>(func.values).label
                    )

                    else -> !ApiException("Unknown tuya type")
                }
            )
        }

        val integration = Integration.Tuya(
            deviceId = tuyaDeviceId,
            capsSchema = writeCaps + readCaps
        )

        !register(
            deviceId,
            name,
            resource,
            integration
        )
    }


    fun register(
        deviceId: String,
        name: String,
        resource: String,
        integration: Integration
    ) = AppStack.Do {
        val jwt = !authStackJwt
        val service = !getService<IoTDeviceService.Interface>()
        val now = Clock.System.now()
        !service.command
            .handle(UUID.fromString(deviceId))
            .throwOnFailureHandler(
                IoTDevice.Command.Register(
                    createdBy = jwt.payload.id,
                    createdAt = now,
                    resource = resource,
                    name = name,
                    integration = integration,
                ),
            )
        deviceId

    }

    //TODO: List de commands!
    fun sendCommand(deviceId: UUID, data: String, code: String) = AppStack.Do {
        val service = !getService<IoTDeviceService.Interface>()
        !service.command
            .handle(deviceId)
            .throwOnFailureHandler(
                IoTDevice.Command.SendCommand(
                    data = data,
                    key = code
                ),
            )
        deviceId
    }

    fun setCapValue(deviceId: UUID, key: String, value: String) = AppStack.Do {
        val service = !getService<IoTDeviceService.Interface>()
        !service.command
            .handle(deviceId)
            .throwOnFailureHandler(
                IoTDevice.Command.SetCapabilityValue(
                    key = key,
                    value = value,
                    at = Clock.System.now()
                ),
            )
        deviceId
    }

    fun setEnabled(deviceId: UUID, enabled: Boolean) = AppStack.Do {
        val service = !getService<IoTDeviceService.Interface>()
        !service.command
            .handle(deviceId)
            .throwOnFailureHandler(
                IoTDevice.Command.SetEnabled(
                    enabled = enabled
                ),
            )
        deviceId
    }

    object Query {

        fun readState(id: UUID) = AppStack.Do {
            val authService = !getService<IoTDeviceService.Interface>()
            !authService.query.readState(id)
        }

        fun get(id: String) = AppStack.Do {
            !appStackAuthValidateAdmin
            !appStackDbQuery {
                IoTDeviceProjection.IoTDeviceTable
                    .selectAll()
                    .where { IoTDeviceProjection.IoTDeviceTable.id eq id }
                    .singleOrNull()
                    ?.toMqttDeviceProjection
                    ?.let { !it }
            }
        }

        fun list() = AppStack.Do {
            !appStackAuthValidateAdmin
            !appStackDbQuery {
                IoTDeviceProjection.IoTDeviceTable.selectAll()
                    .map { !it.toMqttDeviceProjection }
                    .filter { it.enabled ?: true }
            }

        }

    }

}