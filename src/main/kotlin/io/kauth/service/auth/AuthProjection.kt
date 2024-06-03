package io.kauth.service.auth

import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.appStackDbQuery
import io.kauth.monad.stack.appStackSqlProjector
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import java.util.*


object AuthProjection {

    object User: Table("users") {
        val id = text("id").uniqueIndex()
        val firstname = text("firstname")
        val lastname = text("lastname")
        val email = text("email")
        val roles = array<String>("roles")
    }

    val sqlEventHandler = appStackSqlProjector<Auth.UserEvent>(
        streamName = "\$ce-user",
        consumerGroup = "user-sql-projection",
        tables = listOf(User)
    ) { event ->
        AppStack.Do {

            val userId = UUID.fromString(event.retrieveId("user"))
            val state = !AuthApi.readState(userId) ?: return@Do

            !appStackDbQuery {
                User.upsert() {
                    it[id] = userId.toString()
                    it[firstname] = state.personalData.firstName
                    it[lastname] = state.personalData.lastName
                    it[email] = state.email
                    it[roles] = state.roles
                }
            }

        }
    }

}