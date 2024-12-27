package io.kauth.service.deviceproject

import io.kauth.monad.apicall.ApiCall
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackSqlProjector
import io.kauth.service.iotdevice.IoTDeviceService
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert
import java.util.*

object DeviceProjectProjection {

    object DeviceProjectTable: Table("devices_projects") {
        val id = text("id").uniqueIndex()
        val name = text("name")
        val owners = array<String>("owners")
        val createdAt = timestamp("created_at")
        val createdBy = text("created_by")
        val enabled = bool("enabled").nullable()
    }

    @Serializable
    data class Projection(
        val id: String,
        val name: String,
        val createdAt: Instant,
        val createdBy: String,
        val owners: List<String>,
        val enabled: Boolean?
    )

    val ResultRow.toDeviceProjectProjection: Projection
        get() {
            val row = this
            return Projection(
                row[DeviceProjectTable.id],
                row[DeviceProjectTable.name],
                row[DeviceProjectTable.createdAt],
                row[DeviceProjectTable.createdBy],
                row[DeviceProjectTable.owners],
                row[DeviceProjectTable.enabled],
            )
        }


    val sqlEventHandler = appStackSqlProjector<DeviceProject.Event>(
        streamName = "\$ce-${DeviceProjectService.STREAM_NAME}",
        consumerGroup = "device-project-sql-projection-v1",
        tables = listOf(DeviceProjectTable)
    ) { event ->
        AppStack.Do {
            val entity = UUID.fromString(event.retrieveId(DeviceProjectService.STREAM_NAME))
            val actualState = !DeviceProjectApi.Query.readState(entity) ?: return@Do
            !appStackDbQuery {
                DeviceProjectTable.upsert() { it ->
                    it[id] = entity.toString()
                    it[name] = actualState.name
                    it[createdAt] = actualState.createdAt
                    it[owners] = actualState.owners
                    it[createdBy] = actualState.createdBy
                    it[enabled] = actualState.enabled
                }
            }
        }
    }
}
