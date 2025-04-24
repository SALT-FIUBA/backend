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

}