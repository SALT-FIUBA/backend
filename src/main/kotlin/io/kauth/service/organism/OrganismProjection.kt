package io.kauth.service.organism

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackSqlProjector
import io.kauth.service.auth.AuthApi
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
        val createdByEmail = text("created_by_email").nullable()
        val createdAt = timestamp("created_at")
    }

    @Serializable
    data class OrganismProjection(
        val id: String,
        val name: String,
        val tag: String,
        val description: String,
        val createdBy: String,
        val createdByEmail: String? = null,
        val createdAt: Instant
    )

    val ResultRow.toOrganismProjection get() =
        OrganismProjection(
            this[OrganismTable.id],
            this[OrganismTable.name],
            this[OrganismTable.tag],
            this[OrganismTable.description],
            this[OrganismTable.createdBy],
            this[OrganismTable.createdByEmail],
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
            val user = !AuthApi.Query.readState(UUID.fromString(state.createdBy))

            !appStackDbQuery {
                OrganismTable.upsert() {
                    it[id] = entity.toString()
                    it[tag] = state.tag
                    it[name] = state.name
                    it[description] = state.description
                    it[createdBy] = state.createdBy
                    it[createdByEmail] = user?.email
                    it[createdAt] = state.createdAt
                }
            }
        }
    }

}