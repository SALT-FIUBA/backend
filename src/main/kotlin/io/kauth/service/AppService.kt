package io.kauth.service

import io.kauth.monad.stack.AuthStack

interface AppService {
    val name: String
    val start: AuthStack<*>
}