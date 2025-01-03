package io.kauth.service.iotdevice

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.client.tuya.*
import io.kauth.exception.ApiException
import io.kauth.exception.not
import io.kauth.monad.apicall.*
import io.kauth.monad.stack.*
import io.kauth.service.iotdevice.IoTDeviceProjection.toMqttDeviceProjection
import io.kauth.service.iotdevice.model.iotdevice.*
import io.kauth.service.reservation.ReservationApi
import io.kauth.util.not
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.selectAll
import java.util.*

object IoTDeviceApi {

    fun registerTasmotaIntegration(
        name: String,
        resource: String,
        topics: TasmotaTopics,
        caps: Map<String, CapabilitySchema<TasmotaCapability>>
    ) = ApiCall.Do {
        //TODO Check on subscription api that topic is not in use!
        val jwt = !apiCallJwt
        val log = !apiCallLog
        log.info("Create device $resource")
        val deviceId = !ReservationApi.takeIfNotTaken("device-tasmota-${name}") { UUID.randomUUID().toString() }
            .toApiCall()
        val integration = Integration.Tasmota(
            topics = topics,
            capsSchema = caps + (topics.status to CapabilitySchema(
                TasmotaCapability.Status,
                "Status",
                CapabilitySchema.Access.readonly
            ))
        )
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
    ) = ApiCall.Do {
        //TODO Check on subscription api that topic is not in use!
        val jwt = !apiCallJwt
        val log = !apiCallLog
        val json = !apiCallJson

        log.info("Create device $resource")

        //Crear indice tuyaDeviceId -> deviceId
        val deviceId = !ReservationApi.takeIfNotTaken("device-tuya-${name}") { UUID.randomUUID().toString() }.toApiCall()

        //Index tuyaDeviceId -> deviceId
        //esta bien aca? se puede crear desde un evento ?

        val tuyaId = !ReservationApi.readState("device-${tuyaDeviceId}").toApiCall()

        if (tuyaId != null && tuyaId.taken) {
            val owner = !Query.readState(UUID.fromString(tuyaId.ownerId)).toApiCall()
            if (owner != null) {
                !ApiException("Device ID $tuyaDeviceId already taken by ${owner?.name}")
            }
        }

        !ReservationApi.takeIfNotTaken("device-${tuyaDeviceId}") { deviceId }.toApiCall()

        val tuyaClient = !apiCallGetService<Tuya.Client>()

        val dataModel = !tuyaClient.queryDataModel(tuyaDeviceId)

        val capabilities = dataModel.result
            ?.services
            ?.firstOrNull()
            ?.properties ?: emptyList()

        //TODO user queryDataModel
        /*
        val model = !client.queryDataModel("eb906f4ba762eb801da5ii")
        println(model)
         */

        if (capabilities.isEmpty()) {
            !ApiException("Tuya Client error " + dataModel.msg)
        }

        val integration = Integration.Tuya(
            deviceId = tuyaDeviceId,
            capsSchema = capabilities
                .groupBy { it.code }
                .mapValues {
                    it.value.first().let {
                        CapabilitySchema(
                            name = it.name,
                            access =
                            when (it.accessMode) {
                                DeviceModel.Service.Property.AccessMode.ro -> CapabilitySchema.Access.readonly
                                DeviceModel.Service.Property.AccessMode.rw -> CapabilitySchema.Access.writeread
                            },
                            cap = when (it.typeSpec) {
                                is DeviceModel.Service.Property.TypeSpecification.BitMap -> TuyaCapabilityType.BitMapCap(
                                    it.typeSpec.label
                                )

                                DeviceModel.Service.Property.TypeSpecification.Bool -> TuyaCapabilityType.BooleanCap
                                is DeviceModel.Service.Property.TypeSpecification.Enumerative -> TuyaCapabilityType.EnumCap(
                                    it.typeSpec.range
                                )

                                is DeviceModel.Service.Property.TypeSpecification.StringValue -> TuyaCapabilityType.StringCap(
                                    it.typeSpec.maxlen
                                )

                                is DeviceModel.Service.Property.TypeSpecification.Value -> TuyaCapabilityType.ValueCap(
                                    it.typeSpec.unit ?: "",
                                    it.typeSpec.step,
                                    it.typeSpec.max,
                                    it.typeSpec.min
                                )
                            }
                        )

                    }
                }
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
    ) = ApiCall.Do {
        val jwt = !apiCallJwt
        val service = !apiCallGetService<IoTDeviceService.Interface>()
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
            .toApiCall()
        deviceId
    }

    //TODO: List de commands!
    fun sendCommand(deviceId: UUID, cmds: List<DeviceCommand>) = ApiCall.Do {
        val service = !apiCallGetService<IoTDeviceService.Interface>()
        !service.command
            .handle(deviceId)
            .throwOnFailureHandler(
                IoTDevice.Command.SendCommand(cmds),
            )
            .toApiCall()
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

    fun setCapValues(deviceId: UUID, caps: List<Pair<String, String>>) = AppStack.Do {
        val service = !getService<IoTDeviceService.Interface>()
        !service.command
            .handle(deviceId)
            .throwOnFailureHandler(
                IoTDevice.Command.SetCapabilityValues(
                    caps = caps,
                    at = Clock.System.now()
                ),
            )
        deviceId
    }

    fun setEnabled(deviceId: UUID, enabled: Boolean) = ApiCall.Do {
        val service = !apiCallGetService<IoTDeviceService.Interface>()
        !service.command
            .handle(deviceId)
            .throwOnFailureHandler(
                IoTDevice.Command.SetEnabled(
                    enabled = enabled
                ),
            )
            .toApiCall()
        deviceId
    }

    object Query {

        fun readState(id: UUID) = AppStack.Do {
            val authService = !getService<IoTDeviceService.Interface>()
            !authService.query.readState(id)
        }

        fun get(id: String) = ApiCall.Do {
            !apiCallStackDbQuery {
                IoTDeviceProjection.IoTDeviceTable
                    .selectAll()
                    .where { IoTDeviceProjection.IoTDeviceTable.id eq id }
                    .singleOrNull()
                    ?.toMqttDeviceProjection
                    ?.let { !it }
            }
        }

        fun list() = ApiCall.Do {
            !apiCallStackDbQuery{
                IoTDeviceProjection.IoTDeviceTable.selectAll()
                    .map { !it.toMqttDeviceProjection }
                    //.filter { it.enabled ?: true }
            }
        }

        fun listDevices(resource: String) = ApiCall.Do {
            !apiCallStackDbQuery {
                IoTDeviceProjection.IoTDeviceTable.selectAll()
                    .map { !it.toMqttDeviceProjection }
                    .filter { it.resource == resource }
            }
        }
    }
}