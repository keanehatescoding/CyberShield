package com.example.cybershield.core.domain.util

sealed class Result<out T> {
    data class  Success<out T>(val data: T)        : Result<T>()
    data class  Error(
        val exception: Exception,
        val isStale: Boolean = false
    ) : Result<Nothing>()
    data object Loading                               : Result<Nothing>()
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