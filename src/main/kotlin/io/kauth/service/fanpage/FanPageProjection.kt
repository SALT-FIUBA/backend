package io.kauth.service.fanpage

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackSqlProjector
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.json
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert
import java.util.*


object FanPageProjection {

    object FanPageTable: Table("fan_pages") {
        val id = text("id").uniqueIndex()
        val description = text("description").nullable()
        val createdAt = timestamp("created_at")
        val admins = json<List<String>>("admins", Json)
        val name = text("name")
        val createdBy = text("created_by")
        val profilePhoto = text("profile_photo")
        val location = text("location")
        val email = text("email").nullable()
        val phone = text("phone")
        val website = text("website")
        val category = text("category").nullable()
    }

    @Serializable
    data class FanPageProjection(
        val id: String,
        val description: String?,
        val createdAt: Instant,
        val admins: List<String>,
        val name: String,
        val createdBy: String,
        val profilePhoto: String,
        val location: String,
        val email: String?,
        val phone: String,
        val website: String,
        val category: String?
    )

    val ResultRow.toFanPageProjection get() = FanPageProjection(
        id = this[FanPageTable.id],
        description = this[FanPageTable.description],
        createdAt = this[FanPageTable.createdAt],
        admins = this[FanPageTable.admins],
        name = this[FanPageTable.name],
        createdBy = this[FanPageTable.createdBy],
        profilePhoto = this[FanPageTable.profilePhoto],
        location = this[FanPageTable.location],
        email = this[FanPageTable.email],
        phone = this[FanPageTable.phone],
        website = this[FanPageTable.website],
        category = this[FanPageTable.category]
    )

    val sqlEventHandler = appStackSqlProjector<FanPage.Event>(
        streamName = "\$ce-fanpage",
        consumerGroup = "fanpage-sql-projection",
        tables = listOf(FanPageTable)
    ) { event ->
        AppStack.Do {
            val fanPageId = UUID.fromString(event.retrieveId("fanpage"))
            val state = !FanPageApi.Query.readState(fanPageId) ?: return@Do
            !appStackDbQuery {
                FanPageTable.upsert {
                    it[id] = fanPageId.toString()
                    it[description] = state.description
                    it[createdAt] = state.createdAt
                    it[admins] = state.admins
                    it[name] = state.name
                    it[createdBy] = state.createdBy
                    it[profilePhoto] = state.profilePhoto
                    it[location] =state.location
                    it[phone] = state.phone
                    it[website] = state.website
                    it[category] = state.category
                }
            }
        }
    }

}