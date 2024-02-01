package io.kauth.service.auth

import io.kauth.client.eventStore.EventStoreClientPersistenceSubs
import io.kauth.client.eventStore.subscribeToStream
import io.kauth.monad.stack.AuthStack
import io.kauth.monad.stack.getService
import io.kauth.util.Async
import io.kauth.util.not

object AuthProjection {

    val eventHandler = AuthStack.Do {

        val streamName = "\$ce-user"
        val consumerGroup = "some-consumer-group"

        /* TODO: Event Handler
            + Se encarga de guardar en la db los eventos
            + Se encarga de mandar eventos a una queue de integracion
         */

        val client = !getService<EventStoreClientPersistenceSubs>()

        //retryForEver
        !client.subscribeToStream<Auth.UserEvent>(
            streamName,
            consumerGroup
        ) { event ->
            Async {
                println(event)
                //Este lee eventos tambien.. puede leer snapshot + evento actual
                //Update db
                //Integration messages
                //Idempotence
            }
        }

    }

}