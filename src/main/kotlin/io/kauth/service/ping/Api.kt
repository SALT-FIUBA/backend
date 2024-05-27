package io.kauth.service.ping

import io.kauth.monad.stack.AppStack


object Api {

    val ping = AppStack.Do {
        "pong"
    }

}
