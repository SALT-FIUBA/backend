package io.kauth.service.auth

import io.kauth.client.eventStore.EventStoreClientPersistenceSubs
import io.kauth.client.eventStore.subscribeToStream
import io.kauth.monad.stack.AuthStack
import io.kauth.monad.stack.authStackLog
import io.kauth.monad.stack.getService
import io.kauth.util.Async
import io.kauth.util.not
import java.util.UUID

object AuthEventHandler {

    val eventHandler = AuthStack.Do {

        val log = !authStackLog

        val streamName = "\$ce-user"
        val consumerGroup = "some-consumer-group-3"

        /* TODO: Event Handler
            + Se encarga de guardar en la db los eventos
            + Se encarga de mandar eventos a una queue de integracion
         */

        val client = !getService<EventStoreClientPersistenceSubs>()

        with(log) {
            !client.subscribeToStream<Auth.UserEvent>(
                streamName,
                consumerGroup
            ) { event ->
                Async {

                    val userId = event.retrieveId("user") ?: return@Async

                    val userUuid = UUID.fromString(userId)

                    val state = !AuthApi.readState(userUuid)

                    log.info(event.id.toString()) //esto te da idempotence
                    log.info(event.value.toString())
                    log.info(state?.toString())

                }
            }
        }
    }

}