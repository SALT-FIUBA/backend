package io.kauth.service.publisher

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.abstractions.result.Output
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.authStackJwt
import io.kauth.monad.stack.authStackLog
import io.kauth.monad.stack.getService
import io.kauth.service.organism.Organism
import io.kauth.service.organism.OrganismService
import io.kauth.service.reservation.ReservationApi
import io.kauth.util.not
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.encodeToJsonElement
import java.util.*


object PublisherApi {

    fun readState(id: UUID) = AppStack.Do {
        val authService = !getService<PublisherService.Interface>()
        !authService.query.readState(id)
    }

    inline fun <reified T> publish(
        message: T,
        resource: String,
        channel: Publisher.Channel,
    ) = AppStack.Do {

        val log = !authStackLog

        val service = !getService<PublisherService.Interface>()

        log.info("Publish message $message")

        val id = UUID.randomUUID()

        !service.command
            .handle(id)
            .throwOnFailureHandler(
                Publisher.Command.Publish(
                    channel = channel,
                    resource = resource,
                    data = serialization.encodeToJsonElement(message)
                ),
                UUID.randomUUID()
            )

        id

    }

    fun result(
        idempotence: UUID,
        id: UUID,
        result: String
    ) = AppStack.Do {

        val log = !authStackLog

        val service = !getService<PublisherService.Interface>()

        log.info("Publish result for $id is $result")

        !service.command
            .handle(id)
            .throwOnFailureHandler(
                Publisher.Command.PublishResult(
                    result = result
                ),
                idempotence
            )

        id

    }


}