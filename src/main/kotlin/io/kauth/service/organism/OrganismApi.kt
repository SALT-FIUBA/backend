package io.kauth.service.organism

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.exception.ApiException
import io.kauth.exception.allowIf
import io.kauth.exception.not
import io.kauth.monad.stack.*
import io.kauth.service.auth.Auth
import io.kauth.service.auth.AuthApi.appStackAuthValidateAdmin
import io.kauth.service.auth.AuthApi.appStackAuthValidateSupervisor
import io.kauth.service.auth.AuthService
import io.kauth.service.organism.OrganismProjection.OrganismTable
import io.kauth.service.organism.OrganismProjection.toOrganismProjection
import io.kauth.service.organism.OrganismProjection.toOrganismUserInfoProjection
import io.kauth.service.reservation.ReservationApi
import io.kauth.util.not
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.*

object OrganismApi {

    object Command {

        fun addUser(
            organism: UUID,
            role: Auth.Role,
            user: UUID
        ) = AppStack.Do {
            val jwt = !authStackJwt

            val service = !getService<OrganismService.Interface>()
            val organismState = !service.query.readState(organism) ?: !ApiException("Organism does not exists")

            !allowIf(
                Auth.InternalRole.admin.name in jwt.payload.roles ||
                        jwt.payload.roles.contains(Auth.Role.supervisor.name) && organismState.supervisors.any { it.id == jwt.payload.uuid }
            )

            val auth = !getService<AuthService.Interface>()
            !auth.query.readState(user) ?: !ApiException("User does not exists")

            val userInfo = Organism.UserInfo(
                id = user,
                addedBy = jwt.payload.uuid,
                addedAt = Clock.System.now()
            )

            !service.command
                .handle(organism)
                .throwOnFailureHandler(
                    when(role) {
                        Auth.Role.operators -> Organism.Command.AddOperator(userInfo)
                        Auth.Role.supervisor -> Organism.Command.AddSupervisor(userInfo)
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

            if (Auth.InternalRole.admin.name !in jwt.payload.roles) {
               !ApiException("Not Authorized")
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

        fun organismsList() = AppStack.Do {
            !appStackAuthValidateAdmin
            !appStackDbQuery {
                OrganismTable.selectAll()
                    .map { it.toOrganismProjection }
            }
        }

        fun supervisorList(organism: UUID) = AppStack.Do {
            !appStackAuthValidateAdmin
            !appStackDbQuery {
                OrganismProjection.OrganismUserInfoTable.selectAll()
                    .where {
                        (OrganismProjection.OrganismUserInfoTable.organismId eq organism.toString()) and
                                (OrganismProjection.OrganismUserInfoTable.role eq Auth.Role.supervisor.name)
                    }
                    .map { it.toOrganismUserInfoProjection}
            }
        }

        fun operatorList(organism: UUID) = AppStack.Do {
            !appStackAuthValidateAdmin
            !appStackDbQuery {
                OrganismProjection.OrganismUserInfoTable.selectAll()
                    .where {
                        (OrganismProjection.OrganismUserInfoTable.organismId eq organism.toString()) and
                                (OrganismProjection.OrganismUserInfoTable.role eq Auth.Role.operators.name)
                    }
                    .map { it.toOrganismUserInfoProjection}
            }
        }

    }

}

