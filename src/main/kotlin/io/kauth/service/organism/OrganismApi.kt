package io.kauth.service.organism

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.monad.stack.*
import io.kauth.service.reservation.ReservationApi
import io.kauth.util.not
import kotlinx.datetime.Clock
import java.util.*

object OrganismApi {

    fun readState(id: UUID) = AppStack.Do {
        val authService = !getService<OrganismService.Interface>()
        !authService.query.readState(id)
    }

    fun create(
        tag: String,
        name: String,
        description: String,
    ) = AppStack.Do {

        val log = !authStackLog
        val jwt = !authStackJwt

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
                UUID.randomUUID()
            )

        id

    }

}

