package io.kauth.service

import io.kauth.monad.stack.AuthStack

interface AppService {
    val name: String get() = this::class.simpleName ?: "Unknown"
    val start: AuthStack<*>
}