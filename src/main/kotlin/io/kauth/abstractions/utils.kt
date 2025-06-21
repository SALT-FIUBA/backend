package io.kauth.abstractions

import io.kauth.util.Async
import io.kauth.util.not
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

fun repeatForever(action: Async<*>): Async<Nothing> = Async {
    while (true) {
        try {
            !action
        } catch (e: CancellationException) {
            println("Cancelation exception error")
            throw e
        } catch (e: Throwable) {
            println("ERROR ${e.message} retrying in 10 seconds")
            delay(10.seconds)
        }
    }
    error("Unreachable")
}

fun <T> forever(action: Async<T>): Async<T> = Async {
    while (true) {
        try {
            return@Async !action
        } catch (e: CancellationException) {
            println("Cancelation exception error")
            throw e
        } catch (e: Throwable) {
            println("ERROR ${e.message} retrying in 10 seconds")
            delay(10.seconds)
        }
    }
    error("Unreachable")
}

val <T> Async<T>.forever: Async<T> get() = forever(this)
