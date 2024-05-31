package io.kauth.util

data class AppLogger(
    val other: io.ktor.util.logging.Logger
) : io.ktor.util.logging.Logger by other {

    val DEBUG: StackTraceElement
        get() {
            val throwable = Throwable()
            val current = throwable.stackTrace[2]
            return current
        }

    override fun info(p0: String?) {
        other.info("[${DEBUG.className}] ${p0}")
    }

}
