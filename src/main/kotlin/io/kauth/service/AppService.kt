package io.kauth.service

import io.kauth.monad.stack.AppStack

interface AppService {
    val name: String get() = this::class.simpleName ?: "Unknown"
    val start: AppStack<*>
}

fun runServices(vararg services: AppService): AppStack<*> = AppStack.Do {
    services.forEach {
        !it.start
    }
}