package io.kauth.abstractions

import io.kauth.util.Async
import io.kauth.util.not
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

fun forever(action: Async<Any?>): Async<Unit> = Async {
    while (true) {
        try {
            !action
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            delay(10.seconds)
        }
    }
}