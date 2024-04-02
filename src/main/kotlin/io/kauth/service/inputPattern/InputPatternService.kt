package io.kauth.service.inputPattern

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kauth.monad.stack.AuthStack
import io.kauth.monad.stack.registerService
import io.kauth.service.AppService
import io.kauth.util.ApiResponse
import io.kauth.util.Async
import io.kauth.util.not
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object InputPatternService: AppService {

    object InputPattern : Table() {
        val id: Column<String> = text("sequence_id")
        val serviceName: Column<String> = text("service_name")
        val timestamp: Column<Instant> = timestamp("timestamp")
        override val primaryKey = PrimaryKey(id, name = "PK_InputPattern_ID")
    }

    interface Interface {
        fun <R> Async<R>.idempotency(sequence: String): Async<ApiResponse<R>>
    }

    override val start: AuthStack<*>
        get() = AuthStack.Do {

            val dataSource = HikariDataSource(
                HikariConfig().apply {
                    username = "salt"
                    password = "1234"
                    jdbcUrl = "jdbc:postgresql://localhost:5432/salt"
                    maximumPoolSize = 10
                    isAutoCommit = false
                }
            )

            val database = Database.connect(dataSource)

            newSuspendedTransaction(db = database) {
                SchemaUtils.createMissingTablesAndColumns(InputPattern)
            }

            !registerService(
                Interface::class,
                object : Interface {
                    override fun <R> Async<R>.idempotency(sequence: String): Async<ApiResponse<R>> = Async {
                        newSuspendedTransaction(db = database) {
                            try {
                                InputPattern.insert {
                                    it[id] = sequence
                                    it[serviceName] = this::class.simpleName ?: "Unknown"
                                    it[timestamp] = Clock.System.now()
                                }
                                val result = !this@idempotency
                                ApiResponse.Success(result)
                            } catch (e: Throwable) {
                                ApiResponse.Error(e.message ?: "Error")
                            }
                        }
                    }
                }
            )

        }

}