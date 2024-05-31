package io.kauth.service.organism

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackSqlProjector
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
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

    val sqlEventHandler = appStackSqlProjector<Organism.Event>(
        streamName = "\$ce-organism",
        consumerGroup = "organism-sql-projection",
        tables = listOf(OrganismTable)
    ) { event ->
        AppStack.Do {

            val entity = UUID.fromString(event.retrieveId("organism"))
            val state = !OrganismApi.readState(entity) ?: return@Do

            transaction(db) {
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