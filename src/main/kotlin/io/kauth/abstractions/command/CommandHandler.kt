package io.kauth.abstractions.command

import io.kauth.abstractions.result.Output
import io.kauth.abstractions.result.throwOnFailure
import io.kauth.monad.stack.AppStack
import java.util.UUID

//EventStoreHandler te obliga a pasar el event id por si queres usar la idempotencia
typealias CommandHandler<C, O> = (command: C, eventId: UUID) -> AppStack<O>
typealias CommandResultHandler<C> = CommandHandler<C, Output>
typealias CommandThrowOnErrorHandler<C> = CommandHandler<C, Unit>

val <C> CommandResultHandler<C>.throwOnFailureHandler get(): CommandThrowOnErrorHandler<C> = { command, eventId ->
    AppStack.Do {
        val result = !this@throwOnFailureHandler.invoke(command, eventId)
        !result.throwOnFailure
    }
}