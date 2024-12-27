package io.kauth.client.tuya

import io.kauth.util.Async
import io.kauth.util.not
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class CommandRequest(
    val commands: List<Command>
) {
    @Serializable
    data class Command(
        val code: String,
        val value: JsonPrimitive
    )
}

@Serializable
data class CodeValue(
    val value: JsonPrimitive,
    val code: String
)

@Serializable
data class Property(
    val code: String,
    val custom_name: String,
    val dp_id: Int,
    val time: Long,
    val type: String,
    val value: JsonPrimitive
)

@Serializable
data class PropertiesResponse(
    val properties: List<Property>
)

@Serializable
data class SpecificationResponse(
    val functions: List<Specification>,
    val status: List<Specification>
) {
    @Serializable
    data class Specification(
        val code: String,
        val name: String,
        val type: String, //"Enum", "Boolean" "Bitmap" "Integer"
        val values: String
    )

    @Serializable
    data class EnumValues(
        val range: List<String>
    )

    @Serializable
    data class BitMapValues(
        val label: List<String>
    )

    @Serializable
    data class IntegerValues(
        val unit: String,
        val min: String,
        val max: String,
        val scale: Int,
        val step: Int
    )
}

@Serializable
data class DeviceStatus(
    val active_time: Long,
    val biz_type: Int,
    val category: String,
    val create_time: Long,
    val icon: String,
    val id: String,
    val ip: String,
    val lat: String,
    val local_key: String,
    val lon: String,
    val model: String,
    val name: String,
    val online: Boolean,
    val owner_id: String,
    val product_id: String,
    val product_name: String,
    val status: List<CodeValue>,
    val sub: Boolean,
    val time_zone: String,
    val uid: String,
    val update_time: Long,
    val uuid: String
)

@Serializable
data class PropertiesRequest(
    val properties: String
)

@Serializable
data class DataModel(
    val model: String
)

@Serializable
data class DeviceModel(
    val modelId: String,
    val services: List<Service>
) {
    @Serializable
    data class Service(
        val actions: List<String>,
        val code: String,
        val description: String,
        val events: List<String>,
        val name: String,
        val properties: List<Property>
    ) {
        @Serializable
        data class Property(
            val abilityId: Int,
            val accessMode: AccessMode,
            val code: String,
            val description: String,
            val name: String,
            val typeSpec: TypeSpecification,
            val extensions: Extensions? = null
        ) {
            @Serializable
            enum class AccessMode {
                rw,
                ro,
            }

            @Serializable
            sealed class TypeSpecification {

                @Serializable
                @SerialName("bool")
                data object Bool : TypeSpecification()

                @Serializable
                @SerialName("bitmap")
                data class BitMap(
                    val label: List<String>,
                    val maxlen: Int
                ) : TypeSpecification()

                @Serializable
                @SerialName("enum")
                data class Enumerative(
                    val range: List<String>
                ) : TypeSpecification()

                @Serializable
                @SerialName("value")
                data class Value(
                    val max: Int,
                    val min: Int,
                    val scale: Int,
                    val step: Int,
                    val unit: String
                ): TypeSpecification()

                @Serializable
                @SerialName("string")
                data class StringValue(
                    val maxlen: Int
                ): TypeSpecification()

            }

            @Serializable
            data class Extensions(
                val scope: String? = null
            )
        }
    }
}

fun Tuya.Client.querySpecification(deviceId: String) =
    request<Unit, SpecificationResponse>(
        HttpMethod.Get,
        "/v1.0/iot-03/devices/${deviceId}/specification"
    )

fun Tuya.Client.queryDeviceStatus(deviceId: String) =
    request<Unit, DeviceStatus>(
        HttpMethod.Get,
        "/v1.0/devices/${deviceId}"
    )

fun Tuya.Client.queryStatus(deviceId: String) =
    request<Unit, List<CodeValue>>(
        HttpMethod.Get,
        "/v1.0/devices/${deviceId}/status"
    )

fun Tuya.Client.sendCommand(deviceId: String, code: String, value: JsonPrimitive) =
    this.request<CommandRequest, JsonPrimitive>(
        HttpMethod.Post,
        "/v1.0/devices/${deviceId}/commands"
    ) {
        CommandRequest(
            commands = listOf(CommandRequest.Command(code, value))
        )
    }

fun Tuya.Client.queryDataModel(deviceId: String): Async<TuyaApiResult<DeviceModel>> = Async {
    val result = !this.request<Unit, DataModel>(
        HttpMethod.Get,
        "/v2.0/cloud/thing/${deviceId}/model"
    )
    result.map { json.decodeFromString(it.model) }
}

fun Tuya.Client.queryProperties(deviceId: String) =
    this.request<Unit, PropertiesResponse>(
        HttpMethod.Get,
        "/v2.0/cloud/thing/${deviceId}/shadow/properties"
    )

fun Tuya.Client.sendProperties(deviceId: String, data: Map<String, JsonPrimitive>) =
    this.request<PropertiesRequest, JsonObject>(
        HttpMethod.Post,
        "/v2.0/cloud/thing/${deviceId}/shadow/properties/issue"
    ) {
        PropertiesRequest(
            properties = json.encodeToString(data)
        )
    }

