package io.kauth.service.ping

import io.kauth.monad.stack.AuthStack
import io.kauth.service.AppService

object PingService : AppService {

    override val name: String
        get() = "PingService"

    override val start = AuthStack.Do {
        !Rest.api
    }

}
