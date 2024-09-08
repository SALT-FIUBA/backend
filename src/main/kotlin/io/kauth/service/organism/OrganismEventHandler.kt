package io.kauth.service.organism

import io.kauth.client.eventStore.EventStoreClientPersistenceSubs
import io.kauth.client.eventStore.subscribeToStream
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.getService
import io.kauth.service.auth.Auth
import io.kauth.service.auth.AuthService
import io.kauth.util.Async
import io.kauth.util.not
import java.util.UUID

object OrganismEventHandler {

    val eventHandler = AppStack.Do {
        val streamName = "\$ce-user"
        val consumerGroup = "organism-users"
        val client = !getService<EventStoreClientPersistenceSubs>()
        !client.subscribeToStream<Auth.UserEvent>(
            streamName,
            consumerGroup
        ) { event ->
            Async {

                val value = event.value

                if (value !is Auth.UserEvent.UserCreated) {
                    return@Async Unit
                }

                val userId = event.retrieveId(AuthService.USERS_STREAM_NAME)?.let { UUID.fromString(it) }

                if (userId == null) {
                    return@Async Unit
                }

                val roles = value.user.roles.firstOrNull()

                val organismRole = roles?.let { Organism.OrganismRole.formString(it) }

                if (organismRole == null) {
                    return@Async Unit
                }

                !OrganismApi.Command.addUser(
                    organism = organismRole.organismId,
                    role = organismRole.role,
                    user = userId,
                    createdBy = null
                )

            }
        }
    }

}