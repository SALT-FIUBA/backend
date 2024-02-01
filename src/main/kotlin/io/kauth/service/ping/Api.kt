package io.kauth.service.ping

import io.kauth.monad.stack.AuthStack


object Api {

    val ping = AuthStack.Do {
        "pong"
    }

}
