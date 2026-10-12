package com.mukhda.domain

/**
 * Marker for typed errors. Named `AppError` so it doesn't shadow `kotlin.Error`.
 */
interface AppError

/**
 * Typed success/failure result (android-error-handling skill). Named `Outcome`
 * so it doesn't clash with `kotlin.Result`.
 */
sealed interface Outcome<out D, out E : AppError> {
    data class Success<out D>(val data: D) : Outcome<D, Nothing>
    data class Error<out E : AppError>(val error: E) : Outcome<Nothing, E>
}

typealias EmptyOutcome<E> = Outcome<Unit, E>

inline fun <T, E : AppError, R> Outcome<T, E>.map(map: (T) -> R): Outcome<R, E> = when (this) {
    is Outcome.Error -> Outcome.Error(error)
    is Outcome.Success -> Outcome.Success(map(data))
}

inline fun <T, E : AppError> Outcome<T, E>.onSuccess(action: (T) -> Unit): Outcome<T, E> {
    if (this is Outcome.Success) action(data)
    return this
}

inline fun <T, E : AppError> Outcome<T, E>.onFailure(action: (E) -> Unit): Outcome<T, E> {
    if (this is Outcome.Error) action(error)
    return this
}

fun <T, E : AppError> Outcome<T, E>.asEmptyOutcome(): EmptyOutcome<E> = map { }
