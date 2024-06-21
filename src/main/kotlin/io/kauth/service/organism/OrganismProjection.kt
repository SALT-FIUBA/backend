package io.kauth.service.organism

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackSqlProjector
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import java.util.*


object OrganismProjection {

    object OrganismTable: Table("organims") {
        val id = text("id").uniqueIndex()
        val name = text("name")
        val tag = text("tag")
        val description = text("description")
        val createdBy = text("created_by")
        val createdAt = timestamp("created_at")
    }

    @Serializable
    data class OrganismProjection(
        val id: String,
        val name: String,
        val tag: String,
        val description: String,
        val createdBy: String,
        val createdAt: Instant
    )

    val ResultRow.toOrganismProjection get() =
        OrganismProjection(
            this[OrganismTable.id],
            this[OrganismTable.name],
            this[OrganismTable.tag],
            this[OrganismTable.description],
            this[OrganismTable.createdBy],
            this[OrganismTable.createdAt],
        )

    val sqlEventHandler = appStackSqlProjector<Organism.Event>(
        streamName = "\$ce-organism",
        consumerGroup = "organism-sql-projection",
        tables = listOf(OrganismTable)
    ) { event ->
        AppStack.Do {

            val entity = UUID.fromString(event.retrieveId("organism"))
            val state = !OrganismApi.Query.readState(entity) ?: return@Do

            !appStackDbQuery {
                OrganismTable.upsert() {
                    it[id] = entity.toString()
                    it[tag] = state.tag
                    it[name] = state.name
                    it[description] = state.description
                    it[createdBy] = state.createdBy
                    it[createdAt] = state.createdAt
                }
            }
        }
    }

}