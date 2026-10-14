package com.ioi.youtube.domain.model

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Success<T>(
        val data: T,
        val fromCache: Boolean = false,
    ) : UiState<T>

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : UiState<Nothing>
}

fun <T, R> UiState<T>.map(transform: (T) -> R): UiState<R> = when (this) {
    is UiState.Success -> UiState.Success(transform(data), fromCache)
    is UiState.Error -> this
    UiState.Loading -> UiState.Loading
}
