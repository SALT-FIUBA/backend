package io.kauth.service.publisher

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.abstractions.result.AppResult
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.authStackLog
import io.kauth.monad.stack.getService
import io.kauth.util.not
import kotlinx.serialization.json.encodeToJsonElement
import java.util.*


object PublisherApi {

    fun readState(id: UUID) = AppStack.Do {
        val authService = !getService<PublisherService.Interface>()
        !authService.query.readState(id)
    }

    inline fun <reified T> publish(
        messageId: UUID,
        message: T,
        resource: String,
        channel: Publisher.Channel,
    ) = AppStack.Do {

        val log = !authStackLog

        val service = !getService<PublisherService.Interface>()

        log.info("Publish message $message")

        !service.command
            .handle(messageId)
            .throwOnFailureHandler(
                Publisher.Command.Publish(
                    channel = channel,
                    resource = resource,
                    data = serialization.encodeToJsonElement(message)
                ),
            )

    }

    fun result(
        id: UUID,
        result: AppResult<String>
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
            )

        id

    }


}