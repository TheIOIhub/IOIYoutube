package com.ioi.youtube.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.data.local.CachedVideoDao
import com.ioi.youtube.data.local.CachedVideoEntity
import com.ioi.youtube.data.local.IOIDatabase
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.FeedTopic
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
class FeedRepositoryImplTest {

    private lateinit var database: IOIDatabase
    private lateinit var videoDao: CachedVideoDao
    private lateinit var remote: FakeRemoteDataSource
    private lateinit var repository: FeedRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, IOIDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        videoDao = database.cachedVideoDao()
        remote = FakeRemoteDataSource()
        repository = FeedRepositoryImpl(remote, videoDao)
    }

    @After
    fun tearDown() = database.close()

    private fun raw(id: String, views: Long = 10, uploadedAt: Long? = null) = RawVideoSummary(
        id = id,
        title = id,
        uploaderName = "Ch",
        thumbnailUrl = "u",
        durationSeconds = 1,
        viewCount = views,
        uploadDate = null,
        uploadedAt = uploadedAt,
    )

    private suspend fun seedCache(vararg ids: String) {
        videoDao.replaceSource(
            FeedRepositoryImpl.SOURCE_HOME,
            ids.map { id ->
                CachedVideoEntity(
                    id = id,
                    sourceKey = FeedRepositoryImpl.SOURCE_HOME,
                    title = id,
                    uploaderName = "Ch",
                    thumbnailUrl = "u",
                    durationSeconds = 1,
                    viewCount = 10,
                    uploadDate = null,
                    cachedAt = 0,
                )
            },
        )
    }

    @Test
    fun `all with relevance emits cache then fresh and writes cache`() = runTest {
        seedCache("cached1")
        remote.feed = listOf(raw("fresh1"))

        repository.getFeedPage(FeedTopic.ALL, FeedSort.RELEVANCE).test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)

            val cached = awaitItem() as UiState.Success
            assertThat(cached.fromCache).isTrue()
            assertThat(cached.data.videos.map { it.id }).containsExactly("cached1")

            val fresh = awaitItem() as UiState.Success
            assertThat(fresh.fromCache).isFalse()
            assertThat(fresh.data.videos.map { it.id }).containsExactly("fresh1")
            awaitComplete()
        }

        assertThat(videoDao.getBySource(FeedRepositoryImpl.SOURCE_HOME).map { it.id })
            .containsExactly("fresh1")
    }

    @Test
    fun `topic feed ignores and does not write the cache`() = runTest {
        seedCache("cached1")
        remote.feedByTopic[FeedTopic.GAMES] = listOf(raw("game1"))

        repository.getFeedPage(FeedTopic.GAMES, FeedSort.RELEVANCE).test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val fresh = awaitItem() as UiState.Success
            assertThat(fresh.data.videos.map { it.id }).containsExactly("game1")
            awaitComplete()
        }

        assertThat(videoDao.getBySource(FeedRepositoryImpl.SOURCE_HOME).map { it.id })
            .containsExactly("cached1")
    }

    @Test
    fun `non relevance sort ignores and does not write the cache`() = runTest {
        seedCache("cached1")
        remote.feed = listOf(raw("fresh1", views = 500))

        repository.getFeedPage(FeedTopic.ALL, FeedSort.POPULAR).test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val fresh = awaitItem() as UiState.Success
            assertThat(fresh.fromCache).isFalse()
            assertThat(fresh.data.videos.map { it.id }).containsExactly("fresh1")
            awaitComplete()
        }

        assertThat(videoDao.getBySource(FeedRepositoryImpl.SOURCE_HOME).map { it.id })
            .containsExactly("cached1")
    }

    @Test
    fun `newest sort is applied to fresh videos`() = runTest {
        remote.feed = listOf(raw("old", uploadedAt = 100), raw("new", uploadedAt = 300))

        repository.getFeedPage(FeedTopic.ALL, FeedSort.NEWEST).test {
            awaitItem()
            val fresh = awaitItem() as UiState.Success
            assertThat(fresh.data.videos.map { it.id }).containsExactly("new", "old").inOrder()
            awaitComplete()
        }
    }

    @Test
    fun `error is emitted only when the cache is empty`() = runTest {
        remote.feedError = IOException("offline")

        repository.getFeedPage(FeedTopic.ALL, FeedSort.RELEVANCE).test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val error = awaitItem() as UiState.Error
            assertThat(error.message).isEqualTo(FeedRepositoryImpl.ERROR_LOAD_FEED)
            awaitComplete()
        }
    }

    @Test
    fun `feed shorts pass exclude ids and map to domain`() = runTest {
        remote.feedShorts = listOf(raw("short1"))

        repository.getFeedShorts(count = 4, excludeIds = setOf("seen1")).test {
            awaitItem()
            val success = awaitItem() as UiState.Success
            assertThat(success.data.map { it.id }).containsExactly("short1")
            awaitComplete()
        }

        assertThat(remote.lastShortExcludeIds).containsExactly("seen1")
    }

    @Test
    fun `feed shorts error maps to shorts message`() = runTest {
        remote.feedShortsError = IOException("offline")

        repository.getFeedShorts(2, emptySet()).test {
            awaitItem()
            val error = awaitItem() as UiState.Error
            assertThat(error.message).isEqualTo(FeedRepositoryImpl.ERROR_LOAD_FEED_SHORTS)
            awaitComplete()
        }
    }
}
