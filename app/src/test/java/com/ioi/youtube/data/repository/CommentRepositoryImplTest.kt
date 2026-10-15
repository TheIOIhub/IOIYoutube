package com.ioi.youtube.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.data.model.RawComment
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.testutil.FakeRemoteDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

class CommentRepositoryImplTest {

    private val remote = FakeRemoteDataSource()
    private val repository = CommentRepositoryImpl(remote)

    @Test
    fun `comments emit success from remote`() = runTest {
        remote.comments = listOf(
            RawComment("c1", "Alice", null, "First!", 10, "1 day ago", true, true),
        )

        repository.getComments("vid1").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val success = awaitItem() as UiState.Success
            assertThat(success.data).hasSize(1)
            assertThat(success.data.first().authorName).isEqualTo("Alice")
            awaitComplete()
        }
    }

    @Test
    fun `comments emit error when remote fails`() = runTest {
        remote.commentsError = IOException("offline")

        repository.getComments("vid1").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val error = awaitItem() as UiState.Error
            assertThat(error.message).isEqualTo(CommentRepositoryImpl.ERROR_LOAD_COMMENTS)
            awaitComplete()
        }
    }

    @Test
    fun `empty comments from disabled section emit success with empty list`() = runTest {
        remote.comments = emptyList()

        repository.getComments("vid1").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val success = awaitItem() as UiState.Success
            assertThat(success.data).isEmpty()
            awaitComplete()
        }
    }
}
