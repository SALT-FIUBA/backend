package io.kauth.service.organism

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.exception.ApiException
import io.kauth.exception.allowIf
import io.kauth.exception.not
import io.kauth.monad.apicall.ApiCall
import io.kauth.monad.stack.*
import io.kauth.service.auth.Auth
import io.kauth.service.auth.AuthApi
import io.kauth.service.auth.AuthService
import io.kauth.service.organism.OrganismProjection.OrganismTable
import io.kauth.service.organism.OrganismProjection.toOrganismProjection
import io.kauth.service.organism.OrganismProjection.toOrganismUserInfoProjection
import io.kauth.service.reservation.ReservationApi
import io.kauth.service.salt.DeviceProjection
import io.kauth.service.salt.DeviceProjection.toDeviceProjection
import io.kauth.util.not
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.*

object OrganismApi {

    object Command {

        fun createUser(
            organism: UUID,
            role: Organism.Role,
            email: String,
            password: String,
            personalData: Auth.User.PersonalData,
        ) = ApiCall.Do {

            val jwt = jwt ?: !ApiException("UnAuth")

            val orgRole = Organism.OrganismRole(role, organism)
            val supervisorRole = Organism.OrganismRole(Organism.Role.supervisor, organism)

            !allowIf(
                "admin" in jwt.payload.roles ||
                        (supervisorRole.string in jwt.payload.roles && role != Organism.Role.supervisor)
            ) {
                "Not Allowed"
            }

            !AuthApi.register(
                email,
                password,
                personalData,
                listOf(orgRole.string)
            )

        }

        //Esto se tiene que ejecutar cada vez que se crea un usuario!!
        fun addUser(
            organism: UUID,
            role: Organism.Role,
            user: UUID,
            createdBy: UUID?
        ) = AppStack.Do {

            val orgRole = Organism.OrganismRole(role, organism)

            val service = !getService<OrganismService.Interface>()
            val auth = !getService<AuthService.Interface>()

            val userData = !auth.query.readState(user) ?: !ApiException("User does not exists")

            !allowIf(orgRole.string in userData.roles) {
                "Invalid role for user"
            }

            val userInfo = Organism.UserInfo(
                id = user,
                addedBy = createdBy,
                addedAt = Clock.System.now()
            )

            !service.command
                .handle(organism)
                .throwOnFailureHandler(
                    when(role) {
                        Organism.Role.operators -> Organism.Command.AddOperator(userInfo)
                        Organism.Role.supervisor -> Organism.Command.AddSupervisor(userInfo)
                    }
                )
            userInfo
        }

        fun create(
            tag: String,
            name: String,
            description: String,
        ) = AppStack.Do {

            val log = !authStackLog
            val jwt = !authStackJwt

            if (name.isBlank()) {
                !ApiException("Invalid name")
            }

            allowIf("admin" in jwt.payload.roles) {
                "Not authorized"
            }

            val service = !getService<OrganismService.Interface>()

            val id = !ReservationApi.takeIfNotTaken("organism-${name}") { UUID.randomUUID().toString() }

            log.info("Create organism $name")

            !service.command
                .handle(UUID.fromString(id))
                .throwOnFailureHandler(
                    Organism.Command.CreateOrganism(
                        tag = tag,
                        name = name,
                        description = description,
                        createdBy = jwt.payload.id,
                        createdAt = Clock.System.now()
                    ),
                )

            id

        }

    }

    object Query {

        fun readState(id: UUID) = AppStack.Do {
            val service = !getService<OrganismService.Interface>()
            !service.query.readState(id)
        }

        fun organism(id: UUID) = AppStack.Do {
            !appStackDbQuery {
                val data = OrganismTable.selectAll()
                    .where { OrganismTable.id eq id.toString() }
                    .map { it.toOrganismProjection }
                    .firstOrNull()
                data?.let { it ->

                    val supervisors =
                        OrganismProjection.OrganismUserInfoTable.selectAll()
                            .where {
                                (OrganismProjection.OrganismUserInfoTable.organismId eq id.toString()) and
                                        (OrganismProjection.OrganismUserInfoTable.role eq Organism.Role.supervisor.name)
                            }
                            .map { it.toOrganismUserInfoProjection }

                    val operators =
                        OrganismProjection.OrganismUserInfoTable.selectAll()
                            .where {
                                (OrganismProjection.OrganismUserInfoTable.organismId eq id.toString()) and
                                        (OrganismProjection.OrganismUserInfoTable.role eq Organism.Role.operators.name)
                            }
                            .map { it.toOrganismUserInfoProjection}

                    val devices = DeviceProjection.DeviceTable.selectAll()
                        .where {
                            DeviceProjection.DeviceTable.organismId eq id.toString()
                        }
                        .map { it.toDeviceProjection }

                    OrganismProjection.AggregatedProjection(
                        it,
                        supervisors,
                        operators,
                        devices
                    )

                }
            }
        }

        fun organismsList() = AppStack.Do {
            !appStackDbQuery {
                OrganismTable.selectAll()
                    .map { it.toOrganismProjection }
            }
        }

        fun supervisorList(organism: UUID) = AppStack.Do {
            !appStackDbQuery {
                OrganismProjection.OrganismUserInfoTable.selectAll()
                    .where {
                        (OrganismProjection.OrganismUserInfoTable.organismId eq organism.toString()) and
                                (OrganismProjection.OrganismUserInfoTable.role eq Organism.Role.supervisor.name)
                    }
                    .map { it.toOrganismUserInfoProjection}
            }
        }

        fun operatorList(organism: UUID) = AppStack.Do {
            !appStackDbQuery {
                OrganismProjection.OrganismUserInfoTable.selectAll()
                    .where {
                        (OrganismProjection.OrganismUserInfoTable.organismId eq organism.toString()) and
                                (OrganismProjection.OrganismUserInfoTable.role eq Organism.Role.operators.name)
                    }
                    .map { it.toOrganismUserInfoProjection}
            }
        }

    }

}

