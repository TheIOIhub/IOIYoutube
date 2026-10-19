package com.ioi.youtube.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.testutil.FakeRemoteDataSource
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric потрiбен, бо гiлка помилки викликає `android.util.Log`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortsRepositoryImplTest {

    private val remote = FakeRemoteDataSource()
    private val repository = ShortsRepositoryImpl(remote)

    private fun raw(id: String) = RawVideoSummary(
        id = id,
        title = "Short $id",
        uploaderName = "Channel",
        thumbnailUrl = "https://img/$id.jpg",
        durationSeconds = 0,
        viewCount = 10,
        uploadDate = null,
    )

    @Test
    fun `emits loading then mapped shorts`() = runTest {
        remote.shorts = listOf(raw("aaaaaaaaaaa"))

        repository.getShorts().test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)

            val state = awaitItem()
            assertThat(state).isInstanceOf(UiState.Success::class.java)
            assertThat((state as UiState.Success).data.map { it.id }).containsExactly("aaaaaaaaaaa")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits error with the pinned message`() = runTest {
        remote.shortsError = IOException("offline")

        repository.getShorts().test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)

            val state = awaitItem()
            assertThat(state).isInstanceOf(UiState.Error::class.java)
            assertThat((state as UiState.Error).message).isEqualTo("Не вдалося завантажити шортси")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `more shorts use the dedicated remote call`() = runTest {
        remote.moreShorts = listOf(raw("bbbbbbbbbbb"))

        repository.getMoreShorts().test {
            awaitItem()
            assertThat(awaitItem()).isInstanceOf(UiState.Success::class.java)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(remote.moreShortsCalls).isEqualTo(1)
        assertThat(remote.shortsCalls).isEqualTo(0)
    }
}
