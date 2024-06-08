package io.kauth.service.auth

import io.kauth.client.eventStore.EventStoreClientPersistenceSubs
import io.kauth.client.eventStore.subscribeToStream
import io.kauth.monad.stack.AppStack
import io.kauth.monad.stack.getService
import io.kauth.util.Async
import io.kauth.util.not

object AuthEventHandler {
    val eventHandler = AppStack.Do {
        val streamName = "\$ce-user"
        val consumerGroup = "some-consumer-group-3"
        val client = !getService<EventStoreClientPersistenceSubs>()
        !client.subscribeToStream<Auth.UserEvent>(
            streamName,
            consumerGroup
        ) { event ->
            Async {
            }
        }
    }
}