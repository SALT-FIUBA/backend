package io.kauth.util

import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

typealias Async<T> = suspend () -> T
fun <T> Async(block: suspend () -> T) = block
fun <T> CompletableFuture<T>.toAsync(): Async<T> = Async { await() }
suspend operator fun <T> Async<T>.not() = this()
val <T> Async<T>.io get(): IO<T> = IO {
    runBlocking { !this@io }
}

fun <T> List<Async<T>>.sequential(): Async<List<T>> = Async { map { !it } }

fun <T> List<Async<T>>.parallel(): Async<List<T>> = Async {
    coroutineScope {
        this@parallel
            .map { value-> async { !value} }
            .map { it.await() }
    }
}
