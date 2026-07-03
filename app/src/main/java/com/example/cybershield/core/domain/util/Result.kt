package com.example.cybershield.core.domain.util

import kotlinx.coroutines.CancellationException

sealed class Result<out T> {
    data class Success<out T>(
        val data: T,
    ) : Result<T>()

    data class Error(
        val exception: Exception,
        val isStale: Boolean = false,
    ) : Result<Nothing>()

    data object Loading : Result<Nothing>()
}

// Extension helpers
fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) action(data)
    return this
}

fun <T> Result<T>.onError(action: (Exception) -> Unit): Result<T> {
    if (this is Result.Error) action(exception)
    return this
}

val <T> Result<T>.dataOrNull: T?
    get() = (this as? Result.Success)?.data

/** Transforms the success value, passing Error/Loading through unchanged. */
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> =
    when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Error -> this
        Result.Loading -> Result.Loading
    }

/**
 * Runs [block], which must itself produce a [Result].
 *
 * Unlike a bare `catch (e: Exception)`, this rethrows [CancellationException]
 * instead of swallowing it, so cancelling the coroutine (e.g. leaving a screen)
 * still propagates correctly instead of being reported as a [Result.Error].
 */
inline fun <T> resultOf(block: () -> Result<T>): Result<T> =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.Error(e)
    }