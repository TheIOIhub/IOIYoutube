package com.ioi.youtube.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.data.local.CachedVideoDao
import com.ioi.youtube.data.local.IOIDatabase
import com.ioi.youtube.data.mapper.toDomain
import com.ioi.youtube.data.mapper.toEntity
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.testutil.FakeRemoteDataSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchRepositoryImplTest {

    private lateinit var database: IOIDatabase
    private lateinit var videoDao: CachedVideoDao
    private lateinit var remote: FakeRemoteDataSource
    private lateinit var repository: SearchRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, IOIDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        videoDao = database.cachedVideoDao()
        remote = FakeRemoteDataSource()
        repository = SearchRepositoryImpl(remote, videoDao)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `search emits fresh results`() = runTest {
        remote.searchResults = listOf(rawVideo("res1"))

        repository.search("kotlin").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val success = awaitItem() as UiState.Success
            assertThat(success.data).isEqualTo(SearchResults("kotlin", success.data.videos))
            assertThat(success.data.videos.map { it.id }).containsExactly("res1")
            awaitComplete()
        }

        assertThat(videoDao.getBySource(SearchRepositoryImpl.searchKey("kotlin")).map { it.id })
            .containsExactly("res1")
    }

    @Test
    fun `blank query returns empty results without network call`() = runTest {
        repository.search("   ").test {
            val success = awaitItem() as UiState.Success
            assertThat(success.data.videos).isEmpty()
            assertThat(success.data.query).isEqualTo("")
            awaitComplete()
        }

        assertThat(remote.searchCalls).isEqualTo(0)
    }

    @Test
    fun `search emits cached results when network fails`() = runTest {
        remote.searchError = IOException("offline")
        val now = System.currentTimeMillis()
        videoDao.insertAll(
            listOf(
                rawVideo("cached1").toDomain().toEntity(
                    SearchRepositoryImpl.searchKey("kotlin"),
                    now,
                ),
            ),
        )

        repository.search("kotlin").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val cached = awaitItem() as UiState.Success
            assertThat(cached.fromCache).isTrue()
            assertThat(cached.data.videos.map { it.id }).containsExactly("cached1")
            awaitComplete()
        }
    }

    @Test
    fun `search emits error when network fails and cache empty`() = runTest {
        remote.searchError = IOException("offline")

        repository.search("kotlin").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val error = awaitItem() as UiState.Error
            assertThat(error.message).isEqualTo(SearchRepositoryImpl.ERROR_LOAD_SEARCH)
            awaitComplete()
        }
    }

    private fun rawVideo(id: String) = RawVideoSummary(
        id = id,
        title = "Video $id",
        uploaderName = "Channel",
        thumbnailUrl = "https://example.com/$id.jpg",
        durationSeconds = 60,
        viewCount = 100,
        uploadDate = null,
    )
}
