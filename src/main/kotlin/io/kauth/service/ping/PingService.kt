package io.kauth.service.ping

import io.kauth.monad.stack.AppStack
import io.kauth.service.AppService

object PingService : AppService {

    override val start = AppStack.Do {
        !Rest.api
    }

}
