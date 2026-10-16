package com.ammiratv.core.common

import javax.inject.Qualifier

/**
 * Qualifiers for Kotlin Coroutine dispatchers, injected via Hilt.
 * This allows swapping dispatchers in tests for deterministic behavior.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: AmmiraTvDispatchers)

/**
 * Marks the process-wide CoroutineScope. Use it for work that must outlive a screen
 * (e.g. EPG downloads started right before the user leaves the "add source" page).
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

enum class AmmiraTvDispatchers {
    IO,
    DEFAULT,
    MAIN
}
