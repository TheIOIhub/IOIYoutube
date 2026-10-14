package com.ioi.youtube.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UiStateTest {

    @Test
    fun `map transforms success data and keeps fromCache`() {
        val state: UiState<Int> = UiState.Success(2, fromCache = true)

        val mapped = state.map { it * 10 }

        assertEquals(UiState.Success(20, fromCache = true), mapped)
    }

    @Test
    fun `map passes through error and loading`() {
        val error: UiState<Int> = UiState.Error("boom", IllegalStateException("x"))

        assertEquals(error, error.map { it + 1 })
        assertEquals(UiState.Loading, (UiState.Loading as UiState<Int>).map { it + 1 })
    }
}
