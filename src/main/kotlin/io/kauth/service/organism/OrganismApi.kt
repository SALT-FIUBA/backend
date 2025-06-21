package io.kauth.service.organism

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.exception.ApiException
import io.kauth.exception.allowIf
import io.kauth.exception.not
import io.kauth.monad.apicall.ApiCall
import io.kauth.monad.apicall.apiCallGetService
import io.kauth.monad.apicall.apiCallLog
import io.kauth.monad.apicall.toApiCall
import io.kauth.monad.stack.*
import io.kauth.service.auth.Auth
import io.kauth.service.auth.AuthApi
import io.kauth.service.organism.OrganismProjection.OrganismTable
import io.kauth.service.organism.OrganismProjection.toOrganismProjection
import io.kauth.service.organism.OrganismProjection.toOrganismUserInfoProjection
import io.kauth.service.reservation.ReservationApi
import io.kauth.service.salt.DeviceProjection
import io.kauth.service.salt.DeviceProjection.toDeviceProjection
import io.kauth.util.not
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.*

object OrganismApi {

    object Command {

        fun addUser(
            email: String,
            organism: UUID,
            role: List<Organism.Role>,
        ) = ApiCall.Do {
            //val jwt = jwt ?: !ApiException("UnAuth")
            val roles = role.map { Organism.OrganismRole(it, organism).string }
            val writeRole = Organism.OrganismRole(Organism.Role.write, organism)
            /*
            !allowIf("admin" in jwt.payload.roles || (writeRole.string in jwt.payload.roles)) {
                "Not Allowed"
            }
             */
            !AuthApi.addRoles(
                email,
                roles
            )
        }

        fun create(
            tag: String,
            name: String,
            description: String,
        ) = ApiCall.Do {

            val log = !apiCallLog
            val jwt = jwt ?: !ApiException("UnAuth")

            if (name.isBlank()) {
                !ApiException("Invalid name")
            }

            allowIf("admin" in jwt.payload.roles) {
                "Not authorized"
            }

            val service = !apiCallGetService<OrganismService.Interface>()

            val id = !ReservationApi.takeIfNotTaken("organism-${name}") { UUID.randomUUID().toString() }.toApiCall()

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
                ).toApiCall()

            id

        }

        fun delete(
            id: UUID
        ) = ApiCall.Do {
            val log = !apiCallLog
            val jwt = jwt ?: !ApiException("UnAuth")
            allowIf("admin" in jwt.payload.roles) { "Not authorized" }
            val service = !apiCallGetService<OrganismService.Interface>()
            log.info("Delete organism $id")
            !service.command
                .handle(id)
                .throwOnFailureHandler(
                    Organism.Command.DeleteOrganism(
                        deletedBy = jwt.payload.id,
                        deletedAt = Clock.System.now()
                    ),
                ).toApiCall()
        }

        fun edit(
            id: UUID,
            tag: String? = null,
            name: String? = null,
            description: String? = null
        ) = ApiCall.Do {
            val log = !apiCallLog
            val jwt = jwt ?: !ApiException("UnAuth")
            allowIf("admin" in jwt.payload.roles) { "Not authorized" }
            val service = !apiCallGetService<OrganismService.Interface>()
            log.info("Edit organism $id")
            !service.command
                .handle(id)
                .throwOnFailureHandler(
                    Organism.Command.EditOrganism(
                        tag = tag,
                        name = name,
                        description = description,
                        editedBy = jwt.payload.id,
                        editedAt = Clock.System.now()
                    ),
                ).toApiCall()
        }

    }

    object Query {

        fun readState(id: UUID) = AppStack.Do {
            val service = !getService<OrganismService.Interface>()
            !service.query.readState(id)
        }

        fun organism(id: UUID) = AppStack.Do {
            !appStackDbQueryNeon {
                val data = OrganismTable.selectAll()
                    .where { (OrganismTable.id eq id.toString()) and (OrganismTable.deleted eq false) }
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
            !appStackDbQueryNeon {
                OrganismTable.selectAll()
                    .where { OrganismTable.deleted eq false }
                    .map { it.toOrganismProjection }
            }
        }

        fun supervisorList(organism: UUID) = AppStack.Do {
            !appStackDbQueryNeon {
                OrganismProjection.OrganismUserInfoTable.selectAll()
                    .where {
                        (OrganismProjection.OrganismUserInfoTable.organismId eq organism.toString()) and
                                (OrganismProjection.OrganismUserInfoTable.role eq Organism.Role.supervisor.name)
                    }
                    .map { it.toOrganismUserInfoProjection}
            }
        }

        fun operatorList(organism: UUID) = AppStack.Do {
            !appStackDbQueryNeon {
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
