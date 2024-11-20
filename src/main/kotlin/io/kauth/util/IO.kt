package io.kauth.util


typealias IO<T> = () -> T
fun <T>IO(block: () -> T) = block
operator fun <T> IO<T>.not() = this()
fun <I,O> IO<I>.map(f: (I) -> O) = IO {
    f(this())
}
