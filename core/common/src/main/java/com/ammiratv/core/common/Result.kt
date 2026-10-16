package com.ammiratv.core.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * A generic wrapper for results that can be Success, Error, or Loading.
 * Used throughout the app to handle async operations consistently.
 */
sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Error(val exception: Throwable? = null, val message: String? = null) : Result<Nothing>
    data object Loading : Result<Nothing>
}

/**
 * Extension to convert a Flow<T> into a Flow<Result<T>>,
 * automatically wrapping emissions in Success and catching errors.
 */
fun <T> Flow<T>.asResult(): Flow<Result<T>> {
    return this
        .map<T, Result<T>> { Result.Success(it) }
        .catch { emit(Result.Error(it)) }
}
