package io.kauth.service.occasion

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackSqlProjector
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.json
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert
import java.util.*

object OccasionProjection {

    object OccasionTable : Table("occasions") {
        val id = text("id").uniqueIndex()
        val description = text("description")
        val date = date("date")
        val categories = json<List<Occasion.Category>>("categories", Json)
        val owners = json<List<String>>("owners", Json)
        val createdAt = timestamp("created_at")
        val name = text("name").nullable()
    }

    @Serializable
    data class OccasionProjection(
        val id: String,
        val description: String,
        val date: LocalDate,
        val categories: List<Occasion.Category>,
        val owners: List<String>,
        val createdAt: Instant,
        val name: String? = null
    )

    val ResultRow.toOccasionProjection get() = OccasionProjection(
        id = this[OccasionTable.id],
        description = this[OccasionTable.description],
        date = this[OccasionTable.date],
        categories = this[OccasionTable.categories],
        owners = this[OccasionTable.owners],
        createdAt = this[OccasionTable.createdAt],
        name = this[OccasionTable.name]
    )

    val sqlEventHandler = appStackSqlProjector<Occasion.Event>(
        streamName = "\$ce-occasion",
        consumerGroup = "occasion-sql-projection",
        tables = listOf(OccasionTable)
    ) { event ->
        AppStack.Do {
            val occasionId = UUID.fromString(event.retrieveId("occasion"))
            val state = !OccasionApi.Query.readState(occasionId) ?: return@Do
            !appStackDbQuery {
                OccasionTable.upsert {
                    it[id] = occasionId.toString()
                    it[description] = state.description
                    it[date] = state.date
                    it[categories] =state.categories
                    it[owners] = state.owners
                    it[createdAt] = state.createdAt
                    it[name] = state.name
                }
            }
        }
    }

}
