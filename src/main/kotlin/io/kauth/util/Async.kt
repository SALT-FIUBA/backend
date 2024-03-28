package io.kauth.util

import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

typealias Async<T> = suspend () -> T
fun <T> Async(block: suspend () -> T) = block
fun <T> CompletableFuture<T>.toAsync(): Async<T> = Async { await() }
suspend operator fun <T> Async<T>.not() = this()
val <T> Async<T>.io get(): IO<T> = IO {
    runBlocking { !this@io }
}
